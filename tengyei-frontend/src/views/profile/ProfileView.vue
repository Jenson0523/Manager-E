<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { strongPasswordRule } from '@/utils/password'

const auth = useAuthStore()
const router = useRouter()

const userInfo = computed(() => auth.userInfo)

const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [strongPasswordRule()],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_: unknown, value: string, cb: (e?: Error) => void) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

async function submitPassword() {
  await pwdFormRef.value?.validate()
  pwdLoading.value = true
  try {
    await meApi.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    await auth.logout()
    router.push('/login')
  } finally {
    pwdLoading.value = false
  }
}
</script>

<template>
  <div class="profile">
    <el-card shadow="never" class="info-card">
      <template #header>基本信息</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ userInfo?.realName }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ userInfo?.username }}</el-descriptions-item>
        <el-descriptions-item label="身份">
          <el-tag :type="userInfo?.isSuperAdmin ? 'danger' : 'primary'">
            {{ userInfo?.isSuperAdmin ? '平台超管' : '企业用户' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据范围">{{ userInfo?.dataScope }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag
            v-for="r in userInfo?.roleCodes"
            :key="r"
            size="small"
            style="margin-right: 4px"
          >{{ r }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="pwd-card">
      <template #header>修改密码</template>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px" style="max-width: 400px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8-20 位，含大小写字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="pwdLoading" @click="submitPassword">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.info-card,
.pwd-card {
  border-radius: 10px;
}
</style>
