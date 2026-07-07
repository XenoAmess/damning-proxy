<template>
  <div class="chat-toolbar">
    <div class="toolbar-left">
      <el-select-v2
        :model-value="instanceSlug"
        :options="instanceOptions"
        placeholder="选择实例"
        style="width: 200px"
        @update:model-value="emit('update:instanceSlug', $event); emit('instance-change')"
      />
      <el-select-v2
        :model-value="model"
        :options="modelOptions"
        placeholder="选择模型"
        style="width: 180px; margin-left: 12px"
        allow-create
        filterable
        clearable
        @update:model-value="emit('update:model', $event)"
      />
    </div>
    <div class="toolbar-right">
      <el-button link @click="emit('toggle-params')">
        <el-icon><Setting /></el-icon> 参数
      </el-button>
      <el-button style="margin-left: 12px" @click="emit('clear')">清空当前</el-button>
      <el-button style="margin-left: 12px" type="primary" :disabled="!canGenerate" @click="emit('generate-image')">生成图片</el-button>
      <el-checkbox :model-value="selectMode" style="margin-left: 12px" @update:model-value="emit('update:selectMode', $event)">选择模式</el-checkbox>
    </div>
  </div>
</template>

<script setup>
import { Setting } from '@element-plus/icons-vue'

const props = defineProps({
  instanceSlug: { type: String, default: '' },
  model: { type: String, default: '' },
  instanceOptions: { type: Array, default: () => [] },
  modelOptions: { type: Array, default: () => [] },
  showParams: { type: Boolean, default: false },
  selectMode: { type: Boolean, default: false },
  canGenerate: { type: Boolean, default: false },
})

const emit = defineEmits(['update:instanceSlug', 'update:model', 'instance-change', 'toggle-params', 'clear', 'generate-image', 'update:selectMode'])
</script>

<style scoped>
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
</style>
