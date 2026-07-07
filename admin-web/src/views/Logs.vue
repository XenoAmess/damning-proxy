<template>
  <div>
    <LogFilterBar
      :filters="filters"
      :instances="instances"
      :status-options="statusOptions"
      @clear="clear"
      @prune="openPruneDialog"
      @update:filters="filters.value = $event"
      @search="search"
      @reset="resetFilters"
    />
    <LogPruneDialog
      :visible="pruneDialogVisible"
      :keep-count="pruneKeepCount"
      :mode="pruneMode"
      :loading="pruneLoading"
      :total="pagination.total"
      @update:visible="pruneDialogVisible = $event"
      @update:keep-count="pruneKeepCount = $event"
      @update:mode="pruneMode = $event"
      @confirm="prune"
    />
    <LogCardList
      :logs="logs"
      :loading="loading"
      :pagination="pagination"
      :set-card-ref="setCardRef"
      @open="openFriendly"
      @remove="remove"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    />
    <LogDetailDrawer
      :visible="detailVisible"
      :current="current"
      :active-tab="activeTab"
      @update:visible="detailVisible = $event"
      @update:active-tab="activeTab = $event"
      @debug="debugSnapshot"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { listInstances } from '../api/damning.js'
import { listLogs, getLogFriendly, deleteLog, clearLogs, pruneLogs } from '../api/damning.js'
import LogFilterBar from '../components/logs/LogFilterBar.vue'
import LogPruneDialog from '../components/logs/LogPruneDialog.vue'
import LogCardList from '../components/logs/LogCardList.vue'
import LogDetailDrawer from '../components/logs/LogDetailDrawer.vue'

const logs = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const activeTab = ref('summary')
const current = ref(null)
const cardRefs = ref({})
const loadedFriendlyIds = ref(new Set())
const router = useRouter()
let observer = null

const pruneDialogVisible = ref(false)
const pruneLoading = ref(false)
const pruneKeepCount = ref(10000)
const pruneMode = ref('oldest')

const instances = ref([])
const pagination = ref({
  limit: 20,
  offset: 0,
  total: 0,
})
const filters = ref({
  instanceId: null,
  status: '',
  path: '',
  startTime: null,
  endTime: null,
})

const statusOptions = [
  { label: '全部', value: '' },
  { label: '成功', value: 'success' },
  { label: '错误', value: 'error' },
]

function debugSnapshot(snapshot, phase) {
  router.push(`/plugins/${snapshot.pluginId || 0}/debug?phase=${phase}&logId=${current.value.id}`)
}

async function load() {
  loading.value = true
  try {
    const params = {
      limit: pagination.value.limit,
      offset: pagination.value.offset,
      instanceId: filters.value.instanceId,
      status: filters.value.status,
      path: filters.value.path,
      startTime: filters.value.startTime,
      endTime: filters.value.endTime,
    }
    const res = await listLogs(params)
    logs.value = res.data.items
    pagination.value.total = res.data.total
    await nextTick()
    observeCards()
  } finally {
    loading.value = false
  }
}

async function loadInstances() {
  try {
    const res = await listInstances()
    instances.value = res.data
  } catch (e) {
    instances.value = []
  }
}

function search() {
  pagination.value.offset = 0
  load()
}

function resetFilters() {
  filters.value = {
    instanceId: null,
    status: '',
    path: '',
    startTime: null,
    endTime: null,
  }
  pagination.value.offset = 0
  load()
}

function handlePageChange(newPage) {
  pagination.value.offset = (newPage - 1) * pagination.value.limit
  load()
}

function handleSizeChange(newSize) {
  pagination.value.limit = newSize
  pagination.value.offset = 0
  load()
}

function observeCards() {
  if (observer) {
    observer.disconnect()
  }
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const id = Number(entry.target.dataset.id)
        if (id && !isNaN(id)) {
          loadFriendlyIfNeeded(id)
        }
      }
    }
  }, { rootMargin: '100px' })

  for (const el of Object.values(cardRefs.value)) {
    if (el) observer.observe(el)
  }
}

async function loadFriendlyIfNeeded(id) {
  if (!id || isNaN(Number(id))) return
  if (loadedFriendlyIds.value.has(id)) return
  loadedFriendlyIds.value.add(id)
  try {
    const res = await getLogFriendly(id)
    const log = logs.value.find(l => l.id === id)
    if (log) {
      log._friendly = res.data
    }
  } catch (e) {
    console.error('加载友好数据失败', e)
    ElMessage.warning('加载友好数据失败')
  }
}

async function openFriendly(id) {
  if (!id || isNaN(Number(id))) return
  detailVisible.value = true
  current.value = null
  activeTab.value = 'summary'
  try {
    await loadFriendlyIfNeeded(id)
    const log = logs.value.find(l => l.id === id)
    current.value = log?._friendly || null
    if (!isChatLike(current.value)) {
      activeTab.value = 'rawRequest'
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
    detailVisible.value = false
  }
}

function isChatLike(log) {
  return log && log.requestPath && log.requestPath.includes('/chat/completions')
}

function setCardRef(id, el) {
  if (el) {
    cardRefs.value[id] = el
  } else {
    delete cardRefs.value[id]
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该日志？', '提示', { type: 'warning' })
    await deleteLog(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

async function clear() {
  try {
    await ElMessageBox.confirm('确定清空所有日志？', '提示', { type: 'warning' })
    await clearLogs()
    ElMessage.success('清空成功')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('清空失败')
    }
  }
}

function openPruneDialog() {
  pruneKeepCount.value = Math.max(10000, Math.floor(pagination.value.total / 2))
  pruneMode.value = 'oldest'
  pruneDialogVisible.value = true
}

async function prune() {
  pruneLoading.value = true
  try {
    const keep = pruneMode.value === 'all' ? 0 : pruneKeepCount.value
    await pruneLogs(keep, pruneMode.value === 'all')
    ElMessage.success('批量清理完成')
    pruneDialogVisible.value = false
    pagination.value.offset = 0
    await load()
  } catch (e) {
    ElMessage.error('批量清理失败')
  } finally {
    pruneLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), loadInstances()])
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<style scoped>
</style>
