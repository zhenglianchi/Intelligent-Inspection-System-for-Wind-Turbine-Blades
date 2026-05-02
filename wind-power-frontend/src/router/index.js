import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/index.js'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { requiresAuth: true } },
  { path: '/chat', name: 'Chat', component: () => import('../views/AgentChat.vue'), meta: { requiresAuth: true } },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !getToken()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && getToken()) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
