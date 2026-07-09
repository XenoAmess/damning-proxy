import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { proxy, chatCompletion, listModels, chatCompletionStream } from './chat.js'

beforeEach(() => {
  vi.spyOn(proxy, 'post').mockResolvedValue({ data: {} })
  vi.spyOn(proxy, 'get').mockResolvedValue({ data: {} })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function createStreamResponse(chunks) {
  let index = 0
  const reader = {
    read: vi.fn(async () => {
      if (index < chunks.length) {
        return { done: false, value: chunks[index++] }
      }
      return { done: true }
    }),
    cancel: vi.fn(),
  }
  const response = {
    ok: true,
    body: {
      pipeThrough: vi.fn(() => ({ getReader: () => reader })),
    },
  }
  return { response, reader }
}

describe('chat api', () => {
  it('chatCompletion sends Authorization header when token provided', async () => {
    await chatCompletion('inst', { model: 'gpt' }, 'token')
    expect(proxy.post).toHaveBeenCalledWith(
      '/inst/chat/completions',
      { model: 'gpt' },
      {
        headers: { Authorization: 'Bearer token' },
        adapter: 'fetch',
      }
    )
  })

  it('chatCompletion omits Authorization header when token is empty', async () => {
    await chatCompletion('inst', { model: 'gpt' }, '')
    expect(proxy.post).toHaveBeenCalledWith(
      '/inst/chat/completions',
      { model: 'gpt' },
      {
        headers: {},
        adapter: 'fetch',
      }
    )
  })

  it('listModels sends Authorization header', async () => {
    await listModels('inst', 'token')
    expect(proxy.get).toHaveBeenCalledWith('/inst/models', {
      headers: { Authorization: 'Bearer token' },
    })
  })

  describe('chatCompletionStream', () => {
    it('yields parsed json events', async () => {
      const fetchSpy = vi
        .fn()
        .mockResolvedValue(
          createStreamResponse(['data: {"id":"1"}\n\n', 'data: {"id":"2"}\n\n', 'data: [DONE]\n\n'])
            .response
        )
      vi.stubGlobal('fetch', fetchSpy)

      const results = []
      for await (const item of chatCompletionStream('inst', { model: 'gpt' }, '')) {
        results.push(item)
      }

      expect(results).toEqual([{ id: '1' }, { id: '2' }])
      expect(fetchSpy).toHaveBeenCalledWith(
        '/v1/proxy/inst/chat/completions',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ model: 'gpt' }),
        })
      )
    })

    it('throws on upstream error event', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            createStreamResponse(['event: error\ndata: {"error":{"message":"bad"}}\n\n']).response
          )
      )

      await expect(
        (async () => {
          for await (const _ of chatCompletionStream('inst', {}, '')) {
            // consume
          }
        })()
      ).rejects.toThrow('上游流式错误')
    })

    it('throws when response is not ok', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: false,
          status: 502,
          text: vi.fn().mockResolvedValue('bad gateway'),
        })
      )

      await expect(
        (async () => {
          for await (const _ of chatCompletionStream('inst', {}, '')) {
            // consume
          }
        })()
      ).rejects.toThrow('HTTP 502: bad gateway')
    })

    it('cancels reader when signal aborts', async () => {
      const { response, reader } = createStreamResponse(['data: {"id":"1"}\n\n'])
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

      const controller = new AbortController()
      const generator = chatCompletionStream('inst', {}, '', controller.signal)
      await generator.next()
      controller.abort()

      for await (const _ of generator) {
        // consume remaining
      }

      expect(reader.cancel).toHaveBeenCalled()
    })
  })
})
