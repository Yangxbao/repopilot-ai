package com.repopilot.dto.repo;

import java.time.LocalDateTime;

/** 代码库列表/详情的返回结构。 */
public record RepoVO(
        long id,
        String name,
        String description,
        String originalFilename,
        String status,
        int fileCount,
        int indexedCount,
        long totalSize,
        String errorMessage,
        boolean isDemo,
        LocalDateTime createdAt
) {
}
