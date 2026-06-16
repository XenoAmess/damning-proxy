<template>
  <div>
    <div class="toolbar">
      <el-button type="danger" @click="clear">清空日志</el-button>
    </div>

    <el-empty v-if="!loading && logs.length === 0" description="暂无流量日志" />

    <div class="log-card-list">
      <div
        v-for="log in logs"
        :key="log.id"
        :ref="el => setCardRef(log.id, el)"
        :data-id="log.id"
        class="log-card"
        :class="{ error: log.responseStatus >= 400 }"
      >
        <div class="log-card-header">
          <div class="log-meta">
            <el-tag size="small" type="info">#{{ log.id }}</el-tag>
            <el-tag v-if="log.instanceSlug" size="small" type="warning" class="instance-tag">
              {{ log.instanceSlug }}
            </el-tag>
            <span class="log-path">{{ log.requestMethod }} {{ log.requestPath }}</span>
            <el-tag
              size="small"
              :type="log.responseStatus >= 400 ? 'danger' : 'success'"
            >
              {{ log.responseStatus || '-' }}
            </el-tag>
          </div>
          <div class="log-time">
            <span v-if="log.durationMs">{{ log.durationMs }}ms · </span>
            {{ formatTime(log.requestTime) }}
          </div>
        </div>

        <div class="log-card-body" @click="openFriendly(log.id)">
          <template v-if="isChatLike(log)">
            <div class="summary-section">
              <div class="summary-label">📝 用户输入</div>
              <div class="summary-text">{{ log._friendly?.userPrompt || '-' }}</div>
            </div>
            <div class="summary-section">
              <div class="summary-label">🤖 模型输出</div>
              <div class="summary-text">{{ log._friendly?.modelOutput || '-' }}</div>
            </div>
          </template>
          <template v-else>
            <div class="summary-section">
              <div class="summary-label">📦 请求类型</div>
              <div class="summary-text">{{ log.requestMethod }} {{ log.requestPath }}</div>
            </div>
          </template>

          <div v-if="log._friendly?.requestPipeline?.length" class="pipeline-row">
            <div class="pipeline-title">请求插件流水线</div>
            <div class="pipeline">
              <div
                v-for="(s, idx) in log._friendly.requestPipeline"
                :key="idx"
                class="pipeline-node"
                :class="{ error: s.error }"
              >
                {{ s.name }}
              </div>
            </div>
          </div>

          <div v-if="log._friendly?.responsePipeline?.length" class="pipeline-row">
            <div class="pipeline-title">响应插件流水线</div>
            <div class="pipeline">
              <div
                v-for="(s, idx) in log._friendly.responsePipeline"
                :key="idx"
                class="pipeline-node"
                :class="{ error: s.error }"
              >
                {{ s.name }}
              </div>
            </div>
          </div>
        </div>

        <div class="log-card-footer">
          <el-button size="small" type="primary" @click.stop="openFriendly(log.id)">
            详情
          </el-button>
          <el-button size="small" type="danger" @click.stop="remove(log.id)">删除</el-button>
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="流量详情" size="70%">
      <div v-if="!current" class="drawer-loading">
        <el-text type="info">加载中...</el-text>
      </div>
      <template v-else>
        <div class="detail-hero">
          <div class="detail-title">
            {{ current.requestMethod }} {{ current.requestPath }}
          </div>
          <div class="detail-subtitle">
            实例 <el-tag type="warning">{{ current.instanceSlug || current.instanceId || '-' }}</el-tag>
            · 状态 <el-tag :type="statusType">{{ current.responseStatus || '-' }}</el-tag>
            <span v-if="current.durationMs"> · 耗时 {{ current.durationMs }}ms</span>
            <span v-if="current.model"> · 模型 {{ current.model }}</span>
          </div>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane v-if="isChatLike(current)" label="对话摘要" name="summary">
            <div class="chat-flow">
              <div class="chat-bubble user">
                <div class="chat-role">用户</div>
                <pre class="chat-text">{{ current.userPrompt || '-' }}</pre>
              </div>
              <div class="chat-bubble assistant">
                <div class="chat-role">模型</div>
                <pre class="chat-text">{{ current.modelOutput || '-' }}</pre>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="请求插件" name="requestPipeline">
            <div v-if="current.requestPipeline?.length" class="pipeline-detail">
              <div
                v-for="(s, idx) in current.requestPipeline"
                :key="idx"
                class="pipeline-step">
                <div class="step-header">
                  <span class="step-name">{{ s.name }}</span>
                  <el-tag size="small" :type="s.error ? 'danger' : 'success'">
                    {{ s.error ? '异常' : '成功' }}
                  </el-tag>
                </div>
                <div class="step-section">
                  <div class="step-label">处理前</div>
                  <pre>{{ formatJson(s.input) }}</pre>
                </div>
                <div class="step-section">
                  <div class="step-label">处理后</div>
                  <pre>{{ formatJson(s.output) }}</pre>
                </div>
                <div v-if="s.log" class="step-section">
                  <div class="step-label">日志</div>
                  <pre class="error-text">{{ s.log }}</pre>
                </div>
              </div>
            </div>
            <el-empty v-else description="无请求插件执行" />
          </el-tab-pane>

          <el-tab-pane label="响应插件" name="responsePipeline">
            <div v-if="current.responsePipeline?.length" class="pipeline-detail">
              <div
                v-for="(s, idx) in current.responsePipeline"
                :key="idx"
                class="pipeline-step">
                <div class="step-header">
                  <span class="step-name">{{ s.name }}</span>
                  <el-tag size="small" :type="s.error ? 'danger' : 'success'">
                    {{ s.error ? '异常' : '成功' }}
                  </el-tag>
                </div>
                <div class="step-section">
                  <div class="step-label">处理前</div>
                  <pre>{{ formatJson(s.input) }}</pre>
                </div>
                <div class="step-section">
                  <div class="step-label">处理后</div>
                  <pre>{{ formatJson(s.output) }}</pre>
                </div>
                <div v-if="s.log" class="step-section">
                  <div class="step-label">日志</div>
                  <pre class="error-text">{{ s.log }}</pre>
                </div>
              </div>
            </div>
            <el-empty v-else description="无响应插件执行" />
          </el-tab-pane>

          <el-tab-pane label="原始请求" name="rawRequest">
            <pre>{{ formatJson(current.rawRequestHeaders) }}</pre>
            <pre>{{ formatJson(current.requestBody) }}</pre>
          </el-tab-pane>

          <el-tab-pane label="原始响应" name="rawResponse">
            <pre>{{ formatJson(current.rawResponseHeaders) }}</pre>
            <pre>{{ formatJson(current.responseBody) }}</pre>
          </el-tab-pane>

          <el-tab-pane label="插件日志" name="pluginLogs">
            <pre>{{ formatJson(current.rawPluginLogs) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listLogs, getLogFriendly, deleteLog, clearLogs } from '../api/damning.js'

const logs = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const activeTab = ref('summary')
const current = ref(null)
const cardRefs = ref({})
const loadedFriendlyIds = ref(new Set())
let observer = null

const statusType = computed(() => {
  if (!current.value || !current.value.responseStatus) return 'info'
  return current.value.responseStatus >= 400 ? 'danger' : 'success'
})

async function load() {
  loading.value = true
  try {
    const res = await listLogs({ limit: 100 })
    logs.value = res.data
    await nextTick()
    observeCards()
  } finally {
    loading.value = false
  }
}

function observeCards() {
  if (observer) {
    observer.disconnect()
  }
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const id = Number(entry.target.dataset.id)
        loadFriendlyIfNeeded(id)
      }
    }
  }, { rootMargin: '100px' })

  for (const el of Object.values(cardRefs.value)) {
    if (el) observer.observe(el)
  }
}

async function loadFriendlyIfNeeded(id) {
  if (loadedFriendlyIds.value.has(id)) return
  loadedFriendlyIds.value.add(id)
  try {
    const res = await getLogFriendly(id)
    const log = logs.value.find(l => l.id === id)
    if (log) {
      log._friendly = res.data
    }
  } catch (e) {
    // ignore per-card load failures
  }
}

async function openFriendly(id) {
  detailVisible.value = true
  current.value = null
  activeTab.value = 'summary'
  try {
    await loadFriendlyIfNeeded(id)
    const log = logs.value.find(l => l.id === id)
    current.value = log?._friendly || null
    if (!isChatLike(current.value)) {
      activeTab.value = 'rawRequest'
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
    detailVisible.value = false
  }
}

function isChatLike(log) {
  return log && log.requestPath && log.requestPath.includes('/chat/completions')
}

function formatJson(value) {
  if (!value) return ''
  try {
    return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
  } catch (e) {
    return value
  }
}

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`
}

function setCardRef(id, el) {
  if (el) {
    cardRefs.value[id] = el
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
onUnmounted(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.log-card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.log-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.log-card.error {
  border-left: 4px solid #f56c6c;
}

.log-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.instance-tag {
  font-weight: 600;
}

.log-path {
  font-weight: 500;
  color: #303133;
}

.log-time {
  font-size: 13px;
  color: #909399;
}

.log-card-body {
  padding: 16px;
  cursor: pointer;
}

.summary-section {
  margin-bottom: 12px;
}

.summary-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.summary-text {
  font-size: 14px;
  color: #303133;
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 6px;
  max-height: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: pre-wrap;
  line-height: 1.4;
}

.pipeline-row {
  margin-top: 12px;
}

.pipeline-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.pipeline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.pipeline::before {
  content: '';
  position: absolute;
}

.pipeline-node {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #d9ecff;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 12px;
  position: relative;
}

.pipeline-node.error {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #fde2e2;
}

.log-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #ebeef5;
}

.drawer-loading {
  padding: 40px;
  text-align: center;
}

.detail-hero {
  margin-bottom: 16px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.detail-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
}

.chat-flow {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-bubble {
  border-radius: 8px;
  padding: 14px;
  background: #f5f7fa;
}

.chat-bubble.user {
  border-left: 4px solid #409eff;
}

.chat-bubble.assistant {
  border-left: 4px solid #67c23a;
}

.chat-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.chat-text {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: #303133;
}

.pipeline-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pipeline-step {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.step-name {
  font-weight: 500;
}

.step-section {
  padding: 12px 14px;
  border-bottom: 1px solid #ebeef5;
}

.step-section:last-child {
  border-bottom: none;
}

.step-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

pre {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
  margin: 0;
  font-size: 13px;
}

.error-text {
  color: #f56c6c;
}

:deep(.el-tabs__content) {
  padding-top: 12px;
}
</style>
