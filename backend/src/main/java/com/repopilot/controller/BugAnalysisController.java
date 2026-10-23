package com.repopilot.controller;

import com.repopilot.common.ApiResponse;
import com.repopilot.common.PageResult;
import com.repopilot.dto.bug.BugAnalysisRequest;
import com.repopilot.dto.bug.BugReportVO;
import com.repopilot.security.SecurityUtils;
import com.repopilot.service.agent.BugAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bug 分析：提交分析请求、查看报告历史与详情。 */
@Tag(name = "Bug 分析")
@RestController
public class BugAnalysisController {

    private final BugAnalysisService bugAnalysisService;

    public BugAnalysisController(BugAnalysisService bugAnalysisService) {
        this.bugAnalysisService = bugAnalysisService;
    }

    @Operation(summary = "提交 Bug 分析（Agent 流水线：提取 → 检索 → 分析 → 报告）")
    @PostMapping("/api/repos/{repoId}/bug-analysis")
    public ApiResponse<BugReportVO> analyze(@PathVariable long repoId,
                                            @Valid @RequestBody BugAnalysisRequest request) {
        return ApiResponse.ok(bugAnalysisService.analyze(repoId, request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "我的分析报告列表")
    @GetMapping("/api/bug-reports")
    public ApiResponse<PageResult<BugReportVO>> history(@RequestParam(required = false) Long repoId,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size) {
        size = Math.min(size, 50);
        return ApiResponse.ok(bugAnalysisService.history(SecurityUtils.currentUserId(), repoId,
                Math.max(page, 1), Math.max(size, 1)));
    }

    @Operation(summary = "分析报告详情")
    @GetMapping("/api/bug-reports/{id}")
    public ApiResponse<BugReportVO> detail(@PathVariable long id) {
        return ApiResponse.ok(bugAnalysisService.getById(id, SecurityUtils.currentUserId()));
    }
}
