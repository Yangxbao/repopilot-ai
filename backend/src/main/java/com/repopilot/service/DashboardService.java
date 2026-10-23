package com.repopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.dto.qa.QaVO;
import com.repopilot.entity.BugReport;
import com.repopilot.entity.QaRecord;
import com.repopilot.entity.Repo;
import com.repopilot.mapper.BugReportMapper;
import com.repopilot.mapper.QaRecordMapper;
import com.repopilot.mapper.RepoMapper;
import com.repopilot.service.ai.AiProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台 Dashboard 的汇总数据。 */
@Service
public class DashboardService {

    private final RepoMapper repoMapper;
    private final QaRecordMapper qaRecordMapper;
    private final BugReportMapper bugReportMapper;
    private final AiProviderRegistry providerRegistry;

    public DashboardService(RepoMapper repoMapper, QaRecordMapper qaRecordMapper,
                            BugReportMapper bugReportMapper, AiProviderRegistry providerRegistry) {
        this.repoMapper = repoMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.bugReportMapper = bugReportMapper;
        this.providerRegistry = providerRegistry;
    }

    public Map<String, Object> summary(long userId) {
        Long repoCount = repoMapper.selectCount(new LambdaQueryWrapper<Repo>().eq(Repo::getUserId, userId));
        Long qaCount = qaRecordMapper.selectCount(new LambdaQueryWrapper<QaRecord>().eq(QaRecord::getUserId, userId));
        Long bugCount = bugReportMapper.selectCount(new LambdaQueryWrapper<BugReport>().eq(BugReport::getUserId, userId));

        // 已索引文件总数：汇总当前用户所有 READY 代码库的 file_count
        List<Repo> repos = repoMapper.selectList(new LambdaQueryWrapper<Repo>().eq(Repo::getUserId, userId));
        long fileTotal = repos.stream()
                .filter(r -> "READY".equals(r.getStatus()))
                .mapToLong(r -> r.getFileCount() == null ? 0 : r.getFileCount())
                .sum();

        List<QaRecord> recentQas = qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getUserId, userId)
                .orderByDesc(QaRecord::getCreatedAt)
                .last("LIMIT 5"));
        List<BugReport> recentReports = bugReportMapper.selectList(new LambdaQueryWrapper<BugReport>()
                .eq(BugReport::getUserId, userId)
                .orderByDesc(BugReport::getCreatedAt)
                .last("LIMIT 5"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("repoCount", repoCount == null ? 0 : repoCount);
        summary.put("fileCount", fileTotal);
        summary.put("qaCount", qaCount == null ? 0 : qaCount);
        summary.put("bugReportCount", bugCount == null ? 0 : bugCount);
        summary.put("aiMode", providerRegistry.isMockMode() ? "mock" : "remote");
        summary.put("aiProvider", providerRegistry.active().getName());
        summary.put("recentQas", recentQas.stream().map(r -> Map.of(
                "id", r.getId(), "question", r.getQuestion(),
                "status", r.getStatus(), "createdAt", String.valueOf(r.getCreatedAt()))).toList());
        summary.put("recentReports", recentReports.stream().map(r -> Map.of(
                "id", r.getId(), "title", r.getTitle(),
                "status", r.getStatus(), "createdAt", String.valueOf(r.getCreatedAt()))).toList());
        return summary;
    }
}
