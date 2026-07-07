<template>
  <div class="plugin-debugger" v-loading="loading">
    <div class="toolbar">
      <el-button @click="goBack">返回</el-button>
      <span class="title" v-if="plugin">{{ plugin.name }} — 插件调试台</span>
      <el-tag v-if="plugin" :type="plugin.enabled ? 'success' : 'info'">{{ plugin.enabled ? '已启用' : '已禁用' }}</el-tag>
      <el-tag v-if="plugin">{{ plugin.language }}</el-tag>
      <el-tag v-if="plugin">{{ plugin.executionPhase }}</el-tag>
      <div class="spacer" />
      <el-switch v-model="editable" active-text="允许编辑" inactive-text="只读" />
      <el-button type="primary" @click="saveScript" :loading="saving" :disabled="!editable">保存脚本</el-button>
      <el-button type="success" @click="run" :loading="running">运行调试</el-button>
    </div>

    <div class="main" :class="{ docked }">
      <div class="panel editor-panel">
        <div class="panel-header">
          <span>脚本编辑器</span>
          <el-button size="small" text @click="resetScript">重置</el-button>
        </div>
        <CodeEditor v-if="plugin" v-model="script" :language="plugin.language" :read-only="!editable" :height="editorHeight" />
      </div>

      <div class="side-panels">
        <div class="panel">
          <div class="panel-header">
            <span>输入上下文</span>
            <el-button size="small" text @click="dockToggle">{{ docked ? '收起' : '固定' }}</el-button>
          </div>

          <el-form label-position="top" size="small">
            <el-form-item label="调试阶段">
              <el-radio-group v-model="phase">
                <el-radio-button label="REQUEST" />
                <el-radio-button label="RESPONSE" />
              </el-radio-group>
            </el-form-item>

            <el-form-item label="数据来源">
              <el-radio-group v-model="sourceType">
                <el-radio-button label="manual">手动输入</el-radio-button>
                <el-radio-button label="instance">从实例加载</el-radio-button>
                <el-radio-button label="log">从日志加载</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="sourceType === 'instance'" label="选择实例">
              <el-select v-model="selectedInstanceId" placeholder="选择实例以应用上游配置" style="width: 100%">
                <el-option v-for="inst in instances" :key="inst.id" :label="inst.name" :value="inst.id" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="sourceType === 'log'" label="日志 ID">
              <el-input v-model="logIdInput" placeholder="输入流量日志 ID" style="width: 160px" />
              <el-button size="small" @click="loadFromLog">加载</el-button>
            </el-form-item>
          </el-form>

          <el-tabs v-model="inputTab">
            <el-tab-pane label="Request Body" name="requestBody">
              <CodeEditor v-model="requestBodyJson" language="JSON" :height="panelHeight" />
            </el-tab-pane>
            <el-tab-pane label="Request Headers" name="requestHeaders">
              <CodeEditor v-model="requestHeadersJson" language="JSON" :height="panelHeight" />
            </el-tab-pane>
            <el-tab-pane v-if="phase === 'RESPONSE'" label="Response Body" name="responseBody">
              <CodeEditor v-model="responseBodyJson" language="JSON" :height="panelHeight" />
            </el-tab-pane>
            <el-tab-pane v-if="phase === 'RESPONSE'" label="Response Headers" name="responseHeaders">
              <CodeEditor v-model="responseHeadersJson" language="JSON" :height="panelHeight" />
            </el-tab-pane>
            <el-tab-pane v-if="phase === 'RESPONSE'" label="Response Status" name="responseStatus">
              <el-input-number v-model="responseStatus" :min="100" :max="599" />
            </el-tab-pane>
          </el-tabs>
        </div>

        <div class="panel output-panel">
          <div class="panel-header">
            <span>输出 / Diff</span>
            <el-tag v-if="result" :type="result.error ? 'danger' : 'success'" size="small">
              {{ result.error ? '执行出错' : '执行成功' }}
            </el-tag>
            <el-tag v-if="result && result.returned" type="warning" size="small">短路返回</el-tag>
            <el-tag v-if="result && result.stopped" type="info" size="small">stop()</el-tag>
          </div>

          <div v-if="result" class="output-body">
            <el-tabs v-model="outputTab">
              <el-tab-pane label="Diff" name="diff">
                <div class="diff-block">
                  <div class="diff-label">执行前</div>
                  <pre>{{ formatJson(result.input) }}</pre>
                  <div class="diff-label">执行后</div>
                  <pre>{{ formatJson(result.output) }}</pre>
                </div>
              </el-tab-pane>
              <el-tab-pane label="最终 Request" name="request">
                <pre>{{ formatJson(result.requestBody) }}</pre>
                <pre v-if="Object.keys(result.requestHeaders || {}).length">Headers: {{ formatJson(result.requestHeaders) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="最终 Response" name="response">
                <pre>Status: {{ result.responseStatus }}</pre>
                <pre>{{ formatJson(result.responseBody) }}</pre>
                <pre v-if="Object.keys(result.responseHeaders || {}).length">Headers: {{ formatJson(result.responseHeaders) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="插件日志" name="logs">
                <div v-if="result.pluginLogs && result.pluginLogs.length" class="log-lines">
                  <div v-for="(log, idx) in result.pluginLogs" :key="idx" class="log-line">{{ log }}</div>
                </div>
                <div v-else class="text-muted">无日志</div>
              </el-tab-pane>
              <el-tab-pane v-if="result.errorMessage" label="错误" name="error">
                <pre class="error-message">{{ result.errorMessage }}</pre>
              </el-tab-pane>
            </el-tabs>
          </div>
          <div v-else class="placeholder">点击“运行调试”查看结果</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CodeEditor from '../components/CodeEditor.vue'
import {
  getPlugin,
  updatePlugin,
  dryRunPlugin,
  listInstances,
  getLogFriendly,
} from '../api/damning.js'

const route = useRoute()
const router = useRouter()
const pluginId = computed(() => route.params.id)

const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const plugin = ref(null)
const script = ref('')
const originalScript = ref('')
const editable = ref(false)

const phase = ref('REQUEST')
const sourceType = ref('manual')
const selectedInstanceId = ref(null)
const logIdInput = ref('')
const inputTab = ref('requestBody')
const outputTab = ref('diff')

const requestBodyJson = ref('{}')
const requestHeadersJson = ref('{"Content-Type": "application/json", "Authorization": "Bearer test"}')
const responseBodyJson = ref('{}')
const responseHeadersJson = ref('{"Content-Type": "application/json"}')
const responseStatus = ref(200)

const instances = ref([])
const result = ref(null)
const docked = ref(false)

const editorHeight = computed(() => docked.value ? 520 : 420)
const panelHeight = computed(() => docked.value ? 240 : 200)

onMounted(async () => {
  await Promise.all([loadPlugin(), loadInstances()])
  const queryPhase = route.query.phase
  if (queryPhase === 'REQUEST' || queryPhase === 'RESPONSE') {
    phase.value = queryPhase
  }
  const logId = route.query.logId
  if (logId) {
    logIdInput.value = logId
    sourceType.value = 'log'
    await loadFromLog()
  }
})

async function loadPlugin() {
  loading.value = true
  try {
    const res = await getPlugin(pluginId.value)
    plugin.value = res.data
    script.value = res.data.script || ''
    originalScript.value = script.value
  } catch (e) {
    ElMessage.error('加载插件失败')
  } finally {
    loading.value = false
  }
}

async function loadInstances() {
  try {
    const res = await listInstances()
    instances.value = res.data
  } catch (e) {
    instances.value = []
  }
}

function resetScript() {
  script.value = originalScript.value
  ElMessage.info('已重置为保存时的脚本')
}

async function saveScript() {
  if (!plugin.value) return
  saving.value = true
  try {
    const form = new FormData()
    form.append('name', plugin.value.name)
    form.append('slug', plugin.value.slug)
    form.append('description', plugin.value.description || '')
    form.append('language', plugin.value.language)
    form.append('mode', plugin.value.mode || 'SINGLE_SCRIPT')
    form.append('script', script.value)
    form.append('executionPhase', plugin.value.executionPhase)
    form.append('enabled', plugin.value.enabled)
    await updatePlugin(plugin.value.id, form)
    originalScript.value = script.value
    ElMessage.success('脚本已保存并生效')
  } catch (e) {
    const msg = e.response?.data
    const detail = typeof msg === 'string' ? msg : (msg?.message || msg?.error?.message || JSON.stringify(msg))
    ElMessage.error(detail || '保存失败')
  } finally {
    saving.value = false
  }
}

function parseJsonSafe(text, defaultValue = null) {
  try {
    return JSON.parse(text)
  } catch (e) {
    return defaultValue
  }
}

function buildPayload() {
  const payload = {
    phase: phase.value,
    requestBody: parseJsonSafe(requestBodyJson.value),
    requestHeaders: parseJsonSafe(requestHeadersJson.value) || {},
  }
  if (selectedInstanceId.value) {
    payload.instanceId = selectedInstanceId.value
  }
  if (phase.value === 'RESPONSE') {
    payload.responseBody = parseJsonSafe(responseBodyJson.value)
    payload.responseHeaders = parseJsonSafe(responseHeadersJson.value) || {}
    payload.responseStatus = responseStatus.value
  }
  return payload
}

async function run() {
  running.value = true
  result.value = null
  try {
    const payload = buildPayload()
    const res = await dryRunPlugin(pluginId.value, payload)
    result.value = res.data
    outputTab.value = res.data.error ? 'error' : 'diff'
  } catch (e) {
    ElMessage.error('调试运行失败')
  } finally {
    running.value = false
  }
}

async function loadFromLog() {
  if (!logIdInput.value) return
  try {
    const res = await getLogFriendly(logIdInput.value)
    const log = res.data
    requestBodyJson.value = formatJson(log.requestBody)
    responseBodyJson.value = formatJson(log.responseBody)
    responseStatus.value = log.responseStatus || 200
    requestHeadersJson.value = formatJson(parseJsonSafe(log.rawRequestHeaders) || {})
    responseHeadersJson.value = formatJson(parseJsonSafe(log.rawResponseHeaders) || {})
    if (log.instanceId) {
      selectedInstanceId.value = log.instanceId
    }
    ElMessage.success('已从日志加载上下文')
  } catch (e) {
    ElMessage.error('加载日志失败')
  }
}

function formatJson(value) {
  if (value === undefined || value === null) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    return String(value)
  }
}

function goBack() {
  router.push('/plugins')
}

function dockToggle() {
  docked.value = !docked.value
}

watch(phase, () => {
  inputTab.value = phase.value === 'REQUEST' ? 'requestBody' : 'responseBody'
})
</script>

<style scoped>
.plugin-debugger {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  padding: 16px;
  box-sizing: border-box;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.toolbar .title {
  font-size: 16px;
  font-weight: 600;
}

.toolbar .spacer {
  flex: 1;
}

.main {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 12px;
}

.main.docked {
  flex-direction: column;
}

.editor-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.main.docked .editor-panel {
  flex: none;
  height: 50%;
}

.side-panels {
  width: 520px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.main.docked .side-panels {
  width: 100%;
  flex-direction: row;
  flex: 1;
}

.panel {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
  min-height: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  font-weight: 500;
  flex-shrink: 0;
}

.panel :deep(.el-tabs) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.panel :deep(.el-tabs__content) {
  flex: 1;
  overflow: auto;
  padding: 8px 12px;
}

.output-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  overflow: auto;
  font-size: 13px;
  margin: 0 0 12px 0;
}

.diff-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}

.diff-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-lines {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 8px 12px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
}

.log-line {
  padding: 2px 0;
  border-bottom: 1px solid #333;
}

.log-line:last-child {
  border-bottom: none;
}

.error-message {
  color: #f56c6c;
}

.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.text-muted {
  color: #909399;
  padding: 12px;
}
</style>
