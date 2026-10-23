package com.repopilot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.entity.QaRecord;
import com.repopilot.mapper.QaRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * AI 服务故障降级测试：
 * 注入一个必然失败的 Provider（配置为 remote 模式指向它），
 * 验证问答接口返回友好错误、应用不崩溃、失败记录被持久化（FAILED）。
 */
@SpringBootTest(properties = {"repopilot.ai.provider=broken"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private QaRecordMapper qaRecordMapper;

    /** 测试专用：总是抛出超时异常的 Provider。 */
    @TestConfiguration
    static class BrokenProviderConfig {
        @Bean
        public com.repopilot.service.ai.AiProvider brokenProvider() {
            return new com.repopilot.service.ai.AiProvider() {
                @Override
                public String getName() {
                    return "broken";
                }

                @Override
                public String getModelName() {
                    return "broken-model";
                }

                @Override
                public boolean isMock() {
                    return false;
                }

                @Override
                public String answerQuestion(com.repopilot.service.ai.AiChatContext context) {
                    throw new com.repopilot.service.ai.AiProviderException(
                            com.repopilot.common.exception.ErrorCode.AI_TIMEOUT, "模拟：AI 服务超时");
                }

                @Override
                public com.repopilot.dto.bug.BugAnalysisResult analyzeBug(com.repopilot.service.ai.AiChatContext context) {
                    throw new com.repopilot.service.ai.AiProviderException(
                            com.repopilot.common.exception.ErrorCode.AI_TIMEOUT, "模拟：AI 服务超时");
                }

                @Override
                public String ping() {
                    throw new com.repopilot.service.ai.AiProviderException(
                            com.repopilot.common.exception.ErrorCode.AI_TIMEOUT, "模拟：AI 服务超时");
                }
            };
        }
    }

    @Test
    void aiFailureIsGraceful() throws Exception {
        // 准备用户与已就绪的示例代码库
        String token = registerAndLogin("ai_fail_user", "secret88");
        MvcResult importResult = mockMvc.perform(post("/api/repos/import-sample")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long repoId = objectMapper.readTree(importResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        waitReady(repoId, token);

        // 提问：AI 失败应返回明确的业务错误码，而不是 500 或挂死
        mockMvc.perform(post("/api/repos/" + repoId + "/qa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"登录逻辑在哪里？\"}"))
                .andExpect(jsonPath("$.code").value(50202));

        // 失败记录已持久化，便于事后排查
        List<QaRecord> failed = qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getUserId, userIdOf(token))
                .eq(QaRecord::getStatus, "FAILED"));
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).getErrorMessage().contains("超时"));

        // 连通性测试同样返回友好结果（success=false + 消息），HTTP 仍是 200
        mockMvc.perform(post("/api/ai-config/test").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.success").value(false));
    }

    private void waitReady(long repoId, String token) throws Exception {
        for (int i = 0; i < 60; i++) {
            MvcResult detail = mockMvc.perform(get("/api/repos/" + repoId)
                    .header("Authorization", "Bearer " + token)).andReturn();
            String s = objectMapper.readTree(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("status").asText();
            if ("READY".equals(s)) {
                return;
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("示例项目索引超时");
    }

    private String registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    private long userIdOf(String token) throws Exception {
        // 简化：直接解析 /api/auth/me
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }
}
