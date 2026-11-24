<script setup>
import { ElMessageBox } from 'element-plus'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const activePath = computed(() => (route.path.startsWith('/tasks') ? '/tasks' : route.path))

async function handleLogout() {
  try {
    await ElMessageBox.confirm(
      '退出后需要重新登录；账号下的项目与报告仍会保留。',
      '确认退出',
      { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await session.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header" height="56px">
      <div class="brand">
        <span class="brand-dot" />
        <span class="brand-name">应用依赖安全与合规分析</span>
        </div>

      <nav v-if="session.loggedIn" class="nav">
        <router-link to="/tasks" :class="['nav-link', { active: activePath === '/tasks' }]">
          任务列表
        </router-link>
        <router-link to="/scan/new" :class="['nav-link', { active: activePath === '/scan/new' }]">
          上传与扫描
        </router-link>
        <router-link to="/reports" :class="['nav-link', { active: activePath === '/reports' }]">
          报告下载
        </router-link>
      </nav>

      <div v-if="session.loggedIn" class="user">
        <span class="user-name">{{ session.username }}</span>
        <el-button link type="primary" @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f5f7fa;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #409eff;
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.nav {
  display: flex;
  gap: 8px;
}

.nav-link {
  padding: 6px 12px;
  border-radius: 4px;
  color: #606266;
  text-decoration: none;
  font-size: 14px;
}

.nav-link:hover {
  background: #f2f6fc;
  color: #409eff;
}

.nav-link.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.user {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 13px;
  color: #909399;
}

.app-main {
  padding: 20px 24px 40px;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
}
</style>
