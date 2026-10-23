package com.repopilot.util;

import com.repopilot.dto.repo.OutlineVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件结构大纲的启发式解析器。
 *
 * <p>刻意不做编译级解析（不引入 JavaParser 等依赖）：
 * 用正则识别常见结构，够用于导航与问答提示。
 * 局限：多行注解、泛型嵌套等复杂声明可能识别不准，属于已知限制（见 docs）。
 */
public final class OutlineParser {

    private OutlineParser() {
    }

    private static final Pattern JAVA_TYPE = Pattern.compile(
            "\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "^[ \\t]*(?:@\\w+(?:\\([^)]*\\))?\\s+)*"
                    + "(?:public|protected|private)\\s+[\\w<>\\[\\],.?$\\s]+?\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^;{)]*)\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern XML_TAG = Pattern.compile("<([A-Za-z_][\\w.:-]*)");

    private static final int MAX_ENTRIES = 50;

    public static OutlineVO parse(String path, String content) {
        String ext = extOf(path);
        List<OutlineVO.Entry> entries = switch (ext) {
            case "java" -> parseJava(content);
            case "md", "markdown" -> parseMarkdown(content);
            case "xml" -> parseXml(content);
            default -> parseGeneric(content);
        };
        return new OutlineVO(path, entries);
    }

    private static List<OutlineVO.Entry> parseJava(String content) {
        List<OutlineVO.Entry> entries = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && entries.size() < MAX_ENTRIES; i++) {
            String line = lines[i];
            // 去掉行内注释与字符串字面量，降低误报
            String cleaned = strip(line);
            Matcher type = JAVA_TYPE.matcher(cleaned);
            if (type.find()) {
                entries.add(new OutlineVO.Entry(i + 1, type.group(1), type.group(2)));
                continue;
            }
            Matcher method = JAVA_METHOD.matcher(cleaned);
            if (method.find()) {
                String params = method.group(2).trim();
                entries.add(new OutlineVO.Entry(i + 1, "method",
                        method.group(1) + "(" + (params.length() > 60 ? params.substring(0, 60) + "…" : params) + ")"));
            }
        }
        return entries;
    }

    private static List<OutlineVO.Entry> parseMarkdown(String content) {
        List<OutlineVO.Entry> entries = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && entries.size() < MAX_ENTRIES; i++) {
            Matcher m = MD_HEADING.matcher(strip(lines[i]));
            if (m.find()) {
                entries.add(new OutlineVO.Entry(i + 1, "h" + m.group(1).length(), m.group(2)));
            }
        }
        return entries;
    }

    private static List<OutlineVO.Entry> parseXml(String content) {
        Set<String> seen = new LinkedHashSet<>();
        List<OutlineVO.Entry> entries = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && entries.size() < MAX_ENTRIES; i++) {
            Matcher m = XML_TAG.matcher(strip(lines[i]));
            if (m.find()) {
                String tag = m.group(1);
                if (seen.add(tag)) {
                    entries.add(new OutlineVO.Entry(i + 1, "tag", tag));
                }
            }
        }
        return entries;
    }

    /** 其他语言：提取出现次数较多的"疑似定义"行（含 = 或 { 的行），仅作兜底。 */
    private static List<OutlineVO.Entry> parseGeneric(String content) {
        List<OutlineVO.Entry> entries = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && entries.size() < MAX_ENTRIES; i++) {
            String line = strip(lines[i]).trim();
            if (line.matches("^(export\\s+)?(function|def|const|let|var|class|struct|fn)\\s+\\w+.*")) {
                entries.add(new OutlineVO.Entry(i + 1, "def",
                        line.length() > 80 ? line.substring(0, 80) + "…" : line));
            }
        }
        return entries;
    }

    private static String strip(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static String extOf(String path) {
        String lower = path.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? "" : lower.substring(dot + 1);
    }
}
