package com.repopilot.service.search;

import java.util.List;
import java.util.Set;

/**
 * 检索服务的抽象接口。
 *
 * <p>当前实现是关键词检索（KeywordSearchService）。接口保持稳定，
 * 后续可以在此后面替换/叠加：Embedding 语义检索、向量数据库、混合检索、
 * RAG 上下文重排等，而不用改动调用方（问答、Agent 工具、搜索接口）。
 */
public interface SearchService {

    /**
     * 在指定代码库中检索。
     *
     * @param repoId    代码库 ID
     * @param query     查询文本
     * @param topK      最多返回的文件数
     * @param extFilter 限定文件扩展名（小写，不含点）；null 表示不限定
     */
    List<SearchHit> search(long repoId, String query, int topK, Set<String> extFilter);

    /** 是否为语义检索实现（当前关键词实现返回 false，用于前端如实展示检索类型）。 */
    default boolean isSemantic() {
        return false;
    }
}
