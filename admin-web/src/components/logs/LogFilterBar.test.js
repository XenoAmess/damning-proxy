import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import LogFilterBar from './LogFilterBar.vue'

const instances = [{ id: 1, name: 'Instance A' }]
const statusOptions = [
  { value: 'success', label: '成功' },
  { value: 'error', label: '失败' },
]

function mountFilterBar(props = {}) {
  return mount(LogFilterBar, {
    global: { plugins: [ElementPlus] },
    props: {
      filters: { instanceId: null, status: '', path: '', startTime: '', endTime: '' },
      instances,
      statusOptions,
      ...props,
    },
  })
}

describe('LogFilterBar', () => {
  it('renders action buttons', () => {
    const wrapper = mountFilterBar()
    expect(wrapper.text()).toContain('清空日志')
    expect(wrapper.text()).toContain('批量清理')
    expect(wrapper.text()).toContain('查询')
    expect(wrapper.text()).toContain('重置')
  })

  it('emits clear when the clear button is clicked', async () => {
    const wrapper = mountFilterBar()
    await wrapper.find('.el-button--danger').trigger('click')
    expect(wrapper.emitted('clear')).toBeTruthy()
  })

  it('emits prune when the bulk prune button is clicked', async () => {
    const wrapper = mountFilterBar()
    await wrapper.find('.el-button--warning').trigger('click')
    expect(wrapper.emitted('prune')).toBeTruthy()
  })

  it('emits search when the search button is clicked', async () => {
    const wrapper = mountFilterBar()
    const buttons = wrapper.findAll('.el-button')
    const searchBtn = buttons.find((btn) => btn.text() === '查询')
    await searchBtn.trigger('click')
    expect(wrapper.emitted('search')).toBeTruthy()
  })

  it('emits reset when the reset button is clicked', async () => {
    const wrapper = mountFilterBar()
    const buttons = wrapper.findAll('.el-button')
    const resetBtn = buttons.find((btn) => btn.text() === '重置')
    await resetBtn.trigger('click')
    expect(wrapper.emitted('reset')).toBeTruthy()
  })
})
