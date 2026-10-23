package com.repopilot.service.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.entity.FileIndex;
import com.repopilot.mapper.FileIndexMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 关键词检索的 MVP 实现：对已索引的文件内容做扫描打分。
 *
 * <p>打分规则（可读性优先，够用且可解释）：
 * <ul>
 *   <li>查询词在文件内容中出现的次数（截断到 50 次）× 词权重（越长的词越具体）；</li>
 *   <li>查询词出现在文件路径中：额外 +10（文件名匹配价值高）；</li>
 *   <li>完整查询短语在内容中出现：额外 +15（整句匹配强信号）；</li>
 *   <li>每个文件最多记录 5 个命中行，用于生成证据片段。</li>
 * </ul>
 *
 * <p>已知的规模边界：每次查询会加载该代码库全部文件内容到内存打分，
 * 适合 ≤ 30MB（导入时的上限）的代码库；更大规模需要引入倒排索引或向量库，
 * 见 docs/ROADMAP.md。
 */
@Service
public class KeywordSearchService implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(KeywordSearchService.class);

    private static final int MAX_LINE_MATCHES = 5;
    private static final int MAX_MATCH_LINE_LENGTH = 240;

    private final FileIndexMapper fileIndexMapper;

    public KeywordSearchService(FileIndexMapper fileIndexMapper) {
        this.fileIndexMapper = fileIndexMapper;
    }

    @Override
    public List<SearchHit> search(long repoId, String query, int topK, Set<String> extFilter) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String phrase = query.trim().toLowerCase(Locale.ROOT);

        List<FileIndex> files = fileIndexMapper.selectList(new LambdaQueryWrapper<FileIndex>()
                .eq(FileIndex::getRepoId, repoId)
                .select(FileIndex::getId, FileIndex::getPath, FileIndex::getExt,
                        FileIndex::getLanguage, FileIndex::getLineCount, FileIndex::getContent));

        List<SearchHit> hits = new ArrayList<>();
        for (FileIndex file : files) {
            if (extFilter != null && (file.getExt() == null || !extFilter.contains(file.getExt()))) {
                continue;
            }
            SearchHit hit = scoreFile(file, terms, phrase);
            if (hit != null) {
                hits.add(hit);
            }
        }
        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        if (hits.size() > topK) {
            hits = hits.subList(0, topK);
        }
        log.debug("关键词检索 repo={} query={} 命中 {} 个文件", repoId, query, hits.size());
        return hits;
    }

    /**
     * 对单个文件打分；无任何命中返回 null。
     * 单独提出来方便对打分逻辑做单元测试。
     */
    SearchHit scoreFile(FileIndex file, List<String> terms, String phrase) {
        String content = file.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        String pathLower = file.getPath().toLowerCase(Locale.ROOT);

        double score = 0;
        Set<String> matchedTerms = new LinkedHashSet<>();
        for (String term : terms) {
            int tf = countOccurrences(lower, term);
            if (tf > 0) {
                double weight = 1.0 + Math.min(term.length(), 10) * 0.1;
                score += Math.min(tf, 50) * weight;
                matchedTerms.add(term);
            }
            if (pathLower.contains(term)) {
                score += 10;
                matchedTerms.add(term);
            }
        }
        if (!phrase.isEmpty() && phrase.length() >= 2 && lower.contains(phrase)) {
            score += 15;
        }
        if (score <= 0 || matchedTerms.isEmpty()) {
            return null;
        }

        List<LineMatch> lineMatches = findLineMatches(content, matchedTerms);
        return new SearchHit(file.getId(), file.getPath(), file.getLanguage(), score, lineMatches);
    }

    /**
     * 分词：提取 ASCII 词（字母数字下划线，长度 ≥2）与中文连续片段（长度 ≥2）。
     * 混合查询（如 "UserController 注册"）会得到两个词。
     */
    static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String token : query.split("[^0-9A-Za-z_\\u4e00-\\u9fff]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches("[\\u4e00-\\u9fff]+")) {
                // 中文片段整体作为一个词（子串匹配），保证"注册流程"这类词能命中
                if (token.length() >= 2) {
                    terms.add(token.toLowerCase(Locale.ROOT));
                }
            } else if (token.length() >= 2) {
                terms.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(terms);
    }

    private static int countOccurrences(String text, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) >= 0) {
            count++;
            idx += term.length();
        }
        return count;
    }

    private static List<LineMatch> findLineMatches(String content, Set<String> terms) {
        List<LineMatch> matches = new ArrayList<>();
        int lineNo = 0;
        for (String rawLine : content.split("\n", -1)) {
            lineNo++;
            String line = rawLine.toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String term : terms) {
                if (line.contains(term)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                String text = rawLine.trim();
                if (text.length() > MAX_MATCH_LINE_LENGTH) {
                    text = text.substring(0, MAX_MATCH_LINE_LENGTH) + "…";
                }
                matches.add(new LineMatch(lineNo, text));
                if (matches.size() >= MAX_LINE_MATCHES) {
                    break;
                }
            }
        }
        return matches;
    }

    /** 供 Agent 工具使用：文档类文件扩展名集合。 */
    public static Set<String> documentExtensions() {
        return new HashSet<>(Arrays.asList("md", "txt", "sql", "yml", "yaml", "properties", "json"));
    }
}
