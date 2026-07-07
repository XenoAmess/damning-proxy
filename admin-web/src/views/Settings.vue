<template>
  <div>
    <h2 style="margin-top: 0; margin-bottom: 20px">全局限流设置</h2>
    <el-form v-loading="loading" :model="form" label-width="180px" style="max-width: 560px">
      <el-form-item label="窗口内最大请求数" required>
        <el-input-number v-model="form.maxRequestsPerWindow" :min="1" :max="1000000" :step="10" />
      </el-form-item>
      <el-form-item label="窗口时长（秒）" required>
        <el-input-number v-model="form.windowSeconds" :min="1" :max="86400" :step="1" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save"> 保存 </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRateLimitSettings, updateRateLimitSettings } from '../api/damning.js'

const form = ref({
  maxRequestsPerWindow: 60,
  windowSeconds: 60,
})
const loading = ref(false)
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getRateLimitSettings()
    form.value = {
      maxRequestsPerWindow: res.data.maxRequestsPerWindow,
      windowSeconds: res.data.windowSeconds,
    }
  } catch (e) {
    ElMessage.error(e.response?.data || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await updateRateLimitSettings({ ...form.value })
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e.response?.data || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
