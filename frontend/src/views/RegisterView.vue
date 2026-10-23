<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = ref({ username: '', password: '', confirm: '', nickname: '' })
const loading = ref(false)

async function submit() {
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(form.value.username)) {
    ElMessage.warning('用户名只能包含字母、数字和下划线，长度 3-32')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度至少 6 位')
    return
  }
  if (form.value.password !== form.value.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    const res = await authApi.register(form.value.username, form.value.password, form.value.nickname)
    auth.setSession(res.token, res.user)
    ElMessage.success('注册成功，已自动登录')
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
        <span style="font-size: 20px; font-weight: 700">注册账号</span>
      </div>
      <p style="color: #64748b; margin: 0 0 20px">第一个注册的用户自动成为管理员</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="字母/数字/下划线，3-32 位" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" placeholder="展示用昵称" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirm" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">注 册</el-button>
      </el-form>
      <p style="margin-top: 16px; text-align: center; color: #64748b">
        已有账号？<router-link to="/login" style="color: var(--rp-primary)">登录</router-link>
      </p>
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
