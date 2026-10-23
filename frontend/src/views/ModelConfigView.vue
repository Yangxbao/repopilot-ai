<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '../api/dashboard'
import type { AiConfigVO } from '../types'

const config = ref<AiConfigVO | null>(null)
const tools = ref<Record<string, string>>({})
const testing = ref(false)
const testResult = ref<{ success: boolean; latencyMs?: number; message?: string; reply?: string } | null>(null)

async function runTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await aiApi.test()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  try {
    config.value = await aiApi.config()
    tools.value = await aiApi.tools()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
})
</script>

<template>
  <div>
    <h2 style="margin-top: 0">AI 模型配置</h2>

    <div v-if="config" class="rp-card" style="margin-bottom: 14px">
      <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 10px">
        <el-tag :type="config.mode === 'mock' ? 'warning' : 'success'" size="large">
          {{ config.mode === 'mock' ? 'Mock 模拟模式' : 'Remote 真实模型' }}
        </el-tag>
        <span style="color: #64748b">provider: {{ config.providerName }} · model: {{ config.model }}</span>
      </div>

      <el-alert v-if="config.mode === 'mock'" type="warning" show-icon :closable="false"
        title="Mock 模式：回答由内置模板生成，引用的代码位置来自真实检索结果，不代表真实模型推理。" />

      <template v-else>
        <p style="margin: 6px 0">
          接口地址：<code class="inline">{{ config.baseUrl }}</code>
          <el-tag size="small" :type="config.apiKeyConfigured ? 'success' : 'danger'" style="margin-left: 8px">
            {{ config.apiKeyConfigured ? 'API Key 已配置' : 'API Key 未配置' }}
          </el-tag>
        </p>
        <el-alert v-if="!config.apiKeyConfigured" type="error" show-icon :closable="false"
          title="未配置 REPOPILOT_AI_API_KEY，调用会失败" />
        <p style="color: #64748b; font-size: 13px">
          超时 {{ config.timeoutSeconds }}s · 重试上限 {{ config.maxRetries }} 次
        </p>
      </template>

      <div style="margin-top: 12px; display: flex; gap: 10px; align-items: center">
        <el-button type="primary" :loading="testing" @click="runTest">测试连通性</el-button>
        <span v-if="testResult">
          <el-tag :type="testResult.success ? 'success' : 'danger'">
            {{ testResult.success ? `连通（${testResult.latencyMs}ms）` : '失败' }}
          </el-tag>
          <span v-if="testResult.message" style="color: #ef4444; font-size: 13px; margin-left: 8px">
            {{ testResult.message }}
          </span>
        </span>
      </div>
    </div>

    <div class="rp-card" style="margin-bottom: 14px">
      <h3 style="margin-top: 0">如何切换为真实模型</h3>
      <p style="color: #475569; font-size: 13.5px">
        通过环境变量配置（OpenAI 兼容接口，DeepSeek / 智谱 GLM / 通义等均适用），然后重启后端：
      </p>
      <pre class="rp-code">set REPOPILOT_AI_PROVIDER=remote
set REPOPILOT_AI_BASE_URL=https://api.deepseek.com/v1
set REPOPILOT_AI_API_KEY=你的密钥
set REPOPILOT_AI_MODEL=deepseek-chat</pre>
      <p style="color: #94a3b8; font-size: 12.5px">
        密钥只保存在环境变量中，不会写入代码库、前端或日志；本页面永远不会显示完整密钥。
      </p>
    </div>

    <div class="rp-card">
      <h3 style="margin-top: 0">Agent 可用工具（由后端白名单控制）</h3>
      <el-table :data="Object.entries(tools)">
        <el-table-column label="工具名" width="160">
          <template #default="{ row }"><code class="inline">{{ row[0] }}</code></template>
        </el-table-column>
        <el-table-column label="说明">
          <template #default="{ row }">{{ row[1] }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
