<template>
  <div
    :ref="(el) => setCardRef(log.id, el)"
    :data-id="log.id"
    class="log-card"
    :class="{ error: log.responseStatus >= 400 }"
  >
    <div class="log-card-header">
      <div class="log-meta">
        <el-tag size="small" type="info"> #{{ log.id }} </el-tag>
        <el-tag v-if="log.instanceSlug" size="small" type="warning" class="instance-tag">
          {{ log.instanceSlug }}
        </el-tag>
        <span class="log-path">{{ log.requestMethod }} {{ log.requestPath }}</span>
        <el-tag size="small" :type="log.responseStatus >= 400 ? 'danger' : 'success'">
          {{ log.responseStatus || '-' }}
        </el-tag>
        <el-tag v-if="log.streaming" size="small" type="primary"> 流式 </el-tag>
      </div>
      <div class="log-time">
        <span v-if="log.durationMs">{{ log.durationMs }}ms · </span>
        <span v-if="log.totalTokens !== undefined && log.totalTokens !== null"
          >{{ log.totalTokens }} tokens ·
        </span>
        <span v-if="log.requestBodyLength">{{ formatBytes(log.requestBodyLength) }} · </span>
        {{ formatTime(log.requestTime) }}
      </div>
    </div>

    <div
      class="log-card-body"
      tabindex="0"
      role="button"
      :aria-label="'日志 #' + log.id + ' ' + log.requestMethod + ' ' + log.requestPath"
      @click="emit('open', log.id)"
      @keydown.enter="emit('open', log.id)"
      @keydown.space.prevent="emit('open', log.id)"
    >
      <template v-if="isChatLike(log)">
        <div class="summary-section">
          <div class="summary-label">📝 用户输入</div>
          <div class="summary-text">
            {{ log._friendly?.userPrompt || '-' }}
          </div>
        </div>
        <div class="summary-section">
          <div class="summary-label">🤖 模型输出</div>
          <div class="summary-text">
            {{ log._friendly?.modelOutput || '-' }}
          </div>
        </div>
        <div
          v-if="(log._friendly?.requestMessages?.length || 0) > 2"
          class="summary-section summary-meta"
        >
          <div class="summary-label">💬 对话历史</div>
          <div class="summary-text">
            共 {{ log._friendly.requestMessages.length }} 条消息 · 点击「详情」查看完整对话
          </div>
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
      <el-button size="small" type="primary" @click.stop="emit('open', log.id)"> 详情 </el-button>
      <el-button size="small" type="danger" @click.stop="emit('remove', log.id)"> 删除 </el-button>
    </div>
  </div>
</template>

<script setup>
import { formatBytes } from '../../utils/parse.js'

const props = defineProps({
  log: { type: Object, required: true },
  setCardRef: { type: Function, required: true },
})

const emit = defineEmits(['open', 'remove'])

function isChatLike(log) {
  return log && log.requestPath && log.requestPath.includes('/chat/completions')
}

function formatTime(value) {
  if (!value) return '-'
  return formatFullTime(value)
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
</script>

<style scoped>
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
</style>
