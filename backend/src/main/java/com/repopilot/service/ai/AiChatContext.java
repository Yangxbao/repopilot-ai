package com.repopilot.service.ai;

import com.repopilot.dto.qa.EvidenceVO;

import java.util.List;

/**
 * 一次 AI 调用的完整上下文：问题 + 检索到的证据 + （Bug 分析时的）原始信息。
 *
 * <p>Mock 与 Remote 两种 Provider 都消费同样的结构：
 * Mock 用它直接拼接模板回答（引用真实检索结果），
 * Remote 用它渲染成提示词发给真正的模型。
 */
public record AiChatContext(
        String repoName,
        String question,
        List<EvidenceVO> evidence,
        String symptom,
        String logContent,
        String expectedResult,
        String actualResult
) {

    public static AiChatContext forQa(String repoName, String question, List<EvidenceVO> evidence) {
        return new AiChatContext(repoName, question, evidence, null, null, null, null);
    }

    public static AiChatContext forBug(String repoName, List<EvidenceVO> evidence,
                                       String symptom, String logContent,
                                       String expectedResult, String actualResult) {
        return new AiChatContext(repoName, null, evidence, symptom, logContent, expectedResult, actualResult);
    }

    /** Bug 分析场景没有 question 字段，用标题代替。 */
    public String questionOrDefault() {
        return question == null ? "未知问题" : question;
    }
}
