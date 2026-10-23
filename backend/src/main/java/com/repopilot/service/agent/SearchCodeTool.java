package com.repopilot.service.agent;

import com.repopilot.service.RepoService;
import com.repopilot.service.search.SearchService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * searchCode：在指定代码库中做关键词检索。
 * 参数：query（必填，1-200 字符）、topK（可选，1-10，默认 5）。
 */
@Component
public class SearchCodeTool implements AgentTool {

    public static final String NAME = "searchCode";

    private final SearchService searchService;
    private final RepoService repoService;

    public SearchCodeTool(SearchService searchService, RepoService repoService) {
        this.searchService = searchService;
        this.repoService = repoService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "在已导入的代码库中按关键词检索源码，返回文件路径、行号与片段";
    }

    @Override
    public ToolResult execute(ToolRunContext ctx, Map<String, Object> params) {
        // 所有权二次校验：即使流程层已检查过，工具层也不信任上游
        repoService.getOwned(ctx.repoId(), ctx.userId());
        String query = strParam(params, "query");
        if (query == null || query.isBlank() || query.length() > 200) {
            return ToolResult.fail("参数 query 必须为 1-200 字符");
        }
        int topK = intParam(params, "topK", 5);
        topK = Math.max(1, Math.min(10, topK));

        var hits = searchService.search(ctx.repoId(), query, topK, null);
        return ToolResult.ok("检索 \"" + truncate(query, 50) + "\" 命中 " + hits.size() + " 个文件", hits);
    }

    static String strParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
