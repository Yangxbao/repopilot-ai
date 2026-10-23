package com.repopilot.service.qa;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.PageResult;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.dto.qa.EvidenceVO;
import com.repopilot.dto.qa.QaRequest;
import com.repopilot.dto.qa.QaVO;
import com.repopilot.entity.QaRecord;
import com.repopilot.entity.Repo;
import com.repopilot.mapper.QaRecordMapper;
import com.repopilot.service.RepoService;
import com.repopilot.service.ai.AiChatContext;
import com.repopilot.service.ai.AiProviderException;
import com.repopilot.service.ai.AiProviderRegistry;
import com.repopilot.service.search.SearchHit;
import com.repopilot.service.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 代码问答编排：所有权检查 → 关键词检索 → 组装证据 → AI 生成 → 落库。
 *
 * <p>失败策略：AI 调用失败时仍会保存一条 FAILED 记录（含错误信息），
 * 然后抛出业务异常让前端提示；模型服务故障不会导致应用崩溃。
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    private final RepoService repoService;
    private final SearchService searchService;
    private final AiProviderRegistry providerRegistry;
    private final QaRecordMapper qaRecordMapper;
    private final ObjectMapper objectMapper;
    private final com.repopilot.config.AppProperties properties;

    public QaService(RepoService repoService, SearchService searchService,
                     AiProviderRegistry providerRegistry, QaRecordMapper qaRecordMapper,
                     ObjectMapper objectMapper, com.repopilot.config.AppProperties properties) {
        this.repoService = repoService;
        this.searchService = searchService;
        this.providerRegistry = providerRegistry;
        this.qaRecordMapper = qaRecordMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public QaVO ask(long repoId, QaRequest request, long userId) {
        Repo repo = repoService.getOwned(repoId, userId);
        if (!"READY".equals(repo.getStatus())) {
            throw new BizException(ErrorCode.REPO_NOT_READY, "代码库尚未完成索引，无法问答");
        }

        long start = System.currentTimeMillis();
        // 1. 检索
        List<SearchHit> hits = searchService.search(repoId, request.question(), properties.qa().topK(), null);
        // 2. 证据
        List<EvidenceVO> evidence = EvidenceFactory.fromHits(hits);
        // 3. AI 生成（mock 或 remote）
        String answer;
        try {
            answer = providerRegistry.active()
                    .answerQuestion(AiChatContext.forQa(repo.getName(), request.question(), evidence));
        } catch (AiProviderException e) {
            saveRecord(repoId, userId, request.question(), evidence, e, (int) (System.currentTimeMillis() - start));
            throw new BizException(e.getErrorCode(), e.getMessage());
        }
        int duration = (int) (System.currentTimeMillis() - start);
        QaRecord record = saveRecord(repoId, userId, request.question(), evidence, null, duration);
        record.setAnswer(answer);
        // 记录先插入（拿到 ID）再回填答案，保证历史里能看到完整回答
        qaRecordMapper.updateById(record);
        return toVO(record, repo.getName());
    }

    public PageResult<QaVO> history(long userId, Long repoId, long page, long size) {
        Page<QaRecord> result = qaRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<QaRecord>()
                        .eq(QaRecord::getUserId, userId)
                        .eq(repoId != null, QaRecord::getRepoId, repoId)
                        .orderByDesc(QaRecord::getCreatedAt));
        List<QaVO> vos = result.getRecords().stream().map(r -> toVO(r, null)).toList();
        return new PageResult<>(vos, result.getTotal(), page, size);
    }

    public QaVO getById(long id, long userId) {
        QaRecord record = qaRecordMapper.selectById(id);
        if (record == null || record.getUserId() == null || record.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "问答记录不存在");
        }
        return toVO(record, null);
    }

    private QaRecord saveRecord(long repoId, long userId, String question,
                                List<EvidenceVO> evidence, AiProviderException error, int durationMs) {
        QaRecord record = new QaRecord();
        record.setRepoId(repoId);
        record.setUserId(userId);
        record.setQuestion(question);
        record.setProvider(providerRegistry.active().getName());
        record.setModel(providerRegistry.active().getModelName());
        record.setDurationMs(durationMs);
        if (error == null) {
            record.setStatus("OK");
        } else {
            record.setStatus("FAILED");
            record.setErrorMessage(truncate(error.getMessage(), 1000));
        }
        try {
            record.setEvidenceJson(objectMapper.writeValueAsString(evidence));
        } catch (Exception e) {
            log.warn("证据序列化失败", e);
        }
        qaRecordMapper.insert(record);
        return record;
    }

    private QaVO toVO(QaRecord record, String repoName) {
        List<EvidenceVO> evidence = List.of();
        if (record.getEvidenceJson() != null) {
            try {
                evidence = objectMapper.readValue(record.getEvidenceJson(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        return new QaVO(record.getId(), record.getRepoId(), repoName, record.getQuestion(),
                record.getAnswer(), evidence, record.getProvider(), record.getModel(),
                record.getDurationMs() == null ? 0 : record.getDurationMs(),
                record.getStatus(), record.getErrorMessage(), record.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
