<script setup lang="ts">
import { ref } from 'vue'
import { announcementApi, type AnnouncementDetailVO } from '@/api/announcement'
import { useIsMobile } from '@/utils/responsive'

const isMobile = useIsMobile()
const visible = ref(false)
const loading = ref(false)
const data = ref<AnnouncementDetailVO | null>(null)

const LEVEL_LABEL: Record<string, string> = { INFO: '普通', WARN: '重要', URGENT: '紧急' }
const LEVEL_TAG: Record<string, 'info' | 'warning' | 'danger'> = {
  INFO: 'info', WARN: 'warning', URGENT: 'danger',
}

async function open(id: number) {
  visible.value = true
  loading.value = true
  data.value = null
  try {
    data.value = await announcementApi.detail(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="通知详情" width="480px" :fullscreen="isMobile" append-to-body>
    <div v-loading="loading">
      <template v-if="data">
        <div class="detail-title">
          <el-tag :type="LEVEL_TAG[data.level] ?? 'info'" size="small">{{ LEVEL_LABEL[data.level] ?? data.level }}</el-tag>
          <span>{{ data.title }}</span>
        </div>
        <div v-if="data.content" class="detail-content">{{ data.content }}</div>
        <el-descriptions :column="1" size="small" border class="detail-meta">
          <el-descriptions-item label="发布方">{{ data.source }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ data.publisherName || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="data.publisherRoles?.length" label="角色">
            {{ data.publisherRoles.join('、') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="data.publisherDepts?.length" label="部门">
            {{ data.publisherDepts.join('、') }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ data.createdAt?.replace('T', ' ') }}</el-descriptions-item>
          <el-descriptions-item v-if="data.startAt || data.endAt" label="展示时间">
            {{ (data.startAt?.replace('T', ' ') || '立即') + ' ~ ' + (data.endAt?.replace('T', ' ') || '长期') }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.detail-content {
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
  line-height: 1.7;
  margin-bottom: 14px;
}
.detail-meta {
  margin-top: 4px;
}
</style>
