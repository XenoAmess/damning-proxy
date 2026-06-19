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
            <el-tag v-if="log.streaming" size="small" type="primary">流式</el-tag>
          </div>
          <div class="log-time">
            <span v-if="log.durationMs">{{ log.durationMs }}ms · </span>
            <span v-if="log.requestBodyLength">{{ formatBytes(log.requestBodyLength) }} · </span>
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
            · 配置 <el-tag type="warning">{{ current.profileId || '-' }}</el-tag>
            · 状态 <el-tag :type="statusType">{{ current.responseStatus || '-' }}</el-tag>
            <span v-if="current.durationMs !== null && current.durationMs !== undefined"> · 耗时 {{ current.durationMs }}ms</span>
            <span v-if="current.model"> · 模型 {{ current.model }}</span>
            <span v-if="current.streaming"> · 流式</span>
          </div>
          <div class="detail-times">
            <div class="detail-time-item">
              <span class="detail-time-label">请求时间</span>
              <span class="detail-time-value">{{ formatFullTime(current.requestTime) }}</span>
            </div>
            <div v-if="current.responseTime" class="detail-time-item">
              <span class="detail-time-label">响应时间</span>
              <span class="detail-time-value">{{ formatFullTime(current.responseTime) }}</span>
            </div>
          </div>
          <div v-if="current.errorMessage" class="detail-error">
            <el-alert :title="current.errorMessage" type="error" :closable="false" show-icon />
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
                <div class="chat-text markdown-body" v-html="renderModelOutput(current.modelOutput)"></div>
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
            <el-empty v-if="!current.rawRequestHeaders && !current.requestBody" description="无原始请求内容" />
            <div v-if="current.rawRequestHeaders" class="detail-section">
              <div class="detail-section-title">请求头</div>
              <pre>{{ formatJsonHeader(current.rawRequestHeaders) }}</pre>
            </div>
            <div v-if="current.requestBody" class="detail-section">
              <div class="detail-section-title">请求体</div>
              <pre>{{ formatJson(current.requestBody) }}</pre>
            </div>
            <div class="detail-section">
              <div class="detail-section-title">请求大小</div>
              <div class="meta-value">{{ formatBytes(current.requestBodyLength) }}</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="原始响应" name="rawResponse">
            <el-empty v-if="!current.rawResponseHeaders && !current.responseBody" description="无原始响应内容" />
            <div v-if="current.rawResponseHeaders" class="detail-section">
              <div class="detail-section-title">响应头</div>
              <pre>{{ formatJsonHeader(current.rawResponseHeaders) }}</pre>
            </div>
            <div v-if="current.responseBody" class="detail-section">
              <div class="detail-section-title">响应体</div>
              <pre>{{ formatJson(current.responseBody) }}</pre>
            </div>
            <div class="detail-section">
              <div class="detail-section-title">响应大小</div>
              <div class="meta-value">{{ formatBytes(current.responseBodyLength) }}</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="插件日志" name="pluginLogs">
            <div v-if="current.rawPluginLogs" class="detail-section">
              <div class="detail-section-title">插件日志</div>
              <pre>{{ formatJson(current.rawPluginLogs) }}</pre>
            </div>
            <el-empty v-else description="无插件日志" />
          </el-tab-pane>

          <el-tab-pane label="元信息" name="meta">
            <div class="meta-grid">
              <div class="meta-item">
                <div class="meta-label">日志 ID</div>
                <div class="meta-value">#{{ current.id }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">请求方法</div>
                <div class="meta-value">{{ current.requestMethod }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">请求路径</div>
                <div class="meta-value">{{ current.requestPath }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">实例</div>
                <div class="meta-value">{{ current.instanceSlug || current.instanceId || '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">上游配置 ID</div>
                <div class="meta-value">{{ current.profileId || '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">响应状态码</div>
                <div class="meta-value">
                  <el-tag size="small" :type="statusType">{{ current.responseStatus || '-' }}</el-tag>
                </div>
              </div>
              <div class="meta-item">
                <div class="meta-label">耗时</div>
                <div class="meta-value">{{ current.durationMs !== null && current.durationMs !== undefined ? current.durationMs + 'ms' : '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">请求时间</div>
                <div class="meta-value">{{ formatFullTime(current.requestTime) }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">响应时间</div>
                <div class="meta-value">{{ formatFullTime(current.responseTime) }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">模型</div>
                <div class="meta-value">{{ current.model || '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">上游地址</div>
                <div class="meta-value">{{ current.upstreamBaseUrl || '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">超时时间</div>
                <div class="meta-value">{{ current.timeoutMs !== null && current.timeoutMs !== undefined ? current.timeoutMs + 'ms' : '-' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">是否流式</div>
                <div class="meta-value">{{ current.streaming ? '是' : '否' }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">请求大小</div>
                <div class="meta-value">{{ formatBytes(current.requestBodyLength) }}</div>
              </div>
              <div class="meta-item">
                <div class="meta-label">响应大小</div>
                <div class="meta-value">{{ formatBytes(current.responseBodyLength) }}</div>
              </div>
              <div v-if="current.errorMessage" class="meta-item meta-item-full">
                <div class="meta-label">错误信息</div>
                <div class="meta-value error-text">{{ current.errorMessage }}</div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
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

function renderModelOutput(text) {
  if (typeof text !== 'string') return ''
  const parsed = parseThink(text)
  let html = marked.parse(parsed.text, { breaks: true, gfm: true })
  if (parsed.reasoning) {
    html = `<div class="reasoning-block"><div class="reasoning-label">推理过程</div><pre>${parsed.reasoning}</pre></div>` + html
  }
  return html
}

function parseThink(content) {
  if (typeof content !== 'string') {
    return { text: content || '', reasoning: '' }
  }
  const match = content.match(/<think>([\s\S]*?)<\/think>/)
  if (!match) {
    return { text: content, reasoning: '' }
  }
  const reasoning = match[1].trim()
  const text = content.replace(/<think>[\s\S]*?<\/think>/, '').trim()
  return { text, reasoning }
}

function formatJson(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    return String(value)
  }
}

function formatJsonHeader(value) {
  if (!value) return ''
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch (e) {
      return String(value)
    }
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return value
  }
}

function formatFullTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  const Y = d.getFullYear()
  const M = (d.getMonth() + 1).toString().padStart(2, '0')
  const D = d.getDate().toString().padStart(2, '0')
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  const s = d.getSeconds().toString().padStart(2, '0')
  const ms = d.getMilliseconds().toString().padStart(3, '0')
  return `${Y}-${M}-${D} ${h}:${m}:${s}.${ms}`
}

function formatTime(value) {
  if (!value) return '-'
  return formatFullTime(value)
}

function formatBytes(value) {
  if (value === null || value === undefined) return '-'
  const n = Number(value)
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / (1024 * 1024)).toFixed(2) + ' MB'
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

.detail-error {
  margin-top: 12px;
}

.detail-times {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.detail-time-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-time-label {
  font-size: 12px;
  color: #909399;
}

.detail-time-value {
  font-size: 13px;
  color: #303133;
  font-family: monospace;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.meta-item {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
}

.meta-item-full {
  grid-column: 1 / -1;
}

.meta-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.meta-value {
  font-size: 13px;
  color: #303133;
  word-break: break-all;
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
