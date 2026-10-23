package com.repopilot.dto.repo;

import java.util.ArrayList;
import java.util.List;

/** 文件树节点。目录与文件统一为一棵树，前端用 el-tree 直接渲染。 */
public class FileNode {

    private String label;
    /** directory / file */
    private String type;
    private long size;
    private List<FileNode> children = new ArrayList<>();

    public FileNode(String label, String type) {
        this.label = label;
        this.type = type;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public List<FileNode> getChildren() { return children; }
    public void setChildren(List<FileNode> children) { this.children = children; }
}
