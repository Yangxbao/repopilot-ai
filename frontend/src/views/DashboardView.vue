<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '../api/dashboard'
import type { DashboardSummary } from '../types'

const router = useRouter()
const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    summary.value = await dashboardApi.summary()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h2 style="margin-top: 0">工作台</h2>

    <el-alert v-if="summary?.aiMode === 'mock'" type="warning" show-icon :closable="false"
      title="当前为 Mock 模拟模式：AI 回答由内置模板生成（引用的代码位置来自真实检索）。可在「AI 模型配置」页切换为真实模型。"
      style="margin-bottom: 16px" />

    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px">
      <div class="rp-card rp-stat" v-loading="loading">
        <span class="value">{{ summary?.repoCount ?? '-' }}</span><span class="label">我的代码库</span>
      </div>
      <div class="rp-card rp-stat">
        <span class="value">{{ summary?.fileCount ?? '-' }}</span><span class="label">已索引文件</span>
      </div>
      <div class="rp-card rp-stat">
        <span class="value">{{ summary?.qaCount ?? '-' }}</span><span class="label">问答次数</span>
      </div>
      <div class="rp-card rp-stat">
        <span class="value">{{ summary?.bugReportCount ?? '-' }}</span><span class="label">Bug 分析报告</span>
      </div>
    </div>

    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; margin-top: 16px">
      <div class="rp-card">
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0 0 10px">最近问答</h3>
          <el-button text type="primary" @click="router.push('/qa')">去提问 →</el-button>
        </div>
        <el-empty v-if="!summary?.recentQas?.length" description="还没有问答记录，去「代码问答」体验" :image-size="70" />
        <ul v-else style="list-style: none; margin: 0; padding: 0">
          <li v-for="q in summary.recentQas" :key="q.id"
            style="padding: 9px 0; border-bottom: 1px dashed var(--rp-border); display: flex; justify-content: space-between; gap: 10px">
            <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ q.question }}</span>
            <el-tag size="small" :type="q.status === 'OK' ? 'success' : 'danger'">{{ q.status }}</el-tag>
          </li>
        </ul>
      </div>

      <div class="rp-card">
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0 0 10px">最近 Bug 分析</h3>
          <el-button text type="primary" @click="router.push('/bug')">去分析 →</el-button>
        </div>
        <el-empty v-if="!summary?.recentReports?.length" description="还没有分析报告" :image-size="70" />
        <ul v-else style="list-style: none; margin: 0; padding: 0">
          <li v-for="r in summary.recentReports" :key="r.id"
            style="padding: 9px 0; border-bottom: 1px dashed var(--rp-border); display: flex; justify-content: space-between; gap: 10px">
            <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ r.title }}</span>
            <el-tag size="small" :type="r.status === 'DONE' ? 'success' : 'danger'">{{ r.status }}</el-tag>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
