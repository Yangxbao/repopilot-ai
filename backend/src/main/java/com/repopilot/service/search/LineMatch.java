package com.repopilot.service.search;

import java.util.List;

/** 一行命中：行号（1 开始）与该行文本（已截断）。 */
public record LineMatch(int lineNo, String text) {
}
