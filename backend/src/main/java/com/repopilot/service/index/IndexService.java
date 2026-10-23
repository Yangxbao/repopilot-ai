package com.repopilot.service.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.config.AppProperties;
import com.repopilot.entity.FileIndex;
import com.repopilot.entity.Repo;
import com.repopilot.mapper.FileIndexMapper;
import com.repopilot.mapper.RepoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 索引构建：读取解压目录中的文本文件，写入 file_index 表。
 *
 * <p>在独立线程池中异步执行；repo.status 从 INDEXING → READY / FAILED。
 * 重建索引复用同一入口（先清空旧记录再重建）。
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);
    private static final int PROGRESS_UPDATE_EVERY = 100;

    private final FileIndexMapper fileIndexMapper;
    private final RepoMapper repoMapper;
    private final AppProperties properties;

    public IndexService(FileIndexMapper fileIndexMapper, RepoMapper repoMapper, AppProperties properties) {
        this.fileIndexMapper = fileIndexMapper;
        this.repoMapper = repoMapper;
        this.properties = properties;
    }

    /** 某代码库源文件在磁盘上的位置（normalize 保证路径比较的一致性）。 */
    public Path sourceDir(long repoId) {
        return Path.of(properties.dataDir(), "repos", String.valueOf(repoId), "src").normalize();
    }

    @Async("indexExecutor")
    public void buildIndexAsync(long repoId) {
        try {
            buildIndex(repoId);
        } catch (Exception e) {
            // @Async 方法的异常不会传播到调用方，必须在这里兜底记录
            log.error("索引构建失败 repoId={}", repoId, e);
            markFailed(repoId, "索引构建失败：" + e.getMessage());
        }
    }

    public void buildIndex(long repoId) {
        Path srcDir = sourceDir(repoId);
        if (!Files.isDirectory(srcDir)) {
            markFailed(repoId, "源文件目录不存在，请重新上传");
            return;
        }
        // 重建场景：先清掉旧索引
        fileIndexMapper.delete(new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getRepoId, repoId));

        List<Path> files;
        try (Stream<Path> walk = Files.walk(srcDir)) {
            files = walk.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            markFailed(repoId, "读取源文件目录失败：" + e.getMessage());
            return;
        }

        updateRepo(repoId, r -> r.setStatus("INDEXING"));

        int indexed = 0;
        long totalSize = 0;
        try {
            for (Path file : files) {
                String relative = srcDir.relativize(file).toString().replace('\\', '/');
                // String 解码默认用 REPLACE 处理非法字节，避免异常文件中断整个索引
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                FileIndex row = new FileIndex();
                row.setRepoId(repoId);
                row.setPath(relative);
                row.setExt(ZipImporter.extensionOf(relative));
                row.setLanguage(ZipImporter.languageOf(relative));
                row.setSize((long) content.length());
                row.setLineCount(countLines(content));
                row.setContent(content);
                fileIndexMapper.insert(row);

                indexed++;
                totalSize += row.getSize();
                if (indexed % PROGRESS_UPDATE_EVERY == 0) {
                    final int progressIndexed = indexed;
                    final long progressSize = totalSize;
                    updateRepo(repoId, r -> {
                        r.setIndexedCount(progressIndexed);
                        r.setTotalSize(progressSize);
                    });
                }
            }
        } catch (IOException e) {
            markFailed(repoId, "读取文件失败：" + e.getMessage());
            return;
        }

        long finalTotal1 = totalSize;
        int finalIndexed = indexed;
        updateRepo(repoId, r -> {
            r.setStatus("READY");
            r.setFileCount(finalIndexed);
            r.setIndexedCount(finalIndexed);
            r.setTotalSize(finalTotal1);
            r.setErrorMessage(null);
        });
        log.info("索引构建完成 repoId={} files={} size={}", repoId, indexed, totalSize);
    }

    private void markFailed(long repoId, String message) {
        updateRepo(repoId, r -> {
            r.setStatus("FAILED");
            r.setErrorMessage(truncate(message, 1000));
        });
    }

    private void updateRepo(long repoId, java.util.function.Consumer<Repo> mutator) {
        Repo repo = repoMapper.selectById(repoId);
        if (repo == null) {
            return;
        }
        mutator.accept(repo);
        repoMapper.updateById(repo);
    }

    private static int countLines(String content) {
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return content.isEmpty() ? 0 : lines;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
