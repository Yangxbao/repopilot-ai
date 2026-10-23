package com.repopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件索引记录：只保存安全过滤后的文本文件全文。
 * (repo_id, path) 有唯一约束，重复导入/重建索引前会先删除旧记录。
 */
@TableName("file_index")
public class FileIndex {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    /** ZIP 内相对路径，统一使用 / 分隔 */
    private String path;
    private String ext;
    private String language;
    private Long size;
    private Integer lineCount;
    private String content;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Integer getLineCount() { return lineCount; }
    public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
