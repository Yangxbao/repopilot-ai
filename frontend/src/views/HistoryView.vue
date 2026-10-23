<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { bugApi, qaApi } from '../api/qa'
import type { BugReportVO, PageResult, QaVO } from '../types'
import EvidenceList from '../components/EvidenceList.vue'

const md = new MarkdownIt({ html: false })
const tab = ref<'qa' | 'bug'>('qa')
const qaPage = ref(1)
const bugPage = ref(1)
const qaData = ref<PageResult<QaVO> | null>(null)
const bugData = ref<PageResult<BugReportVO> | null>(null)
const loading = ref(false)
const detailVisible = ref(false)
const qaDetail = ref<QaVO | null>(null)
const bugDetail = ref<BugReportVO | null>(null)
const confidenceType = (c: string) => (c === 'HIGH' ? 'danger' : c === 'MEDIUM' ? 'warning' : 'info')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'qa') {
      qaData.value = await qaApi.history(undefined, qaPage.value, 10)
    } else {
      bugData.value = await bugApi.history(undefined, bugPage.value, 10)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function openQa(row: QaVO) {
  try {
    qaDetail.value = await qaApi.detail(row.id)
    bugDetail.value = null
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function openBug(row: BugReportVO) {
  try {
    bugDetail.value = await bugApi.detail(row.id)
    qaDetail.value = null
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

watch(tab, () => {
  qaPage.value = 1
  bugPage.value = 1
  load()
})

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <h2 style="margin-top: 0">历史分析报告</h2>

    <div class="rp-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="问答记录" name="qa">
          <el-table :data="qaData?.list || []">
            <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="provider" label="AI 模式" width="90" />
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">{{ row.durationMs }}ms</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'OK' ? 'success' : 'danger'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openQa(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="qaPage" :total="qaData?.total || 0" :page-size="10"
            layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end" @current-change="load" />
        </el-tab-pane>

        <el-tab-pane label="Bug 分析报告" name="bug">
          <el-table :data="bugData?.list || []">
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'DONE' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="provider" label="AI 模式" width="90" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openBug(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="bugPage" :total="bugData?.total || 0" :page-size="10"
            layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end" @current-change="load" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="detailVisible" width="720px" :title="qaDetail ? '问答详情' : 'Bug 分析报告'">
      <template v-if="qaDetail">
        <p><b>问题：</b>{{ qaDetail.question }}</p>
        <div class="md-body" v-html="md.render(qaDetail.answer || '')"></div>
        <h4>证据引用</h4>
        <EvidenceList :evidence="qaDetail.evidence" />
      </template>
      <template v-else-if="bugDetail">
        <p><b>{{ bugDetail.title }}</b></p>
        <p v-if="bugDetail.symptom"><b>现象：</b>{{ bugDetail.symptom }}</p>
        <template v-if="bugDetail.report">
          <p style="color: #475569">{{ bugDetail.report.summary }}</p>
          <h4>可能原因</h4>
          <div v-for="(c, i) in bugDetail.report.possibleCauses" :key="i"
            style="border: 1px solid var(--rp-border); border-radius: 8px; padding: 8px 12px; margin-bottom: 8px">
            <div style="display: flex; justify-content: space-between">
              <span>{{ c.cause }}</span>
              <el-tag size="small" :type="confidenceType(c.confidence)">{{ c.confidence }}</el-tag>
            </div>
            <div style="margin-top: 4px">
              <code v-for="e in c.evidence" :key="e" class="inline" style="margin-right: 6px">{{ e }}</code>
            </div>
          </div>
          <h4>排查步骤</h4>
          <ol style="padding-left: 20px; margin: 0">
            <li v-for="s in bugDetail.report.troubleshootingSteps" :key="s.priority" style="margin: 4px 0">
              <b>{{ s.step }}</b> — {{ s.detail }}
            </li>
          </ol>
          <h4>仍需确认</h4>
          <ul style="padding-left: 20px; margin: 0">
            <li v-for="(q, i) in bugDetail.report.openQuestions" :key="i">{{ q }}</li>
          </ul>
          <h4>结论</h4>
          <p style="color: #475569">{{ bugDetail.report.conclusion }}</p>
        </template>
        <el-alert v-else-if="bugDetail.status === 'FAILED'" type="error" show-icon :closable="false"
          :title="bugDetail.errorMessage || '分析失败'" />
      </template>
    </el-dialog>
  </div>
</template>
