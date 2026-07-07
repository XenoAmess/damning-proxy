<template>
  <div
    class="message"
    :class="[msg.role, { selected: isSelected, selectable: selectMode }]"
    @click="selectMode && emit('toggle-select', index, !isSelected)"
  >
    <el-checkbox
      v-if="selectMode"
      :model-value="isSelected"
      class="select-checkbox"
      size="large"
      @click.stop
      @update:model-value="emit('toggle-select', index, $event)"
    />
    <div class="message-avatar">
      <el-avatar :size="36" :icon="msg.role === 'user' ? User : ChatLineRound" />
    </div>
    <div class="message-content">
      <div class="message-header">
        <span class="role-name">{{ msg.role === 'user' ? '我' : '助手' }}</span>
        <span v-if="msg.time" class="message-time">{{ formatTime(msg.time) }}</span>
        <el-button
          v-if="msg.role === 'user'"
          link
          size="small"
          class="action-btn"
          :icon="RefreshRight"
          title="重新发送"
          @click.stop="emit('resend', index)"
        >
          重发
        </el-button>
        <el-button
          v-else
          link
          size="small"
          class="action-btn"
          :icon="Refresh"
          title="重新生成"
          @click.stop="emit('regenerate', index)"
        >
          重新生成
        </el-button>
        <el-button
          link
          size="small"
          class="copy-btn"
          :icon="CopyDocument"
          title="复制"
          @click.stop="emit('copy', msg)"
        />
      </div>
      <div class="message-body">
        <div v-if="Array.isArray(msg.content)">
          <template v-for="(part, pidx) in msg.content" :key="pidx">
            <MarkdownRenderer
              v-if="part.type === 'text'"
              :content="parseThink(part.text).text"
              class="text-part markdown-body"
            />
            <div v-else-if="part.type === 'image_url'" class="image-part">
              <img :src="part.image_url.url" alt="uploaded" />
            </div>
            <div v-else-if="part.type === 'file'" class="file-part">
              <el-tag>📎 {{ part.file.name }}</el-tag>
            </div>
          </template>
        </div>
        <MarkdownRenderer
          v-else
          :content="parseThink(msg.content).text"
          class="text-part markdown-body"
        />

        <div v-if="msg.reasoning || parseThink(msg.content).reasoning" class="reasoning-block">
          <div class="reasoning-toggle" @click="toggleReasoning">
            <el-icon><ArrowDown v-if="showReasoning" /><ArrowRight v-else /></el-icon>
            推理过程
          </div>
          <pre v-if="showReasoning" class="reasoning-content">{{
            msg.reasoning || parseThink(msg.content).reasoning
          }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  User,
  ChatLineRound,
  CopyDocument,
  RefreshRight,
  Refresh,
  ArrowDown,
  ArrowRight,
} from '@element-plus/icons-vue'
import MarkdownRenderer from '../MarkdownRenderer.vue'
import { parseThink } from '../../utils/parse.js'

const props = defineProps({
  msg: { type: Object, required: true },
  index: { type: Number, required: true },
  selectMode: { type: Boolean, default: false },
  isSelected: { type: Boolean, default: false },
})

const showReasoning = ref(false)
function toggleReasoning() {
  showReasoning.value = !showReasoning.value
}

const emit = defineEmits(['toggle-select', 'resend', 'regenerate', 'copy'])

function formatTime(ts) {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
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

.message.user .message-header {
  flex-direction: row-reverse;
}

.copy-btn {
  margin-left: auto;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.copy-btn:hover {
  opacity: 1;
}

.message.user .copy-btn {
  margin-left: 0;
  margin-right: auto;
}

.action-btn {
  opacity: 0.6;
  transition: opacity 0.2s;
  font-size: 12px;
}

.action-btn:hover {
  opacity: 1;
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

.text-part :deep(ul),
.text-part :deep(ol) {
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

.text-part :deep(th),
.text-part :deep(td) {
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
</style>
