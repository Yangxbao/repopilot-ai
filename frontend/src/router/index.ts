import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/register', component: () => import('../views/RegisterView.vue') },
    {
      path: '/',
      component: () => import('../views/LayoutView.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'repos', component: () => import('../views/ReposView.vue') },
        { path: 'repos/:id', component: () => import('../views/RepoDetailView.vue') },
        { path: 'qa', component: () => import('../views/QaView.vue') },
        { path: 'qa/:repoId', component: () => import('../views/QaView.vue') },
        { path: 'bug', component: () => import('../views/BugAnalysisView.vue') },
        { path: 'bug/:repoId', component: () => import('../views/BugAnalysisView.vue') },
        { path: 'history', component: () => import('../views/HistoryView.vue') },
        { path: 'model', component: () => import('../views/ModelConfigView.vue') },
        { path: 'users', component: () => import('../views/UsersView.vue'), meta: { admin: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

// 路由守卫：未登录跳登录页；非 ADMIN 访问管理页跳工作台
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.path.startsWith('/login') && !to.path.startsWith('/register') && !auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.admin && !auth.isAdmin) {
    return '/dashboard'
  }
})

export default router
