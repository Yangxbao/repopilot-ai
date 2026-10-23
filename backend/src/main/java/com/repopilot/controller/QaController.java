package com.repopilot.controller;

import com.repopilot.common.ApiResponse;
import com.repopilot.common.PageResult;
import com.repopilot.dto.qa.QaRequest;
import com.repopilot.dto.qa.QaVO;
import com.repopilot.security.SecurityUtils;
import com.repopilot.service.qa.QaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 代码问答与历史记录。 */
@Tag(name = "代码问答")
@RestController
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @Operation(summary = "提问（先检索代码再交给 AI）")
    @PostMapping("/api/repos/{repoId}/qa")
    public ApiResponse<QaVO> ask(@PathVariable long repoId, @Valid @RequestBody QaRequest request) {
        return ApiResponse.ok(qaService.ask(repoId, request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "我的问答历史")
    @GetMapping("/api/qa")
    public ApiResponse<PageResult<QaVO>> history(@RequestParam(required = false) Long repoId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        size = Math.min(size, 50);
        return ApiResponse.ok(qaService.history(SecurityUtils.currentUserId(), repoId,
                Math.max(page, 1), Math.max(size, 1)));
    }

    @Operation(summary = "问答记录详情")
    @GetMapping("/api/qa/{id}")
    public ApiResponse<QaVO> detail(@PathVariable long id) {
        return ApiResponse.ok(qaService.getById(id, SecurityUtils.currentUserId()));
    }
}
