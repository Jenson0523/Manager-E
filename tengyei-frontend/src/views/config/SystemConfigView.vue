<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi, type SystemConfigVO } from '@/api/config'

const loading = ref(false)
const configs = ref<SystemConfigVO[]>([])
const editingKey = ref<string | null>(null)
const editingValue = ref('')

async function fetchList() {
  loading.value = true
  try {
    configs.value = await configApi.list()
  } finally {
    loading.value = false
  }
}

function startEdit(cfg: SystemConfigVO) {
  editingKey.value = cfg.configKey
  editingValue.value = cfg.configValue ?? ''
}

function cancelEdit() {
  editingKey.value = null
}

async function saveEdit(cfg: SystemConfigVO) {
  await configApi.update(cfg.configKey, editingValue.value)
  cfg.configValue = editingValue.value
  editingKey.value = null
  ElMessage.success('配置已保存')
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <el-table v-loading="loading" :data="configs" stripe>
      <el-table-column prop="configKey" label="配置键" width="240" />
      <el-table-column prop="description" label="说明" min-width="200" />
      <el-table-column label="配置值" min-width="240">
        <template #default="{ row }">
          <template v-if="editingKey === (row as SystemConfigVO).configKey">
            <el-input v-model="editingValue" size="small" style="width: 200px" />
          </template>
          <span v-else>{{ (row as SystemConfigVO).configValue }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <template v-if="editingKey === (row as SystemConfigVO).configKey">
            <el-button link type="primary" @click="saveEdit(row as SystemConfigVO)">保存</el-button>
            <el-button link @click="cancelEdit">取消</el-button>
          </template>
          <el-button v-else link type="primary" @click="startEdit(row as SystemConfigVO)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
}
</style>
