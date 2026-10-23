package com.repopilot.controller;

import com.repopilot.common.ApiResponse;
import com.repopilot.common.PageResult;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.dto.repo.FileContentVO;
import com.repopilot.dto.repo.FileNode;
import com.repopilot.dto.repo.OutlineVO;
import com.repopilot.dto.repo.RepoVO;
import com.repopilot.security.SecurityUtils;
import com.repopilot.service.RepoService;
import com.repopilot.service.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 代码库：上传导入、列表、文件树、文件内容、大纲、搜索、重建索引、删除。 */
@Tag(name = "代码库")
@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final RepoService repoService;
    private final SearchService searchService;

    public RepoController(RepoService repoService, SearchService searchService) {
        this.repoService = repoService;
        this.searchService = searchService;
    }

    @Operation(summary = "上传 ZIP 创建代码库")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<RepoVO> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("name") String name,
                                      @RequestParam(value = "description", required = false) String description) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION, "代码库名称不能为空");
        }
        return ApiResponse.ok(repoService.createFromZip(
                SecurityUtils.currentUserId(), name, description, file));
    }

    @Operation(summary = "一键导入内置示例项目（演示数据，幂等）")
    @PostMapping("/import-sample")
    public ApiResponse<RepoVO> importSample() {
        return ApiResponse.ok(repoService.importSample(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "我的代码库列表")
    @GetMapping
    public ApiResponse<PageResult<RepoVO>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        size = Math.min(size, 50);
        return ApiResponse.ok(repoService.list(SecurityUtils.currentUserId(), Math.max(page, 1), Math.max(size, 1)));
    }

    @Operation(summary = "代码库详情")
    @GetMapping("/{id}")
    public ApiResponse<RepoVO> detail(@PathVariable long id) {
        return ApiResponse.ok(repoService.detail(id, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "文件树")
    @GetMapping("/{id}/tree")
    public ApiResponse<List<FileNode>> tree(@PathVariable long id) {
        return ApiResponse.ok(repoService.tree(id, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "读取文件内容（按行）")
    @GetMapping("/{id}/files")
    public ApiResponse<FileContentVO> file(@PathVariable long id, @RequestParam String path) {
        return ApiResponse.ok(repoService.readFile(id, SecurityUtils.currentUserId(), path));
    }

    @Operation(summary = "文件结构大纲（启发式解析）")
    @GetMapping("/{id}/outline")
    public ApiResponse<OutlineVO> outline(@PathVariable long id, @RequestParam String path) {
        return ApiResponse.ok(repoService.outline(id, SecurityUtils.currentUserId(), path));
    }

    @Operation(summary = "直接搜索代码（文件浏览页的搜索框）")
    @GetMapping("/{id}/search")
    public ApiResponse<List<com.repopilot.service.search.SearchHit>> search(
            @PathVariable long id, @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int topK) {
        repoService.getOwned(id, SecurityUtils.currentUserId());
        topK = Math.max(1, Math.min(20, topK));
        return ApiResponse.ok(searchService.search(id, query, topK, null));
    }

    @Operation(summary = "重建索引")
    @PostMapping("/{id}/reindex")
    public ApiResponse<RepoVO> reindex(@PathVariable long id) {
        return ApiResponse.ok(repoService.reindex(id, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "删除代码库及索引数据")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repoService.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }
}
