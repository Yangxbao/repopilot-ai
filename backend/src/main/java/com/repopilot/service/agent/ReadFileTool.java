package com.repopilot.service.agent;

import com.repopilot.dto.repo.FileContentVO;
import com.repopilot.service.RepoService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * readFile：读取已索引文件的指定行区间。
 * 参数：path（必填，必须是已索引的相对路径）、startLine / endLine（可选，1 开始）。
 * 限制：单次最多返回 200 行、每行截断 240 字符；路径只能是索引内的相对路径，
 * 无法通过该工具读取服务器上的任意文件。
 */
@Component
public class ReadFileTool implements AgentTool {

    public static final String NAME = "readFile";
    private static final int MAX_LINES = 200;

    private final RepoService repoService;

    public ReadFileTool(RepoService repoService) {
        this.repoService = repoService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "读取已索引文件的一个行区间（最多 200 行），用于查看完整上下文";
    }

    @Override
    public ToolResult execute(ToolRunContext ctx, Map<String, Object> params) {
        repoService.getOwned(ctx.repoId(), ctx.userId());
        String path = SearchCodeTool.strParam(params, "path");
        if (path == null || path.isBlank()) {
            return ToolResult.fail("参数 path 不能为空");
        }
        FileContentVO content;
        try {
            content = repoService.readFile(ctx.repoId(), ctx.userId(), path);
        } catch (Exception e) {
            return ToolResult.fail("读取失败：" + e.getMessage());
        }
        int start = Math.max(1, SearchCodeTool.intParam(params, "startLine", 1));
        int end = Math.min(content.lines().size(), SearchCodeTool.intParam(params, "endLine", start + MAX_LINES - 1));
        end = Math.max(start, Math.min(end, start + MAX_LINES - 1));

        List<String> slice = new ArrayList<>();
        for (int i = start - 1; i < end && i < content.lines().size(); i++) {
            String line = content.lines().get(i);
            slice.add(line.length() > 240 ? line.substring(0, 240) + "…" : line);
        }
        return ToolResult.ok("读取 " + content.path() + " 第 " + start + "-" + end + " 行", slice);
    }
}
