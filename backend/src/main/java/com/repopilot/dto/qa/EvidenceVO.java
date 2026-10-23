package com.repopilot.dto.qa;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 证据引用：一条回答结论所依据的代码位置。
 * 贯穿"检索 → AI 上下文 → 回答展示"全流程，JSON 化后存入 qa_record.evidence_json。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceVO(
        String path,
        Integer startLine,
        Integer endLine,
        String snippet,
        String reason
) {
}
