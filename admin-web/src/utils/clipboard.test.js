import { describe, it, expect, vi } from 'vitest'
import { copyToClipboard } from './clipboard.js'

describe('copyToClipboard', () => {
  it('uses navigator.clipboard.writeText when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    globalThis.navigator.clipboard = { writeText }
    await copyToClipboard('hello')
    expect(writeText).toHaveBeenCalledWith('hello')
  })

  it('falls back to execCommand when clipboard api fails', async () => {
    globalThis.navigator.clipboard = {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    }
    const execCommandSpy = vi.spyOn(document, 'execCommand').mockReturnValue(true)
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    await copyToClipboard('fallback')

    expect(execCommandSpy).toHaveBeenCalledWith('copy')

    execCommandSpy.mockRestore()
    appendChildSpy.mockRestore()
    removeChildSpy.mockRestore()
  })

  it('throws when both clipboard and execCommand fail', async () => {
    globalThis.navigator.clipboard = {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    }
    const execCommandSpy = vi.spyOn(document, 'execCommand').mockImplementation(() => {
      throw new Error('execCommand unavailable')
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    await expect(copyToClipboard('fail')).rejects.toThrow(
      'Clipboard API and execCommand both unavailable'
    )

    execCommandSpy.mockRestore()
  })
})
