<template>
  <div class="toolbar">
    <el-button type="danger" @click="emit('clear')"> 清空日志 </el-button>
    <el-button type="warning" @click="emit('prune')"> 批量清理 </el-button>
  </div>

  <div class="log-filters">
    <el-select
      :model-value="filters.instanceId"
      placeholder="实例"
      clearable
      style="width: 160px"
      @update:model-value="emit('update:filters', { ...filters, instanceId: $event })"
    >
      <el-option v-for="inst in instances" :key="inst.id" :label="inst.name" :value="inst.id" />
    </el-select>
    <el-select
      :model-value="filters.status"
      placeholder="状态"
      clearable
      style="width: 120px"
      @update:model-value="emit('update:filters', { ...filters, status: $event })"
    >
      <el-option
        v-for="opt in statusOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    <el-input
      :model-value="filters.path"
      placeholder="路径关键字"
      clearable
      style="width: 180px"
      @update:model-value="emit('update:filters', { ...filters, path: $event })"
    />
    <el-date-picker
      :model-value="filters.startTime"
      type="datetime"
      placeholder="开始时间"
      value-format="YYYY-MM-DDTHH:mm:ss"
      style="width: 180px"
      @update:model-value="emit('update:filters', { ...filters, startTime: $event })"
    />
    <el-date-picker
      :model-value="filters.endTime"
      type="datetime"
      placeholder="结束时间"
      value-format="YYYY-MM-DDTHH:mm:ss"
      style="width: 180px"
      @update:model-value="emit('update:filters', { ...filters, endTime: $event })"
    />
    <el-button type="primary" @click="emit('search')"> 查询 </el-button>
    <el-button @click="emit('reset')"> 重置 </el-button>
  </div>
</template>

<script setup>
const props = defineProps({
  filters: { type: Object, required: true },
  instances: { type: Array, default: () => [] },
  statusOptions: { type: Array, default: () => [] },
})

const emit = defineEmits(['clear', 'prune', 'update:filters', 'search', 'reset'])
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.log-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
</style>
