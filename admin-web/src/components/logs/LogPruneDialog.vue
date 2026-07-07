<template>
  <el-dialog
    :model-value="visible"
    title="批量清理日志"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="prune-form">
      <p class="prune-hint">仅保留最近若干条日志，超过部分按请求时间从早到晚删除。</p>
      <el-form label-position="top">
        <el-form-item label="保留条数">
          <el-input-number
            :model-value="keepCount"
            :min="0"
            :step="1000"
            style="width: 160px"
            @update:model-value="emit('update:keepCount', $event)"
          />
        </el-form-item>
        <el-form-item label="删除方式">
          <el-radio-group :model-value="mode" @update:model-value="emit('update:mode', $event)">
            <el-radio-button label="oldest"> 只删最早的记录 </el-radio-button>
            <el-radio-button label="all"> 全部清空（忽略保留数） </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <p v-if="mode === 'oldest'" class="prune-estimate">
        将删除 {{ Math.max(0, total - keepCount) }} 条日志。
      </p>
      <p v-else class="prune-estimate">将删除全部 {{ total }} 条日志。</p>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :loading="loading" @click="emit('confirm')">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  keepCount: { type: Number, default: 10000 },
  mode: { type: String, default: 'oldest' },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
})

const emit = defineEmits(['update:visible', 'update:keepCount', 'update:mode', 'confirm'])
</script>

<style scoped>
.prune-form {
  padding: 8px 0;
}

.prune-hint {
  color: #606266;
  font-size: 14px;
  margin-bottom: 16px;
}

.prune-estimate {
  color: #f56c6c;
  font-size: 14px;
  margin-top: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
