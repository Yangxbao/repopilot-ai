<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form.value.username, form.value.password)
    auth.setSession(res.token, res.user)
    ElMessage.success(`欢迎回来，${res.user.nickname || res.user.username}`)
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 6px">
        <span class="logo">RP</span>
        <span style="font-size: 20px; font-weight: 700">RepoPilot AI</span>
      </div>
      <p style="color: #64748b; margin: 0 0 20px">代码库知识问答与 Bug 分析 Agent</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="用户名" data-testid="login-username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="密码"
            data-testid="login-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <p style="margin-top: 16px; text-align: center; color: #64748b">
        还没有账号？<router-link to="/register" style="color: var(--rp-primary)">注册</router-link>
      </p>
      <el-alert type="info" :closable="false" show-icon
        title="第一个注册的用户自动成为管理员" />
    </div>
  </div>
</template>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--rp-bg);
}

.auth-card {
  width: 380px;
  background: #fff;
  border: 1px solid var(--rp-border);
  border-radius: 14px;
  padding: 32px;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--rp-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
</style>
