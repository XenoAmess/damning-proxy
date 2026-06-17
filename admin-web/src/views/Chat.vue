<template>
  <div class="chat-layout">
    <div class="session-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" @click="createSession" style="width: 100%">
          <el-icon><Plus /></el-icon> 新建会话
        </el-button>
      </div>
      <el-menu :default-active="currentSessionId" class="session-menu">
        <el-menu-item
          v-for="session in sessions"
          :key="session.id"
          :index="session.id"
          @click="switchSession(session.id)"
        >
          <div class="session-item">
            <span class="session-title">{{ session.title || '新会话' }}</span>
            <el-button
              link
              size="small"
              type="danger"
              class="delete-btn"
              @click.stop="deleteSession(session.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </el-menu-item>
      </el-menu>
    </div>

    <div class="chat-main">
      <div class="chat-toolbar">
        <div class="toolbar-left">
          <el-select-v2
            v-model="config.instanceSlug"
            :options="instanceOptions"
            placeholder="选择实例"
            style="width: 200px"
            @change="onInstanceChange"
          />
          <el-select-v2
            v-model="config.model"
            :options="modelOptions"
            placeholder="选择模型"
            style="width: 180px; margin-left: 12px"
            allow-create
            filterable
            clearable
          />
        </div>
        <div class="toolbar-right">
          <el-button style="margin-left: 12px" @click="clearCurrentHistory">清空当前</el-button>
          <el-button style="margin-left: 12px" type="primary" :disabled="selectedMessages.length === 0" @click="generateImage">生成图片</el-button>
          <el-checkbox v-model="selectMode" style="margin-left: 12px">选择模式</el-checkbox>
        </div>
      </div>

      <div class="messages" ref="messagesRef">
        <div v-if="currentMessages.length === 0" class="empty-state">
          <el-empty description="选择实例并开始对话" />
        </div>

        <div
          v-for="(msg, index) in currentMessages"
          :key="index"
          class="message"
          :class="[msg.role, { selected: isSelected(index), selectable: selectMode }]"
          @click="selectMode && toggleSelect(index)"
        >
          <el-checkbox
            v-if="selectMode"
            v-model="selectedIndices[index]"
            class="select-checkbox"
            size="large"
            @click.stop
          />
          <div class="message-avatar">
            <el-avatar :size="36" :icon="msg.role === 'user' ? User : ChatLineRound" />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="role-name">{{ msg.role === 'user' ? '我' : '助手' }}</span>
              <span class="message-time" v-if="msg.time">{{ formatTime(msg.time) }}</span>
            </div>
            <div class="message-body">
              <div v-if="Array.isArray(msg.content)">
                <template v-for="(part, pidx) in msg.content" :key="pidx">
                  <div v-if="part.type === 'text'" class="text-part markdown-body" v-html="renderMarkdown(parseThink(part.text).text)"></div>
                  <div v-else-if="part.type === 'image_url'" class="image-part">
                    <img :src="part.image_url.url" alt="uploaded" />
                  </div>
                  <div v-else-if="part.type === 'file'" class="file-part">
                    <el-tag>📎 {{ part.file.name }}</el-tag>
                  </div>
                </template>
              </div>
              <div v-else class="text-part markdown-body" v-html="renderMarkdown(parseThink(msg.content).text)"></div>

              <div v-if="msg.reasoning || parseThink(msg.content).reasoning" class="reasoning-block">
                <div class="reasoning-toggle" @click="msg._showReasoning = !msg._showReasoning">
                  <el-icon><ArrowDown v-if="msg._showReasoning" /><ArrowRight v-else /></el-icon>
                  推理过程
                </div>
                <pre v-if="msg._showReasoning" class="reasoning-content">{{ msg.reasoning || parseThink(msg.content).reasoning }}</pre>
              </div>
            </div>
          </div>
        </div>

        <div v-show="false" class="image-export-container" ref="imageExportRef">
          <div class="image-export-header">对话记录</div>
          <div
            v-for="(msg, index) in selectedMessages"
            :key="index"
            class="image-export-message"
            :class="msg.role"
          >
            <div class="image-export-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
            <div class="image-export-content">
              <div v-if="Array.isArray(msg.content)">
                <template v-for="(part, pidx) in msg.content" :key="pidx">
                  <div v-if="part.type === 'text'" v-html="renderMarkdown(parseThink(part.text).text)"></div>
                  <div v-else-if="part.type === 'image_url'" class="image-export-image">
                    [图片]
                  </div>
                  <div v-else-if="part.type === 'file'" class="image-export-file">
                    📎 {{ part.file.name }}
                  </div>
                </template>
              </div>
              <div v-else v-html="renderMarkdown(parseThink(msg.content).text)"></div>
              <div v-if="msg.reasoning || parseThink(msg.content).reasoning" class="image-export-reasoning">
                <div class="image-export-reasoning-title">推理过程</div>
                <pre>{{ msg.reasoning || parseThink(msg.content).reasoning }}</pre>
              </div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="message assistant">
          <div class="message-avatar">
            <el-avatar :size="36" :icon="ChatLineRound" />
          </div>
          <div class="message-content">
            <el-text class="typing" size="small">思考中...</el-text>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-toolbar">
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            multiple
            class="upload-inline"
          >
            <el-button :icon="Paperclip">附件 / 图片</el-button>
          </el-upload>
          <el-switch v-model="config.stream" active-text="流式输出" style="margin-left: 12px" />
        </div>

        <div v-if="pendingFiles.length > 0" class="file-preview-list">
          <div v-for="(file, idx) in pendingFiles" :key="idx" class="file-preview"
          >
            <img v-if="file.type.startsWith('image/')" :src="file.dataUrl" class="preview-img" />
            <el-tag v-else closable @close="removeFile(idx)">📎 {{ file.name }}</el-tag>
            <div v-if="file.type.startsWith('image/')" class="preview-remove" @click="removeFile(idx)"
            >×</div>
          </div>
        </div>

        <div class="input-row">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入消息..."
            resize="none"
            @keydown.enter.exact.prevent="send"
          />
          <el-button
            type="primary"
            :disabled="!canSend || loading"
            @click="send"
            class="send-btn"
          >
            <el-icon><Promotion /></el-icon> 发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import {
  Plus, Delete, User, ChatLineRound, Paperclip, Promotion,
  ArrowDown, ArrowRight,
} from '@element-plus/icons-vue'
import { listInstances, listProfiles } from '../api/damning.js'
import { chatCompletion, chatCompletionStream, listModels } from '../api/chat.js'
import html2canvas from 'html2canvas'

const TYPEWRITER_DELAY = 16
const TYPEWRITER_CHUNK = 2

const STORAGE_KEY = 'damning-proxy-chat-sessions'
const CONFIG_KEY = 'damning-proxy-chat-config'

const sessions = ref([])
const currentSessionId = ref('')
const instances = ref([])
const profiles = ref([])
const inputText = ref('')
const pendingFiles = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const uploadRef = ref(null)
const typewriterTarget = ref(null)
const typewriterBuffer = ref('')
const imageExportRef = ref(null)
const selectMode = ref(false)
const selectedIndices = ref([])

const selectedMessages = computed(() => {
  if (!currentSession.value) return []
  return currentSession.value.messages.filter((_, index) => selectedIndices.value[index])
})

watch(currentMessages, () => {
  selectedIndices.value = currentMessages.value.map(() => false)
}, { immediate: true })

function toggleSelect(index) {
  selectedIndices.value[index] = !selectedIndices.value[index]
}

function isSelected(index) {
  return !!selectedIndices.value[index]
}

async function generateImage() {
  if (selectedMessages.value.length === 0) return
  await nextTick()
  try {
    const canvas = await html2canvas(imageExportRef.value, {
      backgroundColor: '#ffffff',
      scale: 2,
      useCORS: true,
    })
    const link = document.createElement('a')
    link.download = `chat-${new Date().toISOString().slice(0, 10)}-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('图片已生成并下载')
  } catch (e) {
    ElMessage.error('生成图片失败: ' + (e.message || e))
  }
}

const config = ref({
  instanceSlug: '',
  model: '',
  token: '',
  stream: true,
})

const currentSession = computed(() =>
  sessions.value.find(s => s.id === currentSessionId.value)
)

const currentMessages = computed(() => {
  return currentSession.value ? currentSession.value.messages : []
})

const instanceOptions = computed(() =>
  instances.value.map(i => ({ value: i.slug, label: `${i.name} (${i.slug})` }))
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

watch(() => config.value, () => {
  saveConfig()
}, { deep: true })

watch(sessions, () => {
  saveSessions()
}, { deep: true })

function loadConfig() {
  try {
    const saved = localStorage.getItem(CONFIG_KEY)
    if (saved) {
      config.value = { ...config.value, ...JSON.parse(saved) }
    }
  } catch (e) {
    // ignore
  }
}

function saveConfig() {
  localStorage.setItem(CONFIG_KEY, JSON.stringify(config.value))
}

function loadSessions() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      sessions.value = JSON.parse(saved)
    }
  } catch (e) {
    sessions.value = []
  }
  if (sessions.value.length === 0) {
    createSession()
  } else if (!currentSessionId.value) {
    currentSessionId.value = sessions.value[0].id
  }
}

function saveSessions() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
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
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : ''
    }
    if (sessions.value.length === 0) {
      createSession()
    }
  } catch (e) {
    if (e !== 'cancel') {
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
    const [instRes, profileRes] = await Promise.all([
      listInstances(),
      listProfiles(),
    ])
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
  const instance = instances.value.find(i => i.slug === config.value.instanceSlug)
  if (!instance) {
    config.value.token = ''
    return
  }
  const profile = profiles.value.find(p => p.id === instance.profileId)
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
      modelOptions.value = data.data.map(m => ({ value: m.id, label: m.id }))
    } else {
      modelOptions.value = []
    }
  } catch (e) {
    modelOptions.value = []
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

  const history = currentSession.value.messages.filter(m =>
    m.role === 'user' || m.role === 'assistant'
  )

  const body = {
    model: config.value.model || 'default',
    messages: history.map(m => ({
      role: m.role,
      content: m.content,
    })),
    stream: config.value.stream,
  }

  loading.value = true
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
      for await (const chunk of chatCompletionStream(config.value.instanceSlug, body, config.value.token)) {
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
        currentSession.value.messages[assistantIndex].reasoning = data.choices[0].message?.reasoning_content || ''
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
  return new Promise(resolve => setTimeout(resolve, ms))
}

function updateSessionTitle(text) {
  const session = currentSession.value
  if (session && (!session.title || session.title === '新会话')) {
    session.title = text.slice(0, 20)
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function formatTime(ts) {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function renderMarkdown(text) {
  if (typeof text !== 'string') return ''
  return marked.parse(text, { breaks: true, gfm: true })
}

function parseThink(content) {
  if (typeof content !== 'string') {
    return { text: content || '', reasoning: '' }
  }
  const match = content.match(/\u003cthink\u003e([\s\S]*?)\u003c\/think\u003e/)
  if (!match) {
    return { text: content, reasoning: '' }
  }
  const reasoning = match[1].trim()
  const text = content.replace(/\u003cthink\u003e[\s\S]*?\u003c\/think\u003e/, '').trim()
  return { text, reasoning }
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.session-sidebar {
  width: 220px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.session-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  background: transparent;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-menu .el-menu-item:hover .delete-btn {
  opacity: 1;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message {
  display: flex;
  margin-bottom: 20px;
  max-width: 90%;
}

.message.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.message-avatar {
  margin: 0 12px;
  flex-shrink: 0;
}

.message-content {
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  max-width: calc(100% - 60px);
  overflow-wrap: break-word;
}

.message.user .message-content {
  background: #e6f7ff;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}

.role-name {
  font-weight: 600;
  color: #303133;
}

.message-time {
  color: #909399;
  font-size: 12px;
}

.message-body {
  line-height: 1.6;
  color: #303133;
}

.text-part {
  white-space: normal;
}

.text-part :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.text-part :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.9em;
}

.text-part :deep(p) {
  margin: 0 0 8px;
}

.text-part :deep(p):last-child {
  margin-bottom: 0;
}

.text-part :deep(ul), .text-part :deep(ol) {
  margin: 0 0 8px 20px;
  padding: 0;
}

.text-part :deep(li) {
  margin-bottom: 4px;
}

.text-part :deep(table) {
  border-collapse: collapse;
  margin-bottom: 8px;
}

.text-part :deep(th), .text-part :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
}

.text-part :deep(blockquote) {
  margin: 0 0 8px;
  padding-left: 12px;
  border-left: 4px solid #dcdfe6;
  color: #606266;
}

.image-part img {
  max-width: 240px;
  max-height: 240px;
  border-radius: 6px;
  margin-top: 8px;
}

.file-part {
  margin-top: 8px;
}

.reasoning-block {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 6px;
}

.reasoning-toggle {
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.reasoning-content {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  background: #fff;
  padding: 8px;
  border-radius: 4px;
}

.typing {
  color: #909399;
}

.input-area {
  border-top: 1px solid #e4e7ed;
  padding: 12px 16px;
  background: #fff;
}

.input-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.upload-inline {
  display: inline-block;
}

.file-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.file-preview {
  position: relative;
}

.preview-img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.preview-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  background: #f56c6c;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-row .el-textarea {
  flex: 1;
}

.send-btn {
  height: 74px;
}
</style>
