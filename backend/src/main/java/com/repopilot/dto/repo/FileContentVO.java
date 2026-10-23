package com.repopilot.dto.repo;

import java.util.List;

/** 文件内容返回结构：全文 + 按行数组（便于前端做行号显示）。 */
public record FileContentVO(
        String path,
        String language,
        int lineCount,
        List<String> lines,
        String content
) {
}
