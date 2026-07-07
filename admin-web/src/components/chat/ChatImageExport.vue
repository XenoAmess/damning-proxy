<template>
  <div v-show="false" class="image-export-container" ref="imageExportRef">
    <div class="image-export-header">对话记录</div>
    <div
      v-for="(msg, index) in selectedMessages"
      :key="index"
      class="image-export-message"
      :class="msg.role"
    >
      <div class="image-export-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
      <div class="image-export-content">
        <div v-if="Array.isArray(msg.content)">
          <template v-for="(part, pidx) in msg.content" :key="pidx">
            <div v-if="part.type === 'text'" v-html="renderMarkdown(parseThink(part.text).text)"></div>
            <div v-else-if="part.type === 'image_url'" class="image-export-image">
              [图片]
            </div>
            <div v-else-if="part.type === 'file'" class="image-export-file">
              📎 {{ part.file.name }}
            </div>
          </template>
        </div>
        <div v-else v-html="renderMarkdown(parseThink(msg.content).text)"></div>
        <div v-if="msg.reasoning || parseThink(msg.content).reasoning" class="image-export-reasoning">
          <div class="image-export-reasoning-title">推理过程</div>
          <pre>{{ msg.reasoning || parseThink(msg.content).reasoning }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { marked } from 'marked'
import { parseThink, sanitizeHtml } from '../../utils/parse.js'

const props = defineProps({
  selectedMessages: { type: Array, default: () => [] }
})

const imageExportRef = ref(null)
defineExpose({ imageExportRef })

function renderMarkdown(text) {
  if (typeof text !== 'string') return ''
  return sanitizeHtml(marked.parse(text, { breaks: true, gfm: true }))
}
</script>

<style scoped>
</style>
