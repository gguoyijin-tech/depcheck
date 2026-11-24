import { createRouter, createWebHashHistory } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const routes = [
  { path: '/', redirect: '/tasks' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/scan/new',
    name: 'scan-new',
    component: () => import('@/views/UploadView.vue'),
    meta: { title: '上传与扫描' }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('@/views/TaskListView.vue'),
    meta: { title: '任务列表' }
  },
  {
    path: '/tasks/:id',
    name: 'task-detail',
    component: () => import('@/views/TaskDetailView.vue'),
    meta: { title: '任务详情' }
  },
  {
    path: '/reports',
    name: 'reports',
    component: () => import('@/views/ReportsView.vue'),
    meta: { title: '报告下载' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/tasks' }
]

// 用 hash 模式：后端还没有 SPA fallback，刷新 /tasks 不会 404
const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · depcheck` : 'depcheck'
  const session = useSessionStore()
  if (!to.meta.public && !session.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && session.loggedIn) {
    return { name: 'tasks' }
  }
  return true
})

export default router
