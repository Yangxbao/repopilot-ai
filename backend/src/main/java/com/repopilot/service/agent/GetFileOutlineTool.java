package com.repopilot.service.agent;

import com.repopilot.dto.repo.OutlineVO;
import com.repopilot.service.RepoService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * getFileOutline：获取文件的类 / 方法 / 标题结构（启发式解析）。
 */
@Component
public class GetFileOutlineTool implements AgentTool {

    public static final String NAME = "getFileOutline";

    private final RepoService repoService;

    public GetFileOutlineTool(RepoService repoService) {
        this.repoService = repoService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "获取文件的结构大纲（Java 类/方法、Markdown 标题、XML 标签）";
    }

    @Override
    public ToolResult execute(ToolRunContext ctx, Map<String, Object> params) {
        repoService.getOwned(ctx.repoId(), ctx.userId());
        String path = SearchCodeTool.strParam(params, "path");
        if (path == null || path.isBlank()) {
            return ToolResult.fail("参数 path 不能为空");
        }
        try {
            OutlineVO outline = repoService.outline(ctx.repoId(), ctx.userId(), path);
            return ToolResult.ok("解析 " + path + " 得到 " + outline.entries().size() + " 个结构条目", outline);
        } catch (Exception e) {
            return ToolResult.fail("解析失败：" + e.getMessage());
        }
    }
}
