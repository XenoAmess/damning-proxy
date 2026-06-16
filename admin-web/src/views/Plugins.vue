<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增插件</el-button>
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
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑插件' : '新增插件'" width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="语言" required>
          <el-radio-group v-model="form.language">
            <el-radio-button label="GROOVY" />
            <el-radio-button label="JS" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="执行阶段" required>
          <el-radio-group v-model="form.executionPhase">
            <el-radio-button label="REQUEST" />
            <el-radio-button label="RESPONSE" />
            <el-radio-button label="BOTH" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="脚本" required>
          <el-input v-model="form.script" type="textarea" :rows="10"
            placeholder="// context 对象提供 request/response 访问" />
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
import { listPlugins, createPlugin, updatePlugin, deletePlugin } from '../api/damning.js'

const plugins = ref([])
const loading = ref(false)
const visible = ref(false)
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

function openDialog(row) {
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

async function save() {
  try {
    if (form.value.id) {
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

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
