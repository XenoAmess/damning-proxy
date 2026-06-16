<template>
  <div>
    <div class="toolbar">
      <el-button type="danger" @click="clear">清空日志</el-button>
    </div>
    <el-table :data="logs" v-loading="loading" @row-click="showDetail">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="profileId" label="Profile" width="80" />
      <el-table-column prop="requestMethod" label="方法" width="70" />
      <el-table-column prop="requestPath" label="路径" show-overflow-tooltip />
      <el-table-column prop="responseStatus" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.responseStatus >= 400 ? 'danger' : 'success'">{{ row.responseStatus || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
      <el-table-column prop="requestTime" label="时间" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click.stop="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="日志详情" width="800px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="请求" name="request">
          <pre>{{ formatJson(current.requestHeaders) }}</pre>
          <pre>{{ formatJson(current.requestBody) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="响应" name="response">
          <pre>{{ formatJson(current.responseHeaders) }}</pre>
          <pre>{{ formatJson(current.responseBody) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="插件日志" name="plugin">
          <pre>{{ formatJson(current.pluginLogs) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listLogs, deleteLog, clearLogs } from '../api/daming.js'

const logs = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const activeTab = ref('request')
const current = ref({})

async function load() {
  loading.value = true
  try {
    const res = await listLogs({ limit: 100 })
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

function showDetail(row) {
  current.value = row
  activeTab.value = 'request'
  detailVisible.value = true
}

function formatJson(value) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return value
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该日志？', '提示', { type: 'warning' })
    await deleteLog(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function clear() {
  try {
    await ElMessageBox.confirm('确定清空所有日志？', '提示', { type: 'warning' })
    await clearLogs()
    ElMessage.success('清空成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('清空失败')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
pre {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
}
</style>
