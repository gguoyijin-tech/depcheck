<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProject } from '@/api/project'
import { cancelTask, createTask, getSummary, getTask, listDependencies, listVulnerabilities } from '@/api/task'
import StatusTag from '@/components/StatusTag.vue'
import { downloadHtmlReport } from '@/utils/download'
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

const summary = ref(null)
const tab = ref('vuln')
const vuln = ref({ page: 1, size: 10, total: 0, records: [] })
const dep = ref({ page: 1, size: 10, total: 0, records: [] })
const vulnLoading = ref(false)
const depLoading = ref(false)
const downloadLoading = ref(false)

async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    task.value = await getTask(taskId)
    if (task.value && task.value.status === 'SUCCESS' && !summary.value) {
      loadSummary()
      loadVulns()
    }
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

async function loadSummary() {
  try {
    summary.value = await getSummary(taskId)
  } catch {
    summary.value = null
  }
}

async function loadVulns(page = 1) {
  vulnLoading.value = true
  try {
    const data = await listVulnerabilities(taskId, { page, size: vuln.value.size })
    vuln.value = { ...vuln.value, ...data }
  } finally {
    vulnLoading.value = false
  }
}

async function loadDeps(page = 1) {
  depLoading.value = true
  try {
    const data = await listDependencies(taskId, { page, size: dep.value.size })
    dep.value = { ...dep.value, ...data }
  } finally {
    depLoading.value = false
  }
}

function handleTab(name) {
  if (name === 'dep' && dep.value.total === 0) {
    loadDeps()
  }
}

async function handleDownload() {
  downloadLoading.value = true
  try {
    await downloadHtmlReport(taskId)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    downloadLoading.value = false
  }
}

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
          <el-button v-if="task.hasReport" :loading="downloadLoading" @click="handleDownload">下载报告</el-button>
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

      <template v-if="task.status === 'SUCCESS'">
        <el-row :gutter="12">
          <el-col :span="6"><el-card shadow="never" class="stat"><div class="num">{{ summary ? summary.dependencyCount : '-' }}</div><div class="lbl">依赖总数</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never" class="stat"><div class="num danger">{{ summary ? summary.vulnerabilityCount : '-' }}</div><div class="lbl">漏洞总数</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never" class="stat"><div class="num warn">{{ summary ? summary.severityCounts.CRITICAL + summary.severityCounts.HIGH : '-' }}</div><div class="lbl">严重 / 高危</div></el-card></el-col>
          <el-col :span="6"><el-card shadow="never" class="stat"><div class="num warn">{{ summary ? summary.licenseRiskCounts.HIGH : '-' }}</div><div class="lbl">许可证高风险</div></el-card></el-col>
        </el-row>
        <el-card shadow="never" class="card">
          <el-tabs v-model="tab" @tab-change="handleTab">
            <el-tab-pane label="漏洞" name="vuln">
              <el-table v-loading="vulnLoading" :data="vuln.records" stripe size="small">
                <el-table-column prop="cveId" label="CVE" width="170" />
                <el-table-column prop="cvssScore" label="CVSS" width="80" />
                <el-table-column label="等级" width="110"><template #default="{ row }"><el-tag size="small" effect="light">{{ row.severity || 'UNKNOWN' }}</el-tag></template></el-table-column>
                <el-table-column label="依赖" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.dependencyName || '-' }}<span v-if="row.dependencyVersion" class="muted">:{{ row.dependencyVersion }}</span></template></el-table-column>
                <el-table-column prop="cwe" label="CWE" width="110" show-overflow-tooltip />
              </el-table>
              <el-pagination class="pager" layout="total, prev, pager, next" :total="vuln.total" :page-size="vuln.size" :current-page="vuln.page" @current-change="loadVulns" />
            </el-tab-pane>
            <el-tab-pane label="依赖" name="dep">
              <el-table v-loading="depLoading" :data="dep.records" stripe size="small">
                <el-table-column prop="name" label="依赖" min-width="240" show-overflow-tooltip />
                <el-table-column prop="version" label="版本" width="150" show-overflow-tooltip />
                <el-table-column prop="licenseName" label="许可证" width="150" show-overflow-tooltip />
                <el-table-column label="风险" width="100"><template #default="{ row }"><el-tag size="small" effect="light">{{ row.licenseRisk || 'UNKNOWN' }}</el-tag></template></el-table-column>
                <el-table-column prop="vulnerabilityCount" label="漏洞" width="80" />
              </el-table>
              <el-pagination class="pager" layout="total, prev, pager, next" :total="dep.total" :page-size="dep.size" :current-page="dep.page" @current-change="loadDeps" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </template>
      <el-card v-else shadow="never" class="card">
        <template #header><span class="card-title">扫描结果</span></template>
        <el-empty description="任务结束后这里会展示漏洞、依赖与许可证结果" />
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
.stat {
  text-align: center;
}

.num {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.num.danger {
  color: #f56c6c;
}

.num.warn {
  color: #e6a23c;
}

.lbl {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>