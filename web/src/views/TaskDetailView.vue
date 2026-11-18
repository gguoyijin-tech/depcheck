<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProject } from '@/api/project'
import { cancelTask, createTask, getTask } from '@/api/task'
import StatusTag from '@/components/StatusTag.vue'
import {
  formatDateTime,
  formatDuration,
  isActive,
  progressStatus,
  stageText
} from '@/utils/task'

const route = useRoute()
const router = useRouter()

const taskId = Number(route.params.id)
const task = ref(null)
const project = ref(null)
const loading = ref(true)
const canceling = ref(false)
const rescanning = ref(false)
let timer = null

const active = computed(() => (task.value ? isActive(task.value.status) : false))
const progress = computed(() => (task.value && task.value.progress) || 0)

async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    task.value = await getTask(taskId)
    if (task.value && task.value.projectId && !project.value) {
      try {
        project.value = await getProject(task.value.projectId)
      } catch {
        project.value = null
      }
    }
  } catch {
    task.value = null
  } finally {
    loading.value = false
  }
}

function ensureTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  // 只有还在跑才轮询，结束后自动停
  timer = setInterval(async () => {
    if (!task.value || !isActive(task.value.status)) {
      clearInterval(timer)
      timer = null
      return
    }
    await load(true)
  }, 2000)
}

onMounted(async () => {
  await load()
  ensureTimer()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      '排队中会立即取消；正在执行的会在当前阶段结束后生效（DC 的分析阶段不可中断）。',
      '取消任务',
      { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '不取消' }
    )
  } catch {
    return
  }
  canceling.value = true
  try {
    await cancelTask(taskId)
    ElMessage.success('已请求取消')
    await load(true)
    ensureTimer()
  } catch {
    // 拦截器已提示
  } finally {
    canceling.value = false
  }
}

async function handleRescan() {
  if (!task.value) {
    return
  }
  rescanning.value = true
  try {
    const created = await createTask(task.value.projectId)
    ElMessage.success('已提交新任务')
    router.push(`/tasks/${created.id}`)
  } catch {
    // 拦截器已提示
  } finally {
    rescanning.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-page-header content="任务详情" @back="router.push('/tasks')" />

    <el-skeleton v-if="loading && !task" :rows="6" animated />

    <el-empty v-else-if="!task" description="任务不存在，或不属于当前会话">
      <el-button type="primary" @click="router.push('/tasks')">返回列表</el-button>
    </el-empty>

    <template v-else>
      <el-card shadow="never" class="card">
        <div class="head">
          <div class="head-left">
            <h2>任务 {{ task.taskNo }}</h2>
            <StatusTag :status="task.status" />
          </div>
          <div class="head-actions">
            <el-button v-if="active" type="warning" :loading="canceling" @click="handleCancel">
              取消任务
            </el-button>
            <el-button v-else type="primary" :loading="rescanning" @click="handleRescan">
              重新扫描
            </el-button>
            <el-button @click="router.push('/tasks')">返回列表</el-button>
          </div>
        </div>

        <el-progress
          :percentage="progress"
          :status="progressStatus(task.status)"
          :stroke-width="14"
          :indeterminate="active && progress === 0"
          text-inside
        />

        <div class="stage-line">
          <span>当前阶段：<strong>{{ stageText(task.stage) }}</strong></span>
          <span>耗时：{{ formatDuration(task.durationMs) }}</span>
          <span v-if="active" class="hint">每 2 秒自动刷新</span>
        </div>
      </el-card>

      <el-alert
        v-if="task.status === 'FAILED'"
        type="error"
        show-icon
        :closable="false"
        title="扫描失败"
        :description="`错误码 ${task.errorCode}：${task.errorMsg || '无详细信息'}`"
      />

      <el-alert
        v-if="task.status === 'CANCELED'"
        type="info"
        show-icon
        :closable="false"
        title="任务已取消"
        description="报告目录已清理，未写入任何依赖与漏洞记录。"
      />

      <el-card shadow="never" class="card">
        <template #header><span class="card-title">基本信息</span></template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="任务 ID">{{ task.id }}</el-descriptions-item>
          <el-descriptions-item label="任务号">{{ task.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ task.status }}</el-descriptions-item>
          <el-descriptions-item label="项目 ID">{{ task.projectId }}</el-descriptions-item>
          <el-descriptions-item label="项目名称">
            {{ project ? project.name : `#${task.projectId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(task.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="card">
        <template #header><span class="card-title">扫描结果</span></template>
        <el-empty description="依赖清单 / 漏洞清单 / 许可证合规将在 D10 接入（数据已在 dependency、vulnerability 表中）">
          <el-tag effect="plain" type="info">D10 交付</el-tag>
        </el-empty>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  border-radius: 8px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head-left h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.stage-line {
  margin-top: 10px;
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #606266;
}

.hint {
  color: #909399;
}

.card-title {
  font-weight: 600;
  color: #303133;
}
</style>
