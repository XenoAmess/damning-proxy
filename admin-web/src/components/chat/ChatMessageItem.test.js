import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../../test-utils.js'
import ChatMessageItem from './ChatMessageItem.vue'

function mountMessage(props = {}) {
  return mountWithElementPlus(ChatMessageItem, {
    props: {
      msg: { role: 'assistant', content: 'hello' },
      index: 0,
      ...props,
    },
  })
}

describe('ChatMessageItem', () => {
  it('renders assistant label', () => {
    const wrapper = mountMessage()
    expect(wrapper.text()).toContain('助手')
  })

  it('renders user label', () => {
    const wrapper = mountMessage({ msg: { role: 'user', content: 'hi' } })
    expect(wrapper.text()).toContain('我')
  })

  it('shows checkbox in select mode', () => {
    const wrapper = mountMessage({ selectMode: true, isSelected: true })
    expect(wrapper.find('.el-checkbox').exists()).toBe(true)
  })

  it('emits toggle-select when checkbox changes', async () => {
    const wrapper = mountMessage({ selectMode: true, isSelected: false })
    await wrapper.findComponent({ name: 'ElCheckbox' }).vm.$emit('update:model-value', true)
    expect(wrapper.emitted('toggle-select')).toBeTruthy()
    expect(wrapper.emitted('toggle-select')[0]).toEqual([0, true])
  })

  it('emits resend for user message', async () => {
    const wrapper = mountMessage({ msg: { role: 'user', content: 'hi' } })
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('重发'))
    await btn.trigger('click')
    expect(wrapper.emitted('resend')).toBeTruthy()
    expect(wrapper.emitted('resend')[0]).toEqual([0])
  })

  it('emits regenerate for assistant message', async () => {
    const wrapper = mountMessage({ msg: { role: 'assistant', content: 'hi' } })
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('重新生成'))
    await btn.trigger('click')
    expect(wrapper.emitted('regenerate')).toBeTruthy()
    expect(wrapper.emitted('regenerate')[0]).toEqual([0])
  })

  it('emits copy when copy button clicked', async () => {
    const msg = { role: 'user', content: 'copy me' }
    const wrapper = mountMessage({ msg })
    const btn = wrapper.findAll('.el-button').find((b) => b.attributes('title') === '复制')
    await btn.trigger('click')
    expect(wrapper.emitted('copy')).toBeTruthy()
    expect(wrapper.emitted('copy')[0]).toEqual([msg])
  })

  it('toggles reasoning block', async () => {
    const wrapper = mountMessage({
      msg: { role: 'assistant', content: '<think>steps</think>answer' },
    })
    expect(wrapper.text()).toContain('推理过程')
    expect(wrapper.text()).not.toContain('steps')
    await wrapper.find('.reasoning-toggle').trigger('click')
    expect(wrapper.text()).toContain('steps')
  })
})
