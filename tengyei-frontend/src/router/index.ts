import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('@/views/403View.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/403',
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // Guest-only routes (login page): redirect to dashboard if already logged in
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return '/dashboard'
  }

  // Protected routes: redirect to login if not authenticated
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }

  // Authenticated but no userInfo yet: fetch it (page refresh scenario)
  if (auth.isLoggedIn && !auth.userInfo) {
    try {
      await auth.fetchUserInfo()
    } catch {
      // Token invalid — clear and redirect to login
      await auth.logout()
      return '/login'
    }
  }

  return true
})

export default router
