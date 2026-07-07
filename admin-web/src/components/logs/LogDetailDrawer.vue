<template>
  <el-drawer
    :model-value="visible"
    title="流量详情"
    size="70%"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="!current" class="drawer-loading">
      <el-text type="info"> 加载中... </el-text>
    </div>
    <template v-else>
      <div class="detail-hero">
        <div class="detail-title">{{ current.requestMethod }} {{ current.requestPath }}</div>
        <div class="detail-subtitle">
          实例
          <el-tag type="warning">
            {{ current.instanceSlug || current.instanceId || '-' }}
          </el-tag>
          · 配置
          <el-tag type="warning">
            {{ current.profileId || '-' }}
          </el-tag>
          · 状态
          <el-tag :type="statusType">
            {{ current.responseStatus || '-' }}
          </el-tag>
          <span v-if="current.durationMs !== null && current.durationMs !== undefined">
            · 耗时 {{ current.durationMs }}ms</span
          >
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

      <el-tabs :model-value="activeTab" @update:model-value="emit('update:activeTab', $event)">
        <el-tab-pane v-if="isChatLike(current)" label="对话摘要" name="summary">
          <div class="chat-flow">
            <template v-for="(msg, idx) in current.requestMessages || []" :key="'req-' + idx">
              <div :class="['chat-bubble', bubbleClass(msg.role)]">
                <div class="chat-role">
                  <span class="chat-role-label">{{ roleLabel(msg.role) }}</span>
                  <el-tag v-if="msg.name" size="small" type="info" class="chat-role-name">
                    {{ msg.name }}
                  </el-tag>
                </div>
                <div v-if="msg.reasoningContent" class="chat-text reasoning">
                  {{ msg.reasoningContent }}
                </div>
                <div v-if="msg.role === 'tool' && msg.content" class="chat-tool-calls">
                  <div class="tool-call-item tool-result-item">
                    📋 <strong>工具结果</strong>
                    <span v-if="msg.toolResultCallId" class="tool-call-id">{{
                      msg.toolResultCallId
                    }}</span>
                    <pre class="tool-call-args">{{ tryFormatJson(msg.content) }}</pre>
                  </div>
                </div>
                <div
                  v-else-if="msg.content"
                  class="chat-text markdown-body"
                  v-html="renderModelOutput(msg.content)"
                />
                <pre
                  v-else-if="
                    !msg.toolCallIds?.length && !msg.reasoningContent && msg.role !== 'tool'
                  "
                  class="chat-text muted"
                >
（无文本内容）</pre>
                <div v-if="msg.toolCallIds && msg.toolCallIds.length" class="chat-tool-calls">
                  <div v-for="(id, i) in msg.toolCallIds" :key="id" class="tool-call-item">
                    🔧 <strong>{{ msg.toolCallFunctions?.[i] || '工具调用' }}</strong>
                    <span class="tool-call-id">{{ id }}</span>
                    <pre v-if="msg.toolCallArguments?.[i]" class="tool-call-args">{{
                      tryFormatJson(msg.toolCallArguments[i])
                    }}</pre>
                  </div>
                </div>
              </div>
            </template>
            <template v-for="(msg, idx) in current.responseMessages || []" :key="'res-' + idx">
              <div :class="['chat-bubble', bubbleClass(msg.role || 'assistant')]">
                <div class="chat-role">
                  <span class="chat-role-label">{{ roleLabel(msg.role || 'assistant') }}</span>
                  <el-tag v-if="msg.name" size="small" type="info" class="chat-role-name">
                    {{ msg.name }}
                  </el-tag>
                </div>
                <pre v-if="msg.reasoningContent" class="chat-text reasoning">{{
                  msg.reasoningContent
                }}</pre>
                <div
                  v-if="msg.content"
                  class="chat-text markdown-body"
                  v-html="renderModelOutput(msg.content)"
                />
                <pre
                  v-else-if="!msg.reasoningContent && !msg.toolCallIds?.length"
                  class="chat-text muted"
                >
（无文本内容）</pre>
                <div v-if="msg.toolCallIds && msg.toolCallIds.length" class="chat-tool-calls">
                  <div v-for="(id, i) in msg.toolCallIds" :key="id" class="tool-call-item">
                    🔧 <strong>{{ msg.toolCallFunctions?.[i] || '工具调用' }}</strong>
                    <span class="tool-call-id">{{ id }}</span>
                    <pre v-if="msg.toolCallArguments?.[i]" class="tool-call-args">{{
                      tryFormatJson(msg.toolCallArguments[i])
                    }}</pre>
                  </div>
                </div>
              </div>
            </template>
            <el-empty
              v-if="!current.requestMessages?.length && !current.responseMessages?.length"
              description="没有可显示的消息"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="请求插件" name="requestPipeline">
          <div v-if="current.requestPipeline?.length" class="pipeline-detail">
            <div v-for="(s, idx) in current.requestPipeline" :key="idx" class="pipeline-step">
              <div class="step-header">
                <span class="step-name">{{ s.name }}</span>
                <el-button
                  size="small"
                  text
                  type="primary"
                  @click.stop="emit('debug', s, 'REQUEST')"
                >
                  调试此插件
                </el-button>
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
            <div v-for="(s, idx) in current.responsePipeline" :key="idx" class="pipeline-step">
              <div class="step-header">
                <span class="step-name">{{ s.name }}</span>
                <el-button
                  size="small"
                  text
                  type="primary"
                  @click.stop="emit('debug', s, 'RESPONSE')"
                >
                  调试此插件
                </el-button>
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
          <el-empty
            v-if="!current.rawRequestHeaders && !current.requestBody"
            description="无原始请求内容"
          />
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
            <div class="meta-value">
              {{ formatBytes(current.requestBodyLength) }}
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="原始响应" name="rawResponse">
          <el-empty
            v-if="!current.rawResponseHeaders && !current.responseBody"
            description="无原始响应内容"
          />
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
            <div class="meta-value">
              {{ formatBytes(current.responseBodyLength) }}
            </div>
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
              <div class="meta-value">
                {{ current.requestMethod }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">请求路径</div>
              <div class="meta-value">
                {{ current.requestPath }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">实例</div>
              <div class="meta-value">
                {{ current.instanceSlug || current.instanceId || '-' }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">上游配置 ID</div>
              <div class="meta-value">
                {{ current.profileId || '-' }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">响应状态码</div>
              <div class="meta-value">
                <el-tag size="small" :type="statusType">
                  {{ current.responseStatus || '-' }}
                </el-tag>
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">Token 用量</div>
              <div class="meta-value">
                <template v-if="current.totalTokens !== null && current.totalTokens !== undefined">
                  prompt {{ current.promptTokens || 0 }} / completion
                  {{ current.completionTokens || 0 }} / total {{ current.totalTokens }}
                </template>
                <span v-else>-</span>
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">耗时</div>
              <div class="meta-value">
                {{
                  current.durationMs !== null && current.durationMs !== undefined
                    ? current.durationMs + 'ms'
                    : '-'
                }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">请求时间</div>
              <div class="meta-value">
                {{ formatFullTime(current.requestTime) }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">响应时间</div>
              <div class="meta-value">
                {{ formatFullTime(current.responseTime) }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">模型</div>
              <div class="meta-value">
                {{ current.model || '-' }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">上游地址</div>
              <div class="meta-value">
                {{ current.upstreamBaseUrl || '-' }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">超时时间</div>
              <div class="meta-value">
                {{
                  current.timeoutMs !== null && current.timeoutMs !== undefined
                    ? current.timeoutMs + 'ms'
                    : '-'
                }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">是否流式</div>
              <div class="meta-value">
                {{ current.streaming ? '是' : '否' }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">请求大小</div>
              <div class="meta-value">
                {{ formatBytes(current.requestBodyLength) }}
              </div>
            </div>
            <div class="meta-item">
              <div class="meta-label">响应大小</div>
              <div class="meta-value">
                {{ formatBytes(current.responseBodyLength) }}
              </div>
            </div>
            <div v-if="current.errorMessage" class="meta-item meta-item-full">
              <div class="meta-label">错误信息</div>
              <div class="meta-value error-text">
                {{ current.errorMessage }}
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import { parseThink, formatBytes, sanitizeHtml } from '../../utils/parse.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  current: { type: Object, default: null },
  activeTab: { type: String, default: 'summary' },
})

const emit = defineEmits(['update:visible', 'update:activeTab', 'debug'])

const statusType = computed(() => {
  if (!props.current || !props.current.responseStatus) return 'info'
  return props.current.responseStatus >= 400 ? 'danger' : 'success'
})

const ROLE_LABELS = {
  system: '系统',
  developer: '开发者',
  user: '用户',
  assistant: '模型',
  tool: '工具',
  function: '函数',
}

function isChatLike(log) {
  return log && log.requestPath && log.requestPath.includes('/chat/completions')
}

function roleLabel(role) {
  if (!role) return '消息'
  return ROLE_LABELS[role] || role
}

function bubbleClass(role) {
  switch (role) {
    case 'system':
    case 'developer':
      return 'system'
    case 'tool':
    case 'function':
      return 'tool'
    case 'assistant':
      return 'assistant'
    case 'user':
    default:
      return 'user'
  }
}

function renderModelOutput(text) {
  if (typeof text !== 'string') return ''
  const parsed = parseThink(text)
  let html = sanitizeHtml(marked.parse(parsed.text, { breaks: true, gfm: true }))
  if (parsed.reasoning) {
    html =
      `<div class="reasoning-block"><div class="reasoning-label">推理过程</div><pre>${sanitizeHtml(parsed.reasoning)}</pre></div>` +
      html
  }
  return html
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

function tryFormatJson(text) {
  try {
    const obj = JSON.parse(text)
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return text
  }
}
</script>

<style scoped>
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

.chat-bubble.system {
  border-left: 4px solid #909399;
  background: #f4f4f5;
}

.chat-bubble.tool {
  border-left: 4px solid #e6a23c;
  background: #fdf6ec;
}

.chat-role {
  display: flex;
  align-items: center;
  gap: 6px;
}

.chat-role-label {
  font-size: 12px;
  color: #909399;
}

.chat-role-name {
  font-size: 11px !important;
}

.chat-text.muted {
  color: #c0c4cc;
  font-style: italic;
}

.chat-text.reasoning {
  background: #f4f4f5;
  border-left: 3px solid #909399;
  padding: 8px 10px;
  margin-bottom: 8px;
  white-space: pre-wrap;
  color: #606266;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.chat-tool-calls {
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
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

.tool-call-item {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-left: 3px solid #409eff;
  border-radius: 4px;
}

.tool-call-id {
  font-size: 11px;
  color: #909399;
  margin-left: 8px;
  font-family: monospace;
}

.tool-call-args {
  margin: 6px 0 0 0;
  padding: 6px 10px;
  font-size: 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 120px;
}

.tool-result-item {
  border-left-color: #67c23a !important;
  background: #f0f9eb !important;
}

:deep(.el-tabs__content) {
  padding-top: 12px;
}
</style>
