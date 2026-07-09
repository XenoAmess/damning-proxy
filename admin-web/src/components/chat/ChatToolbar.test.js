import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../../test-utils.js'
import ChatToolbar from './ChatToolbar.vue'

function mountToolbar(props = {}) {
  return mountWithElementPlus(ChatToolbar, {
    props: {
      instanceSlug: '',
      model: '',
      instanceOptions: [],
      modelOptions: [],
      selectMode: false,
      canGenerate: true,
      ...props,
    },
  })
}

describe('ChatToolbar', () => {
  it('emits toggle-params when params button clicked', async () => {
    const wrapper = mountToolbar()
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('参数'))
    await btn.trigger('click')
    expect(wrapper.emitted('toggle-params')).toBeTruthy()
  })

  it('emits clear when clear button clicked', async () => {
    const wrapper = mountToolbar()
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('清空当前'))
    await btn.trigger('click')
    expect(wrapper.emitted('clear')).toBeTruthy()
  })

  it('emits generate-image when generate image button clicked', async () => {
    const wrapper = mountToolbar({ canGenerate: true })
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('生成图片'))
    await btn.trigger('click')
    expect(wrapper.emitted('generate-image')).toBeTruthy()
  })

  it('disables generate image button when canGenerate is false', () => {
    const wrapper = mountToolbar({ canGenerate: false })
    const btn = wrapper.findAll('.el-button').find((b) => b.text().includes('生成图片'))
    expect(btn.attributes('disabled')).not.toBeUndefined()
  })

  it('emits update:selectMode when checkbox changes', async () => {
    const wrapper = mountToolbar()
    const checkbox = wrapper.findComponent({ name: 'ElCheckbox' })
    await checkbox.vm.$emit('update:model-value', true)
    expect(wrapper.emitted('update:selectMode')).toBeTruthy()
    expect(wrapper.emitted('update:selectMode')[0]).toEqual([true])
  })

  it('emits update:instanceSlug and instance-change when instance changes', async () => {
    const wrapper = mountToolbar({
      instanceOptions: [{ value: 'inst', label: 'Inst' }],
    })
    const select = wrapper.findComponent({ name: 'ElSelectV2' })
    await select.vm.$emit('update:model-value', 'inst')
    expect(wrapper.emitted('update:instanceSlug')).toBeTruthy()
    expect(wrapper.emitted('update:instanceSlug')[0]).toEqual(['inst'])
    expect(wrapper.emitted('instance-change')).toBeTruthy()
  })

  it('emits update:model when model changes', async () => {
    const wrapper = mountToolbar({
      modelOptions: [{ value: 'gpt', label: 'GPT' }],
    })
    const selects = wrapper.findAllComponents({ name: 'ElSelectV2' })
    await selects[1].vm.$emit('update:model-value', 'gpt')
    expect(wrapper.emitted('update:model')).toBeTruthy()
    expect(wrapper.emitted('update:model')[0]).toEqual(['gpt'])
  })
})
