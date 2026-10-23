package com.repopilot.dto.qa;

import java.time.LocalDateTime;
import java.util.List;

/** 问答记录返回结构（历史列表与详情共用）。 */
public record QaVO(
        long id,
        long repoId,
        String repoName,
        String question,
        String answer,
        List<EvidenceVO> evidence,
        String provider,
        String model,
        int durationMs,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
}
