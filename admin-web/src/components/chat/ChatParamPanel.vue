<template>
  <div class="param-panel">
    <div class="param-row">
      <div class="param-item">
        <label>Temperature</label>
        <el-input-number
          :model-value="temperature"
          :min="0"
          :max="2"
          :step="0.1"
          controls-position="right"
          style="width: 120px"
          @update:model-value="emit('update:temperature', $event)"
        />
      </div>
      <div class="param-item">
        <label>Top P</label>
        <el-input-number
          :model-value="topP"
          :min="0"
          :max="1"
          :step="0.1"
          controls-position="right"
          style="width: 120px"
          @update:model-value="emit('update:topP', $event)"
        />
      </div>
      <div class="param-item">
        <label>Max Tokens</label>
        <el-input-number
          :model-value="maxTokens"
          :min="1"
          :max="32768"
          :step="1"
          controls-position="right"
          style="width: 130px"
          @update:model-value="emit('update:maxTokens', $event)"
        />
      </div>
    </div>
    <div class="param-item" style="margin-top: 8px">
      <label>System Prompt</label>
      <el-input
        :model-value="systemPrompt"
        type="textarea"
        :rows="2"
        placeholder="系统提示词（将作为第一条 system 消息发送）"
        @update:model-value="emit('update:systemPrompt', $event)"
      />
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  temperature: { type: Number, default: null },
  topP: { type: Number, default: null },
  maxTokens: { type: Number, default: null },
  systemPrompt: { type: String, default: '' },
})

const emit = defineEmits([
  'update:temperature',
  'update:topP',
  'update:maxTokens',
  'update:systemPrompt',
])
</script>

<style scoped>
.param-panel {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.param-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-item label {
  font-size: 12px;
  color: #606266;
}
</style>
