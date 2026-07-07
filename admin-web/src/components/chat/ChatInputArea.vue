
<template>
  <div class="input-area">
    <div class="input-toolbar">
      <el-upload
        ref="uploadRef"
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="onFileChange"
        multiple
        class="upload-inline"
      >
        <el-button :icon="Paperclip">附件 / 图片</el-button>
      </el-upload>
      <el-switch :model-value="stream" active-text="流式输出" style="margin-left: 12px" @update:model-value="emit('update:stream', $event)" />
    </div>

    <div v-if="pendingFiles.length > 0" class="file-preview-list">
      <div v-for="(file, idx) in pendingFiles" :key="idx" class="file-preview"
      >
        <img v-if="file.type.startsWith('image/')" :src="file.dataUrl" class="preview-img" />
        <el-tag v-else closable @close="emit('remove-file', idx)">📎 {{ file.name }}</el-tag>
        <div v-if="file.type.startsWith('image/')" class="preview-remove" @click="emit('remove-file', idx)"
        >×</div>
      </div>
    </div>

    <div class="input-row">
      <el-input
        :model-value="inputText"
        type="textarea"
        :rows="3"
        placeholder="输入消息..."
        resize="none"
        @update:model-value="emit('update:inputText', $event)"
        @keydown.enter.exact.prevent="emit('send')"
      />
      <el-button
        v-if="!loading"
        type="primary"
        :disabled="!canSend"
        @click="emit('send')"
        class="send-btn"
      >
        <el-icon><Promotion /></el-icon> 发送
      </el-button>
      <el-button
        v-else
        type="danger"
        @click="emit('stop')"
        class="send-btn"
      >
        <el-icon><Delete /></el-icon> 停止
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Paperclip, Promotion, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  inputText: { type: String, default: '' },
  pendingFiles: { type: Array, default: () => [] },
  stream: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
  canSend: { type: Boolean, default: false },
})

const emit = defineEmits(['update:inputText', 'add-file', 'remove-file', 'update:stream', 'send', 'stop'])

const uploadRef = ref(null)

function onFileChange(file) {
  emit('add-file', file)
}
</script>

<style scoped>
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
