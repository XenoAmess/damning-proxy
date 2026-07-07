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
  let currentEvent = ''
  let currentData = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += value
    let lineEnd
    while ((lineEnd = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, lineEnd)
      buffer = buffer.slice(lineEnd + 1)
      if (line.trim() === '') {
        if (currentData) {
          const data = currentData.trimEnd()
          if (data !== '[DONE]') {
            try {
              const parsed = JSON.parse(data)
              if (currentEvent === 'error') {
                const message = parsed.error?.message || JSON.stringify(parsed.error || parsed)
                throw new Error(`上游流式错误: ${message}`)
              }
              yield parsed
            } catch (e) {
              if (e.message.startsWith('上游流式错误')) {
                throw e
              }
              // ignore malformed chunks
            }
          }
          currentData = ''
          currentEvent = ''
        }
        continue
      }
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        currentData += line.slice(5).trimStart() + '\n'
      }
    }
  }

  const remaining = buffer.trim()
  if (remaining) {
    if (remaining.startsWith('event:')) {
      currentEvent = remaining.slice(6).trim()
    } else if (remaining.startsWith('data:')) {
      currentData += remaining.slice(5).trimStart() + '\n'
    }
    if (currentData) {
      const data = currentData.trimEnd()
      if (data !== '[DONE]') {
        try {
          const parsed = JSON.parse(data)
          if (currentEvent === 'error') {
            const message = parsed.error?.message || JSON.stringify(parsed.error || parsed)
            throw new Error(`上游流式错误: ${message}`)
          }
          yield parsed
        } catch (e) {
          if (e.message.startsWith('上游流式错误')) {
            throw e
          }
        }
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
