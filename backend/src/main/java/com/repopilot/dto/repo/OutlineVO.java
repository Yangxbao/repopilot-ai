package com.repopilot.dto.repo;

import java.util.List;

/**
 * 文件结构大纲（启发式解析，非编译级精确）：
 * Java 返回 class/interface/enum/record 与方法签名；Markdown 返回标题；XML 返回标签。
 */
public record OutlineVO(String path, List<Entry> entries) {

    public record Entry(int line, String kind, String text) {
    }
}
