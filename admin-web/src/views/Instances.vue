<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增实例</el-button>
      <el-button @click="exportInstances">导出实例</el-button>
      <el-upload
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleImport"
        accept=".json"
        class="upload-inline"
      >
        <el-button>导入实例</el-button>
      </el-upload>
    </div>
    <el-table :data="instances" v-loading="loading" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="标识" />
      <el-table-column label="上游配置" show-overflow-tooltip>
        <template #default="{ row }">
          {{ profileName(row.profileId) }}
        </template>
      </el-table-column>
      <el-table-column label="插件组" show-overflow-tooltip>
        <template #default="{ row }">
          {{ groupName(row.pluginGroupId) }}
        </template>
      </el-table-column>
      <el-table-column prop="defaultModel" label="默认模型" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="OpenAI URL">
        <template #default="{ row }">
          <el-button
            size="small"
            link
            type="primary"
            @click="copyOpenAiUrl(row.slug)"
          >
            {{ openAiUrl(row.slug) }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑实例' : '新增实例'" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="标识" required>
          <el-input v-model="form.slug" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="上游配置" required>
          <el-select-v2
            v-model="form.profileId"
            :options="profileOptions"
            placeholder="选择上游配置"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="插件组" required>
          <el-select-v2
            v-model="form.pluginGroupId"
            :options="groupOptions"
            placeholder="选择插件组"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="默认模型">
          <el-input v-model="form.defaultModel" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
  listInstances,
  createInstance,
  updateInstance,
  deleteInstance,
  exportInstances as exportInstancesApi,
  importInstances,
  listProfiles,
  listPluginGroups,
} from '../api/damning.js'

const instances = ref([])
const profiles = ref([])
const groups = ref([])
const loading = ref(false)
const visible = ref(false)
const selectedIds = ref([])
const form = ref({
  name: '',
  slug: '',
  profileId: null,
  pluginGroupId: null,
  defaultModel: '',
  enabled: true,
})

const profileOptions = computed(() => profiles.value.map(p => ({ value: p.id, label: `${p.name} (${p.slug})` })))
const groupOptions = computed(() => groups.value.map(g => ({ value: g.id, label: `${g.name} (${g.slug})` })))

function profileName(id) {
  const p = profiles.value.find(x => x.id === id)
  return p ? `${p.name} (${p.slug})` : id
}

function groupName(id) {
  const g = groups.value.find(x => x.id === id)
  return g ? `${g.name} (${g.slug})` : id
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function openAiUrl(slug) {
  const origin = window.location.origin
  return `${origin}/v1/proxy/${slug}`
}

async function copyOpenAiUrl(slug) {
  const url = openAiUrl(slug)
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

async function load() {
  loading.value = true
  try {
    const [iRes, pRes, gRes] = await Promise.all([
      listInstances(),
      listProfiles(),
      listPluginGroups(),
    ])
    instances.value = iRes.data
    profiles.value = pRes.data
    groups.value = gRes.data
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  form.value = row ? { ...row } : {
    name: '',
    slug: '',
    profileId: null,
    pluginGroupId: null,
    defaultModel: '',
    enabled: true,
  }
  visible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await updateInstance(form.value.id, form.value)
    } else {
      await createInstance(form.value)
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
    await ElMessageBox.confirm('确定删除该实例？', '提示', { type: 'warning' })
    await deleteInstance(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function exportInstances() {
  try {
    const res = await exportInstancesApi(selectedIds.value)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `instances-${new Date().toISOString().slice(0, 10)}.json`
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
      ElMessage.error('文件格式错误：应为实例数组')
      return
    }
    const res = await importInstances(list)
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
</style>
