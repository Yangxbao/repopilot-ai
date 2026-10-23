import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发模式把 /api 代理到本机 8080 的后端，前端用相对路径请求即可
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
