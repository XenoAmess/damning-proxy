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
    responseType: 'text',
    adapter: 'fetch',
  })
}

export async function* chatCompletionStream(instanceSlug, body, token) {
  const headers = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`/v1/proxy/${instanceSlug}/chat/completions`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop()
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed || !trimmed.startsWith('data: ')) continue
      const data = trimmed.slice(6)
      if (data === '[DONE]') return
      try {
        yield JSON.parse(data)
      } catch (e) {
        // ignore malformed chunks
      }
    }
  }

  const remaining = buffer.trim()
  if (remaining && remaining.startsWith('data: ')) {
    const data = remaining.slice(6)
    if (data !== '[DONE]') {
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
