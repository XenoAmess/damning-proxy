<template>
  <el-dialog v-model="visible" :title="title" width="720px" :close-on-click-modal="false">
    <div v-if="items.length === 0" class="empty-preview">
      <el-empty description="没有可导入的数据" />
    </div>
    <div v-else class="preview-body">
      <p class="preview-summary">
        共 {{ items.length }} 条记录，新增 <strong>{{ willCreate }}</strong> 条，覆盖
        <strong>{{ willUpdate }}</strong> 条。
      </p>
      <el-table :data="pagedItems" height="360" size="small">
        <el-table-column type="index" width="50" />
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column prop="slug" label="标识" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row._existingId" size="small" type="warning"> 覆盖 </el-tag>
            <el-tag v-else size="small" type="success"> 新增 </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="preview-pagination">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="items.length"
          layout="prev, pager, next"
          small
        />
      </div>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="confirm">确认导入</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const visible = ref(false)
const title = ref('导入预览')
const items = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const resolveFn = ref(null)

const willCreate = computed(() => items.value.filter((i) => !i._existingId).length)
const willUpdate = computed(() => items.value.filter((i) => i._existingId).length)
const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})

function open(options) {
  title.value = options.title || '导入预览'
  items.value = options.items || []
  page.value = 1
  loading.value = false
  visible.value = true
  return new Promise((resolve) => {
    resolveFn.value = resolve
  })
}

function confirm() {
  loading.value = true
  if (resolveFn.value) {
    resolveFn.value(items.value)
  }
}

function done() {
  loading.value = false
  visible.value = false
  if (resolveFn.value) {
    resolveFn.value(null)
  }
}

defineExpose({ open, done })
</script>

<style scoped>
.preview-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.preview-summary {
  margin: 0;
  color: #606266;
}
.preview-pagination {
  display: flex;
  justify-content: flex-end;
}
.empty-preview {
  padding: 20px 0;
}
</style>
