import 'fake-indexeddb/auto'
import { vi, beforeEach } from 'vitest'
import { ElMessage } from 'element-plus'

function createMatchMediaMock() {
  return vi.fn(function (query) {
    return {
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }
  })
}

function createObserverMock() {
  return vi.fn(function () {
    return {
      observe: vi.fn(),
      unobserve: vi.fn(),
      disconnect: vi.fn(),
    }
  })
}

function setupPolyfills() {
  globalThis.matchMedia = createMatchMediaMock()
  globalThis.IntersectionObserver = createObserverMock()
  globalThis.ResizeObserver = createObserverMock()
  globalThis.navigator.clipboard = {
    writeText: vi.fn().mockResolvedValue(undefined),
  }

  if (!document.execCommand) {
    document.execCommand = vi.fn()
  }
}

setupPolyfills()

beforeEach(() => {
  setupPolyfills()
})

// Mock Element Plus message methods to avoid DOM side effects in tests
vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined)
vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined)
vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined)
vi.spyOn(ElMessage, 'info').mockImplementation(() => undefined)
