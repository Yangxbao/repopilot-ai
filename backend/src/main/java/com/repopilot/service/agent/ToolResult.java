package com.repopilot.service.agent;

/** 工具执行结果：summary 写入调用日志，data 供流程继续处理。 */
public record ToolResult(boolean success, String summary, Object data) {

    public static ToolResult ok(String summary, Object data) {
        return new ToolResult(true, summary, data);
    }

    public static ToolResult fail(String summary) {
        return new ToolResult(false, summary, null);
    }
}
