package com.repopilot.service.ai;

import com.repopilot.common.exception.ErrorCode;
import com.repopilot.config.AppProperties;
import com.repopilot.dto.bug.BugAnalysisResult;
import com.repopilot.dto.qa.EvidenceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mock AI 模式与 Remote AI 失败场景的测试。 */
class AiProviderTest {

    private final MockAiProvider mock = new MockAiProvider();

    private static final List<EvidenceVO> EVIDENCE = List.of(
            new EvidenceVO("src/main/java/com/taskflow/service/UserService.java", 20, 24,
                    "20: public User register(String username) {\n21:     if (userMapper.findByUsername(username) != null) {",
                    "关键词检索命中"));

    @Test
    @DisplayName("Mock 问答：明确标注模拟模式并引用真实检索路径")
    void mockQaCitesRealEvidence() {
        String answer = mock.answerQuestion(AiChatContext.forQa("TaskFlow", "用户注册流程在哪里实现？", EVIDENCE));
        assertTrue(answer.contains("Mock 模拟模式"));
        assertTrue(answer.contains("UserService.java"));
        assertTrue(answer.contains("第 20 行"));
    }

    @Test
    @DisplayName("Mock 问答：没有证据时如实说明无法确定，不编造")
    void mockQaWithoutEvidenceIsHonest() {
        String answer = mock.answerQuestion(AiChatContext.forQa("TaskFlow", "支付逻辑在哪里？", List.of()));
        assertTrue(answer.contains("无法确定"));
        assertFalse(answer.contains(".java:"));
    }

    @Test
    @DisplayName("Mock Bug 分析：生成结构化报告（含排查步骤）")
    void mockBugAnalysis() {
        BugAnalysisResult result = mock.analyzeBug(AiChatContext.forBug(
                "TaskFlow", EVIDENCE, "接口 500", "java.lang.NullPointerException at TaskService.getTaskDetail(TaskService.java:52)",
                "返回任务详情", "抛出 NPE"));
        assertTrue(result.structured());
        assertNotNull(result.summary());
        assertFalse(result.troubleshootingSteps().isEmpty());
        assertFalse(result.possibleCauses().isEmpty());
        assertTrue(result.summary().contains("NullPointerException"));
    }

    @Test
    @DisplayName("Remote 模式：未配置 API Key 时快速失败并给出可操作提示")
    void remoteWithoutKeyFailsFast() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("x".repeat(40), 1),
                new AppProperties.Ai("remote", "http://127.0.0.1:59999/v1", "", "gpt-4o-mini", 2, 0),
                new AppProperties.Upload(30, 5000, 512, 30, 500),
                new AppProperties.Qa(6),
                new AppProperties.Agent(8),
                "target/test-data");
        RemoteAiProvider provider = new RemoteAiProvider(properties, new com.fasterxml.jackson.databind.ObjectMapper());
        AiProviderException e = assertThrows(AiProviderException.class,
                () -> provider.answerQuestion(AiChatContext.forQa("r", "q", EVIDENCE)));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, e.getErrorCode());
    }

    @Test
    @DisplayName("Remote 模式：服务不可达时超时失败，应用不崩溃（AI_TIMEOUT）")
    void remoteUnreachableTimesOut() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("x".repeat(40), 1),
                new AppProperties.Ai("remote", "http://127.0.0.1:59999/v1", "test-key", "gpt-4o-mini", 2, 0),
                new AppProperties.Upload(30, 5000, 512, 30, 500),
                new AppProperties.Qa(6),
                new AppProperties.Agent(8),
                "target/test-data");
        RemoteAiProvider provider = new RemoteAiProvider(properties, new com.fasterxml.jackson.databind.ObjectMapper());
        AiProviderException e = assertThrows(AiProviderException.class,
                () -> provider.chat("system", "ping", 8));
        assertEquals(ErrorCode.AI_TIMEOUT, e.getErrorCode());
    }

    @Test
    @DisplayName("Remote 模式：AI 返回非法 JSON 时降级为纯文本报告（structured=false）")
    void remoteMalformedJsonDegrades() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("x".repeat(40), 1),
                new AppProperties.Ai("remote", "http://127.0.0.1:59999/v1", "k", "m", 2, 0),
                new AppProperties.Upload(30, 5000, 512, 30, 500),
                new AppProperties.Qa(6),
                new AppProperties.Agent(8),
                "target/test-data");
        RemoteAiProvider provider = new RemoteAiProvider(properties, new com.fasterxml.jackson.databind.ObjectMapper());
        BugAnalysisResult result = provider.parseBugResult("抱歉，我无法按格式输出");
        assertFalse(result.structured());
        assertTrue(result.summary().contains("抱歉"));
    }
}
