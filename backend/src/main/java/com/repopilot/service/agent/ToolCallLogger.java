package com.repopilot.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.entity.ToolCallLog;
import com.repopilot.mapper.ToolCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具调用日志记录器：每次调用落库（成功/失败都记录）。
 * 日志写失败不影响主流程（审计是尽力而为，业务优先）。
 */
@Component
public class ToolCallLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolCallLogger.class);

    private final ToolCallLogMapper mapper;
    private final ObjectMapper objectMapper;

    public ToolCallLogger(ToolCallLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(ToolRunContext ctx, String toolName, Object params,
                       ToolResult result, long durationMs) {
        try {
            ToolCallLog entry = new ToolCallLog();
            entry.setUserId(ctx.userId());
            entry.setRepoId(ctx.repoId());
            entry.setBizType(ctx.bizType());
            entry.setBizId(ctx.bizId());
            entry.setToolName(toolName);
            entry.setParamsJson(truncate(toJson(params), 2000));
            entry.setResultSummary(truncate(result == null ? null : result.summary(), 1000));
            entry.setSuccess(result != null && result.success());
            entry.setErrorMessage(result == null || result.success() ? null : result.summary());
            entry.setDurationMs((int) durationMs);
            mapper.insert(entry);
        } catch (Exception e) {
            log.warn("工具调用日志写入失败：{}", e.getMessage());
        }
    }

    private String toJson(Object params) {
        if (params == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return String.valueOf(params);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
