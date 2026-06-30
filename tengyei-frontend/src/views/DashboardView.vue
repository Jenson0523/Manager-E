<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import SuperDashboard from './dashboard/SuperDashboard.vue'
import CompanyDashboard from './dashboard/CompanyDashboard.vue'

const auth = useAuthStore()
// Platform tier (tenant_id === 0: owner or platform staff) sees the platform dashboard;
// company-tier users see the company dashboard. Keys on tier, not the is_super_admin flag.
const isPlatform = computed(() => auth.userInfo?.tenantId === 0)
</script>

<template>
  <SuperDashboard v-if="isPlatform" />
  <CompanyDashboard v-else />
</template>
