package com.repopilot.service.ai;

import com.repopilot.dto.bug.BugAnalysisResult;

/**
 * AI 模型适配层接口。
 *
 * <p>这是"可替换 AI 后端"的关键抽象：
 * <ul>
 *   <li>MockAiProvider：无需 API Key 的演示实现；</li>
 *   <li>RemoteAiProvider：OpenAI 兼容 HTTP 接口（OpenAI / DeepSeek / GLM / 通义等）。</li>
 * </ul>
 * 上层（问答、Bug 分析）只依赖本接口，切换模式不影响业务代码。
 */
public interface AiProvider {

    /** 标识名：mock / remote */
    String getName();

    /** 模型名（Mock 模式返回 mock-template 之类的占位名）。 */
    String getModelName();

    /** 是否为模拟实现（前端需要如实展示"模拟模式"）。 */
    boolean isMock();

    /** 代码问答：基于证据回答问题，返回 Markdown 文本。 */
    String answerQuestion(AiChatContext context);

    /** Bug 分析：基于证据与原始信息生成结构化分析报告。 */
    BugAnalysisResult analyzeBug(AiChatContext context);

    /** 连通性测试（AI 模型配置页使用）。 */
    String ping();
}
