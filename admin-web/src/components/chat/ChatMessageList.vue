<template>
  <div ref="messagesRef" class="messages">
    <div v-if="currentMessages.length === 0" class="empty-state">
      <el-empty description="选择实例并开始对话" />
    </div>

    <ChatMessageItem
      v-for="(msg, index) in currentMessages"
      :key="index"
      :msg="msg"
      :index="index"
      :select-mode="selectMode"
      :is-selected="selectedIndices[index]"
      @toggle-select="(...args) => emit('toggle-select', ...args)"
      @resend="(...args) => emit('resend', ...args)"
      @regenerate="(...args) => emit('regenerate', ...args)"
      @copy="(...args) => emit('copy', ...args)"
    />

    <ChatImageExport ref="imageExportRef" :selected-messages="selectedMessages" />

    <div v-if="loading" class="message assistant">
      <div class="message-avatar">
        <el-avatar :size="36" :icon="ChatLineRound" />
      </div>
      <div class="message-content">
        <el-text class="typing" size="small"> 思考中... </el-text>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineExpose } from 'vue'
import { ChatLineRound } from '@element-plus/icons-vue'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatImageExport from './ChatImageExport.vue'

const props = defineProps({
  currentMessages: { type: Array, default: () => [] },
  selectedIndices: { type: Array, default: () => [] },
  selectMode: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  selectedMessages: { type: Array, default: () => [] },
})

const emit = defineEmits(['toggle-select', 'resend', 'regenerate', 'copy'])

const messagesRef = ref(null)
const imageExportRef = ref(null)
defineExpose({ messagesRef, imageExportRef })
</script>

<style scoped>
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

.typing {
  color: #909399;
}
</style>
