import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { UserInfo, LoginRequest } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('access_token'))
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => userInfo.value?.isSuperAdmin ?? false)
  const permissions = computed(() => userInfo.value?.permissions ?? [])
  const routes = computed(() => userInfo.value?.routes ?? [])

  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload)
    token.value = res.accessToken
    localStorage.setItem('access_token', res.accessToken)
    if (res.pwdResetRequired) {
      return { pwdResetRequired: true }
    }
    await fetchUserInfo()
    return { pwdResetRequired: false }
  }

  async function fetchUserInfo() {
    userInfo.value = await authApi.userinfo()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      userInfo.value = null
      localStorage.removeItem('access_token')
    }
  }

  function hasPermission(perm: string): boolean {
    if (isSuperAdmin.value) return true
    if (permissions.value.includes('*')) return true
    // permissions claim holds raw codes (e.g. "user:create"); templates may pass "PERM_user:create"
    const raw = perm.startsWith('PERM_') ? perm.slice(5) : perm
    return permissions.value.includes(raw)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isSuperAdmin,
    permissions,
    routes,
    login,
    logout,
    fetchUserInfo,
    hasPermission,
  }
})
