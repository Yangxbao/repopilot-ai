<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { dashboardApi } from '../api/dashboard'
import { onMounted } from 'vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const sidebarOpen = ref(false)
const aiMode = ref('mock')

onMounted(async () => {
  if (auth.isLoggedIn) {
    try {
      aiMode.value = (await dashboardApi.summary()).aiMode
    } catch {
      /* 汇总失败不影响布局 */
    }
  }
})

const menus = computed(() => {
  const base = [
    { path: '/dashboard', label: '工作台', icon: '🏠' },
    { path: '/repos', label: '代码库', icon: '📦' },
    { path: '/qa', label: '代码问答', icon: '💬' },
    { path: '/bug', label: 'Bug 分析', icon: '🐛' },
    { path: '/history', label: '历史报告', icon: '🗂️' },
    { path: '/model', label: 'AI 模型配置', icon: '🤖' }
  ]
  return auth.isAdmin ? [...base, { path: '/users', label: '用户管理', icon: '👥' }] : base
})

function logout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="rp-layout">
    <aside class="rp-sidebar" :class="{ open: sidebarOpen }">
      <div class="rp-brand"><span class="logo">RP</span> RepoPilot AI</div>
      <el-menu class="rp-menu" :default-active="route.path" router>
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path" @click="sidebarOpen = false">
          <span style="margin-right: 8px">{{ m.icon }}</span>{{ m.label }}
          <el-tag v-if="m.path === '/qa' && aiMode === 'mock'" size="small" type="warning"
            style="margin-left: auto">模拟</el-tag>
        </el-menu-item>
      </el-menu>
      <div style="padding: 14px 16px; font-size: 12px; opacity: 0.6">v0.1.0 · 学习项目</div>
    </aside>

    <div class="rp-main">
      <header class="rp-header">
        <div style="display: flex; align-items: center; gap: 12px">
          <el-button text @click="sidebarOpen = !sidebarOpen" style="display: none" class="mobile-toggle">☰</el-button>
          <span style="font-weight: 600">{{ route.meta.title || 'RepoPilot AI' }}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 12px">
          <el-tag :type="aiMode === 'mock' ? 'warning' : 'success'" size="small">
            {{ aiMode === 'mock' ? 'Mock 模拟模式' : 'Remote 真实模型' }}
          </el-tag>
          <el-dropdown>
            <span style="cursor: pointer; display: flex; align-items: center; gap: 6px">
              <el-avatar :size="28" style="background: var(--rp-primary)">
                {{ (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1).toUpperCase() }}
              </el-avatar>
              <span>{{ auth.user?.nickname || auth.user?.username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="rp-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.mobile-toggle {
  display: none;
}

@media (max-width: 768px) {
  .mobile-toggle {
    display: inline-flex;
  }
}
</style>
