package com.repopilot.service.qa;

import com.repopilot.dto.qa.EvidenceVO;
import com.repopilot.service.search.SearchHit;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索结果 → 证据引用的转换。
 * 证据是整条链路的"事实层"：无论是 Mock 模板的回答、真实模型的提示词，
 * 还是前端展示的引用卡片，用的都是同一份 EvidenceVO，保证前后一致。
 */
public final class EvidenceFactory {

    private EvidenceFactory() {
    }

    public static List<EvidenceVO> fromHits(List<SearchHit> hits) {
        List<EvidenceVO> evidence = new ArrayList<>();
        for (SearchHit hit : hits) {
            if (hit.matches() == null || hit.matches().isEmpty()) {
                continue;
            }
            var first = hit.matches().get(0);
            var last = hit.matches().get(hit.matches().size() - 1);
            String snippet = joinSnippet(hit);
            evidence.add(new EvidenceVO(
                    hit.path(),
                    first.lineNo(),
                    last.lineNo(),
                    snippet,
                    "关键词检索命中（相关性得分 " + (int) hit.score() + "）"));
        }
        return evidence;
    }

    private static String joinSnippet(SearchHit hit) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (var match : hit.matches()) {
            if (shown > 0) {
                sb.append("\n");
            }
            sb.append(match.lineNo()).append(": ").append(match.text());
            shown++;
        }
        return sb.toString();
    }
}
