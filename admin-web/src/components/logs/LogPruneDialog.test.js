import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../../test-utils.js'
import LogPruneDialog from './LogPruneDialog.vue'

const dialogStub = { template: '<div><slot/><slot name="footer"/></div>' }

function mountDialog(props = {}) {
  return mountWithElementPlus(LogPruneDialog, {
    props: {
      visible: true,
      keepCount: 1000,
      mode: 'oldest',
      total: 5000,
      ...props,
    },
    global: {
      stubs: { ElDialog: dialogStub },
    },
  })
}

describe('LogPruneDialog', () => {
  it('shows delete estimate in oldest mode', () => {
    const wrapper = mountDialog()
    expect(wrapper.text()).toContain('将删除 4000 条日志')
  })

  it('shows delete all estimate in all mode', () => {
    const wrapper = mountDialog({ mode: 'all' })
    expect(wrapper.text()).toContain('将删除全部 5000 条日志')
  })

  it('emits update:visible when cancel clicked', async () => {
    const wrapper = mountDialog()
    const cancelBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('取消'))
    await cancelBtn.trigger('click')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')[0]).toEqual([false])
  })

  it('emits confirm when confirm clicked', async () => {
    const wrapper = mountDialog()
    const confirmBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('确定'))
    await confirmBtn.trigger('click')
    expect(wrapper.emitted('confirm')).toBeTruthy()
  })

  it('emits update:keepCount when input changes', async () => {
    const wrapper = mountDialog()
    await wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:model-value', 2000)
    expect(wrapper.emitted('update:keepCount')).toBeTruthy()
    expect(wrapper.emitted('update:keepCount')[0]).toEqual([2000])
  })
})
