package com.repopilot.service.ai;

import com.repopilot.dto.bug.BugAnalysisResult;
import com.repopilot.dto.qa.EvidenceVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock AI Provider：不需要任何 API Key。
 *
 * <p>诚实声明（这一点很重要）：
 * <ul>
 *   <li>它生成的"分析文字"来自内置模板，<b>不是</b>真实的模型推理；</li>
 *   <li>但它引用的文件路径、行号、代码片段都来自真实的检索结果，
 *       因此可以完整演示"检索 → 证据 → 回答 → 记录"的业务链路；</li>
 *   <li>前端与文档都会明确标注当前处于模拟模式。</li>
 * </ul>
 */
@Component
public class MockAiProvider implements AiProvider {

    private static final String MOCK_BANNER = "> ⚠️ 当前为 **Mock 模拟模式**：回答由内置模板生成，"
            + "引用的代码位置来自真实的关键词检索结果，不代表真实模型推理。"
            + "配置 Remote 模式后可获得真正的 AI 分析。\n";

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return "mock-template";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public String answerQuestion(AiChatContext context) {
        List<EvidenceVO> evidence = context.evidence() == null ? List.of() : context.evidence();
        if (evidence.isEmpty()) {
            return MOCK_BANNER + "## 无法确定\n\n"
                    + "在代码库「" + context.repoName() + "」中没有检索到与该问题相关的代码或文档。\n\n"
                    + "**建议**：\n"
                    + "1. 换一个更具体的关键词（例如类名、方法名、接口路径）；\n"
                    + "2. 确认相关代码已经包含在导入的代码库中；\n"
                    + "3. 到「代码库详情」页面用搜索功能直接验证关键词是否存在。\n\n"
                    + "根据" + mockPolicy() + "，此时不应编造任何文件、类名或行号。";
        }

        StringBuilder sb = new StringBuilder(MOCK_BANNER);
        sb.append("## 直接回答\n\n");
        sb.append("根据对代码库「").append(context.repoName()).append("」的关键词检索，")
                .append("与问题「").append(context.question()).append("」最相关的代码位于以下位置：\n\n");

        sb.append("## 相关代码位置\n\n");
        int limit = Math.min(evidence.size(), 3);
        for (int i = 0; i < limit; i++) {
            EvidenceVO e = evidence.get(i);
            sb.append(i + 1).append(". **`").append(e.path()).append("`")
                    .append(e.startLine() != null ? " 第 " + e.startLine() + " 行起" : "").append("**\n");
            if (e.snippet() != null && !e.snippet().isBlank()) {
                sb.append("   ```\n   ").append(e.snippet().replace("\n", "\n   ")).append("\n   ```\n");
            }
        }
        if (evidence.size() > limit) {
            sb.append("\n其他相关文件：");
            for (int i = limit; i < evidence.size(); i++) {
                sb.append("`").append(evidence.get(i).path()).append("` ");
            }
            sb.append("\n");
        }

        sb.append("\n## 判断依据\n\n");
        sb.append("以上位置是关键词检索按相关性排序的前 ").append(evidence.size())
                .append(" 个命中结果，属于**直接证据**（代码中确实包含与问题匹配的内容）。\n\n");

        sb.append("## 局限与建议的验证步骤\n\n");
        sb.append("1. 打开「代码库详情」页面，核对上述文件内容是否回答了你的问题；\n");
        sb.append("2. Mock 模式不做语义推理，若关键词检索没有覆盖业务逻辑，请改用更具体的词重新提问；\n");
        sb.append("3. 如需真正的 AI 分析与原因推断，请在「AI 模型配置」页面切换到 Remote 模式。\n");
        return sb.toString();
    }

    @Override
    public BugAnalysisResult analyzeBug(AiChatContext context) {
        List<EvidenceVO> evidence = context.evidence() == null ? List.of() : context.evidence();
        String exceptionHint = extractExceptionHint(context.logContent());

        StringBuilder summary = new StringBuilder();
        summary.append("（Mock 模拟模式）针对问题「").append(context.questionOrDefault())
                .append("」");
        if (exceptionHint != null) {
            summary.append("，从日志中识别到异常类型 ").append(exceptionHint);
        }
        summary.append("。已检索到 ").append(evidence.size())
                .append(" 个可能相关的代码位置，以下分析由模板生成，请人工核对。");

        List<BugAnalysisResult.Cause> causes = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(evidence.size(), 3); i++) {
            EvidenceVO e = evidence.get(i);
            String loc = e.path() + (e.startLine() != null ? ":" + e.startLine() : "");
            causes.add(new BugAnalysisResult.Cause(
                    "【模板推测】" + e.path() + " 中包含与错误信息匹配的代码，可能是问题入口或关联逻辑。",
                    List.of(loc), i == 0 ? "MEDIUM" : "LOW"));
        }
        if (causes.isEmpty()) {
            causes.add(new BugAnalysisResult.Cause(
                    "【模板推测】未检索到直接相关的代码，可能是依赖库、环境配置或数据库层面的问题。",
                    List.of(), "LOW"));
        }

        List<BugAnalysisResult.Step> steps = new java.util.ArrayList<>();
        int priority = 1;
        if (exceptionHint != null) {
            steps.add(new BugAnalysisResult.Step(priority++, "定位异常抛出点",
                    "在「代码库详情」中搜索 " + exceptionHint + "，找到 throw 语句并阅读其触发条件。"));
        }
        if (!evidence.isEmpty()) {
            steps.add(new BugAnalysisResult.Step(priority++, "核对相关代码",
                    "按证据列表逐个打开 " + evidence.get(0).path() + " 等文件，核对输入与输出是否符合预期。"));
        }
        steps.add(new BugAnalysisResult.Step(priority++, "补充最小复现信息",
            "记录请求参数、环境版本、完整堆栈，缩小问题范围。"));
        steps.add(new BugAnalysisResult.Step(priority, "编写临时日志验证猜测",
            "在怀疑的代码路径添加日志（或使用调试断点）确认实际执行分支。"));

        List<String> openQuestions = new java.util.ArrayList<>();
        openQuestions.add("错误是否可以稳定复现？触发条件是什么？");
        if (context.expectedResult() == null || context.expectedResult().isBlank()) {
            openQuestions.add("预期行为是什么？（未填写「预期结果」字段）");
        }
        if (context.logContent() == null || context.logContent().isBlank()) {
            openQuestions.add("请提供完整异常堆栈以便更精确地定位。");
        }

        List<String> tests = new java.util.ArrayList<>();
        tests.add("针对怀疑的代码路径编写/运行单元测试，覆盖边界条件。");
        tests.add("用固定输入在测试环境手动复现，确认修改前后行为差异。");

        String conclusion = evidence.isEmpty()
                ? "当前证据不足，无法从代码层面定位原因，请补充日志与复现步骤后重试。"
                : "初步定位到 " + evidence.size() + " 处相关代码，请按排查步骤核对。Mock 模式不提供最终结论。";

        return new BugAnalysisResult(summary.toString(), causes, steps, openQuestions, tests,
                conclusion, true, getName());
    }

    @Override
    public String ping() {
        return "pong (mock)";
    }

    private static String mockPolicy() {
        return "系统约定";
    }

    private static String extractExceptionHint(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return null;
        }
        var matcher = java.util.regex.Pattern
                .compile("([A-Za-z][A-Za-z0-9_]*(?:Exception|Error))")
                .matcher(logContent);
        return matcher.find() ? matcher.group(1) : null;
    }
}
