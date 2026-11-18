<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const nickname = ref(session.nickname || '')
const submitting = ref(false)

function handleSubmit() {
  submitting.value = true
  try {
    session.login(nickname.value)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/tasks')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="never">
      <div class="login-head">
        <h1>应用依赖安全与合规分析平台</h1>
        <p>上传 Java 工程压缩包，扫描第三方依赖的已知漏洞与许可证风险</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="你的称呼">
          <el-input
            v-model="nickname"
            placeholder="例如：张工"
            maxlength="32"
            show-word-limit
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="submitting" @click="handleSubmit">
          进入平台
        </el-button>
      </el-form>

      <el-alert
        class="login-tip"
        type="info"
        :closable="false"
        show-icon
        title="当前是演示登录"
        description="后端用 X-Session-Id 做数据隔离。浏览器会生成并保存一个会话标识，换浏览器就看不到之前的项目；D11 会替换成正式登录。"
      />
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 62vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 460px;
  border-radius: 8px;
}

.login-head h1 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}

.login-head p {
  margin: 0 0 20px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.login-btn {
  width: 100%;
}

.login-tip {
  margin-top: 20px;
}
</style>
