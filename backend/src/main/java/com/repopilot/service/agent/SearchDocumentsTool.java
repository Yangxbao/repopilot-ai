package com.repopilot.service.agent;

import com.repopilot.service.search.KeywordSearchService;
import com.repopilot.service.search.SearchService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * searchDocuments：在文档类文件（md / txt / sql / yml / properties 等）中检索。
 * 用于找配置说明、设计文档、建表语句等"非代码"证据。
 */
@Component
public class SearchDocumentsTool implements AgentTool {

    public static final String NAME = "searchDocuments";

    private final SearchService searchService;
    private final com.repopilot.service.RepoService repoService;

    public SearchDocumentsTool(SearchService searchService, com.repopilot.service.RepoService repoService) {
        this.searchService = searchService;
        this.repoService = repoService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "在项目文档与配置文件（Markdown/YAML/SQL/properties 等）中检索";
    }

    @Override
    public ToolResult execute(ToolRunContext ctx, Map<String, Object> params) {
        repoService.getOwned(ctx.repoId(), ctx.userId());
        String query = SearchCodeTool.strParam(params, "query");
        if (query == null || query.isBlank() || query.length() > 200) {
            return ToolResult.fail("参数 query 必须为 1-200 字符");
        }
        int topK = Math.max(1, Math.min(10, SearchCodeTool.intParam(params, "topK", 5)));
        var hits = searchService.search(ctx.repoId(), query, topK, KeywordSearchService.documentExtensions());
        return ToolResult.ok("文档检索 \"" + SearchCodeTool.truncate(query, 50) + "\" 命中 " + hits.size() + " 个文件", hits);
    }
}
