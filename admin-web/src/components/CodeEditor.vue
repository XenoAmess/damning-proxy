<template>
  <div ref="editorRef" class="code-editor" :class="{ readonly: readOnly }" />
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { EditorView, keymap, lineNumbers } from '@codemirror/view'
import { EditorState, Compartment } from '@codemirror/state'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { oneDark } from '@codemirror/theme-one-dark'
import { javascript } from '@codemirror/lang-javascript'
import { json } from '@codemirror/lang-json'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'JS' },
  readOnly: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  height: { type: Number, default: 260 },
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const editorView = ref(null)
const languageCompartment = new Compartment()
const readOnlyCompartment = new Compartment()

const languageSupport = computed(() => {
  switch (props.language) {
    case 'JS':
    case 'JAVASCRIPT':
      return javascript()
    case 'GROOVY':
      return javascript({ typescript: false })
    case 'JSON':
      return json()
    default:
      return javascript()
  }
})

function createState(value) {
  return EditorState.create({
    doc: value,
    extensions: [
      lineNumbers(),
      history(),
      keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
      oneDark,
      languageCompartment.of(languageSupport.value),
      readOnlyCompartment.of(EditorState.readOnly.of(props.readOnly)),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }),
      EditorView.theme({
        '&': { height: props.height + 'px', width: '100%', fontSize: '14px' },
        '.cm-scroller': { overflow: 'auto' },
        '.cm-gutters': { backgroundColor: '#1e1e1e', borderRight: '1px solid #333' },
      }),
    ],
  })
}

onMounted(() => {
  editorView.value = new EditorView({
    state: createState(props.modelValue),
    parent: editorRef.value,
  })
})

onUnmounted(() => {
  editorView.value?.destroy()
})

watch(() => props.modelValue, (value) => {
  if (!editorView.value) return
  const current = editorView.value.state.doc.toString()
  if (value !== current) {
    editorView.value.dispatch({
      changes: { from: 0, to: editorView.value.state.doc.length, insert: value || '' },
    })
  }
})

watch(() => props.language, () => {
  if (!editorView.value) return
  editorView.value.dispatch({
    effects: languageCompartment.reconfigure(languageSupport.value),
  })
})

watch(() => props.readOnly, (value) => {
  if (!editorView.value) return
  editorView.value.dispatch({
    effects: readOnlyCompartment.reconfigure(EditorState.readOnly.of(value)),
  })
})
</script>

<style scoped>
.code-editor {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.code-editor.readonly {
  opacity: 0.8;
}
</style>
