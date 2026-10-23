package com.repopilot.dto.bug;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Bug 分析的结构化报告。
 * Remote 模式要求 AI 按此 JSON Schema 输出；Mock 模式由模板直接构造。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BugAnalysisResult(
        String summary,
        List<Cause> possibleCauses,
        List<Step> troubleshootingSteps,
        List<String> openQuestions,
        List<String> testSuggestions,
        String conclusion,
        /** 结构化解析失败时为 true：此时 summary 保存 AI 返回的原始文本 */
        boolean structured,
        String provider
) {

    public record Cause(String cause, List<String> evidence, String confidence) {
    }

    public record Step(int priority, String step, String detail) {
    }
}
