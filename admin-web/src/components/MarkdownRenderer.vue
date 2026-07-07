<template>
  <div ref="root" v-html="html" />
</template>

<script setup>
import { computed, ref, onMounted, onUpdated } from 'vue'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import { sanitizeHtml } from '../utils/parse.js'
import { copyToClipboard } from '../utils/clipboard.js'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
})

const root = ref(null)

const html = computed(() => {
  return sanitizeHtml(marked.parse(props.content || '', { breaks: true, gfm: true }))
})

function addCopyButtons() {
  if (!root.value) return
  root.value.querySelectorAll('pre').forEach((pre) => {
    if (pre.querySelector('.copy-code-btn')) return
    const btn = document.createElement('button')
    btn.className = 'copy-code-btn'
    btn.textContent = '复制'
    btn.type = 'button'
    btn.addEventListener('click', async (event) => {
      event.stopPropagation()
      const code = pre.querySelector('code')?.innerText || pre.innerText || ''
      try {
        await copyToClipboard(code)
        ElMessage.success('代码已复制')
      } catch (e) {
        ElMessage.error('复制失败')
      }
    })
    pre.appendChild(btn)
  })
}

onMounted(addCopyButtons)
onUpdated(addCopyButtons)
</script>

<style scoped>
div :deep(pre) {
  position: relative;
}

div :deep(.copy-code-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

div :deep(pre:hover .copy-code-btn) {
  opacity: 1;
}

div :deep(.copy-code-btn:hover) {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}
</style>
