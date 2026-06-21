import axios from 'axios'

const proxy = axios.create({
  baseURL: '/v1/proxy',
  headers: {
    'Content-Type': 'application/json',
  },
})

export function chatCompletion(instanceSlug, body, token) {
  const headers = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return proxy.post(`/${instanceSlug}/chat/completions`, body, {
    headers,
    adapter: 'fetch',
  })
}

export function createAbortController() {
  return new AbortController()
}

export async function* chatCompletionStream(instanceSlug, body, token, signal) {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`/v1/proxy/${instanceSlug}/chat/completions`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text}`)
  }

  if (!response.body) {
    throw new Error('Response body is null')
  }

  const reader = response.body
    .pipeThrough(new TextDecoderStream())
    .getReader()

  if (signal) {
    signal.addEventListener('abort', () => reader.cancel(), { once: true })
  }

  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += value
    let lineEnd
    while ((lineEnd = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, lineEnd).trim()
      buffer = buffer.slice(lineEnd + 1)
      if (!line || !line.startsWith('data:')) continue
      const data = line.slice(5).trimStart()
      if (data === '[DONE]') return
      if (!data) continue
      try {
        yield JSON.parse(data)
      } catch (e) {
        // ignore malformed chunks
      }
    }
  }

  const remaining = buffer.trim()
  if (remaining && remaining.startsWith('data:')) {
    const data = remaining.slice(5).trimStart()
    if (data && data !== '[DONE]') {
      try {
        yield JSON.parse(data)
      } catch (e) {
        // ignore
      }
    }
  }
}

export function listModels(instanceSlug, token) {
  const headers = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return proxy.get(`/${instanceSlug}/models`, { headers })
}
