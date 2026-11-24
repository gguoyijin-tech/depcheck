<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const form = ref({ username: '', password: '' })
const submitting = ref(false)

async function handleSubmit() {
  if (!form.value.username || !form.value.password) {
    return
  }
  submitting.value = true
  try {
    await session.login(form.value.username, form.value.password)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/tasks')
  } catch {
    // 提示由 http 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="never">
      <h1>应用依赖安全与合规分析平台</h1>
      <p class="sub">上传 Java 工程压缩包，扫描第三方依赖的已知漏洞与许可证风险</p>

      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="submitting" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <el-alert
        class="login-tip"
        type="info"
        :closable="false"
        show-icon
        title="使用集团分配的固定账号登录"
        description="账号由管理员统一分配，无需注册；同一账号在任何浏览器登录都能看到自己提交的任务。连续输错 5 次将锁定 1 分钟。"
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
  width: 440px;
  border-radius: 8px;
}

.login-card h1 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}

.sub {
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
