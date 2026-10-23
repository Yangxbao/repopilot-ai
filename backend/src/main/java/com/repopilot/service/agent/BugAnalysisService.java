package com.repopilot.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.PageResult;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.config.AppProperties;
import com.repopilot.dto.bug.BugAnalysisRequest;
import com.repopilot.dto.bug.BugAnalysisResult;
import com.repopilot.dto.bug.BugReportVO;
import com.repopilot.dto.qa.EvidenceVO;
import com.repopilot.entity.BugReport;
import com.repopilot.entity.Repo;
import com.repopilot.mapper.BugReportMapper;
import com.repopilot.service.RepoService;
import com.repopilot.service.ai.AiProviderRegistry;
import com.repopilot.service.qa.EvidenceFactory;
import com.repopilot.service.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bug 分析 Agent 的编排服务（固定流水线，而非自由决策的循环）。
 *
 * <p>流水线步骤（每一步的检索都会通过 ToolRegistry 留痕）：
 * <ol>
 *   <li>extract：纯 Java 提取异常类名、堆栈帧、HTTP 路径、关键词；</li>
 *   <li>searchCode：按提取结果逐个检索（最多 4 轮，节省预算）；</li>
 *   <li>searchDocuments：在文档/配置中补充检索 1 轮；</li>
 *   <li>analyzeIssue：把证据交给 AI Provider 生成结构化报告。</li>
 * </ol>
 *
 * <p>为什么用固定流水线：可复现、可审计、成本可控。
 * 模型自由决定调用哪些工具的"ReAct 循环"作为后续路线（见 docs/ROADMAP.md），
 * 届时也会继续复用这里的工具白名单与预算机制。
 */
@Service
public class BugAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(BugAnalysisService.class);
    private static final long RUN_DEADLINE_MS = 120_000;
    private static final int MAX_CODE_SEARCH_ROUNDS = 4;
    private static final int MAX_EVIDENCE = 8;

    private final RepoService repoService;
    private final ToolRegistry toolRegistry;
    private final AiProviderRegistry providerRegistry;
    private final BugReportMapper bugReportMapper;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public BugAnalysisService(RepoService repoService, ToolRegistry toolRegistry,
                              AiProviderRegistry providerRegistry, BugReportMapper bugReportMapper,
                              ObjectMapper objectMapper, AppProperties properties) {
        this.repoService = repoService;
        this.toolRegistry = toolRegistry;
        this.providerRegistry = providerRegistry;
        this.bugReportMapper = bugReportMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public BugReportVO analyze(long repoId, BugAnalysisRequest request, long userId) {
        Repo repo = repoService.getOwned(repoId, userId);
        if (!"READY".equals(repo.getStatus())) {
            throw new BizException(ErrorCode.REPO_NOT_READY, "代码库尚未完成索引，无法分析");
        }

        long start = System.currentTimeMillis();
        BugReport report = new BugReport();
        report.setRepoId(repoId);
        report.setUserId(userId);
        report.setTitle(request.title());
        report.setSymptom(request.symptom());
        report.setLogContent(request.logContent());
        report.setExpectedResult(request.expectedResult());
        report.setActualResult(request.actualResult());
        report.setStatus("RUNNING");
        report.setProvider(providerRegistry.active().getName());
        report.setModel(providerRegistry.active().getModelName());
        bugReportMapper.insert(report);

        try {
            BugAnalysisResult result = runPipeline(repo, request, userId, report.getId());
            int duration = (int) (System.currentTimeMillis() - start);
            report.setReportJson(objectMapper.writeValueAsString(result));
            report.setStatus("DONE");
            report.setDurationMs(duration);
            bugReportMapper.updateById(report);
            return toVO(report, repo.getName());
        } catch (BizException e) {
            report.setStatus("FAILED");
            report.setErrorMessage(truncate(e.getMessage(), 1000));
            report.setDurationMs((int) (System.currentTimeMillis() - start));
            bugReportMapper.updateById(report);
            throw e;
        } catch (Exception e) {
            log.error("Bug 分析流程异常 reportId={}", report.getId(), e);
            report.setStatus("FAILED");
            report.setErrorMessage(truncate("分析流程异常：" + e.getMessage(), 1000));
            report.setDurationMs((int) (System.currentTimeMillis() - start));
            bugReportMapper.updateById(report);
            throw new BizException(ErrorCode.INTERNAL, "分析流程异常，请稍后重试");
        }
    }

    private BugAnalysisResult runPipeline(Repo repo, BugAnalysisRequest request, long userId, long reportId) {
        ToolRunContext runCtx = new ToolRunContext(userId, repo.getId(), "BUG_ANALYSIS", reportId,
                properties.agent().maxToolCalls(), System.currentTimeMillis() + RUN_DEADLINE_MS);

        // 1. 提取关键信息（纯 Java，确定性强）
        var keyInfo = ErrorInfoExtractor.extract(request.title(), request.symptom(), request.logContent());
        List<String> queries = ErrorInfoExtractor.buildSearchQueries(keyInfo);

        // 2. 逐轮检索代码
        Map<String, SearchHit> bestByPath = new LinkedHashMap<>();
        int rounds = 0;
        for (String query : queries) {
            if (rounds >= MAX_CODE_SEARCH_ROUNDS || bestByPath.size() >= MAX_EVIDENCE) {
                break;
            }
            ToolResult result = toolRegistry.execute(runCtx, SearchCodeTool.NAME, Map.of("query", query, "topK", 5));
            if (result.success() && result.data() instanceof List<?> hits) {
                rounds++;
                mergeHits(bestByPath, hits);
            }
        }

        // 3. 文档补充检索
        if (!keyInfo.keywords().isEmpty() && bestByPath.size() < MAX_EVIDENCE) {
            ToolResult docResult = toolRegistry.execute(runCtx, SearchDocumentsTool.NAME,
                    Map.of("query", keyInfo.keywords().get(0), "topK", 3));
            if (docResult.success() && docResult.data() instanceof List<?> hits) {
                mergeHits(bestByPath, hits);
            }
        }

        // 4. 组装证据并生成报告
        List<EvidenceVO> evidence = EvidenceFactory.fromHits(new ArrayList<>(bestByPath.values()));
        if (evidence.size() > MAX_EVIDENCE) {
            evidence = evidence.subList(0, MAX_EVIDENCE);
        }

        Map<String, Object> analyzeParams = new LinkedHashMap<>();
        analyzeParams.put("evidence", evidence);
        analyzeParams.put("repoName", repo.getName());
        analyzeParams.put("symptom", request.symptom());
        analyzeParams.put("logContent", request.logContent());
        analyzeParams.put("expectedResult", request.expectedResult());
        analyzeParams.put("actualResult", request.actualResult());
        ToolResult analyzeResult = toolRegistry.execute(runCtx, AnalyzeIssueTool.NAME, analyzeParams);
        if (!analyzeResult.success() || !(analyzeResult.data() instanceof BugAnalysisResult result)) {
            throw new BizException(ErrorCode.AI_ERROR,
                    analyzeResult == null ? "分析失败" : analyzeResult.summary());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void mergeHits(Map<String, SearchHit> bestByPath, List<?> hits) {
        for (Object obj : hits) {
            if (obj instanceof SearchHit hit) {
                bestByPath.merge(hit.path(), hit, (oldHit, newHit) ->
                        newHit.score() > oldHit.score() ? newHit : oldHit);
            }
        }
    }

    public PageResult<BugReportVO> history(long userId, Long repoId, long page, long size) {
        Page<BugReport> result = bugReportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BugReport>()
                        .eq(BugReport::getUserId, userId)
                        .eq(repoId != null, BugReport::getRepoId, repoId)
                        .orderByDesc(BugReport::getCreatedAt));
        List<BugReportVO> vos = result.getRecords().stream().map(r -> toVO(r, null)).toList();
        return new PageResult<>(vos, result.getTotal(), page, size);
    }

    public BugReportVO getById(long id, long userId) {
        BugReport report = bugReportMapper.selectById(id);
        if (report == null || report.getUserId() == null || report.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "分析报告不存在");
        }
        return toVO(report, null);
    }

    private BugReportVO toVO(BugReport report, String repoName) {
        BugAnalysisResult result = null;
        if (report.getReportJson() != null) {
            try {
                result = objectMapper.readValue(report.getReportJson(), BugAnalysisResult.class);
            } catch (Exception ignored) {
            }
        }
        return new BugReportVO(report.getId(), report.getRepoId(), repoName, report.getTitle(),
                report.getSymptom(), report.getLogContent(), report.getExpectedResult(),
                report.getActualResult(), result, report.getStatus(), report.getProvider(),
                report.getModel(), report.getDurationMs() == null ? 0 : report.getDurationMs(),
                report.getErrorMessage(), report.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
