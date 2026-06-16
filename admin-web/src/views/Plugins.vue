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
        accept=".json"
        class="upload-inline"
      >
        <el-button>导入插件</el-button>
      </el-upload>
    </div>
    <el-table :data="plugins" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="language" label="语言" width="90" />
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

    <el-dialog v-model="visible" :title="form.id ? (readOnly ? '查看插件' : '编辑插件') : '新增插件'" width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" :disabled="readOnly" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" :disabled="readOnly" />
        </el-form-item>
        <el-form-item label="语言" required>
          <el-radio-group v-model="form.language" :disabled="readOnly">
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
        <el-form-item label="脚本" required>
          <el-input v-model="form.script" type="textarea" :rows="10"
            placeholder="// context 对象提供 request/response 访问" :disabled="readOnly" />
        </el-form-item>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPlugins, createPlugin, updatePlugin, deletePlugin } from '../api/damning.js'

const plugins = ref([])
const loading = ref(false)
const visible = ref(false)
const readOnly = ref(false)
const form = ref({
  name: '',
  description: '',
  language: 'GROOVY',
  script: '',
  executionPhase: 'BOTH',
  enabled: true,
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
  form.value = row ? { ...row } : {
    name: '',
    description: '',
    language: 'GROOVY',
    script: '',
    executionPhase: 'BOTH',
    enabled: true,
  }
  visible.value = true
}

function isSample(row) {
  return row && (row.name === '大明战锤提示词（Groovy）' || row.name === '大明战锤提示词（JS）')
}

function copyPlugin(row) {
  form.value = {
    name: row.name + '（副本）',
    description: row.description || '',
    language: row.language,
    script: row.script,
    executionPhase: row.executionPhase,
    enabled: row.enabled,
  }
  readOnly.value = false
  visible.value = true
}

async function save() {
  try {
    if (form.value.id && !readOnly.value) {
      await updatePlugin(form.value.id, form.value)
    } else {
      await createPlugin(form.value)
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
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function exportPlugins() {
  const data = plugins.value.map(p => ({
    name: p.name,
    description: p.description,
    language: p.language,
    executionPhase: p.executionPhase,
    script: p.script,
    enabled: p.enabled,
  }))
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `plugins-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

async function handleImport(file) {
  const raw = file.raw
  if (!raw) return
  try {
    const text = await raw.text()
    const list = JSON.parse(text)
    if (!Array.isArray(list)) {
      ElMessage.error('文件格式错误：应为插件数组')
      return
    }
    for (const item of list) {
      const payload = {
        name: item.name,
        description: item.description || '',
        language: item.language || 'GROOVY',
        executionPhase: item.executionPhase || 'BOTH',
        script: item.script || '',
        enabled: item.enabled !== false,
      }
      await createPlugin(payload)
    }
    ElMessage.success('导入成功')
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
</style>
