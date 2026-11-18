<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { listProjects } from '@/api/project'
import { cancelTask, createTask, listTasks } from '@/api/task'
import StatusTag from '@/components/StatusTag.vue'
import {
  STATUS_OPTIONS,
  formatDateTime,
  formatDuration,
  isActive,
  progressStatus,
  stageText
} from '@/utils/task'

const router = useRouter()

const loading = ref(false)
const autoRefresh = ref(true)
const statusFilter = ref('')
const tasks = ref([])
const projects = ref([])
let timer = null

const projectNames = computed(() => {
  const map = {}
  projects.value.forEach((project) => {
    map[project.id] = project.name
  })
  return map
})

const summary = computed(() => ({
  total: tasks.value.length,
  running: tasks.value.filter((task) => isActive(task.status)).length,
  success: tasks.value.filter((task) => task.status === 'SUCCESS').length,
  failed: tasks.value.filter((task) => task.status === 'FAILED').length
}))

async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    const [list, projectList] = await Promise.all([
      listTasks(statusFilter.value),
      projects.value.length ? Promise.resolve(projects.value) : listProjects()
    ])
    tasks.value = list
    projects.value = projectList
  } catch {
    // 拦截器已统一提示
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function ensureTimer() {
  if (timer) {
    clearInterval(timer)
  }
  // 3 秒轮询：只在"自动刷新"打开时执行
  timer = setInterval(() => {
    if (autoRefresh.value) {
      load(true)
    }
  }, 3000)
}

onMounted(() => {
  load()
  ensureTimer()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

watch(statusFilter, () => load())

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消任务 ${row.taskNo} 吗？排队中会立即取消，正在执行的会在当前阶段结束后生效。`,
      '取消任务',
      { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '不取消' }
    )
  } catch {
    return
  }
  try {
    await cancelTask(row.id)
    ElMessage.success('已请求取消')
    await load(true)
  } catch {
    // 拦截器已提示（例如任务已结束 → 1001）
  }
}

async function handleRescan(row) {
  try {
    const task = await createTask(row.projectId)
    ElMessage.success('已提交新任务')
    router.push(`/tasks/${task.id}`)
  } catch {
    // 拦截器已提示
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>任务列表</h2>
        <p class="sub">当前会话的任务，3 秒自动刷新；进度为阶段进度，不是精确百分比。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load()">刷新</el-button>
        <el-button type="primary" @click="router.push('/scan/new')">上传与扫描</el-button>
      </div>
    </div>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" style="width: 160px">
          <el-option
            v-for="item in STATUS_OPTIONS"
            :key="item.value || 'all'"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <div class="switch-line">
          <el-switch v-model="autoRefresh" />
          <span>自动刷新（3 秒）</span>
        </div>

        <div class="summary">
          <el-tag type="info" effect="plain">共 {{ summary.total }}</el-tag>
          <el-tag type="warning" effect="plain">进行中 {{ summary.running }}</el-tag>
          <el-tag type="success" effect="plain">成功 {{ summary.success }}</el-tag>
          <el-tag type="danger" effect="plain">失败 {{ summary.failed }}</el-tag>
        </div>
      </div>

      <el-table v-loading="loading" :data="tasks" stripe size="default" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskNo" label="任务号" width="160" />
        <el-table-column label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ projectNames[row.projectId] || `#${row.projectId}` }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="220">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="progressStatus(row.status)"
              :stroke-width="8"
            />
            <span class="stage">{{ stageText(row.stage) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/tasks/${row.id}`)">详情</el-button>
            <el-button
              v-if="isActive(row.status)"
              link
              type="warning"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
            <el-button v-else link type="primary" @click="handleRescan(row)">重新扫描</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tasks.length === 0" description="还没有任务">
        <el-button type="primary" @click="router.push('/scan/new')">上传一个工程开始扫描</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #303133;
}

.sub {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.card {
  border-radius: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.summary {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.stage {
  font-size: 12px;
  color: #909399;
}
</style>
