package com.repopilot.service.agent;

/**
 * 一次 Agent 运行（一次问答或一次 Bug 分析）中工具调用的上下文与预算。
 *
 * <p>预算机制：
 * <ul>
 *   <li>maxToolCalls：单次分析最多调用多少次工具（防止失控循环）；</li>
 *   <li>deadline：整个运行的最后期限，超时后不再执行新的工具调用。</li>
 * </ul>
 */
public class ToolRunContext {

    private final long userId;
    private final long repoId;
    private final String bizType;
    private final Long bizId;
    private final int maxToolCalls;
    private final long deadlineMillis;
    private int usedCalls;

    public ToolRunContext(long userId, long repoId, String bizType, Long bizId, int maxToolCalls, long deadlineMillis) {
        this.userId = userId;
        this.repoId = repoId;
        this.bizType = bizType;
        this.bizId = bizId;
        this.maxToolCalls = maxToolCalls;
        this.deadlineMillis = deadlineMillis;
    }

    public boolean tryAcquire() {
        if (System.currentTimeMillis() > deadlineMillis) {
            return false;
        }
        if (usedCalls >= maxToolCalls) {
            return false;
        }
        usedCalls++;
        return true;
    }

    public int usedCalls() {
        return usedCalls;
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public long userId() {
        return userId;
    }

    public long repoId() {
        return repoId;
    }

    public String bizType() {
        return bizType;
    }

    public Long bizId() {
        return bizId;
    }
}
