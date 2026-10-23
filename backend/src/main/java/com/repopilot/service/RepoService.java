package com.repopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repopilot.common.PageResult;
import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.config.AppProperties;
import com.repopilot.dto.repo.FileContentVO;
import com.repopilot.dto.repo.FileNode;
import com.repopilot.dto.repo.OutlineVO;
import com.repopilot.dto.repo.RepoVO;
import com.repopilot.entity.FileIndex;
import com.repopilot.entity.Repo;
import com.repopilot.mapper.FileIndexMapper;
import com.repopilot.mapper.RepoMapper;
import com.repopilot.service.index.IndexService;
import com.repopilot.service.index.SampleProjectProvider;
import com.repopilot.service.index.ZipImporter;
import com.repopilot.util.OutlineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码库业务：导入、查询、文件浏览、重建索引、删除。
 *
 * <p>安全要点：所有按 ID 访问的方法都通过 {@link #getOwned} 做所有权检查。
 * 检查失败统一返回 NOT_FOUND 而不是 403，避免攻击者用差异响应枚举他人资源 ID。
 */
@Service
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    private final RepoMapper repoMapper;
    private final FileIndexMapper fileIndexMapper;
    private final ZipImporter zipImporter;
    private final IndexService indexService;
    private final SampleProjectProvider sampleProjectProvider;
    private final AppProperties properties;

    public RepoService(RepoMapper repoMapper, FileIndexMapper fileIndexMapper,
                       ZipImporter zipImporter, IndexService indexService,
                       SampleProjectProvider sampleProjectProvider, AppProperties properties) {
        this.repoMapper = repoMapper;
        this.fileIndexMapper = fileIndexMapper;
        this.zipImporter = zipImporter;
        this.indexService = indexService;
        this.sampleProjectProvider = sampleProjectProvider;
        this.properties = properties;
    }

    // ---------- 导入 ----------

    public RepoVO createFromZip(long userId, String name, String description, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择要上传的 ZIP 文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".zip")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只支持 ZIP 格式的压缩包");
        }
        Repo repo = createRepoRow(userId, sanitizeName(name), description, original, false);
        Path srcDir = indexService.sourceDir(repo.getId());
        try (InputStream in = file.getInputStream()) {
            // 解压是同步的：非法压缩包当场报错，用户无需轮询才知道失败
            zipImporter.extract(in, srcDir, properties.upload());
        } catch (BizException e) {
            // 解压失败的代码库没有任何可用内容，删除记录后把错误抛给前端
            repoMapper.deleteById(repo.getId());
            throw e;
        } catch (IOException e) {
            repoMapper.deleteById(repo.getId());
            throw new BizException(ErrorCode.BAD_REQUEST, "读取上传文件失败");
        }
        indexService.buildIndexAsync(repo.getId());
        log.info("用户 {} 上传代码库 {}（{}）", userId, repo.getId(), repo.getName());
        return toVO(repo);
    }

    /**
     * 导入内置示例项目（演示数据）。幂等：已导入过则直接返回已有记录。
     */
    public RepoVO importSample(long userId) {
        Repo existing = repoMapper.selectOne(new LambdaQueryWrapper<Repo>()
                .eq(Repo::getUserId, userId)
                .eq(Repo::getIsDemo, true)
                .last("LIMIT 1"));
        if (existing != null) {
            return toVO(existing);
        }
        List<SampleProjectProvider.SampleFile> files = sampleProjectProvider.loadFiles();
        if (files.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL, "内置示例项目缺失，请联系维护者");
        }
        Repo repo = createRepoRow(userId, SampleProjectProvider.SAMPLE_NAME,
                "随应用内置的演示 Java 项目，用于体验完整流程（非真实用户数据）", "sample-taskflow.zip", true);
        Path srcDir = indexService.sourceDir(repo.getId());
        try {
            for (SampleProjectProvider.SampleFile f : files) {
                Path target = srcDir.resolve(f.path()).normalize();
                if (!target.startsWith(srcDir)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, f.content());
            }
        } catch (IOException e) {
            repoMapper.deleteById(repo.getId());
            ZipImporter.cleanupDirectory(srcDir);
            throw new BizException(ErrorCode.INTERNAL, "写入示例项目文件失败");
        }
        indexService.buildIndexAsync(repo.getId());
        return toVO(repo);
    }

    private Repo createRepoRow(long userId, String name, String description, String originalFilename, boolean demo) {
        Long count = repoMapper.selectCount(new LambdaQueryWrapper<Repo>()
                .eq(Repo::getUserId, userId)
                .eq(Repo::getName, name));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.REPO_NAME_CONFLICT, "已存在同名代码库：" + name);
        }
        Repo repo = new Repo();
        repo.setUserId(userId);
        repo.setName(name);
        repo.setDescription(truncate(description, 500));
        repo.setOriginalFilename(originalFilename);
        repo.setStatus("PENDING");
        repo.setFileCount(0);
        repo.setIndexedCount(0);
        repo.setTotalSize(0L);
        repo.setIsDemo(demo);
        repoMapper.insert(repo);
        return repo;
    }

    // ---------- 查询 ----------

    public PageResult<RepoVO> list(long userId, long page, long size) {
        Page<Repo> result = repoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Repo>()
                        .eq(Repo::getUserId, userId)
                        .orderByDesc(Repo::getCreatedAt));
        List<RepoVO> vos = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(vos, result.getTotal(), page, size);
    }

    public Repo getOwned(long repoId, long userId) {
        Repo repo = repoMapper.selectById(repoId);
        if (repo == null || repo.getUserId() == null || repo.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "代码库不存在");
        }
        return repo;
    }

    public RepoVO detail(long repoId, long userId) {
        return toVO(getOwned(repoId, userId));
    }

    /** 构建文件树（目录在前、字母序）。 */
    public List<FileNode> tree(long repoId, long userId) {
        getOwned(repoId, userId);
        List<FileIndex> files = fileIndexMapper.selectList(new LambdaQueryWrapper<FileIndex>()
                .eq(FileIndex::getRepoId, repoId)
                .select(FileIndex::getId, FileIndex::getPath, FileIndex::getSize));

        Map<String, FileNode> dirNodes = new HashMap<>();
        FileNode root = new FileNode("/", "directory");
        for (FileIndex file : files) {
            String[] segments = file.getPath().split("/");
            FileNode parent = root;
            StringBuilder dirPath = new StringBuilder();
            for (int i = 0; i < segments.length - 1; i++) {
                dirPath.append(segments[i]).append('/');
                final FileNode currentParent = parent;
                final String segment = segments[i];
                FileNode dir = dirNodes.computeIfAbsent(dirPath.toString(), k -> {
                    FileNode node = new FileNode(segment, "directory");
                    currentParent.getChildren().add(node);
                    return node;
                });
                parent = dir;
            }
            FileNode fileNode = new FileNode(segments[segments.length - 1], "file");
            fileNode.setSize(file.getSize() == null ? 0 : file.getSize());
            parent.getChildren().add(fileNode);
        }
        sortTree(root);
        return root.getChildren();
    }

    private void sortTree(FileNode node) {
        node.getChildren().sort(Comparator
                .comparing((FileNode n) -> n.getType().equals("directory") ? 0 : 1)
                .thenComparing(FileNode::getLabel, String.CASE_INSENSITIVE_ORDER));
        node.getChildren().forEach(this::sortTree);
    }

    public FileContentVO readFile(long repoId, long userId, String path) {
        getOwned(repoId, userId);
        String normalized = normalizePath(path);
        FileIndex file = fileIndexMapper.selectOne(new LambdaQueryWrapper<FileIndex>()
                .eq(FileIndex::getRepoId, repoId)
                .eq(FileIndex::getPath, normalized)
                .last("LIMIT 1"));
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在或未被索引");
        }
        String content = file.getContent() == null ? "" : file.getContent();
        return new FileContentVO(file.getPath(), file.getLanguage(),
                file.getLineCount() == null ? 0 : file.getLineCount(),
                List.of(content.split("\n", -1)), content);
    }

    public OutlineVO outline(long repoId, long userId, String path) {
        FileContentVO content = readFile(repoId, userId, path);
        return OutlineParser.parse(content.path(), content.content());
    }

    // ---------- 维护 ----------

    public RepoVO reindex(long repoId, long userId) {
        Repo repo = getOwned(repoId, userId);
        if ("PENDING".equals(repo.getStatus()) || "INDEXING".equals(repo.getStatus())) {
            throw new BizException(ErrorCode.REPO_NOT_READY, "当前正在索引中，请稍后再试");
        }
        Path srcDir = indexService.sourceDir(repoId);
        if (!Files.isDirectory(srcDir)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原始文件已不存在，请删除后重新上传");
        }
        updateStatus(repoId, "INDEXING", null);
        indexService.buildIndexAsync(repoId);
        repo.setStatus("INDEXING");
        return toVO(repo);
    }

    public void delete(long repoId, long userId) {
        getOwned(repoId, userId);
        fileIndexMapper.delete(new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getRepoId, repoId));
        repoMapper.deleteById(repoId);
        ZipImporter.cleanupDirectory(indexService.sourceDir(repoId).getParent());
        log.info("代码库已删除 repoId={} user={}", repoId, userId);
    }

    // ---------- 辅助 ----------

    private void updateStatus(long repoId, String status, String errorMessage) {
        Repo repo = repoMapper.selectById(repoId);
        if (repo != null) {
            repo.setStatus(status);
            repo.setErrorMessage(errorMessage);
            repoMapper.updateById(repo);
        }
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名代码库";
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new BizException(ErrorCode.VALIDATION, "代码库名称最长 100 字符");
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件路径不能为空");
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..") || normalized.contains("\0") || normalized.length() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        return normalized;
    }

    public RepoVO toVO(Repo repo) {
        return new RepoVO(repo.getId(), repo.getName(), repo.getDescription(), repo.getOriginalFilename(),
                repo.getStatus(), repo.getFileCount() == null ? 0 : repo.getFileCount(),
                repo.getIndexedCount() == null ? 0 : repo.getIndexedCount(),
                repo.getTotalSize() == null ? 0 : repo.getTotalSize(),
                repo.getErrorMessage(), Boolean.TRUE.equals(repo.getIsDemo()), repo.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
