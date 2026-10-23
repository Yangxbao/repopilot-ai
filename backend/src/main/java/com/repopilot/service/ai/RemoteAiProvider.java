package com.repopilot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.config.AppProperties;
import com.repopilot.dto.bug.BugAnalysisResult;
import com.repopilot.dto.qa.EvidenceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remote AI Provider：对接 OpenAI 兼容的 /chat/completions 接口。
 * 同样适用于 DeepSeek（https://api.deepseek.com/v1）、智谱 GLM、通义 DashScope 兼容模式等。
 *
 * <p>可靠性设计：
 * <ul>
 *   <li>连接超时 10s、读取超时可配置（默认 60s）；</li>
 *   <li>网络异常 / 5xx 自动重试（上限可配置），4xx 立即失败；</li>
 *   <li>所有失败都抛出 {@link AiProviderException}，由上层记录失败状态，应用不崩溃。</li>
 * </ul>
 */
@Component
public class RemoteAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteAiProvider.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RestClient client;
    private volatile String clientBaseUrl;

    public RemoteAiProvider(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "remote";
    }

    @Override
    public String getModelName() {
        return properties.ai().model();
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public String answerQuestion(AiChatContext context) {
        String system = """
                你是资深的代码库分析助手。你只能基于用户提供的"代码证据"回答问题，规则：
                1. 每一个结论都必须标注来源，格式为 [文件路径:行号]，且路径必须来自证据列表；
                2. 证据不足以回答时，明确说明"证据不足，无法确定"，绝不编造文件、类名、函数名或行号；
                3. 区分"有直接证据支撑的结论"和"推测"，推测要单独说明；
                4. 最后给出用户可以执行的验证步骤；
                5. 使用中文、Markdown 格式，简洁专业。
                """;
        String user = renderQaPrompt(context);
        return chat(system, user, 2048);
    }

    @Override
    public BugAnalysisResult analyzeBug(AiChatContext context) {
        String system = """
                你是 Bug 排查助手。基于给出的代码证据与错误信息，输出严格的 JSON（不要 markdown 代码块，不要多余文字），
                结构如下：
                {
                  "summary": "一句话概括问题",
                  "possibleCauses": [{"cause": "可能原因", "evidence": ["文件路径:行号"], "confidence": "HIGH|MEDium|LOW"}],
                  "troubleshootingSteps": [{"priority": 1, "step": "排查步骤", "detail": "具体怎么做"}],
                  "openQuestions": ["仍需确认的信息"],
                  "testSuggestions": ["推荐的测试与验证方法"],
                  "conclusion": "结论与把握程度"
                }
                要求：
                1. evidence 里只能引用证据列表中出现过的文件路径；
                2. 证据不足时宁可写"证据不足"，不得编造路径或行号；
                3. troubleshootingSteps 按优先级从高到低排序，一般 3-6 条。
                """;
        String user = renderBugPrompt(context);
        String raw = chat(system, user, 3000);
        return parseBugResult(raw);
    }

    @Override
    public String ping() {
        return chat("你是一个连通性测试端点。", "回复 pong 两个字母即可。", 8);
    }

    /** 实际的 HTTP 调用与重试逻辑。 */
    String chat(String system, String user, int maxTokens) {
        String apiKey = properties.ai().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException(ErrorCode.AI_NOT_CONFIGURED,
                    "Remote 模式未配置 API Key（环境变量 REPOPILOT_AI_API_KEY）");
        }
        RestClient restClient = ensureClient();

        Map<String, Object> body = Map.of(
                "model", properties.ai().model(),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "temperature", 0.2,
                "max_tokens", maxTokens);

        int attempts = Math.max(1, properties.ai().maxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                return extractContent(response);
            } catch (HttpStatusCodeException e) {
                // 4xx 是请求本身的问题（如 Key 无效），重试没有意义
                if (e.getStatusCode().is4xxClientError()) {
                    throw new AiProviderException(ErrorCode.AI_ERROR,
                            "AI 服务返回 " + e.getStatusCode().value() + "（请检查 API Key 与模型名）");
                }
                last = e;
            } catch (ResourceAccessException e) {
                last = e;
            } catch (AiProviderException e) {
                throw e;
            } catch (Exception e) {
                throw new AiProviderException(ErrorCode.AI_ERROR, "AI 服务响应解析失败", e);
            }
            log.warn("AI 调用失败（第 {}/{} 次）：{}", attempt, attempts, last.getMessage());
            sleep(attempt * 500L);
        }
        if (last instanceof ResourceAccessException) {
            throw new AiProviderException(ErrorCode.AI_TIMEOUT,
                    "AI 服务连接/响应超时，已重试 " + attempts + " 次");
        }
        throw new AiProviderException(ErrorCode.AI_ERROR, "AI 服务暂不可用，已重试 " + attempts + " 次");
    }

    private synchronized RestClient ensureClient() {
        String baseUrl = properties.ai().baseUrl();
        if (client == null || !baseUrl.equals(clientBaseUrl)) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(properties.ai().timeoutSeconds()));
            client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.ai().apiKey())
                    .requestFactory(factory)
                    .build();
            clientBaseUrl = baseUrl;
        }
        return client;
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            throw new AiProviderException(ErrorCode.AI_ERROR, "AI 服务返回空响应");
        }
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new AiProviderException(ErrorCode.AI_ERROR, "AI 服务响应中缺少内容");
        }
        return content.asText();
    }

    BugAnalysisResult parseBugResult(String raw) {
        String json = stripCodeFence(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            List<BugAnalysisResult.Cause> causes = new ArrayList<>();
            for (JsonNode c : node.path("possibleCauses")) {
                causes.add(new BugAnalysisResult.Cause(
                        c.path("cause").asText(""),
                        toStringList(c.path("evidence")),
                        c.path("confidence").asText("LOW").toUpperCase()));
            }
            List<BugAnalysisResult.Step> steps = new ArrayList<>();
            for (JsonNode s : node.path("troubleshootingSteps")) {
                steps.add(new BugAnalysisResult.Step(
                        s.path("priority").asInt(99),
                        s.path("step").asText(""),
                        s.path("detail").asText("")));
            }
            return new BugAnalysisResult(
                    node.path("summary").asText(""),
                    causes,
                    steps,
                    toStringList(node.path("openQuestions")),
                    toStringList(node.path("testSuggestions")),
                    node.path("conclusion").asText(""),
                    true,
                    getName());
        } catch (Exception e) {
            // 模型没有按 JSON 输出：降级为非结构化文本，仍然可见但标记 structured=false
            log.warn("AI 返回内容无法解析为结构化报告，降级为纯文本");
            return new BugAnalysisResult(raw, List.of(), List.of(), List.of(), List.of(),
                    "AI 未按约定格式返回，以上为原始输出。", false, getName());
        }
    }

    private String renderQaPrompt(AiChatContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("代码库：").append(context.repoName()).append("\n");
        sb.append("问题：").append(context.question()).append("\n\n");
        sb.append("代码证据（按相关性排序）：\n");
        appendEvidence(sb, context.evidence());
        sb.append("\n请基于以上证据回答。");
        return sb.toString();
    }

    private String renderBugPrompt(AiChatContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("代码库：").append(context.repoName()).append("\n");
        sb.append("错误现象：").append(nullSafe(context.symptom())).append("\n");
        sb.append("预期结果：").append(nullSafe(context.expectedResult())).append("\n");
        sb.append("实际结果：").append(nullSafe(context.actualResult())).append("\n");
        sb.append("日志/堆栈：\n").append(truncate(nullSafe(context.logContent()), 6000)).append("\n\n");
        sb.append("代码证据：\n");
        appendEvidence(sb, context.evidence());
        sb.append("\n请输出分析报告 JSON。");
        return sb.toString();
    }

    private void appendEvidence(StringBuilder sb, List<EvidenceVO> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            sb.append("（无）\n");
            return;
        }
        for (int i = 0; i < evidence.size(); i++) {
            EvidenceVO e = evidence.get(i);
            sb.append("[").append(i + 1).append("] ").append(e.path());
            if (e.startLine() != null) {
                sb.append(":").append(e.startLine());
                if (e.endLine() != null && !e.endLine().equals(e.startLine())) {
                    sb.append("-").append(e.endLine());
                }
            }
            sb.append("\n```\n").append(truncate(nullSafe(e.snippet()), 1200)).append("\n```\n");
        }
    }

    private static String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "（未提供）" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "\n…（已截断）" : s;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
