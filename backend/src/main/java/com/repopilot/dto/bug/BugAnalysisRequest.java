package com.repopilot.dto.bug;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bug 分析请求：标题必填，其余字段尽量提供，信息越全分析越准。 */
public record BugAnalysisRequest(
        @NotBlank(message = "问题标题不能为空")
        @Size(min = 2, max = 200, message = "标题长度必须为 2-200 字符")
        String title,
        @Size(max = 4000, message = "错误现象最长 4000 字符")
        String symptom,
        @Size(max = 20000, message = "日志/堆栈最长 20000 字符")
        String logContent,
        @Size(max = 2000, message = "预期结果最长 2000 字符")
        String expectedResult,
        @Size(max = 2000, message = "实际结果最长 2000 字符")
        String actualResult
) {
}
