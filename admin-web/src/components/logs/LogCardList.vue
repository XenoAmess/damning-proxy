<template>
  <el-empty v-if="!loading && logs.length === 0" description="暂无流量日志" />

  <div class="log-card-list">
    <LogCard
      v-for="log in logs"
      :key="log.id"
      :log="log"
      :set-card-ref="setCardRef"
      @open="(...args) => emit('open', ...args)"
      @remove="(...args) => emit('remove', ...args)"
    />
  </div>

  <div class="log-pagination">
    <el-pagination
      :current-page="currentPage"
      :page-size="pagination.limit"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next"
      @size-change="emit('size-change', $event)"
      @current-change="emit('page-change', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import LogCard from './LogCard.vue'

const props = defineProps({
  logs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  pagination: { type: Object, required: true },
  setCardRef: { type: Function, required: true },
})

const emit = defineEmits(['open', 'remove', 'page-change', 'size-change'])

const currentPage = computed(() => Math.floor(props.pagination.offset / props.pagination.limit) + 1)
</script>

<style scoped>
.log-card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
