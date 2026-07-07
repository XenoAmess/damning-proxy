import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status >= 500) {
        ElMessage.error(`服务器错误 (${status})`)
      } else if (status === 404) {
        ElMessage.error('资源不存在')
      } else if (status === 409) {
        ElMessage.warning(typeof data === 'string' ? data : '冲突')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error('网络错误，请检查连接')
    }
    return Promise.reject(error)
  }
)

export function listProfiles() {
  return api.get('/profiles')
}

export function createProfile(data) {
  return api.post('/profiles', data)
}

export function updateProfile(id, data) {
  return api.put(`/profiles/${id}`, data)
}

export function deleteProfile(id) {
  return api.delete(`/profiles/${id}`)
}

export function exportProfiles(ids) {
  return api.post('/profiles/export', { ids })
}

export function importProfiles(data) {
  return api.post('/profiles/import', data)
}

export function listPlugins() {
  return api.get('/plugins')
}

export function createPlugin(data) {
  return api.post('/plugins', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function updatePlugin(id, data) {
  return api.put(`/plugins/${id}`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deletePlugin(id) {
  return api.delete(`/plugins/${id}`)
}

export function getPlugin(id) {
  return api.get(`/plugins/${id}`)
}

export function dryRunPlugin(id, data) {
  return api.post(`/plugins/${id}/dry-run`, data)
}

export function exportPlugins(ids) {
  return api.post(
    '/plugins/export',
    { ids },
    {
      responseType: 'blob',
      headers: { Accept: 'application/zip' },
    }
  )
}

export function importPlugins(data) {
  const isZip = data instanceof Blob && data.type === 'application/zip'
  return api.post('/plugins/import', data, {
    headers: { 'Content-Type': isZip ? 'application/zip' : 'application/json' },
  })
}

export function listPluginGroups() {
  return api.get('/plugin-groups')
}

export function createPluginGroup(data) {
  return api.post('/plugin-groups', data)
}

export function updatePluginGroup(id, data) {
  return api.put(`/plugin-groups/${id}`, data)
}

export function deletePluginGroup(id) {
  return api.delete(`/plugin-groups/${id}`)
}

export function exportPluginGroups(ids) {
  return api.post('/plugin-groups/export', { ids })
}

export function importPluginGroups(data) {
  return api.post('/plugin-groups/import', data)
}

export function listInstances() {
  return api.get('/instances')
}

export function createInstance(data) {
  return api.post('/instances', data)
}

export function updateInstance(id, data) {
  return api.put(`/instances/${id}`, data)
}

export function deleteInstance(id) {
  return api.delete(`/instances/${id}`)
}

export function exportInstances(ids) {
  return api.post('/instances/export', { ids })
}

export function importInstances(data) {
  return api.post('/instances/import', data)
}

export function listLogs(params) {
  const clean = {}
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== '') {
      clean[key] = value
    }
  }
  return api.get('/logs', { params: clean })
}

export function getLog(id) {
  return api.get(`/logs/${id}`)
}

export function getLogFriendly(id) {
  return api.get(`/logs/${id}/friendly`)
}

export function deleteLog(id) {
  return api.delete(`/logs/${id}`)
}

export function clearLogs() {
  return api.post('/logs/clear')
}

export function pruneLogs(keepCount, deleteAll = false) {
  return api.post('/logs/prune', { keepCount, deleteAll })
}

export function exportLogs(params) {
  const clean = {}
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== '') {
      clean[key] = value
    }
  }
  return api.get('/logs/export', {
    params: clean,
    responseType: 'blob',
  })
}

export function getMetricsSummary(params) {
  return api.get('/metrics/summary', { params })
}

export function getMetricsTimeSeries(params) {
  return api.get('/metrics/time-series', { params })
}

export function getMetricsTopInstances(params) {
  return api.get('/metrics/top-instances', { params })
}

export function getMetricsStatusDistribution(params) {
  return api.get('/metrics/status-distribution', { params })
}

export function getRateLimitSettings() {
  return api.get('/settings/rate-limit')
}

export function updateRateLimitSettings(data) {
  return api.put('/settings/rate-limit', data)
}
