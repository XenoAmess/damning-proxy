<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()"> 新增配置 </el-button>
      <el-button @click="exportProfiles"> 导出配置 </el-button>
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
    <ImportPreviewDialog ref="previewDialog" />
    <el-table v-loading="loading" :data="profiles" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="标识" />
      <el-table-column prop="baseUrl" label="上游地址" show-overflow-tooltip />
      <el-table-column prop="defaultModel" label="默认模型" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)"> 编辑 </el-button>
          <el-button size="small" type="danger" @click="remove(row.id)"> 删除 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑配置' : '新增配置'" width="800px">
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
        <el-form-item label="自定义 Headers" :error="errors.customHeaders">
          <div :class="['editor-wrapper', { 'has-error': errors.customHeaders }]">
            <CodeEditor
              v-model="form.customHeaders"
              language="JSON"
              :height="160"
              placeholder='{"X-Api-Key":"secret"}'
            />
          </div>
        </el-form-item>
        <el-form-item label="自定义 Body" :error="errors.customBody">
          <div :class="['editor-wrapper', { 'has-error': errors.customBody }]">
            <CodeEditor
              v-model="form.customBody"
              language="JSON"
              :height="260"
              placeholder='{"temperature": 0.7, "max_tokens": 2048}'
            />
          </div>
        </el-form-item>
        <el-form-item label="默认模型">
          <el-input v-model="form.defaultModel" />
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" />
        </el-form-item>
        <el-form-item label="熔断失败阈值">
          <el-input-number v-model="form.circuitBreakerFailureThreshold" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="熔断恢复时间(s)">
          <el-input-number v-model="form.circuitBreakerOpenTimeoutSeconds" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false"> 取消 </el-button>
        <el-button type="primary" :loading="saving" @click="save"> 保存 </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProfiles,
  createProfile,
  updateProfile,
  deleteProfile,
  exportProfiles as exportProfilesApi,
  importProfiles,
} from '../api/damning.js'
import { formatTimestamp } from '../utils/format.js'
import { exportJson, importJson } from '../utils/export.js'
import CodeEditor from '../components/CodeEditor.vue'
import ImportPreviewDialog from '../components/ImportPreviewDialog.vue'

const profiles = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const selectedIds = ref([])
const previewDialog = ref(null)
const errors = ref({ customHeaders: '', customBody: '' })
const form = ref({
  name: '',
  slug: '',
  baseUrl: '',
  bearerToken: '',
  customHeaders: '',
  customBody: '',
  defaultModel: '',
  timeoutMs: 600000,
  circuitBreakerFailureThreshold: 3,
  circuitBreakerOpenTimeoutSeconds: 30,
  enabled: true,
})

watch(() => form.value.customHeaders, validateCustomHeaders)
watch(() => form.value.customBody, validateCustomBody)

function validateCustomHeaders() {
  parseJsonField('customHeaders')
}

function validateCustomBody() {
  parseJsonField('customBody')
}

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
  selectedIds.value = rows.map((r) => r.id)
}

function openDialog(row) {
  errors.value = { customHeaders: '', customBody: '' }
  form.value = row
    ? { ...row }
    : {
        name: '',
        slug: '',
        baseUrl: '',
        bearerToken: '',
        customHeaders: '',
        customBody: '',
        defaultModel: '',
        timeoutMs: 600000,
        circuitBreakerFailureThreshold: 3,
        circuitBreakerOpenTimeoutSeconds: 30,
        enabled: true,
      }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    if (!validateJsonFields()) return
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
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确定删除该配置？', '提示', { type: 'warning' })
    await deleteProfile(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

function parseJsonField(field) {
  const raw = form.value[field]
  if (raw && raw.trim() !== '') {
    try {
      JSON.parse(raw)
      errors.value[field] = ''
      return true
    } catch (e) {
      errors.value[field] = 'JSON 格式错误: ' + e.message
      return false
    }
  } else {
    errors.value[field] = ''
    return true
  }
}

function validateJsonFields() {
  let valid = true
  if (!parseJsonField('customHeaders')) valid = false
  if (!parseJsonField('customBody')) valid = false
  return valid
}

async function exportProfiles() {
  try {
    const res = await exportProfilesApi(selectedIds.value)
    exportJson(res.data, `damning_proxy_profiles_${formatTimestamp()}.json`)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(e.response?.data || '导出失败')
  }
}

async function handleImport(file) {
  const raw = file.raw
  if (!raw) return
  try {
    const list = await importJson(raw)
    if (!Array.isArray(list)) {
      ElMessage.error('文件格式错误：应为配置数组')
      return
    }
    const items = list.map((item) => ({
      ...item,
      _existingId: profiles.value.find((p) => p.slug === item.slug)?.id || null,
    }))
    const confirmed = await previewDialog.value.open({ title: '导入配置预览', items })
    if (!confirmed) return
    const res = await importProfiles(
      confirmed.map((i) => {
        delete i._existingId
        return i
      })
    )
    ElMessage.success(`导入成功：新增 ${res.data.imported} 个，跳过 ${res.data.skipped} 个`)
    previewDialog.value.done()
    await load()
  } catch (e) {
    ElMessage.error('导入失败: ' + (e.message || e))
    if (previewDialog.value) previewDialog.value.done()
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
.editor-wrapper {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.2s;
}
.editor-wrapper.has-error {
  border-color: var(--el-color-danger);
}
</style>
