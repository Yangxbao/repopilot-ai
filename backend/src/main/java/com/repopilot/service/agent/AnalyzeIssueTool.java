package com.repopilot.service.agent;

import com.repopilot.dto.bug.BugAnalysisResult;
import com.repopilot.dto.qa.EvidenceVO;
import com.repopilot.service.ai.AiChatContext;
import com.repopilot.service.ai.AiProviderException;
import com.repopilot.service.ai.AiProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * analyzeIssue：把检索证据 + Bug 原始信息交给 AI Provider，产出结构化分析报告。
 * 这是分析流程的"汇总"步骤，参数中的 evidence 必须来自本次运行的前序工具结果。
 */
@Component
public class AnalyzeIssueTool implements AgentTool {

    public static final String NAME = "analyzeIssue";

    private final AiProviderRegistry providerRegistry;

    public AnalyzeIssueTool(AiProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "基于检索到的证据与错误信息生成结构化的 Bug 分析报告";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolRunContext ctx, Map<String, Object> params) {
        List<EvidenceVO> evidence = (List<EvidenceVO>) params.get("evidence");
        if (evidence == null) {
            evidence = List.of();
        }
        AiChatContext context = AiChatContext.forBug(
                SearchCodeTool.strParam(params, "repoName"),
                evidence,
                SearchCodeTool.strParam(params, "symptom"),
                SearchCodeTool.strParam(params, "logContent"),
                SearchCodeTool.strParam(params, "expectedResult"),
                SearchCodeTool.strParam(params, "actualResult"));
        try {
            BugAnalysisResult result = providerRegistry.active().analyzeBug(context);
            return ToolResult.ok("已生成分析报告（provider=" + providerRegistry.active().getName() + "）", result);
        } catch (AiProviderException e) {
            return ToolResult.fail("AI 分析失败：" + e.getMessage());
        }
    }
}
