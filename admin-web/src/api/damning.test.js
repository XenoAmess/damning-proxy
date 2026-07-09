import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { ElMessage } from 'element-plus'
import {
  api,
  listProfiles,
  createProfile,
  listLogs,
  exportLogs,
  clearLogs,
  pruneLogs,
  getRateLimitSettings,
  updateRateLimitSettings,
} from './damning.js'

let mock

beforeEach(() => {
  mock = new MockAdapter(api)
})

afterEach(() => {
  mock.restore()
})

describe('damning api', () => {
  it('fetches profiles', async () => {
    mock.onGet('/profiles').reply(200, [{ id: 1 }])
    await listProfiles()
    expect(mock.history.get[0].url).toBe('/profiles')
  })

  it('creates a profile', async () => {
    mock.onPost('/profiles').reply(201, { id: 1 })
    await createProfile({ name: 'p' })
    expect(JSON.parse(mock.history.post[0].data)).toEqual({ name: 'p' })
  })

  it('cleans empty params for listLogs', async () => {
    mock.onGet('/logs').reply(200, [])
    await listLogs({ instanceId: 1, status: '', path: null, startTime: undefined, endTime: 'x' })
    expect(mock.history.get[0].params).toEqual({ instanceId: 1, endTime: 'x' })
  })

  it('cleans empty params for exportLogs', async () => {
    mock.onGet('/logs/export').reply(200, new Blob())
    await exportLogs({ instanceId: '', status: 'error' })
    expect(mock.history.get[0].params).toEqual({ status: 'error' })
    expect(mock.history.get[0].responseType).toBe('blob')
  })

  it('calls clearLogs', async () => {
    mock.onPost('/logs/clear').reply(200)
    await clearLogs()
    expect(mock.history.post[0].url).toBe('/logs/clear')
  })

  it('calls pruneLogs with default deleteAll false', async () => {
    mock.onPost('/logs/prune').reply(200)
    await pruneLogs(1000)
    expect(JSON.parse(mock.history.post[0].data)).toEqual({ keepCount: 1000, deleteAll: false })
  })

  it('fetches rate limit settings', async () => {
    mock.onGet('/settings/rate-limit').reply(200, { maxRequestsPerWindow: 60 })
    await getRateLimitSettings()
    expect(mock.history.get[0].url).toBe('/settings/rate-limit')
  })

  it('updates rate limit settings', async () => {
    mock.onPut('/settings/rate-limit').reply(200)
    await updateRateLimitSettings({ maxRequestsPerWindow: 100 })
    expect(JSON.parse(mock.history.put[0].data)).toEqual({ maxRequestsPerWindow: 100 })
  })

  describe('response interceptor', () => {
    async function expectMessage(url, status, assertFn) {
      mock.onGet(url).reply(status)
      try {
        await listProfiles()
      } catch (e) {
        // expected to be rejected
      }
      assertFn()
    }

    it('shows error for 500', async () => {
      await expectMessage('/profiles', 500, () => expect(ElMessage.error).toHaveBeenCalled())
    })

    it('shows error for 404', async () => {
      await expectMessage('/profiles', 404, () =>
        expect(ElMessage.error).toHaveBeenCalledWith('资源不存在')
      )
    })

    it('shows warning for 409', async () => {
      mock.onGet('/profiles').reply(409, 'conflict')
      try {
        await listProfiles()
      } catch (e) {
        // expected
      }
      expect(ElMessage.warning).toHaveBeenCalledWith('conflict')
    })

    it('shows error for timeout', async () => {
      mock.onGet('/profiles').timeout()
      try {
        await listProfiles()
      } catch (e) {
        // expected
      }
      expect(ElMessage.error).toHaveBeenCalledWith('请求超时')
    })

    it('shows error for network failure', async () => {
      mock.onGet('/profiles').networkError()
      try {
        await listProfiles()
      } catch (e) {
        // expected
      }
      expect(ElMessage.error).toHaveBeenCalledWith('网络错误，请检查连接')
    })
  })
})
