import { describe, it, expect } from 'vitest'
import { formatTimestamp } from './format.js'

describe('formatTimestamp', () => {
  it('formats a date with zero-padded fields', () => {
    const date = new Date(2026, 6, 8, 9, 5, 3) // month is 0-based
    expect(formatTimestamp(date)).toBe('20260708_090503')
  })

  it('uses current time by default', () => {
    const result = formatTimestamp()
    expect(result).toMatch(/^\d{8}_\d{6}$/)
  })
})
