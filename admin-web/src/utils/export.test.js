import { describe, it, expect, vi } from 'vitest'
import { exportJson, importJson } from './export.js'

describe('exportJson', () => {
  it('creates a blob download and revokes the url', () => {
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob://fake')
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    const clickSpy = vi.fn()
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') {
        return {
          href: '',
          download: '',
          click: clickSpy,
        }
      }
      return document.createElement(tag)
    })

    exportJson({ foo: 'bar' }, 'data.json')

    expect(createObjectURLSpy).toHaveBeenCalled()
    expect(createElementSpy).toHaveBeenCalledWith('a')
    expect(clickSpy).toHaveBeenCalled()
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob://fake')

    createObjectURLSpy.mockRestore()
    revokeObjectURLSpy.mockRestore()
    appendChildSpy.mockRestore()
    removeChildSpy.mockRestore()
    createElementSpy.mockRestore()
  })
})

describe('importJson', () => {
  it('reads a file and returns parsed json', async () => {
    const file = new File(['{"foo":"bar"}'], 'data.json', { type: 'application/json' })
    const result = await importJson(file)
    expect(result).toEqual({ foo: 'bar' })
  })
})
