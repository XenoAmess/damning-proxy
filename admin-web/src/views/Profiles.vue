<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增配置</el-button>
      <el-button @click="exportProfiles">导出配置</el-button>
      <el-upload
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleImport"
        accept=".json"
        class="upload-inline"
      >
        <el-button>导入配置</el-button>
      </el-upload>
    </div>
    <el-table :data="profiles" v-loading="loading" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="标识" />
      <el-table-column prop="baseUrl" label="上游地址" show-overflow-tooltip />
      <el-table-column prop="defaultModel" label="默认模型" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑配置' : '新增配置'" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="标识" required>
          <el-input v-model="form.slug" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="上游地址" required>
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="Bearer Token">
          <el-input v-model="form.bearerToken" type="password" show-password />
        </el-form-item>
        <el-form-item label="自定义 Headers">
          <el-input v-model="form.customHeaders" type="textarea" :rows="3"
            placeholder='{"X-Api-Key":"secret"}' />
        </el-form-item>
        <el-form-item label="默认模型">
          <el-input v-model="form.defaultModel" />
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProfiles, createProfile, updateProfile, deleteProfile, exportProfiles as exportProfilesApi, importProfiles } from '../api/damning.js'
import { formatTimestamp } from '../utils/format.js'

const profiles = ref([])
const loading = ref(false)
const visible = ref(false)
const selectedIds = ref([])
const form = ref({
  name: '',
  slug: '',
  baseUrl: '',
  bearerToken: '',
  customHeaders: '',
  defaultModel: '',
  timeoutMs: 30000,
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    const res = await listProfiles()
    profiles.value = res.data
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function openDialog(row) {
  form.value = row ? { ...row } : {
    name: '',
    slug: '',
    baseUrl: '',
    bearerToken: '',
    customHeaders: '',
    defaultModel: '',
    timeoutMs: 30000,
    enabled: true,
  }
  visible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await updateProfile(form.value.id, form.value)
    } else {
      await createProfile(form.value)
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
    await ElMessageBox.confirm('确定删除该配置？', '提示', { type: 'warning' })
    await deleteProfile(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function exportProfiles() {
  try {
    const res = await exportProfilesApi(selectedIds.value)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `damning_proxy_profiles_${formatTimestamp()}.json`
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
      ElMessage.error('文件格式错误：应为配置数组')
      return
    }
    const res = await importProfiles(list)
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
