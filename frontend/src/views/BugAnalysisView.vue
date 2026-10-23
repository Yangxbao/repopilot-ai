<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bugApi } from '../api/qa'
import { repoApi } from '../api/repo'
import type { BugReportVO, RepoVO } from '../types'

const route = useRoute()
const repos = ref<RepoVO[]>([])
const repoId = ref<number | null>(route.params.repoId ? Number(route.params.repoId) : null)
const form = ref({ title: '', symptom: '', logContent: '', expectedResult: '', actualResult: '' })
const analyzing = ref(false)
const report = ref<BugReportVO | null>(null)

const readyRepos = computed(() => repos.value.filter((r) => r.status === 'READY'))
const confidenceType = (c: string) => (c === 'HIGH' ? 'danger' : c === 'MEDIUM' ? 'warning' : 'info')

async function loadRepos() {
  try {
    const res = await repoApi.list(1, 50)
    repos.value = res.list
    if (repoId.value == null && readyRepos.value.length) {
      repoId.value = readyRepos.value[0].id
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function analyze() {
  if (!repoId.value) return ElMessage.warning('请先选择一个已就绪的代码库')
  if (!form.value.title.trim()) return ElMessage.warning('请填写问题标题')
  analyzing.value = true
  report.value = null
  try {
    report.value = await bugApi.analyze(repoId.value, {
      title: form.value.title,
      symptom: form.value.symptom || undefined,
      logContent: form.value.logContent || undefined,
      expectedResult: form.value.expectedResult || undefined,
      actualResult: form.value.actualResult || undefined
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    analyzing.value = false
  }
}

onMounted(loadRepos)
</script>

<template>
  <div>
    <h2 style="margin-top: 0">Bug 分析工作台</h2>

    <el-alert type="info" show-icon :closable="false" style="margin-bottom: 14px"
      title="Agent 流程：提取关键错误 → 检索相关代码与文档 → 生成按优先级排序的排查步骤。只读分析，不会修改任何代码。" />

    <div style="display: grid; grid-template-columns: 400px 1fr; gap: 14px" class="bug-grid">
      <div class="rp-card">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="代码库" required>
            <el-select v-model="repoId" style="width: 100%" placeholder="选择代码库">
              <el-option v-for="r in readyRepos" :key="r.id" :value="r.id" :label="r.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题标题" required>
            <el-input v-model="form.title" maxlength="200" placeholder="例如：查询任务详情报 NPE" />
          </el-form-item>
          <el-form-item label="错误现象">
            <el-input v-model="form.symptom" type="textarea" :rows="2" maxlength="4000" placeholder="什么操作触发了什么错误" />
          </el-form-item>
          <el-form-item label="日志 / 异常堆栈">
            <el-input v-model="form.logContent" type="textarea" :rows="6" maxlength="20000"
              placeholder="粘贴完整堆栈，Agent 会从中提取异常类名与堆栈帧" />
          </el-form-item>
          <el-form-item label="预期结果">
            <el-input v-model="form.expectedResult" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="实际结果">
            <el-input v-model="form.actualResult" type="textarea" :rows="2" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="analyzing" @click="analyze">
            {{ analyzing ? 'Agent 分析中…' : '开始分析' }}
          </el-button>
        </el-form>
      </div>

      <div v-loading="analyzing" class="rp-card" style="min-height: 300px">
        <el-empty v-if="!report && !analyzing" description="填写左侧信息并开始分析，报告将展示在这里" />
        <template v-if="report">
          <el-alert v-if="report.status === 'FAILED'" type="error" show-icon :closable="false"
            :title="`分析失败：${report.errorMessage}`" />
          <template v-else-if="report.report">
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
              <h3 style="margin: 0">{{ report.title }}</h3>
              <span style="color: #94a3b8; font-size: 12px">
                {{ report.provider }} · {{ report.model }} · {{ report.durationMs }}ms
              </span>
            </div>
            <p style="color: #475569">{{ report.report.summary }}</p>

            <h4>🔍 可能原因</h4>
            <div v-for="(c, i) in report.report.possibleCauses" :key="i" class="cause">
              <div style="display: flex; justify-content: space-between; gap: 8px">
                <span>{{ c.cause }}</span>
                <el-tag size="small" :type="confidenceType(c.confidence)">{{ c.confidence }}</el-tag>
              </div>
              <div v-if="c.evidence.length" style="margin-top: 6px">
                <code v-for="e in c.evidence" :key="e" class="inline" style="margin-right: 6px">{{ e }}</code>
              </div>
            </div>

            <h4>🧭 排查步骤（按优先级）</h4>
            <el-steps direction="vertical" :active="report.report.troubleshootingSteps.length" style="height: auto">
              <el-step v-for="s in report.report.troubleshootingSteps" :key="s.priority"
                :title="`${s.priority}. ${s.step}`" :description="s.detail" />
            </el-steps>

            <h4>❓ 仍需确认</h4>
            <ul style="margin: 0; padding-left: 20px">
              <li v-for="(q, i) in report.report.openQuestions" :key="i" style="margin: 4px 0">{{ q }}</li>
            </ul>

            <h4>🧪 测试与验证建议</h4>
            <ul style="margin: 0; padding-left: 20px">
              <li v-for="(t, i) in report.report.testSuggestions" :key="i" style="margin: 4px 0">{{ t }}</li>
            </ul>

            <h4>📌 结论</h4>
            <p style="color: #475569">{{ report.report.conclusion }}</p>
            <el-alert v-if="!report.report.structured" type="warning" show-icon :closable="false"
              title="本次 AI 未按结构化格式输出，以上为原始文本" />
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cause {
  border: 1px solid var(--rp-border);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  font-size: 13.5px;
}

@media (max-width: 1100px) {
  .bug-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
