<template>
  <div class="chat-layout">
    <ChatSessionSidebar
      :sessions="sessions"
      :current-session-id="currentSessionId"
      @create="createSession"
      @switch="switchSession"
      @delete="deleteSession"
    />
    <div class="chat-main">
      <ChatToolbar
        :instance-slug="config.instanceSlug"
        :model="config.model"
        :instance-options="instanceOptions"
        :model-options="modelOptions"
        :show-params="showParams"
        :select-mode="selectMode"
        :can-generate="selectedMessages.length > 0"
        @update:instance-slug="updateInstanceSlug"
        @update:model="updateModel"
        @instance-change="onInstanceChange"
        @toggle-params="showParams = !showParams"
        @clear="clearCurrentHistory"
        @generate-image="generateImage"
        @update:select-mode="selectMode = $event"
      />
      <ChatParamPanel
        v-if="showParams"
        :temperature="config.temperature"
        :top-p="config.topP"
        :max-tokens="config.maxTokens"
        :system-prompt="config.systemPrompt"
        @update:temperature="updateTemperature"
        @update:top-p="updateTopP"
        @update:max-tokens="updateMaxTokens"
        @update:system-prompt="updateSystemPrompt"
      />
      <ChatMessageList
        ref="messagesRef"
        :current-messages="currentMessages"
        :selected-indices="selectedIndices"
        :select-mode="selectMode"
        :loading="loading"
        :selected-messages="selectedMessages"
        @toggle-select="onToggleSelect"
        @resend="resend"
        @regenerate="regenerate"
        @copy="copyMessage"
      />
      <ChatInputArea
        :input-text="inputText"
        :pending-files="pendingFiles"
        :stream="config.stream"
        :loading="loading"
        :can-send="canSend"
        @update:input-text="inputText = $event"
        @add-file="handleFileChange"
        @remove-file="removeFile"
        @update:stream="config.stream = $event"
        @send="send"
        @stop="stopStreaming"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTimestamp } from '../utils/format.js'
import { copyToClipboard } from '../utils/clipboard.js'
import { getAllSessions, saveAllSessions } from '../utils/chatStorage.js'
import { listInstances, listProfiles } from '../api/damning.js'
import {
  chatCompletion,
  chatCompletionStream,
  createAbortController,
  listModels,
} from '../api/chat.js'
import html2canvas from 'html2canvas'
import ChatSessionSidebar from '../components/chat/ChatSessionSidebar.vue'
import ChatToolbar from '../components/chat/ChatToolbar.vue'
import ChatParamPanel from '../components/chat/ChatParamPanel.vue'
import ChatMessageList from '../components/chat/ChatMessageList.vue'
import ChatInputArea from '../components/chat/ChatInputArea.vue'

const TYPEWRITER_DELAY = 16
const TYPEWRITER_CHUNK = 2
const STORAGE_KEY = 'damning-proxy-chat-sessions'
const CONFIG_KEY = 'damning-proxy-chat-config'
const MAX_SESSIONS = 50
const MAX_MESSAGES_PER_SESSION = 200

const sessions = ref([])
const currentSessionId = ref('')
const storageReady = ref(false)
const instances = ref([])
const profiles = ref([])
const inputText = ref('')
const pendingFiles = ref([])
const loading = ref(false)
const abortController = ref(null)
const messagesRef = ref(null)
const typewriterTarget = ref(null)
const typewriterBuffer = ref('')
const selectMode = ref(false)
const selectedIndices = ref([])
const showParams = ref(false)

const config = ref({
  instanceSlug: '',
  model: '',
  token: '',
  stream: true,
  temperature: null,
  topP: null,
  maxTokens: null,
  systemPrompt: '',
})

const currentSession = computed(() => sessions.value.find((s) => s.id === currentSessionId.value))

const currentMessages = computed(() => {
  return currentSession.value ? currentSession.value.messages : []
})

const selectedMessages = computed(() => {
  if (!currentSession.value) return []
  return currentSession.value.messages.filter((_, index) => selectedIndices.value[index])
})

watch(
  currentMessages,
  () => {
    selectedIndices.value = currentMessages.value.map(() => false)
  },
  { immediate: true }
)

function onToggleSelect(index, val) {
  selectedIndices.value[index] = val
}

function updateInstanceSlug(val) {
  config.value.instanceSlug = val
}

function updateModel(val) {
  config.value.model = val
}

function updateTemperature(val) {
  config.value.temperature = val
}

function updateTopP(val) {
  config.value.topP = val
}

function updateMaxTokens(val) {
  config.value.maxTokens = val
}

function updateSystemPrompt(val) {
  config.value.systemPrompt = val
}

async function generateImage() {
  if (selectedMessages.value.length === 0) return
  await nextTick()
  try {
    const el = messagesRef.value?.imageExportRef?.$el
    if (!el) return
    const canvas = await html2canvas(el, {
      backgroundColor: '#ffffff',
      scale: 2,
      useCORS: true,
    })
    const link = document.createElement('a')
    link.download = `damning_proxy_chat_${formatTimestamp()}_${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('图片已生成并下载')
  } catch (e) {
    ElMessage.error('生成图片失败: ' + (e.message || e))
  }
}

function extractTextContent(msg) {
  if (!msg || msg.content == null) return ''
  if (Array.isArray(msg.content)) {
    return msg.content
      .filter((part) => part.type === 'text')
      .map((part) => part.text)
      .join('\n')
  }
  return msg.content
}

async function copyMessage(msg) {
  try {
    await copyToClipboard(extractTextContent(msg))
    ElMessage.success('复制成功')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

const instanceOptions = computed(() =>
  instances.value.map((i) => ({ value: i.slug, label: `${i.name} (${i.slug})` }))
)

const modelOptions = ref([])

const canSend = computed(() => {
  return config.value.instanceSlug && (inputText.value.trim() || pendingFiles.value.length > 0)
})

onMounted(() => {
  loadConfig()
  loadSessions()
  loadInstances()
})

onUnmounted(() => {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
})

watch(
  () => config.value,
  () => {
    debouncedSaveConfig()
  },
  { deep: true }
)

watch(
  sessions,
  () => {
    debouncedSaveSessions()
  },
  { deep: true }
)

let saveConfigTimer = null
function debouncedSaveConfig() {
  clearTimeout(saveConfigTimer)
  saveConfigTimer = setTimeout(saveConfig, 500)
}

let saveSessionsTimer = null
function debouncedSaveSessions() {
  clearTimeout(saveSessionsTimer)
  saveSessionsTimer = setTimeout(saveSessions, 500)
}

function loadConfig() {
  try {
    const saved = sessionStorage.getItem(CONFIG_KEY)
    if (saved) {
      config.value = { ...config.value, ...JSON.parse(saved) }
    }
  } catch (e) {
    ElMessage.warning('配置数据已损坏，已重置')
  }
}

function saveConfig() {
  sessionStorage.setItem(CONFIG_KEY, JSON.stringify(config.value))
}

async function loadSessions() {
  try {
    let fromDB = await getAllSessions()
    if (fromDB.length === 0) {
      const legacy = localStorage.getItem(STORAGE_KEY)
      if (legacy) {
        const raw = JSON.parse(legacy)
        fromDB = raw.slice(0, MAX_SESSIONS).map((s) => ({
          ...s,
          messages: s.messages ? s.messages.slice(-MAX_MESSAGES_PER_SESSION) : [],
        }))
        await saveAllSessions(fromDB)
        localStorage.removeItem(STORAGE_KEY)
      }
    }
    sessions.value = fromDB.slice(0, MAX_SESSIONS).map((s) => ({
      ...s,
      messages: s.messages ? s.messages.slice(-MAX_MESSAGES_PER_SESSION) : [],
    }))
  } catch (e) {
    sessions.value = []
    ElMessage.warning('会话数据加载失败，已重置')
  }
  if (sessions.value.length === 0) {
    createSession()
  } else if (!currentSessionId.value) {
    currentSessionId.value = sessions.value[0].id
  }
  storageReady.value = true
}

async function saveSessions() {
  if (!storageReady.value) return
  const pruned = sessions.value.slice(0, MAX_SESSIONS).map((s) => ({
    ...s,
    messages: s.messages ? s.messages.slice(-MAX_MESSAGES_PER_SESSION) : [],
  }))
  try {
    await saveAllSessions(pruned)
  } catch (e) {
    console.error('保存会话失败', e)
  }
}

function createSession() {
  const id = 'session-' + Date.now()
  sessions.value.unshift({
    id,
    title: '新会话',
    messages: [],
    createdAt: Date.now(),
  })
  currentSessionId.value = id
}

function switchSession(id) {
  currentSessionId.value = id
}

async function deleteSession(id) {
  try {
    await ElMessageBox.confirm('确定删除该会话？', '提示', { type: 'warning' })
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : ''
    }
    if (sessions.value.length === 0) {
      createSession()
    }
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

function clearCurrentHistory() {
  const session = currentSession.value
  if (session) {
    session.messages = []
    session.title = '新会话'
  }
}

async function loadInstances() {
  try {
    const [instRes, profileRes] = await Promise.all([listInstances(), listProfiles()])
    instances.value = instRes.data
    profiles.value = profileRes.data
    updateTokenFromProfile()
    if (!config.value.instanceSlug && instances.value.length > 0) {
      config.value.instanceSlug = instances.value[0].slug
      updateTokenFromProfile()
    }
    if (config.value.instanceSlug) {
      await loadModels()
    }
  } catch (e) {
    ElMessage.error('加载实例失败')
  }
}

function updateTokenFromProfile() {
  const instance = instances.value.find((i) => i.slug === config.value.instanceSlug)
  if (!instance) {
    config.value.token = ''
    return
  }
  const profile = profiles.value.find((p) => p.id === instance.profileId)
  config.value.token = profile && profile.bearerToken ? profile.bearerToken : ''
}

async function onInstanceChange() {
  updateTokenFromProfile()
  await loadModels()
}

async function loadModels() {
  if (!config.value.instanceSlug) return
  try {
    const res = await listModels(config.value.instanceSlug, config.value.token)
    const data = res.data
    if (data && Array.isArray(data.data)) {
      modelOptions.value = data.data.map((m) => ({ value: m.id, label: m.id }))
    } else {
      modelOptions.value = []
    }
  } catch (e) {
    modelOptions.value = []
    ElMessage.warning('加载模型列表失败')
  }
}

function handleFileChange(file) {
  const raw = file.raw
  if (!raw) return
  const reader = new FileReader()
  reader.onload = () => {
    pendingFiles.value.push({
      name: raw.name,
      type: raw.type,
      dataUrl: reader.result,
    })
  }
  reader.readAsDataURL(raw)
}

function removeFile(idx) {
  pendingFiles.value.splice(idx, 1)
}

function buildContent(text, files) {
  if (files.length === 0) {
    return text
  }
  const parts = []
  if (text.trim()) {
    parts.push({ type: 'text', text })
  }
  for (const file of files) {
    if (file.type.startsWith('image/')) {
      parts.push({ type: 'image_url', image_url: { url: file.dataUrl } })
    } else {
      parts.push({ type: 'file', file: { name: file.name, data_url: file.dataUrl } })
    }
  }
  return parts
}

function stopStreaming() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  typewriterBuffer.value = ''
  typewriterTarget.value = null
  loading.value = false
}

function buildChatBody(history) {
  const messages = history.map((m) => ({
    role: m.role,
    content: m.content,
  }))
  const systemPrompt = (config.value.systemPrompt || '').trim()
  if (systemPrompt) {
    messages.unshift({ role: 'system', content: systemPrompt })
  }

  const body = {
    model: config.value.model || 'default',
    messages,
    stream: config.value.stream,
  }
  if (config.value.temperature != null) {
    body.temperature = config.value.temperature
  }
  if (config.value.topP != null) {
    body.top_p = config.value.topP
  }
  if (config.value.maxTokens != null) {
    body.max_tokens = config.value.maxTokens
  }
  return body
}

async function startAssistantRequest() {
  if (!config.value.instanceSlug) {
    ElMessage.warning('请先选择实例')
    return
  }
  if (loading.value) return

  const history = currentSession.value.messages.filter(
    (m) => m.role === 'user' || m.role === 'assistant'
  )
  const body = buildChatBody(history)

  loading.value = true
  if (abortController.value) {
    abortController.value.abort()
  }
  abortController.value = createAbortController()
  const signal = abortController.value.signal
  const assistantMsg = {
    role: 'assistant',
    content: '',
    reasoning: '',
    time: Date.now(),
    _showReasoning: false,
  }
  currentSession.value.messages.push(assistantMsg)
  const assistantIndex = currentSession.value.messages.length - 1

  try {
    if (config.value.stream) {
      for await (const chunk of chatCompletionStream(
        config.value.instanceSlug,
        body,
        config.value.token,
        signal
      )) {
        const delta = chunk.choices?.[0]?.delta
        if (delta) {
          if (delta.content) {
            appendWithTypewriter(assistantIndex, delta.content)
          }
          if (delta.reasoning_content) {
            currentSession.value.messages[assistantIndex].reasoning += delta.reasoning_content
          }
        }
      }
      await flushTypewriter(assistantIndex)
    } else {
      const res = await chatCompletion(config.value.instanceSlug, body, config.value.token)
      const data = res.data
      if (data.choices && data.choices[0]) {
        appendWithTypewriter(assistantIndex, data.choices[0].message?.content || '')
        currentSession.value.messages[assistantIndex].reasoning =
          data.choices[0].message?.reasoning_content || ''
        await flushTypewriter(assistantIndex)
      } else {
        currentSession.value.messages[assistantIndex].content = JSON.stringify(data)
      }
    }
  } catch (e) {
    await flushTypewriter(assistantIndex)
    currentSession.value.messages[assistantIndex].content += `请求失败: ${e.message}`
    currentSession.value.messages[assistantIndex].error = true
    ElMessage.error(e.message)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function setInputFromMessage(msg) {
  const content = msg.content
  if (Array.isArray(content)) {
    const texts = []
    pendingFiles.value = []
    for (const part of content) {
      if (part.type === 'text') {
        texts.push(part.text)
      } else if (part.type === 'image_url' && part.image_url?.url) {
        pendingFiles.value.push({
          name: 'image',
          type: 'image/*',
          dataUrl: part.image_url.url,
        })
      } else if (part.type === 'file' && part.file?.data_url) {
        pendingFiles.value.push({
          name: part.file.name || 'file',
          type: 'application/octet-stream',
          dataUrl: part.file.data_url,
        })
      }
    }
    inputText.value = texts.join('\n')
  } else {
    inputText.value = typeof content === 'string' ? content : ''
    pendingFiles.value = []
  }
}

async function send() {
  if (!canSend.value || loading.value) return
  if (!config.value.instanceSlug) {
    ElMessage.warning('请先选择实例')
    return
  }

  const text = inputText.value.trim()
  const files = pendingFiles.value.slice()
  inputText.value = ''
  pendingFiles.value = []

  const userContent = buildContent(text, files)
  const userMessage = {
    role: 'user',
    content: userContent,
    time: Date.now(),
  }

  currentSession.value.messages.push(userMessage)
  updateSessionTitle(text || '附件消息')
  scrollToBottom()

  await startAssistantRequest()
}

async function resend(index) {
  const session = currentSession.value
  if (!session || loading.value) return
  const msg = session.messages[index]
  if (!msg || msg.role !== 'user') return
  setInputFromMessage(msg)
  await send()
}

async function regenerate(index) {
  const session = currentSession.value
  if (!session || loading.value) return
  const messages = session.messages
  if (messages[index]?.role !== 'assistant') return
  let prevUserIdx = -1
  for (let i = index - 1; i >= 0; i--) {
    if (messages[i].role === 'user') {
      prevUserIdx = i
      break
    }
  }
  if (prevUserIdx === -1) {
    ElMessage.warning('没有可重试的用户消息')
    return
  }
  messages.splice(prevUserIdx + 1)
  scrollToBottom()
  await startAssistantRequest()
}

function appendWithTypewriter(index, text) {
  if (typewriterTarget.value !== index) {
    typewriterTarget.value = index
    typewriterBuffer.value = ''
  }
  typewriterBuffer.value += text
}

async function flushTypewriter(index) {
  const target = currentSession.value.messages[index]
  if (!target) return
  const buffer = typewriterBuffer.value
  typewriterBuffer.value = ''
  typewriterTarget.value = null
  for (let i = 0; i < buffer.length; i += TYPEWRITER_CHUNK) {
    target.content += buffer.slice(i, i + TYPEWRITER_CHUNK)
    scrollToBottom()
    await sleep(TYPEWRITER_DELAY)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function updateSessionTitle(text) {
  const session = currentSession.value
  if (session && (!session.title || session.title === '新会话')) {
    session.title = text.slice(0, 20)
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value?.messagesRef
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
