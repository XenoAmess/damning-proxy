import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ChatParamPanel from './ChatParamPanel.vue'

describe('ChatParamPanel', () => {
  it('renders parameter labels', () => {
    const wrapper = mount(ChatParamPanel, {
      global: {
        plugins: [ElementPlus],
      },
      props: {
        temperature: 0.7,
        topP: 0.9,
        maxTokens: 2048,
        systemPrompt: 'You are a helpful assistant.',
      },
    })

    const labels = wrapper.findAll('label').map((el) => el.text())
    expect(labels).toContain('Temperature')
    expect(labels).toContain('Top P')
    expect(labels).toContain('Max Tokens')
    expect(labels).toContain('System Prompt')
  })

  it('emits update:temperature when temperature changes', async () => {
    const wrapper = mount(ChatParamPanel, {
      global: {
        plugins: [ElementPlus],
      },
      props: {
        temperature: 1.0,
      },
    })

    await wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:model-value', 1.2)
    expect(wrapper.emitted('update:temperature')).toBeTruthy()
    expect(wrapper.emitted('update:temperature')[0]).toEqual([1.2])
  })
})
