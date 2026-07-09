import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountWithElementPlus } from '../test-utils.js'
import Profiles from '../views/Profiles.vue'
import {
  listProfiles,
  createProfile,
  updateProfile,
  deleteProfile,
  exportProfiles as exportProfilesApi,
  importProfiles,
} from '../api/damning.js'

vi.mock('../api/damning.js', () => ({
  listProfiles: vi.fn().mockResolvedValue({ data: [] }),
  createProfile: vi.fn().mockResolvedValue({}),
  updateProfile: vi.fn().mockResolvedValue({}),
  deleteProfile: vi.fn().mockResolvedValue({}),
  exportProfiles: vi.fn().mockResolvedValue({ data: [] }),
  importProfiles: vi.fn().mockResolvedValue({ data: { imported: 0, skipped: 0 } }),
}))

describe('Profiles', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listProfiles.mockResolvedValue({ data: [] })
    createProfile.mockResolvedValue({})
    updateProfile.mockResolvedValue({})
    deleteProfile.mockResolvedValue({})
    exportProfilesApi.mockResolvedValue({ data: [] })
    importProfiles.mockResolvedValue({ data: { imported: 0, skipped: 0 } })
  })

  async function openDialog(wrapper) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    const addBtn = wrapper.findAll('.el-button').find((b) => b.text().trim() === '新增配置')
    expect(addBtn).toBeDefined()
    await addBtn.trigger('click')
    await wrapper.vm.$nextTick()
  }

  it('renders profile list', async () => {
    const wrapper = mountWithElementPlus(Profiles, {
      global: { stubs: { CodeEditor: true, ImportPreviewDialog: true } },
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(listProfiles).toHaveBeenCalled()
    expect(wrapper.find('.el-table').exists()).toBe(true)
  })

  it('auto-fills Kimi-code preset when provider is selected', async () => {
    const wrapper = mountWithElementPlus(Profiles, {
      global: { stubs: { CodeEditor: true, ImportPreviewDialog: true } },
    })
    await openDialog(wrapper)

    wrapper.vm.handleProviderChange('kimi')
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.form.baseUrl).toBe('https://api.kimi.com/coding/v1')
    expect(wrapper.vm.form.defaultModel).toBe('kimi-for-coding')
    const headers = JSON.parse(wrapper.vm.form.customHeaders)
    expect(headers['User-Agent']).toBe('KimiCLI/1.41.0')
    expect(headers['X-Msh-Platform']).toBe('kimi_cli')
    expect(headers['X-Msh-Version']).toBe('1.41.0')
    expect(headers['X-Msh-Device-Id']).toMatch(/^[0-9a-f]{32}$/)
  })

  it('updates customHeaders when Kimi Device ID is edited', async () => {
    const wrapper = mountWithElementPlus(Profiles, {
      global: { stubs: { CodeEditor: true, ImportPreviewDialog: true } },
    })
    await openDialog(wrapper)

    wrapper.vm.handleProviderChange('kimi')
    await wrapper.vm.$nextTick()

    wrapper.vm.handleDeviceIdInput('customdeviceid')
    await wrapper.vm.$nextTick()

    const headers = JSON.parse(wrapper.vm.form.customHeaders)
    expect(headers['X-Msh-Device-Id']).toBe('customdeviceid')
  })

  it('detects existing Kimi profile from customHeaders', async () => {
    const wrapper = mountWithElementPlus(Profiles, {
      global: { stubs: { CodeEditor: true, ImportPreviewDialog: true } },
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    wrapper.vm.openDialog({
      id: 1,
      name: 'kimi',
      slug: 'kimi',
      baseUrl: 'https://api.kimi.com/coding/v1',
      customHeaders: JSON.stringify({
        'User-Agent': 'KimiCLI/1.41.0',
        'X-Msh-Platform': 'kimi_cli',
        'X-Msh-Device-Id': 'existingdeviceid',
      }),
      defaultModel: 'kimi-for-coding',
      enabled: true,
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.providerType).toBe('kimi')
    expect(wrapper.vm.kimiDeviceId).toBe('existingdeviceid')
  })

  it('sends saved profile through createProfile', async () => {
    const wrapper = mountWithElementPlus(Profiles, {
      global: { stubs: { CodeEditor: true, ImportPreviewDialog: true } },
    })
    await openDialog(wrapper)

    wrapper.vm.form.name = 'kimi'
    wrapper.vm.form.slug = 'kimi'
    wrapper.vm.handleProviderChange('kimi')
    await wrapper.vm.$nextTick()

    await wrapper
      .findAll('.el-button')
      .find((b) => b.text().trim() === '保存')
      .trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(createProfile).toHaveBeenCalled()
    const payload = createProfile.mock.calls[0][0]
    expect(payload.baseUrl).toBe('https://api.kimi.com/coding/v1')
    expect(JSON.parse(payload.customHeaders)['X-Msh-Platform']).toBe('kimi_cli')
  })
})
