package com.repopilot.service.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从错误日志/堆栈中提取关键信息的纯 Java 实现（不需要 AI，结果确定可复现）。
 *
 * <p>提取内容：异常类名、Caused by 链、堆栈帧（类.方法(文件:行)）、
 * HTTP 请求路径、以及按出现频率排序的关键词。提取结果用来构造检索词。
 */
public final class ErrorInfoExtractor {

    private ErrorInfoExtractor() {
    }

    public record KeyInfo(
            List<String> exceptionTypes,
            List<String> classNames,
            List<String> fileRefs,
            List<String> httpPaths,
            List<String> keywords) {
    }

    private static final Pattern EXCEPTION = Pattern.compile("([A-Za-z][A-Za-z0-9_]*(?:Exception|Error))");
    private static final Pattern CAUSED_BY = Pattern.compile("Caused by:\\s*([\\w.]+)");
    private static final Pattern STACK_FRAME = Pattern.compile(
            "at\\s+([\\w$]+)\\.([\\w$<>]+)\\(([^)]*?\\.java):(\\d+)\\)");
    private static final Pattern FILE_REF = Pattern.compile("([\\w\\-/]+\\.java):(\\d+)");
    private static final Pattern HTTP_PATH = Pattern.compile("(?:GET|POST|PUT|DELETE|PATCH)\\s+(/[\\w./{}-]*)");
    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{3,}");

    public static KeyInfo extract(String title, String symptom, String logContent) {
        String combined = String.join("\n",
                title == null ? "" : title,
                symptom == null ? "" : symptom,
                logContent == null ? "" : logContent);

        Set<String> exceptions = new LinkedHashSet<>();
        Matcher ex = EXCEPTION.matcher(combined);
        while (ex.find()) {
            exceptions.add(ex.group(1));
        }

        Set<String> classNames = new LinkedHashSet<>();
        Matcher caused = CAUSED_BY.matcher(combined);
        while (caused.find()) {
            String simple = simpleName(caused.group(1));
            if (!simple.isBlank()) {
                classNames.add(simple);
            }
        }

        Set<String> fileRefs = new LinkedHashSet<>();
        Matcher frame = STACK_FRAME.matcher(combined);
        while (frame.find()) {
            classNames.add(frame.group(1));
            fileRefs.add(frame.group(3) + ":" + frame.group(4));
        }
        Matcher fileRef = FILE_REF.matcher(combined);
        while (fileRef.find()) {
            fileRefs.add(fileRef.group(1) + ":" + fileRef.group(2));
        }

        Set<String> httpPaths = new LinkedHashSet<>();
        Matcher http = HTTP_PATH.matcher(combined);
        while (http.find()) {
            httpPaths.add(http.group(1));
        }

        // 关键词：按出现频率排序的高频 ASCII 词（排除常见噪音）
        Map<String, Integer> freq = new LinkedHashMap<>();
        Matcher word = ASCII_WORD.matcher(combined);
        while (word.find()) {
            String w = word.group().toLowerCase(Locale.ROOT);
            if (isNoise(w)) {
                continue;
            }
            freq.merge(w, 1, Integer::sum);
        }
        List<String> keywords = freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        return new KeyInfo(new ArrayList<>(exceptions), new ArrayList<>(classNames),
                new ArrayList<>(fileRefs), new ArrayList<>(httpPaths), keywords);
    }

    /**
     * 根据提取结果构造检索词序列（按信息价值排序，供 searchCode 逐个检索）。
     * 类名价值最高（可精确命中定义与引用），其次是异常类型与文件名。
     */
    public static List<String> buildSearchQueries(KeyInfo info) {
        List<String> queries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String cls : info.classNames()) {
            if (cls.length() >= 4 && seen.add(cls)) {
                queries.add(cls);
            }
        }
        for (String ex : info.exceptionTypes()) {
            if (seen.add(ex)) {
                queries.add(ex);
            }
        }
        for (String ref : info.fileRefs()) {
            String file = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : ref;
            if (seen.add(file)) {
                queries.add(file);
            }
        }
        for (String path : info.httpPaths()) {
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            if (lastSegment.length() >= 3 && seen.add(lastSegment)) {
                queries.add(lastSegment);
            }
        }
        for (String kw : info.keywords()) {
            if (seen.add(kw)) {
                queries.add(kw);
            }
        }
        return queries;
    }

    private static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        String simple = dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
        // 排除明显的噪音（如 "java.lang.Error" 里的包名片段已经去掉，这里过滤过短词）
        return simple.length() >= 4 ? simple : "";
    }

    private static boolean isNoise(String word) {
        return NOISE_WORDS.contains(word);
    }

    private static final Set<String> NOISE_WORDS = Set.of(
            "java", "lang", "null", "true", "false", "this", "super", "public", "private",
            "static", "final", "return", "throw", "throws", "class", "void", "string",
            "intlongdouble", "object", "error", "exception", "caused", "http", "https",
            "with", "atline", "comfasterxml", "springframework", "apache", "java.lang");
}
