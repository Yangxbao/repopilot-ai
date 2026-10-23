<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { qaApi } from '../api/qa'
import { repoApi } from '../api/repo'
import { useAuthStore } from '../stores/auth'
import type { QaVO, RepoVO } from '../types'
import EvidenceList from '../components/EvidenceList.vue'

const route = useRoute()
const auth = useAuthStore()
const md = new MarkdownIt({ html: false, linkify: false })

const repos = ref<RepoVO[]>([])
const repoId = ref<number | null>(route.params.repoId ? Number(route.params.repoId) : null)
const question = ref('')
const asking = ref(false)
const history = ref<QaVO[]>([])
const current = ref<QaVO | null>(null)

const readyRepos = computed(() => repos.value.filter((r) => r.status === 'READY'))
const renderedAnswer = computed(() => (current.value?.answer ? md.render(current.value.answer) : ''))

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

async function loadHistory() {
  try {
    history.value = (await qaApi.history(repoId.value ?? undefined, 1, 20)).list
  } catch {
    /* 历史加载失败不阻塞提问 */
  }
}

async function ask() {
  if (!repoId.value) return ElMessage.warning('请先选择一个已就绪的代码库')
  const q = question.value.trim()
  if (q.length < 2) return ElMessage.warning('请输入问题')
  asking.value = true
  current.value = null
  try {
    current.value = await qaApi.ask(repoId.value, q)
    question.value = ''
    await loadHistory()
  } catch (e) {
    ElMessage.error((e as Error).message)
    current.value = {
      id: 0, repoId: repoId.value, repoName: null, question: q, answer: null,
      evidence: [], provider: '-', model: null, durationMs: 0, status: 'FAILED',
      errorMessage: (e as Error).message, createdAt: new Date().toISOString()
    }
  } finally {
    asking.value = false
  }
}

async function viewHistory(item: QaVO) {
  try {
    current.value = await qaApi.detail(item.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await loadRepos()
  await loadHistory()
})
</script>

<template>
  <div>
    <h2 style="margin-top: 0">代码问答</h2>

    <el-alert v-if="auth.user && !readyRepos.length" type="warning" show-icon :closable="false"
      title="还没有已就绪的代码库。请先到「代码库」页面导入示例项目或上传 ZIP。"
      style="margin-bottom: 14px" />

    <div style="display: grid; grid-template-columns: 300px 1fr; gap: 14px" class="qa-grid">
      <div class="rp-card" style="align-self: start">
        <div style="font-weight: 600; margin-bottom: 8px">选择代码库</div>
        <el-select v-model="repoId" placeholder="选择代码库" style="width: 100%" @change="loadHistory">
          <el-option v-for="r in readyRepos" :key="r.id" :value="r.id" :label="r.name" />
        </el-select>

        <div style="font-weight: 600; margin: 16px 0 8px">历史问答</div>
        <el-empty v-if="!history.length" description="暂无记录" :image-size="60" />
        <div v-for="h in history" :key="h.id" class="hist" @click="viewHistory(h)">
          <div style="display: flex; justify-content: space-between; gap: 6px">
            <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ h.question }}</span>
            <el-tag size="small" :type="h.status === 'OK' ? 'success' : 'danger'">{{ h.status }}</el-tag>
          </div>
          <div style="color: #94a3b8; font-size: 11px; margin-top: 3px">
            {{ h.provider }} · {{ h.durationMs }}ms
          </div>
        </div>
      </div>

      <div style="display: flex; flex-direction: column; gap: 14px; min-width: 0">
        <div class="rp-card">
          <el-input v-model="question" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="试试问：用户注册流程在哪里实现？ / TaskController 调用了哪些 Service？ / 数据库连接参数配置在哪里？" />
          <div style="display: flex; justify-content: flex-end; margin-top: 10px">
            <el-button type="primary" :loading="asking" @click="ask">提问</el-button>
          </div>
        </div>

        <template v-if="current">
          <div class="rp-card" v-loading="asking">
            <div style="display: flex; gap: 12px; margin-bottom: 10px">
              <el-avatar :size="30" style="background: #f59e0b; flex-shrink: 0">我</el-avatar>
              <div style="background: #f1f5f9; border-radius: 10px; padding: 10px 14px">{{ current.question }}</div>
            </div>
            <div v-if="current.status === 'FAILED'" style="display: flex; gap: 12px">
              <el-avatar :size="30" style="background: var(--rp-primary); flex-shrink: 0">AI</el-avatar>
              <el-alert type="error" :closable="false" show-icon :title="`分析失败：${current.errorMessage}`" />
            </div>
            <div v-else style="display: flex; gap: 12px">
              <el-avatar :size="30" style="background: var(--rp-primary); flex-shrink: 0">AI</el-avatar>
              <div style="flex: 1; min-width: 0">
                <div class="md-body" v-html="renderedAnswer"></div>
                <div style="margin-top: 8px; color: #94a3b8; font-size: 12px">
                  {{ current.provider }} · {{ current.model }} · {{ current.durationMs }}ms
                </div>
              </div>
            </div>
          </div>

          <div class="rp-card" v-if="current.evidence?.length">
            <div style="font-weight: 600; margin-bottom: 10px">📎 证据引用（{{ current.evidence.length }}）</div>
            <EvidenceList :evidence="current.evidence" />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hist {
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  font-size: 13px;
}

.hist:hover {
  background: #f1f5f9;
  border-color: var(--rp-border);
}

@media (max-width: 1000px) {
  .qa-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
