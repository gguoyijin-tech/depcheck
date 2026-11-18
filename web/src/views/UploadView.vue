<script setup>
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { uploadProject } from '@/api/project'
import { createTask } from '@/api/task'

/** 与后端 spring.servlet.multipart.max-file-size 保持一致。 */
const MAX_SIZE = 512 * 1024 * 1024

const router = useRouter()

const fileList = ref([])
const selectedFile = ref(null)
const uploading = ref(false)
const percent = ref(0)
const uploaded = ref(null)
const scanning = ref(false)

const sizeText = computed(() => {
  if (!selectedFile.value) {
    return ''
  }
  return `${(selectedFile.value.size / 1024 / 1024).toFixed(1)} MB`
})

function handleChange(uploadFile, uploadFiles) {
  const raw = uploadFile && uploadFile.raw
  if (!raw) {
    return
  }
  if (!/\.zip$/i.test(raw.name)) {
    ElMessage.error('只支持 .zip 压缩包')
    fileList.value = []
    selectedFile.value = null
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.error('文件超过 512 MB 上限')
    fileList.value = []
    selectedFile.value = null
    return
  }
  selectedFile.value = raw
  uploaded.value = null
  percent.value = 0
  fileList.value = uploadFiles.slice(-1)
}

function handleRemove() {
  selectedFile.value = null
  uploaded.value = null
  percent.value = 0
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要扫描的 zip 文件')
    return
  }
  uploading.value = true
  percent.value = 0
  try {
    uploaded.value = await uploadProject(selectedFile.value, (p) => {
      percent.value = p
    })
    ElMessage.success('上传并解压成功')
  } catch {
    uploaded.value = null
  } finally {
    uploading.value = false
  }
}

async function handleScan() {
  if (!uploaded.value) {
    return
  }
  scanning.value = true
  try {
    const task = await createTask(uploaded.value.id)
    ElMessage.success('任务已提交，正在跳转…')
    router.push(`/tasks/${task.id}`)
  } catch {
    // 错误提示已由 http 拦截器统一弹出
  } finally {
    scanning.value = false
  }
}

function handleReset() {
  fileList.value = []
  selectedFile.value = null
  uploaded.value = null
  percent.value = 0
}
</script>

<template>
  <div class="page">
    <el-page-header content="上传与扫描" @back="router.push('/tasks')" />

    <el-card class="card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>1. 选择工程压缩包</span>
          <span class="card-head-tip">zip · 最大 512 MB · 解压后最大 2 GB / 5 万文件</span>
        </div>
      </template>

      <el-upload
        v-model:file-list="fileList"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :disabled="uploading || !!uploaded"
        accept=".zip"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">把 zip 拖到这里，或 <em>点击选择</em></div>
        <template #tip>
          <div class="upload-tip">
            要看到 85 依赖 / 53 漏洞的基线效果，请上传 petclinic 工程包；demo 小包只有个位数依赖。
          </div>
        </template>
      </el-upload>

      <div v-if="selectedFile" class="file-line">
        已选择：<strong>{{ selectedFile.name }}</strong>（{{ sizeText }}）
      </div>

      <div v-if="uploading" class="progress-line">
        <el-progress :percentage="percent" :stroke-width="10" />
        <span class="progress-tip">
          {{ percent < 100 ? '正在上传…' : '上传完成，服务端正在解压并校验（请稍候）…' }}
        </span>
      </div>

      <div class="actions">
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile || !!uploaded"
          @click="handleUpload"
        >
          上传并解压
        </el-button>
        <el-button :disabled="uploading" @click="handleReset">重新选择</el-button>
      </div>
    </el-card>

    <el-card v-if="uploaded" class="card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>2. 提交扫描</span>
          <el-tag type="success" size="small" effect="light">上传成功</el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="项目 ID">{{ uploaded.id }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ uploaded.name }}</el-descriptions-item>
        <el-descriptions-item label="原始文件名">{{ uploaded.originalName }}</el-descriptions-item>
        <el-descriptions-item label="解压文件数">{{ uploaded.fileCount }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-button type="success" :loading="scanning" @click="handleScan">开始扫描</el-button>
        <el-button @click="handleReset">再传一个</el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="扫描在后台线程池执行"
        description="提交后立即返回任务号，可在任务详情页看进度（PREPARE → SCAN → ANALYZE → REPORT → PERSIST）。petclinic 约 15 秒。"
      />
    </el-card>
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

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #303133;
}

.card-head-tip {
  font-weight: 400;
  font-size: 12px;
  color: #909399;
}

.upload-icon {
  font-size: 42px;
  color: #c0c4cc;
  margin-bottom: 8px;
}

.upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.file-line {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}

.progress-line {
  margin-top: 12px;
}

.progress-tip {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.actions {
  margin: 16px 0;
  display: flex;
  gap: 8px;
}
</style>
