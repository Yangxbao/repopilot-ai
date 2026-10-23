<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api/dashboard'

interface UserRow {
  id: number
  username: string
  nickname: string
  role: string
  status: string
  createdAt: string
}

const rows = ref<UserRow[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await userApi.list(page.value, 10)
    rows.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function toggle(row: UserRow) {
  const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      `确定要${target === 'DISABLED' ? '禁用' : '启用'}用户「${row.username}」吗？`,
      '确认操作',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await userApi.changeStatus(row.id, target)
    ElMessage.success('已更新')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <h2 style="margin-top: 0">用户与权限管理</h2>
    <el-alert type="info" show-icon :closable="false" style="margin-bottom: 14px"
      title="权限说明：本页仅 ADMIN 可见，且后端接口用 @PreAuthorize 校验角色——前端隐藏只是体验优化。" />

    <div class="rp-card">
      <el-table :data="rows">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button text size="small" :type="row.status === 'ACTIVE' ? 'danger' : 'primary'" @click="toggle(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end" @current-change="load" />
    </div>
  </div>
</template>
