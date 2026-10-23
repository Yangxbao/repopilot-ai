package com.repopilot.service.search;

import com.repopilot.entity.FileIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 关键词检索打分逻辑的单元测试（不依赖数据库）。 */
class KeywordSearchServiceTest {

    private FileIndex file(String path, String content) {
        FileIndex f = new FileIndex();
        f.setId(1L);
        f.setPath(path);
        f.setExt("java");
        f.setLanguage("Java");
        f.setLineCount(content.split("\n").length);
        f.setContent(content);
        return f;
    }

    @Test
    @DisplayName("分词：英文、类名与中文混合查询")
    void tokenizeMixed() {
        List<String> terms = KeywordSearchService.tokenize("UserController 注册流程 register");
        assertTrue(terms.contains("usercontroller"));
        assertTrue(terms.contains("register"));
        assertTrue(terms.contains("注册流程"));
        assertEquals(3, terms.size());
    }

    @Test
    @DisplayName("内容命中：返回命中行与行号")
    void contentMatch() {
        String content = """
                public class UserService {
                    // 用户注册流程入口
                    public User register(String username) {
                        return null;
                    }
                }
                """;
        SearchHit hit = new KeywordSearchService(null)
                .scoreFile(file("service/UserService.java", content), List.of("register"), "register");
        assertNotNull(hit);
        assertTrue(hit.score() > 0);
        assertFalse(hit.matches().isEmpty());
        assertEquals(3, hit.matches().get(0).lineNo());
        assertTrue(hit.matches().get(0).text().contains("register"));
    }

    @Test
    @DisplayName("路径命中加权：文件名匹配得分更高")
    void pathMatchBoost() {
        String content = "class TaskService {}";
        SearchHit pathHit = new KeywordSearchService(null)
                .scoreFile(file("service/TaskService.java", content), List.of("taskservice"), "taskservice");
        SearchHit contentHit = new KeywordSearchService(null)
                .scoreFile(file("other/Other.java", content), List.of("taskservice"), "taskservice");
        assertNotNull(pathHit);
        assertNotNull(contentHit);
        assertTrue(pathHit.score() > contentHit.score());
    }

    @Test
    @DisplayName("无命中返回 null，不得编造结果")
    void noMatchReturnsNull() {
        SearchHit hit = new KeywordSearchService(null)
                .scoreFile(file("a.java", "nothing here"), List.of("payment"), "payment");
        assertNull(hit);
    }

    @Test
    @DisplayName("空内容与空查询安全处理")
    void edgeCases() {
        assertNull(new KeywordSearchService(null).scoreFile(file("a.java", ""), List.of("x"), "x"));
        assertTrue(KeywordSearchService.tokenize("  ").isEmpty());
    }
}
