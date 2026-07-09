import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../test-utils.js'
import ImportPreviewDialog from './ImportPreviewDialog.vue'

const dialogStub = { template: '<div><slot/><slot name="footer"/></div>' }

function mountDialog() {
  return mountWithElementPlus(ImportPreviewDialog, {
    global: {
      stubs: { ElDialog: dialogStub },
    },
  })
}

describe('ImportPreviewDialog', () => {
  it('shows empty state when there are no items', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.vm
    dialog.open({ title: 'Test', items: [] })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('没有可导入的数据')
  })

  it('shows summary and paged items', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.vm
    const items = [
      { name: 'A', slug: 'a' },
      { name: 'B', slug: 'b', _existingId: 1 },
    ]
    dialog.open({ title: 'Import', items })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('共 2 条记录')
    expect(wrapper.text()).toContain('新增 1')
    expect(wrapper.text()).toContain('覆盖 1')
  })

  it('resolves with items when confirmed', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.vm
    const items = [{ name: 'A', slug: 'a' }]
    const promise = dialog.open({ title: 'Import', items })
    await wrapper.vm.$nextTick()

    const confirmBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('确认导入'))
    await confirmBtn.trigger('click')

    await expect(promise).resolves.toEqual(items)
  })

  it('resolves with null when done is called', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.vm
    const promise = dialog.open({ title: 'Import', items: [] })
    dialog.done()
    await expect(promise).resolves.toBeNull()
  })
})
