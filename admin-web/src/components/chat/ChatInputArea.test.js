import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../../test-utils.js'
import ChatInputArea from './ChatInputArea.vue'

function mountInput(props = {}) {
  return mountWithElementPlus(ChatInputArea, {
    props: {
      inputText: '',
      pendingFiles: [],
      stream: true,
      loading: false,
      canSend: false,
      ...props,
    },
  })
}

describe('ChatInputArea', () => {
  it('emits update:inputText when typing', async () => {
    const wrapper = mountInput()
    const textarea = wrapper.findComponent({ name: 'ElInput' })
    await textarea.vm.$emit('update:model-value', 'hello')
    expect(wrapper.emitted('update:inputText')).toBeTruthy()
    expect(wrapper.emitted('update:inputText')[0]).toEqual(['hello'])
  })

  it('emits update:stream when switch toggled', async () => {
    const wrapper = mountInput()
    const switchComp = wrapper.findComponent({ name: 'ElSwitch' })
    await switchComp.vm.$emit('update:model-value', false)
    expect(wrapper.emitted('update:stream')).toBeTruthy()
    expect(wrapper.emitted('update:stream')[0]).toEqual([false])
  })

  it('shows send button when not loading and emits send', async () => {
    const wrapper = mountInput({ canSend: true })
    const sendBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('发送'))
    expect(sendBtn.attributes('disabled')).toBeUndefined()
    await sendBtn.trigger('click')
    expect(wrapper.emitted('send')).toBeTruthy()
  })

  it('disables send button when canSend is false', () => {
    const wrapper = mountInput({ canSend: false })
    const sendBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('发送'))
    expect(sendBtn.attributes('disabled')).not.toBeUndefined()
  })

  it('shows stop button when loading and emits stop', async () => {
    const wrapper = mountInput({ loading: true })
    const stopBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('停止'))
    expect(stopBtn.exists()).toBe(true)
    await stopBtn.trigger('click')
    expect(wrapper.emitted('stop')).toBeTruthy()
  })

  it('renders pending image previews', () => {
    const wrapper = mountInput({
      pendingFiles: [{ name: 'img.png', type: 'image/png', dataUrl: 'data:...' }],
    })
    expect(wrapper.find('.preview-img').exists()).toBe(true)
  })

  it('renders pending file tags', () => {
    const wrapper = mountInput({
      pendingFiles: [{ name: 'doc.pdf', type: 'application/pdf' }],
    })
    expect(wrapper.text()).toContain('doc.pdf')
  })

  it('emits remove-file when file tag close clicked', async () => {
    const wrapper = mountInput({
      pendingFiles: [{ name: 'doc.pdf', type: 'application/pdf' }],
    })
    const tag = wrapper.findComponent({ name: 'ElTag' })
    await tag.vm.$emit('close')
    expect(wrapper.emitted('remove-file')).toBeTruthy()
    expect(wrapper.emitted('remove-file')[0]).toEqual([0])
  })
})
