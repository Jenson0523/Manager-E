<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/stores/auth'
import { strongPasswordRule } from '@/utils/password'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [strongPasswordRule()],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_: unknown, value: string, cb: (e?: Error) => void) => {
        if (value !== form.newPassword) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await meApi.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    await auth.logout()
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="reset-page">
    <div class="reset-card">
      <div class="reset-header">
        <div class="reset-icon">🔐</div>
        <h2>修改初始密码</h2>
        <p>为了账号安全，首次登录请修改初始密码</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="8-20 位，含大小写字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleSubmit">
            确认修改
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.reset-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.reset-card {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}
.reset-header {
  text-align: center;
  margin-bottom: 32px;
}
.reset-icon {
  font-size: 40px;
  margin-bottom: 12px;
}
.reset-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px;
}
.reset-header p {
  font-size: 13px;
  color: #6b7280;
  margin: 0;
}
</style>
