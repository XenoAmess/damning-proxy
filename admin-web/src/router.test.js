import { describe, it, expect } from 'vitest'
import router from './router.js'

describe('router', () => {
  it('redirects / to /instances', async () => {
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/instances')
  })

  it('resolves unknown paths to NotFound', () => {
    const route = router.resolve('/unknown')
    expect(route.name).toBe('NotFound')
  })

  it('defines all expected routes', () => {
    const paths = router.getRoutes().map((r) => r.path)
    expect(paths).toContain('/instances')
    expect(paths).toContain('/dashboard')
    expect(paths).toContain('/plugins')
    expect(paths).toContain('/chat')
    expect(paths).toContain('/logs')
    expect(paths).toContain('/settings')
  })
})
