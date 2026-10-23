package com.repopilot.service.search;

import java.util.List;

/**
 * 一次检索命中：文件 + 相关性得分 + 命中的行。
 * score 只用于排序（越大越相关），不表示相似度百分比。
 */
public record SearchHit(
        long fileId,
        String path,
        String language,
        double score,
        List<LineMatch> matches
) {
}
