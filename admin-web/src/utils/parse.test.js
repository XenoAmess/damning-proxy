import { describe, it, expect } from 'vitest'
import { parseThink, sanitizeHtml, formatBytes } from './parse.js'

describe('parseThink', () => {
  it('returns text and empty reasoning when no think tags', () => {
    const result = parseThink('hello world')
    expect(result.text).toBe('hello world')
    expect(result.reasoning).toBe('')
  })

  it('extracts reasoning and strips think tags', () => {
    const result = parseThink('<think>some reasoning</think>visible text')
    expect(result.text).toBe('visible text')
    expect(result.reasoning).toBe('some reasoning')
  })

  it('trims whitespace around reasoning and text', () => {
    const result = parseThink('  <think>  reasoning  </think>  text  ')
    expect(result.text).toBe('text')
    expect(result.reasoning).toBe('reasoning')
  })

  it('removes only the first think block', () => {
    const result = parseThink('<think>first</think>a<think>second</think>b')
    expect(result.text).toBe('a<think>second</think>b')
    expect(result.reasoning).toBe('first')
  })

  it('handles non-string input', () => {
    expect(parseThink(null)).toEqual({ text: '', reasoning: '' })
    expect(parseThink(undefined)).toEqual({ text: '', reasoning: '' })
    expect(parseThink(123)).toEqual({ text: 123, reasoning: '' })
  })
})

describe('sanitizeHtml', () => {
  it('keeps allowed tags', () => {
    expect(sanitizeHtml('<p>hello</p>')).toBe('<p>hello</p>')
  })

  it('removes script tags', () => {
    expect(sanitizeHtml('<script>alert(1)</script><p>safe</p>')).toBe('<p>safe</p>')
  })

  it('removes event attributes', () => {
    expect(sanitizeHtml('<p onclick="alert(1)">safe</p>')).toBe('<p>safe</p>')
  })

  it('allows href and target on anchors', () => {
    const html = '<a href="https://example.com" target="_blank">link</a>'
    expect(sanitizeHtml(html)).toBe(html)
  })

  it('returns non-string input unchanged', () => {
    expect(sanitizeHtml(null)).toBe(null)
    expect(sanitizeHtml(42)).toBe(42)
  })
})

describe('formatBytes', () => {
  it('returns dash for null/undefined', () => {
    expect(formatBytes(null)).toBe('-')
    expect(formatBytes(undefined)).toBe('-')
  })

  it('formats bytes', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
  })

  it('formats kilobytes', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(1536)).toBe('1.5 KB')
  })

  it('formats megabytes', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.00 MB')
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.50 MB')
  })

  it('formats gigabytes', () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.00 GB')
  })
})
