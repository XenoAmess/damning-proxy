import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
})

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

export function listPlugins() {
  return api.get('/plugins')
}

export function createPlugin(data) {
  return api.post('/plugins', data)
}

export function updatePlugin(id, data) {
  return api.put(`/plugins/${id}`, data)
}

export function deletePlugin(id) {
  return api.delete(`/plugins/${id}`)
}

export function exportPlugins(ids) {
  return api.post('/plugins/export', { ids })
}

export function importPlugins(data) {
  return api.post('/plugins/import', data)
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

export function listLogs(params) {
  return api.get('/logs', { params })
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
