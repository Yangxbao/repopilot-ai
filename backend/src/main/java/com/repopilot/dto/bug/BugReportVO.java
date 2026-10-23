package com.repopilot.dto.bug;

import java.time.LocalDateTime;
import java.util.List;

/** Bug 分析报告返回结构。 */
public record BugReportVO(
        long id,
        long repoId,
        String repoName,
        String title,
        String symptom,
        String logContent,
        String expectedResult,
        String actualResult,
        BugAnalysisResult report,
        String status,
        String provider,
        String model,
        int durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
