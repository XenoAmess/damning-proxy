<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增插件</el-button>
      <el-button @click="exportPlugins">导出插件</el-button>
      <el-upload
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleImport"
        accept=".json,.zip"
        class="upload-inline"
      >
        <el-button>导入插件</el-button>
      </el-upload>
    </div>
    <el-table :data="plugins" v-loading="loading" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="language" label="语言" width="90" />
      <el-table-column prop="mode" label="模式" width="110">
        <template #default="{ row }">
          <el-tag>{{ row.mode === 'ZIP_PACKAGE' ? 'ZIP包' : '单脚本' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="executionPhase" label="执行阶段" width="110" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row, false)">查看</el-button>
          <el-button size="small" type="primary" @click="copyPlugin(row)">复制</el-button>
          <el-button v-if="!isSample(row)" size="small" @click="openDialog(row, true)">编辑</el-button>
          <el-button v-if="!isSample(row)" size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="dialogTitle" width="760px" top="5vh" :close-on-click-modal="false">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" :disabled="readOnly" />
        </el-form-item>
        <el-form-item label="标识" required>
          <el-input v-model="form.slug" :disabled="readOnly || !!form.id" placeholder="唯一标识，如 my-plugin" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" :disabled="readOnly" />
        </el-form-item>
        <el-form-item label="模式" required>
          <el-radio-group v-model="form.mode" :disabled="readOnly || !!form.id">
            <el-radio-button label="SINGLE_SCRIPT">单脚本</el-radio-button>
            <el-radio-button label="ZIP_PACKAGE">ZIP包</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="语言" required>
          <el-radio-group v-model="form.language" :disabled="readOnly" @change="applyTemplate">
            <el-radio-button label="GROOVY" />
            <el-radio-button label="JS" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="执行阶段" required>
          <el-radio-group v-model="form.executionPhase" :disabled="readOnly">
            <el-radio-button label="REQUEST" />
            <el-radio-button label="RESPONSE" />
            <el-radio-button label="BOTH" />
          </el-radio-group>
        </el-form-item>

        <template v-if="form.mode === 'SINGLE_SCRIPT'">
          <el-form-item label="脚本" required>
            <CodeEditor v-model="form.script" :language="form.language" :read-only="readOnly"
              placeholder="// context 对象提供 request/response 访问" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="插件包" required>
            <div v-if="!readOnly" class="package-actions">
              <el-upload
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handlePackageSelect"
                accept=".zip"
              >
                <el-button type="primary">{{ packageFile ? '重新选择' : '选择 ZIP' }}</el-button>
              </el-upload>
              <el-button @click="loadTemplate">使用模板</el-button>
            </div>
            <div v-if="packageFile" class="package-info">
              已选择: {{ packageFile.name }} ({{ formatSize(packageFile.size) }})
            </div>
            <div v-else-if="form.id && form.packagePath" class="package-info">
              当前包: {{ form.packagePath }}
            </div>
            <div v-else class="package-info text-muted">
              未选择 ZIP 包
            </div>
          </el-form-item>
          <el-form-item label="包内文件" v-if="packageEntries.length > 0">
            <el-table :data="packageEntries" size="small" height="200" border>
              <el-table-column prop="name" label="路径" show-overflow-tooltip />
              <el-table-column prop="size" label="大小" width="100" />
            </el-table>
          </el-form-item>
        </template>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :disabled="readOnly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="!readOnly" type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPlugins,
  createPlugin,
  updatePlugin,
  deletePlugin,
  exportPlugins as exportPluginsApi,
  importPlugins,
} from '../api/damning.js'
import axios from 'axios'
import CodeEditor from '../components/CodeEditor.vue'
import { formatTimestamp } from '../utils/format.js'

const plugins = ref([])
const loading = ref(false)
const visible = ref(false)
const readOnly = ref(false)
const selectedIds = ref([])
const packageFile = ref(null)
const packageEntries = ref([])

const defaultForm = {
  name: '',
  slug: '',
  description: '',
  language: 'GROOVY',
  mode: 'SINGLE_SCRIPT',
  script: '',
  executionPhase: 'BOTH',
  enabled: true,
}

const form = ref({ ...defaultForm })

const dialogTitle = computed(() => {
  if (form.value.id) {
    return readOnly.value ? '查看插件' : '编辑插件'
  }
  return '新增插件'
})

async function load() {
  loading.value = true
  try {
    const res = await listPlugins()
    plugins.value = res.data
  } finally {
    loading.value = false
  }
}

function openDialog(row, editable = true) {
  readOnly.value = row ? !editable : false
  packageFile.value = null
  packageEntries.value = []
  if (row) {
    form.value = {
      id: row.id,
      name: row.name,
      slug: row.slug,
      description: row.description || '',
      language: row.language,
      mode: row.mode || 'SINGLE_SCRIPT',
      script: row.script || '',
      executionPhase: row.executionPhase,
      enabled: row.enabled,
      packagePath: row.packagePath,
    }
    if (row.mode === 'ZIP_PACKAGE') {
      loadPackageEntries(row.id)
    }
  } else {
    form.value = { ...defaultForm }
    applyTemplate()
  }
  visible.value = true
}

async function loadPackageEntries(pluginId) {
  try {
    const res = await axios.get(`/api/plugins/${pluginId}/entries`)
    packageEntries.value = res.data.map(e => ({ name: e, size: '-' }))
  } catch (e) {
    packageEntries.value = []
  }
}

function isSample(row) {
  return row && row.sample === true
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function applyTemplate() {
  if (form.value.id) return
  if (form.value.mode === 'ZIP_PACKAGE') {
    form.value.script = ''
    return
  }
  if (form.value.language === 'GROOVY') {
    form.value.script = `def body = context.getRequestBody()
if (body == null) return
def messages = body.get("messages")
if (!(messages instanceof List)) return
context.log("Groovy plugin executed, messages: " + messages.size())
`
  } else {
    form.value.script = `const body = context.getRequestBody();
if (!body || !Array.isArray(body.messages)) return;
context.log("JS plugin executed, messages: " + body.messages.length);
`
  }
}

function copyPlugin(row) {
  form.value = {
    name: row.name + '（副本）',
    slug: row.slug + '-copy',
    description: row.description || '',
    language: row.language,
    mode: row.mode || 'SINGLE_SCRIPT',
    script: row.script || '',
    executionPhase: row.executionPhase,
    enabled: row.enabled,
  }
  packageFile.value = null
  packageEntries.value = []
  readOnly.value = false
  visible.value = true
}

function handlePackageSelect(file) {
  packageFile.value = file.raw
  readPackageEntries(file.raw)
}

async function readPackageEntries(file) {
  packageEntries.value = []
  if (!file) return
  try {
    const JSZip = (await import('jszip')).default
    const zip = await JSZip.loadAsync(file)
    packageEntries.value = []
    zip.forEach((relativePath, zipEntry) => {
      if (!zipEntry.dir) {
        packageEntries.value.push({ name: relativePath, size: formatSize(zipEntry._data.uncompressedSize || 0) })
      }
    })
  } catch (e) {
    ElMessage.warning('无法预览 ZIP 内容，请确保文件有效')
  }
}

async function loadTemplate() {
  try {
    const res = await axios.get(`/api/plugins/template?language=${form.value.language}&mode=ZIP_PACKAGE`, {
      responseType: 'blob'
    })
    const blob = res.data
    const file = new File([blob], `plugin-template-${form.value.language.toLowerCase()}-zip_package.zip`, { type: 'application/zip' })
    packageFile.value = file
    await readPackageEntries(file)
    ElMessage.success('模板已加载')
  } catch (e) {
    ElMessage.error('加载模板失败')
  }
}

function formatSize(bytes) {
  if (bytes === 0 || bytes === '-') return '-'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function buildFormData() {
  const fd = new FormData()
  fd.append('name', form.value.name)
  fd.append('slug', form.value.slug)
  fd.append('description', form.value.description || '')
  fd.append('language', form.value.language)
  fd.append('executionPhase', form.value.executionPhase)
  fd.append('mode', form.value.mode)
  fd.append('enabled', form.value.enabled)
  if (form.value.mode === 'SINGLE_SCRIPT') {
    fd.append('script', form.value.script)
  } else if (packageFile.value) {
    fd.append('packageFile', packageFile.value)
  }
  return fd
}

async function save() {
  try {
    const fd = buildFormData()
    if (form.value.id) {
      await updatePlugin(form.value.id, fd)
    } else {
      await createPlugin(fd)
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
    await ElMessageBox.confirm('确定删除该插件？', '提示', { type: 'warning' })
    await deletePlugin(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

async function exportPlugins() {
  try {
    const res = await exportPluginsApi(selectedIds.value)
    const blob = new Blob([res.data], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `damning_proxy_plugins_${formatTimestamp()}.zip`
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
    let res
    if (raw.name.toLowerCase().endsWith('.zip')) {
      res = await importPlugins(raw)
    } else {
      const text = await raw.text()
      const list = JSON.parse(text)
      if (!Array.isArray(list)) {
        ElMessage.error('文件格式错误：应为插件数组')
        return
      }
      res = await importPlugins(list)
    }
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
.package-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.package-info {
  color: #606266;
  font-size: 14px;
}
.text-muted {
  color: #909399;
}
</style>
