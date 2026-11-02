/**
 * 部署新版后，旧页面按需加载的 chunk 已被删除，动态导入会失败。
 * 识别这类错误并自动刷新一次拿新版；用 sessionStorage 时间戳防止无限刷新。
 */

const RELOAD_KEY = 'gudu_chunk_reload_at'
/** 这段时间内只自动刷新一次（毫秒） */
export const RELOAD_WINDOW_MS = 10_000

const CHUNK_ERROR_PATTERNS = [
  'failed to fetch dynamically imported module',
  'importing a module script failed',
  'error loading dynamically imported module',
  'unable to preload css',
]

/** 是否为动态导入 / 预加载失败类错误 */
export function isChunkLoadError(err: unknown): boolean {
  const msg = typeof err === 'string' ? err : (err as { message?: unknown } | null)?.message
  if (typeof msg !== 'string') return false
  const lower = msg.toLowerCase()
  return CHUNK_ERROR_PATTERNS.some((p) => lower.includes(p))
}

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>

function defaultStorage(): StorageLike | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

/**
 * 判断此刻是否允许自动刷新，允许则同时记下时间戳。
 * 窗口内已刷新过、或 sessionStorage 不可用（无法防循环）都返回 false。
 */
export function shouldAutoReload(now: number = Date.now(), storage: StorageLike | null = defaultStorage()): boolean {
  if (!storage) return false
  try {
    const last = Number(storage.getItem(RELOAD_KEY) || 0)
    if (last && now - last >= 0 && now - last < RELOAD_WINDOW_MS) return false
    storage.setItem(RELOAD_KEY, String(now))
    return true
  } catch {
    return false
  }
}

/** 命中动态导入错误且允许刷新时，整页跳转到目标地址，返回是否已触发刷新 */
export function reloadOnChunkError(err: unknown, targetUrl?: string): boolean {
  if (!isChunkLoadError(err) || !shouldAutoReload()) return false
  window.location.assign(targetUrl || window.location.href)
  return true
}
