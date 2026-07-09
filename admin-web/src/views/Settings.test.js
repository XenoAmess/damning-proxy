import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountWithElementPlus } from '../test-utils.js'
import Settings from '../views/Settings.vue'
import { getRateLimitSettings, updateRateLimitSettings } from '../api/damning.js'

vi.mock('../api/damning.js', () => ({
  getRateLimitSettings: vi
    .fn()
    .mockResolvedValue({ data: { maxRequestsPerWindow: 60, windowSeconds: 60 } }),
  updateRateLimitSettings: vi.fn().mockResolvedValue({}),
}))

describe('Settings', () => {
  beforeEach(() => {
    getRateLimitSettings.mockResolvedValue({
      data: { maxRequestsPerWindow: 60, windowSeconds: 60 },
    })
    updateRateLimitSettings.mockResolvedValue({})
  })

  it('loads settings on mount', async () => {
    mountWithElementPlus(Settings)
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(getRateLimitSettings).toHaveBeenCalled()
  })

  it('shows loaded settings', async () => {
    const wrapper = mountWithElementPlus(Settings)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs[0].props('modelValue')).toBe(60)
    expect(inputs[1].props('modelValue')).toBe(60)
  })

  it('saves settings when save button clicked', async () => {
    const wrapper = mountWithElementPlus(Settings)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    const saveBtn = wrapper.findAll('.el-button').find((b) => b.text().includes('保存'))
    await saveBtn.trigger('click')

    expect(updateRateLimitSettings).toHaveBeenCalledWith({
      maxRequestsPerWindow: 60,
      windowSeconds: 60,
    })
  })
})
