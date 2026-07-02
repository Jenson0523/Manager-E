<template>
  <div class="login-page">
    <!-- Left panel: branding -->
    <div class="login-left">
      <div class="brand">
        <div class="brand-logo">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="40" height="40" rx="10" fill="#3b82f6"/>
            <path d="M8 20L20 8L32 20L20 32L8 20Z" fill="white" fill-opacity="0.9"/>
            <path d="M14 20L20 14L26 20L20 26L14 20Z" fill="#3b82f6"/>
          </svg>
        </div>
        <h1 class="brand-name">腾飞企业管理</h1>
        <p class="brand-tagline">高效 · 智能 · 安全</p>
      </div>
      <div class="brand-features">
        <div class="feature-item" v-for="f in features" :key="f.title">
          <div class="feature-icon">{{ f.icon }}</div>
          <div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-desc">{{ f.desc }}</div>
          </div>
        </div>
      </div>
      <div class="brand-footer">© 2026 腾飞科技 · 企业级管理解决方案</div>
    </div>

    <!-- Right panel: login form -->
    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">登录您的企业账号</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              autocomplete="username"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            native-type="submit"
            @click="handleLogin"
            :disabled="loading"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>

        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const features = [
  { icon: '🏢', title: '多企业管理', desc: '统一平台，多租户隔离' },
  { icon: '🔐', title: '精细权限控制', desc: '三级RBAC，数据安全' },
  { icon: '📊', title: '全面数据洞察', desc: '实时报表，智能分析' },
]

async function handleLogin() {
  if (loading.value) return
  errorMsg.value = ''
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const result = await auth.login({ username: form.username, password: form.password })
    if (result.pwdResetRequired) {
      router.push('/reset-password')
      return
    }
    router.push('/dashboard')
  } catch (err: unknown) {
    errorMsg.value = err instanceof Error ? err.message : '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  background: #0f1117;
}

/* Left panel */
.login-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  background: linear-gradient(135deg, #0f1117 0%, #1a1f2e 60%, #0f1117 100%);
  border-right: 1px solid rgba(255,255,255,0.06);
  position: relative;
  overflow: hidden;
}

.login-left::before {
  content: '';
  position: absolute;
  top: -200px;
  right: -200px;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(59,130,246,0.08) 0%, transparent 70%);
  pointer-events: none;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.brand-logo svg {
  width: 56px;
  height: 56px;
  filter: drop-shadow(0 4px 16px rgba(59,130,246,0.4));
}

.brand-name {
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.02em;
  margin: 0;
}

.brand-tagline {
  font-size: 14px;
  color: rgba(255,255,255,0.45);
  letter-spacing: 0.15em;
  margin: 0;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.feature-icon {
  font-size: 22px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59,130,246,0.1);
  border: 1px solid rgba(59,130,246,0.2);
  border-radius: 10px;
  flex-shrink: 0;
}

.feature-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  margin-bottom: 4px;
}

.feature-desc {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}

.brand-footer {
  font-size: 12px;
  color: rgba(255,255,255,0.25);
}

/* Right panel */
.login-right {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #111318;
  padding: 48px;
}

.login-card {
  width: 100%;
  max-width: 360px;
}

.login-header {
  margin-bottom: 36px;
}

.login-title {
  font-size: 26px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 8px;
}

.login-subtitle {
  font-size: 14px;
  color: rgba(255,255,255,0.4);
  margin: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* Override Element Plus for dark theme */
.login-form :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.04) !important;
  border: 1px solid rgba(255,255,255,0.1) !important;
  box-shadow: none !important;
  border-radius: 8px;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(59,130,246,0.6) !important;
  background: rgba(255,255,255,0.06) !important;
}

.login-form :deep(.el-input__inner) {
  color: #ffffff !important;
  font-size: 14px;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(255,255,255,0.3);
}

.login-form :deep(.el-input__prefix-inner .el-icon) {
  color: rgba(255,255,255,0.35);
}

.login-form :deep(.el-form-item__error) {
  color: #ef4444;
  font-size: 12px;
}

.login-btn {
  width: 100%;
  margin-top: 12px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.05em;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8) !important;
  border: none !important;
  transition: all 0.2s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(59,130,246,0.4) !important;
}

.error-msg {
  margin-top: 16px;
  padding: 10px 14px;
  background: rgba(239,68,68,0.1);
  border: 1px solid rgba(239,68,68,0.25);
  border-radius: 8px;
  color: #ef4444;
  font-size: 13px;
  text-align: center;
}

@media (max-width: 768px) {
  .login-left { display: none; }
  .login-right { width: 100%; }
}
</style>
