<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { repoApi } from '../api/repo'
import type { RepoVO } from '../types'

const router = useRouter()
const repos = ref<RepoVO[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = ref({ name: '', description: '', file: null as File | null })
let pollTimer: number | undefined

const statusMeta: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { label: '排队中', type: 'info' },
  INDEXING: { label: '索引中', type: 'warning' },
  READY: { label: '就绪', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const res = await repoApi.list(page.value, 10)
    repos.value = res.list
    total.value = res.total
    // 有正在索引的库时自动轮询刷新
    const busy = res.list.some((r) => r.status === 'PENDING' || r.status === 'INDEXING')
    if (busy && !pollTimer) {
      pollTimer = window.setInterval(load, 2000)
    } else if (!busy && pollTimer) {
      clearInterval(pollTimer)
      pollTimer = undefined
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function onFileChange(file: { raw?: File }) {
  uploadForm.value.file = file.raw || null
}

async function submitUpload() {
  if (!uploadForm.value.name.trim()) return ElMessage.warning('请填写代码库名称')
  if (!uploadForm.value.file) return ElMessage.warning('请选择 ZIP 文件')
  uploading.value = true
  try {
    await repoApi.upload(uploadForm.value.file, uploadForm.value.name, uploadForm.value.description)
    ElMessage.success('上传成功，开始建立索引')
    uploadVisible.value = false
    uploadForm.value = { name: '', description: '', file: null }
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    uploading.value = false
  }
}

async function importSample() {
  try {
    const repo = await repoApi.importSample()
    ElMessage.success(`示例项目已就绪（${repo.status}）`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function reindex(repo: RepoVO) {
  try {
    await repoApi.reindex(repo.id)
    ElMessage.success('已开始重建索引')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function remove(repo: RepoVO) {
  try {
    await ElMessageBox.confirm(
      `将删除代码库「${repo.name}」及其全部索引数据，且不可恢复。确认删除？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await repoApi.remove(repo.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
onBeforeUnmount(() => pollTimer && clearInterval(pollTimer))
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px">
      <h2 style="margin: 0">代码库</h2>
      <div style="display: flex; gap: 10px">
        <el-button @click="importSample">导入示例项目（演示数据）</el-button>
        <el-button type="primary" @click="uploadVisible = true">上传 ZIP</el-button>
      </div>
    </div>

    <el-alert type="info" show-icon :closable="false" style="margin-bottom: 14px"
      title="支持 Java/XML/YAML/JSON/Markdown/SQL/JS/TS/HTML/CSS 等文本文件；自动跳过 .git、node_modules、target、密钥文件与二进制。" />

    <div class="rp-card" v-loading="loading">
      <el-table :data="repos" style="width: 100%">
        <el-table-column label="名称" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/repos/${row.id}`)">
              {{ row.name }}
              <el-tag v-if="row.isDemo" size="small" type="warning" style="margin-left: 6px">演示数据</el-tag>
            </el-link>
            <div style="color: #94a3b8; font-size: 12px">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'FAILED' && row.errorMessage" :content="row.errorMessage">
              <el-tag :type="statusMeta[row.status].type">{{ statusMeta[row.status].label }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusMeta[row.status].type">{{ statusMeta[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件数" width="90">
          <template #default="{ row }">{{ row.indexedCount }} / {{ row.fileCount }}</template>
        </el-table-column>
        <el-table-column label="体积" width="100">
          <template #default="{ row }">{{ formatSize(row.totalSize) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :disabled="row.status !== 'READY'"
              @click="router.push(`/qa/${row.id}`)">问答</el-button>
            <el-button text size="small" :disabled="row.status === 'PENDING' || row.status === 'INDEXING'"
              @click="reindex(row)">重建索引</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next"
        style="margin-top: 14px; justify-content: flex-end" @current-change="load" />
    </div>

    <el-dialog v-model="uploadVisible" title="上传代码库 ZIP" width="480px">
      <el-form label-position="top">
        <el-form-item label="代码库名称" required>
          <el-input v-model="uploadForm.name" maxlength="100" placeholder="例如：order-service" />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input v-model="uploadForm.description" maxlength="500" />
        </el-form-item>
        <el-form-item label="ZIP 文件（≤30MB）" required>
          <el-upload drag :auto-upload="false" :limit="1" accept=".zip" :on-change="onFileChange"
            :on-exceed="() => ElMessage.warning('一次只能上传一个文件')">
            <el-icon style="font-size: 34px; color: #94a3b8">⬆️</el-icon>
            <div>拖拽 ZIP 到这里，或点击选择</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传并建立索引</el-button>
      </template>
    </el-dialog>
  </div>
</template>
