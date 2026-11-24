<script setup>
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listProjects } from '@/api/project'
import { listTasks } from '@/api/task'
import { downloadHtmlReport } from '@/utils/download'
import { formatDateTime, formatDuration } from '@/utils/task'

const router = useRouter()

const tasks = ref([])
const projectNames = ref({})
const loading = ref(false)
const downloadingId = ref(0)

async function load() {
  loading.value = true
  try {
    const [list, projects] = await Promise.all([listTasks('SUCCESS'), listProjects()])
    tasks.value = list
    const map = {}
    projects.forEach((p) => {
      map[p.id] = p.name
    })
    projectNames.value = map
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function handleDownload(row) {
  downloadingId.value = row.id
  try {
    await downloadHtmlReport(row.id)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    downloadingId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>报告下载</h2>
        <p class="sub">只列出扫描成功的任务；报告按保留策略清理后显示“已清理”，重新扫描即可再生成。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tasks" stripe size="middle">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskNo" label="任务号" width="160" />
        <el-table-column label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ projectNames[row.projectId] || `#${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="175">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="报告" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.hasReport" type="success" size="small" effect="light">可下载</el-tag>
            <el-tag v-else size="small" effect="plain">已清理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!row.hasReport"
              :loading="downloadingId === row.id"
              @click="handleDownload(row)"
            >
              下载 HTML
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tasks.length === 0" description="还没有扫描成功的任务">
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
</style>
