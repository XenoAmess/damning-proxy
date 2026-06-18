<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增插件组</el-button>
      <el-button @click="exportPluginGroups">导出插件组</el-button>
      <el-upload
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleImport"
        accept=".json"
        class="upload-inline"
      >
        <el-button>导入插件组</el-button>
      </el-upload>
    </div>
    <el-table :data="groups" v-loading="loading" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="标识" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="插件数" width="90">
        <template #default="{ row }">
          {{ row.items ? row.items.length : 0 }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑插件组' : '新增插件组'" width="800px" top="5vh" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="标识" required>
          <el-input v-model="form.slug" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="插件选择">
          <div class="group-editor">
            <div class="available">
              <div class="section-title">可选插件</div>
              <el-select-v2
                v-model="selectedPluginId"
                :options="availablePluginOptions"
                placeholder="选择插件加入"
                style="width: 100%"
                clearable
              />
              <el-button style="margin-top: 8px" @click="addItem">添加</el-button>
            </div>
            <div class="selected">
              <div class="section-title">已选插件（按顺序执行）</div>
              <el-table :data="form.items" border size="small" class="selected-table">
                <el-table-column label="插件" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ pluginName(row.pluginId) }}
                  </template>
                </el-table-column>
                <el-table-column label="顺序" width="80">
                  <template #default="{ row }">
                    <el-input-number v-model="row.orderIndex" :min="0" :controls="false" style="width: 60px" />
                  </template>
                </el-table-column>
                <el-table-column label="优先级" width="80">
                  <template #default="{ row }">
                    <el-input-number v-model="row.priority" :min="0" :controls="false" style="width: 60px" />
                  </template>
                </el-table-column>
                <el-table-column label="启用" width="70">
                  <template #default="{ row }">
                    <el-switch v-model="row.enabled" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="130" fixed="right">
                  <template #default="{ $index }">
                    <el-button size="small" @click="moveUp($index)" :disabled="$index === 0">↑</el-button>
                    <el-button size="small" @click="moveDown($index)" :disabled="$index === form.items.length - 1">↓</el-button>
                    <el-button size="small" type="danger" @click="removeItem($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPluginGroups,
  createPluginGroup,
  updatePluginGroup,
  deletePluginGroup,
  exportPluginGroups as exportPluginGroupsApi,
  importPluginGroups,
  listPlugins,
} from '../api/damning.js'

const groups = ref([])
const plugins = ref([])
const loading = ref(false)
const visible = ref(false)
const selectedIds = ref([])
const selectedPluginId = ref(null)
const form = ref({
  name: '',
  slug: '',
  description: '',
  enabled: true,
  items: [],
})

const availablePluginOptions = computed(() => {
  const selectedIds = new Set(form.value.items.map(i => i.pluginId))
  return plugins.value
    .filter(p => !selectedIds.has(p.id))
    .map(p => ({ value: p.id, label: p.name }))
})

function pluginName(id) {
  const p = plugins.value.find(x => x.id === id)
  return p ? p.name : id
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function load() {
  loading.value = true
  try {
    const [gRes, pRes] = await Promise.all([listPluginGroups(), listPlugins()])
    groups.value = gRes.data
    plugins.value = pRes.data
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    form.value = {
      id: row.id,
      name: row.name,
      slug: row.slug,
      description: row.description,
      enabled: row.enabled,
      items: (row.items || []).map(i => ({
        pluginId: i.plugin ? i.plugin.id : i.pluginId,
        orderIndex: i.orderIndex,
        priority: i.priority,
        enabled: i.enabled,
      })),
    }
  } else {
    form.value = {
      name: '',
      slug: '',
      description: '',
      enabled: true,
      items: [],
    }
  }
  selectedPluginId.value = null
  visible.value = true
}

function addItem() {
  if (!selectedPluginId.value) return
  form.value.items.push({
    pluginId: selectedPluginId.value,
    orderIndex: form.value.items.length,
    priority: 0,
    enabled: true,
  })
  selectedPluginId.value = null
}

function removeItem(index) {
  form.value.items.splice(index, 1)
}

function moveUp(index) {
  if (index === 0) return
  const items = form.value.items
  const temp = items[index]
  items[index] = items[index - 1]
  items[index - 1] = temp
  recalcOrder()
}

function moveDown(index) {
  if (index === form.value.items.length - 1) return
  const items = form.value.items
  const temp = items[index]
  items[index] = items[index + 1]
  items[index + 1] = temp
  recalcOrder()
}

function recalcOrder() {
  form.value.items.forEach((item, idx) => {
    item.orderIndex = idx
  })
}

async function save() {
  try {
    recalcOrder()
    const payload = {
      name: form.value.name,
      slug: form.value.slug,
      description: form.value.description,
      enabled: form.value.enabled,
      items: form.value.items.map(i => ({
        pluginId: i.pluginId,
        orderIndex: i.orderIndex,
        priority: i.priority,
        enabled: i.enabled,
      })),
    }
    if (form.value.id) {
      await updatePluginGroup(form.value.id, payload)
    } else {
      await createPluginGroup(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data || '保存失败')
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该插件组？', '提示', { type: 'warning' })
    await deletePluginGroup(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function exportPluginGroups() {
  try {
    const res = await exportPluginGroupsApi(selectedIds.value)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `plugin-groups-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(e.response?.data || '导出失败')
  }
}

async function handleImport(file) {
  const raw = file.raw
  if (!raw) return
  try {
    const text = await raw.text()
    const list = JSON.parse(text)
    if (!Array.isArray(list)) {
      ElMessage.error('文件格式错误：应为插件组数组')
      return
    }
    const res = await importPluginGroups(list)
    ElMessage.success(`导入成功：新增 ${res.data.imported} 个，跳过 ${res.data.skipped} 个`)
    await load()
  } catch (e) {
    ElMessage.error('导入失败: ' + (e.message || e))
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.upload-inline {
  display: inline-block;
  margin-left: 12px;
}
.upload-inline :deep(.el-upload) {
  display: inline-block;
}
.group-editor {
  display: flex;
  gap: 16px;
}
.available {
  width: 240px;
  flex-shrink: 0;
}
.selected {
  flex: 1;
}
.section-title {
  font-weight: bold;
  margin-bottom: 8px;
}
.selected-table {
  width: 100%;
}
</style>
