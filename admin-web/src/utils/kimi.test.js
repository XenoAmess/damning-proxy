import { describe, it, expect } from 'vitest'
import {
  generateKimiDeviceId,
  buildKimiHeaders,
  asciiHeaderValue,
  KIMI_CLI_VERSION,
  USER_AGENT,
} from './kimi.js'

describe('kimi utils', () => {
  it('generateKimiDeviceId returns 32 lowercase hex characters', () => {
    const id = generateKimiDeviceId()
    expect(id).toMatch(/^[0-9a-f]{32}$/)
  })

  it('generateKimiDeviceId returns distinct values', () => {
    const a = generateKimiDeviceId()
    const b = generateKimiDeviceId()
    expect(a).not.toBe(b)
  })

  it('asciiHeaderValue strips non-ascii characters', () => {
    expect(asciiHeaderValue('hello 世界')).toBe('hello')
  })

  it('asciiHeaderValue falls back to unknown when empty after sanitization', () => {
    expect(asciiHeaderValue('  ')).toBe('unknown')
    expect(asciiHeaderValue('\u4e16\u754c')).toBe('unknown')
  })

  it('asciiHeaderValue returns original ascii string', () => {
    expect(asciiHeaderValue('Linux 6.1 x86_64')).toBe('Linux 6.1 x86_64')
  })

  it('buildKimiHeaders includes all required special headers', () => {
    const headers = buildKimiHeaders('abc123')
    expect(headers['User-Agent']).toBe(USER_AGENT)
    expect(headers['X-Msh-Platform']).toBe('kimi_cli')
    expect(headers['X-Msh-Version']).toBe(KIMI_CLI_VERSION)
    expect(headers['X-Msh-Device-Id']).toBe('abc123')
    expect(headers).toHaveProperty('X-Msh-Device-Name')
    expect(headers).toHaveProperty('X-Msh-Device-Model')
    expect(headers).toHaveProperty('X-Msh-Os-Version')
  })

  it('buildKimiHeaders uses env overrides', () => {
    const headers = buildKimiHeaders('id', {
      locationHostname: 'my-host',
      platform: 'Linux x86_64',
      userAgent: 'Mozilla/5.0',
    })
    expect(headers['X-Msh-Device-Name']).toBe('my-host')
    expect(headers['X-Msh-Device-Model']).toBe('Linux x86_64')
    expect(headers['X-Msh-Os-Version']).toBe('Mozilla/5.0')
  })
})
