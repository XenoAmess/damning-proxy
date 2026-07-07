<template>
  <div v-loading="loading" class="plugin-editor">
    <div class="toolbar">
      <el-button @click="goBack"> 返回 </el-button>
      <span class="title">{{ plugin.name || '插件编辑器' }}</span>
      <el-tag v-if="plugin.id" :type="plugin.enabled ? 'success' : 'info'">
        {{ plugin.enabled ? '已启用' : '已禁用' }}
      </el-tag>
      <el-tag v-if="plugin.id">
        {{ plugin.language }}
      </el-tag>
      <el-tag v-if="plugin.id">
        {{ plugin.mode === 'ZIP_PACKAGE' ? 'ZIP包' : '单脚本' }}
      </el-tag>
      <el-tag v-if="plugin.id">
        {{ plugin.executionPhase }}
      </el-tag>
      <div class="spacer" />
      <el-switch
        v-if="plugin.id"
        v-model="plugin.enabled"
        active-text="启用"
        inactive-text="禁用"
      />
      <el-button type="primary" :loading="saving" @click="save"> 保存 </el-button>
      <el-button type="success" :loading="running" @click="run"> 运行调试 </el-button>
    </div>

    <div class="main">
      <div class="left-panel">
        <el-form label-position="top" size="small">
          <el-form-item label="名称">
            <el-input v-model="plugin.name" />
          </el-form-item>
          <el-form-item label="标识">
            <el-input v-model="plugin.slug" disabled />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="plugin.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="模式">
            <el-radio-group v-model="plugin.mode">
              <el-radio-button label="SINGLE_SCRIPT" />
              <el-radio-button label="ZIP_PACKAGE" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="语言">
            <el-radio-group v-model="plugin.language">
              <el-radio-button label="GROOVY" />
              <el-radio-button label="JS" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="执行阶段">
            <el-radio-group v-model="plugin.executionPhase">
              <el-radio-button label="REQUEST" />
              <el-radio-button label="RESPONSE" />
              <el-radio-button label="STREAM_CHUNK" />
              <el-radio-button label="BOTH" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="plugin.enabled" />
          </el-form-item>
          <template v-if="plugin.mode === 'ZIP_PACKAGE'">
            <el-form-item label="当前包">
              <div class="package-info">
                {{ plugin.packagePath || '无' }}
              </div>
            </el-form-item>
            <el-form-item label="重新上传 ZIP">
              <el-upload
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handlePackageSelect"
                accept=".zip"
              >
                <el-button type="primary" size="small">
                  {{ packageFile ? '重新选择' : '选择 ZIP' }}
                </el-button>
              </el-upload>
              <div v-if="packageFile" class="package-info">
                已选择: {{ packageFile.name }} ({{ formatBytes(packageFile.size) }})
              </div>
              <div v-else class="package-info text-muted">未选择新 ZIP</div>
            </el-form-item>
            <el-form-item v-if="packageEntries.length" label="包内文件">
              <el-table :data="packageEntries" size="small" height="160" border>
                <el-table-column prop="name" label="路径" show-overflow-tooltip />
                <el-table-column prop="size" label="大小" width="80" />
              </el-table>
            </el-form-item>
          </template>
        </el-form>
      </div>

      <div class="center-panel">
        <div class="panel-header">
          <span>脚本编辑器</span>
          <el-button size="small" text @click="resetScript"> 重置 </el-button>
        </div>
        <CodeEditor
          v-if="plugin.id"
          v-model="script"
          :language="plugin.language"
          :height="editorHeight"
        />
      </div>

      <div class="right-panel">
        <div class="panel input-panel">
          <div class="panel-header">
            <span>输入上下文</span>
          </div>
          <el-form label-position="top" size="small">
            <el-form-item label="调试阶段">
              <el-radio-group v-model="phase">
                <el-radio-button label="REQUEST" />
                <el-radio-button label="RESPONSE" />
                <el-radio-button label="STREAM_CHUNK" />
              </el-radio-group>
            </el-form-item>
            <el-form-item label="数据来源">
              <el-radio-group v-model="sourceType">
                <el-radio-button label="manual"> 手动输入 </el-radio-button>
                <el-radio-button label="instance"> 从实例加载 </el-radio-button>
                <el-radio-button label="log"> 从日志加载 </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="sourceType === 'instance'" label="选择实例">
              <el-select
                v-model="selectedInstanceId"
                placeholder="选择实例以应用上游配置"
                style="width: 100%"
              >
                <el-option
                  v-for="inst in instances"
                  :key="inst.id"
                  :label="inst.name"
                  :value="inst.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="sourceType === 'log'" label="日志 ID">
              <el-input v-model="logIdInput" placeholder="输入流量日志 ID" style="width: 160px" />
              <el-button size="small" @click="loadFromLog"> 加载 </el-button>
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
            <el-tab-pane
              v-if="phase === 'RESPONSE'"
              label="Response Headers"
              name="responseHeaders"
            >
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
            <el-tag v-if="result && result.returned" type="warning" size="small"> 短路返回 </el-tag>
            <el-tag v-if="result && result.stopped" type="info" size="small"> stop() </el-tag>
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
                <pre v-if="Object.keys(result.requestHeaders || {}).length">
Headers: {{ formatJson(result.requestHeaders) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="最终 Response" name="response">
                <pre>Status: {{ result.responseStatus }}</pre>
                <pre>{{ formatJson(result.responseBody) }}</pre>
                <pre v-if="Object.keys(result.responseHeaders || {}).length">
Headers: {{ formatJson(result.responseHeaders) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="插件日志" name="logs">
                <div v-if="result.pluginLogs && result.pluginLogs.length" class="log-lines">
                  <div v-for="(log, idx) in result.pluginLogs" :key="idx" class="log-line">
                    {{ log }}
                  </div>
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
        <div v-if="plugin.id && plugin.mode === 'SINGLE_SCRIPT'" class="panel revisions-panel">
          <div class="panel-header">
            <span>版本历史</span>
            <el-button size="small" text @click="loadRevisions"> 刷新 </el-button>
          </div>
          <div v-if="revisionsLoading" class="text-muted">加载中...</div>
          <div v-else-if="!revisions.length" class="text-muted">暂无历史版本</div>
          <div v-else class="revision-list">
            <div v-for="rev in revisions" :key="rev.id" class="revision-item">
              <div class="revision-meta">
                <span class="revision-id">#{{ rev.id }}</span>
                <span class="revision-time">{{ formatTime(rev.createdAt) }}</span>
              </div>
              <div class="revision-actions">
                <el-button size="small" text @click="previewRevision(rev)"> 预览 </el-button>
                <el-button size="small" type="primary" text @click="rollback(rev)">
                  回滚
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import CodeEditor from '../components/CodeEditor.vue'
import {
  getPlugin,
  updatePlugin,
  dryRunPlugin,
  listInstances,
  listPluginRevisions,
  rollbackPlugin,
  getLogFriendly,
} from '../api/damning.js'
import { formatBytes } from '../utils/parse.js'

const route = useRoute()
const router = useRouter()
const pluginId = computed(() => route.params.id)

const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const plugin = ref({
  name: '',
  slug: '',
  description: '',
  language: 'GROOVY',
  mode: 'SINGLE_SCRIPT',
  executionPhase: 'BOTH',
  enabled: true,
  packagePath: '',
})
const script = ref('')
const originalScript = ref('')

const phase = ref('REQUEST')
const sourceType = ref('manual')
const selectedInstanceId = ref(null)
const logIdInput = ref('')
const inputTab = ref('requestBody')
const outputTab = ref('diff')

const requestBodyJson = ref('{}')
const requestHeadersJson = ref(
  '{"Content-Type": "application/json", "Authorization": "Bearer test"}'
)
const responseBodyJson = ref('{}')
const responseHeadersJson = ref('{"Content-Type": "application/json"}')
const responseStatus = ref(200)

const instances = ref([])
const result = ref(null)
const packageFile = ref(null)
const packageEntries = ref([])
const revisions = ref([])
const revisionsLoading = ref(false)

const editorHeight = ref(window.innerHeight - 120)
const panelHeight = ref(220)

function updateHeights() {
  editorHeight.value = window.innerHeight - 120
  panelHeight.value = Math.max(180, Math.floor((window.innerHeight - 360) / 2))
}

onMounted(async () => {
  updateHeights()
  window.addEventListener('resize', updateHeights)
  await Promise.all([loadPlugin(), loadInstances()])
  loadRevisions()
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

onUnmounted(() => {
  window.removeEventListener('resize', updateHeights)
})

async function loadPlugin() {
  loading.value = true
  try {
    const res = await getPlugin(pluginId.value)
    plugin.value = { ...plugin.value, ...res.data }
    script.value = res.data.script || ''
    originalScript.value = script.value
    if (plugin.value.mode === 'ZIP_PACKAGE') {
      await loadPackageEntries()
    }
  } catch (e) {
    ElMessage.error('加载插件失败')
  } finally {
    loading.value = false
  }
}

async function loadPackageEntries() {
  try {
    const res = await axios.get(`/api/plugins/${pluginId.value}/entries`)
    packageEntries.value = res.data.map((e) => ({ name: e, size: '-' }))
  } catch (e) {
    packageEntries.value = []
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

function handlePackageSelect(file) {
  packageFile.value = file.raw
  readPackageEntries(file.raw)
}

async function readPackageEntries(file) {
  packageEntries.value = []
  if (!file) return
  try {
    const JSZip = (await import('jszip')).default
    const zip = await JSZip.loadAsync(file)
    zip.forEach((relativePath, zipEntry) => {
      if (!zipEntry.dir) {
        packageEntries.value.push({
          name: relativePath,
          size: formatBytes(zipEntry._data.uncompressedSize || 0),
        })
      }
    })
  } catch (e) {
    ElMessage.warning('无法预览 ZIP 内容，请确保文件有效')
  }
}

function buildFormData() {
  const fd = new FormData()
  fd.append('name', plugin.value.name)
  fd.append('slug', plugin.value.slug)
  fd.append('description', plugin.value.description || '')
  fd.append('language', plugin.value.language)
  fd.append('mode', plugin.value.mode)
  fd.append('executionPhase', plugin.value.executionPhase)
  fd.append('enabled', plugin.value.enabled)
  if (plugin.value.mode === 'SINGLE_SCRIPT') {
    fd.append('script', script.value)
  }
  if (packageFile.value) {
    fd.append('packageFile', packageFile.value)
  }
  return fd
}

async function save() {
  if (!plugin.value.id) return
  saving.value = true
  try {
    const fd = buildFormData()
    await updatePlugin(plugin.value.id, fd)
    originalScript.value = script.value
    ElMessage.success('保存成功')
  } catch (e) {
    const msg = e.response?.data
    const detail =
      typeof msg === 'string' ? msg : msg?.message || msg?.error?.message || JSON.stringify(msg)
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

async function loadRevisions() {
  if (!pluginId.value) return
  revisionsLoading.value = true
  try {
    const res = await listPluginRevisions(pluginId.value)
    revisions.value = res.data || []
  } catch (e) {
    revisions.value = []
  } finally {
    revisionsLoading.value = false
  }
}

function previewRevision(rev) {
  script.value = rev.script || ''
  ElMessage.info('已加载历史版本到编辑器，点击保存后生效')
}

async function rollback(rev) {
  if (!pluginId.value) return
  try {
    await rollbackPlugin(pluginId.value, rev.id)
    await loadPlugin()
    await loadRevisions()
    ElMessage.success('回滚成功')
  } catch (e) {
    ElMessage.error('回滚失败')
  }
}

function formatTime(value) {
  if (!value) return ''
  const d = new Date(value)
  return isNaN(d.getTime()) ? value : d.toLocaleString()
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

watch(phase, () => {
  inputTab.value = phase.value === 'REQUEST' ? 'requestBody' : 'responseBody'
})
</script>

<style scoped>
.plugin-editor {
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

.left-panel {
  width: 280px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
}

.center-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}

.right-panel {
  width: 520px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.revisions-panel {
  flex: 0.6;
  min-height: 120px;
}

.revision-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding: 8px 0;
}

.revision-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.revision-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.revision-id {
  color: #606266;
  font-weight: 500;
}

.revision-time {
  color: #909399;
  font-size: 12px;
}

.revision-actions {
  display: flex;
  gap: 4px;
}

.text-muted {
  color: #909399;
  padding: 12px;
}

.package-info {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
</style>
