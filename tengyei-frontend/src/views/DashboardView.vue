<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import SuperDashboard from './dashboard/SuperDashboard.vue'
import CompanyDashboard from './dashboard/CompanyDashboard.vue'

const auth = useAuthStore()
const isSuper = computed(() => auth.isSuperAdmin)
</script>

<template>
  <SuperDashboard v-if="isSuper" />
  <CompanyDashboard v-else />
</template>
