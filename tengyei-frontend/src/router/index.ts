import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/reset-password',
      name: 'ResetPassword',
      component: () => import('@/views/auth/ResetPasswordView.vue'),
      meta: { requiresAuth: true, skipPwdCheck: true },
    },
    {
      path: '/',
      name: 'Main',
      component: () => import('@/layout/MainLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
        {
          path: 'admin/companies',
          name: 'Companies',
          component: () => import('@/views/company/CompanyListView.vue'),
          meta: { title: '企业管理' },
        },
        {
          path: 'admin/audit-logs',
          name: 'AuditLogs',
          component: () => import('@/views/audit/AuditLogView.vue'),
          meta: { title: '操作日志' },
        },
        {
          path: 'admin/system-config',
          name: 'SystemConfig',
          component: () => import('@/views/config/SystemConfigView.vue'),
          meta: { title: '系统设置' },
        },
        {
          path: 'admin/modules',
          name: 'ModuleRegistry',
          component: () => import('@/views/module/ModuleRegistryView.vue'),
          meta: { title: '模块管理' },
        },
        {
          path: 'admin/users',
          name: 'PlatformUsers',
          component: () => import('@/views/platform/PlatformUserListView.vue'),
          meta: { title: '平台人员' },
        },
        {
          path: 'admin/roles',
          name: 'PlatformRoles',
          component: () => import('@/views/platform/PlatformRoleView.vue'),
          meta: { title: '平台角色' },
        },
        {
          path: 'company/org',
          name: 'Org',
          component: () => import('@/views/org/OrgView.vue'),
          meta: { title: '组织管理' },
        },
        {
          path: 'company/users',
          name: 'Users',
          component: () => import('@/views/user/UserListView.vue'),
          meta: { title: '人员管理' },
        },
        {
          path: 'company/roles',
          name: 'Roles',
          component: () => import('@/views/role/RoleView.vue'),
          meta: { title: '角色与权限' },
        },
        // Note: 'company/approval' and other business modules are registered
        // dynamically from module_registry (see beforeEach below).
      ],
    },
    { path: '/403', name: 'Forbidden', component: () => import('@/views/403View.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/403' },
  ],
})

// Dynamically registered module routes (from module_registry)
let moduleRoutesReady = false

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.guestOnly && auth.isLoggedIn) return '/dashboard'
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (auth.isLoggedIn && !auth.userInfo) {
    try {
      await auth.fetchUserInfo()
    } catch {
      await auth.logout()
      return '/login'
    }
  }
  // Register dynamic business-module routes once per session
  if (auth.isLoggedIn && !moduleRoutesReady) {
    if (!auth.modulesLoaded) await auth.fetchModules()
    for (const m of auth.modules) {
      const relPath = m.entryUrl.replace(/^\//, '')
      if (!relPath) continue
      router.addRoute('Main', {
        path: relPath,
        name: 'module-' + m.moduleCode,
        component: () => import('@/views/module/ModuleHostView.vue'),
        meta: { title: m.moduleName, moduleCode: m.moduleCode },
      })
    }
    moduleRoutesReady = true
    // Re-trigger navigation so the freshly added routes can be matched
    return { ...to, replace: true }
  }
  // Force password change if required
  if (auth.userInfo?.pwdResetRequired && !to.meta.skipPwdCheck) {
    return '/reset-password'
  }
  return true
})

export default router
