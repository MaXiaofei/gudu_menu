import { describe, it, expect } from 'vitest'
import { isChunkLoadError, shouldAutoReload, RELOAD_WINDOW_MS } from './chunkReload'

function memStorage(init: Record<string, string> = {}) {
  const m = { ...init }
  return {
    getItem: (k: string) => m[k] ?? null,
    setItem: (k: string, v: string) => {
      m[k] = v
    },
  }
}

describe('动态导入失败识别', () => {
  it('识别各浏览器的报错文案', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: https://x/assets/a.js'))).toBe(true)
    expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('error loading dynamically imported module'))).toBe(true)
    expect(isChunkLoadError('Unable to preload CSS for /assets/a.css')).toBe(true)
  })

  it('其他错误与空值不识别', () => {
    expect(isChunkLoadError(new Error('Network Error'))).toBe(false)
    expect(isChunkLoadError(null)).toBe(false)
    expect(isChunkLoadError(undefined)).toBe(false)
    expect(isChunkLoadError({ message: 123 })).toBe(false)
  })
})

describe('防无限刷新', () => {
  it('首次允许，窗口内再次拒绝，窗口过后又允许', () => {
    const st = memStorage()
    expect(shouldAutoReload(100_000, st)).toBe(true)
    expect(shouldAutoReload(100_000 + RELOAD_WINDOW_MS - 1, st)).toBe(false)
    expect(shouldAutoReload(100_000 + RELOAD_WINDOW_MS, st)).toBe(true)
  })

  it('sessionStorage 不可用或抛错时不刷新', () => {
    expect(shouldAutoReload(1, null)).toBe(false)
    const bad = {
      getItem: () => {
        throw new Error('denied')
      },
      setItem: () => {},
    }
    expect(shouldAutoReload(1, bad)).toBe(false)
  })
})
