<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ApprovalView from '@/views/approval/ApprovalView.vue'

const route = useRoute()
const moduleCode = computed(() => (route.meta.moduleCode as string) || '')
const moduleTitle = computed(() => (route.meta.title as string) || '模块')
</script>

<template>
  <ApprovalView v-if="moduleCode === 'approval'" />
  <div v-else class="module-host">
    <el-result icon="info" :title="moduleTitle" sub-title="该模块内容正在建设中">
      <template #extra>
        <el-tag type="info">模块编码：{{ moduleCode }}</el-tag>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.module-host {
  padding: 40px;
  display: flex;
  justify-content: center;
}
</style>
