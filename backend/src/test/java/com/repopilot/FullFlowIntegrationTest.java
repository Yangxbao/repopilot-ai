package com.repopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成测试（真实走 HTTP 层 + 内存 H2 + Mock AI）：
 * 注册 → 登录 → 上传 ZIP → 索引 → 文件浏览 → 检索 → 问答 → Bug 分析 → 历史 → 权限 → 删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static long repoId;

    @Test
    @Order(1)
    @DisplayName("注册新用户返回 token")
    void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"flow_user_a\",\"password\":\"secret66\",\"nickname\":\"甲\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("flow_user_a"));
    }

    @Test
    @Order(2)
    @DisplayName("重复注册与错误密码登录被拒绝")
    void registerAndLoginGuard() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"flow_user_a\",\"password\":\"secret66\"}"))
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"flow_user_a\",\"password\":\"wrong-pass\"}"))
                .andExpect(jsonPath("$.code").value(40101));

        // 参数校验：密码太短
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"flow_bad\",\"password\":\"123\"}"))
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @Order(3)
    @DisplayName("登录成功并导入示例项目，等待索引 READY")
    void loginAndImportSample() throws Exception {
        String token = login("flow_user_a", "secret66");

        MvcResult importResult = mockMvc.perform(post("/api/repos/import-sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(importResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        repoId = body.path("data").path("id").asLong();
        assertTrue(repoId > 0);

        // 异步索引：轮询直到 READY（最长 20 秒）
        boolean ready = false;
        for (int i = 0; i < 60; i++) {
            MvcResult detail = mockMvc.perform(get("/api/repos/" + repoId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            String statusText = objectMapper.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("status").asText();
            if ("READY".equals(statusText)) {
                ready = true;
                break;
            }
            Thread.sleep(300);
        }
        assertTrue(ready, "示例项目应在 20 秒内完成索引");
    }

    @Test
    @Order(4)
    @DisplayName("文件树与文件内容可访问，包含 TaskService")
    void treeAndFileContent() throws Exception {
        String token = login("flow_user_a", "secret66");
        MvcResult tree = mockMvc.perform(get("/api/repos/" + repoId + "/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String treeJson = tree.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(treeJson.contains("TaskService.java"));
        assertTrue(treeJson.contains("application.yml"));

        mockMvc.perform(get("/api/repos/" + repoId + "/files")
                        .param("path", "src/main/java/com/taskflow/service/TaskService.java")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.language").value("Java"));
    }

    @Test
    @Order(5)
    @DisplayName("关键词检索：搜 TaskService 能命中且带行号")
    void search() throws Exception {
        String token = login("flow_user_a", "secret66");
        MvcResult result = mockMvc.perform(get("/api/repos/" + repoId + "/search")
                        .param("q", "TaskService transition")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode hits = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(hits.isArray() && hits.size() > 0, "应至少命中一个文件");
        assertTrue(hits.get(0).path("path").asText().endsWith(".java"));
        assertTrue(hits.get(0).path("matches").size() > 0);
    }

    @Test
    @Order(6)
    @DisplayName("问答：Mock 模式回答引用真实文件路径并保存记录")
    void askQuestion() throws Exception {
        String token = login("flow_user_a", "secret66");
        MvcResult result = mockMvc.perform(post("/api/repos/" + repoId + "/qa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"TaskController 调用了哪些 Service？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.evidence").isArray())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(data.path("evidence").size() > 0, "Mock 回答应附带检索证据");
        assertTrue(data.path("answer").asText().contains("Mock 模拟模式"), "回答必须如实标注模拟模式");

        long qaId = data.path("id").asLong();
        mockMvc.perform(get("/api/qa/" + qaId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.question").value("TaskController 调用了哪些 Service？"));
    }

    @Test
    @Order(7)
    @DisplayName("Bug 分析：NPE 堆栈生成结构化报告且引用 TaskService")
    void bugAnalysis() throws Exception {
        String token = login("flow_user_a", "secret66");
        String payload = """
                {
                  "title": "查询任务详情报 NPE",
                  "symptom": "GET /tasks/999 返回 500",
                  "logContent": "java.lang.NullPointerException: Cannot invoke \\"com.taskflow.entity.Task.getStatus()\\"\\n  at com.taskflow.service.TaskService.getTaskDetail(TaskService.java:52)\\n  at com.taskflow.controller.TaskController.getTask(TaskController.java:38)",
                  "expectedResult": "任务不存在时返回 404 业务错误",
                  "actualResult": "抛出 NullPointerException"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/repos/" + repoId + "/bug-analysis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(data.path("report").path("troubleshootingSteps").size() > 0, "报告应包含排查步骤");
        String reportText = data.path("report").toString();
        assertTrue(reportText.contains("TaskService.java"), "报告证据应引用 TaskService.java");

        // 报告历史与详情
        mockMvc.perform(get("/api/bug-reports").param("repoId", String.valueOf(repoId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber());
        mockMvc.perform(get("/api/bug-reports/" + data.path("id").asLong())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.report.possibleCauses").isArray());
    }

    @Test
    @Order(8)
    @DisplayName("上传 ZIP 成功创建代码库")
    void uploadZip() throws Exception {
        String token = login("flow_user_a", "secret66");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("demo/Demo.java"));
            zos.write("public class Demo { /* demo upload test */ }".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("demo/README.md"));
            zos.write("# Upload Demo".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", bos.toByteArray());

        MvcResult result = mockMvc.perform(multipart("/api/repos/upload")
                        .file(file)
                        .param("name", "上传测试库")
                        .param("description", "集成测试上传")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long newRepoId = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        assertTrue(newRepoId > 0);

        // 等待索引完成（后台异步）
        for (int i = 0; i < 60; i++) {
            MvcResult detail = mockMvc.perform(get("/api/repos/" + newRepoId).header("Authorization", "Bearer " + token)).andReturn();
            String s = objectMapper.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("status").asText();
            if ("READY".equals(s)) {
                break;
            }
            Thread.sleep(300);
        }
        mockMvc.perform(get("/api/repos/" + newRepoId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.fileCount").value(2));
    }

    @Test
    @Order(9)
    @DisplayName("权限：用户 B 无法访问用户 A 的代码库（统一 404，不泄露存在性）")
    void ownershipCheck() throws Exception {
        String tokenB = registerAndLogin("flow_user_b", "secret77");
        mockMvc.perform(get("/api/repos/" + repoId).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/repos/" + repoId + "/files")
                        .param("path", "src/main/java/com/taskflow/service/TaskService.java")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(post("/api/repos/" + repoId + "/qa")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"test\"}"))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @Order(10)
    @DisplayName("未登录访问受保护接口返回 401")
    void unauthorizedRejected() throws Exception {
        mockMvc.perform(get("/api/repos")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("删除代码库后资源不可访问")
    void deleteRepo() throws Exception {
        String token = login("flow_user_a", "secret66");
        mockMvc.perform(delete("/api/repos/" + repoId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/repos/" + repoId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---------- 辅助 ----------

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    private String registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        return login(username, password);
    }
}
