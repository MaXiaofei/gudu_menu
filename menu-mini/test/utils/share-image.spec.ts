import { describe, it, expect, vi } from 'vitest'
import { shareLayout, drawShareImage, fitText, SHARE_W, SHARE_MAX_ROWS, type Ctx2D } from '@/utils/shareImage'
import { THEMES } from '@/utils/theme'
import { isAuthDenied, isCancelled, saveToAlbum } from '@/utils/album'

function mockCtx() {
  const texts: string[] = []
  const ctx: Ctx2D = {
    fillStyle: '', strokeStyle: '', lineWidth: 1, font: '', textAlign: 'left', textBaseline: 'alphabetic',
    fillRect: vi.fn(),
    fillText: (t: string) => { texts.push(t) },
    measureText: (t: string) => ({ width: [...t].length * 30 }),
    beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), arcTo: vi.fn(), closePath: vi.fn(), fill: vi.fn(), stroke: vi.fn(),
  }
  return { ctx, texts }
}

describe('utils/shareImage 采购清单分享图', () => {
  it('shareLayout：行数决定高度，超出上限折叠', () => {
    const a = shareLayout(0)
    const b = shareLayout(3)
    expect(a.width).toBe(SHARE_W)
    expect(b.rowYs).toHaveLength(3)
    expect(b.height).toBeGreaterThan(a.height)
    expect(b.rowYs[1] - b.rowYs[0]).toBe(76)
    const c = shareLayout(SHARE_MAX_ROWS + 5)
    expect(c.rowYs).toHaveLength(SHARE_MAX_ROWS)
    expect(c.overflow).toBe(5)
  })

  it('fitText：超宽截断加省略号', () => {
    const { ctx } = mockCtx()
    expect(fitText(ctx, '番茄', 100)).toBe('番茄')
    expect(fitText(ctx, '一二三四五六', 100)).toBe('一二…')
  })

  it('drawShareImage：画出标题、每一项名称与数量、页脚计数', () => {
    const { ctx, texts } = mockCtx()
    drawShareImage(ctx, {
      title: '周末采购',
      subtitle: '10月16日 · 待买 2 项',
      rows: [{ name: '鸡蛋', qty: '10 个' }, { name: '番茄', qty: '' }],
    }, THEMES.cream)
    expect(texts).toContain('周末采购')
    expect(texts).toContain('鸡蛋')
    expect(texts).toContain('10 个')
    expect(texts).toContain('番茄')
    expect(texts.some((t) => t.includes('共 2 项'))).toBe(true)
  })

  it('空清单画空态文案', () => {
    const { ctx, texts } = mockCtx()
    drawShareImage(ctx, { title: 't', subtitle: 's', rows: [] }, THEMES.matcha)
    expect(texts).toContain('清单里的都入库了')
  })
})

describe('utils/album 保存相册', () => {
  it('识别授权拒绝 / 取消', () => {
    expect(isAuthDenied('saveImageToPhotosAlbum:fail auth deny')).toBe(true)
    expect(isAuthDenied('saveImageToPhotosAlbum:fail authorize no response')).toBe(true)
    expect(isAuthDenied('saveImageToPhotosAlbum:fail cancel')).toBe(false)
    expect(isCancelled('saveImageToPhotosAlbum:fail cancel')).toBe(true)
  })

  it('授权被拒 → 弹窗引导去设置', async () => {
    const uni = (globalThis as any).uni
    uni.saveImageToPhotosAlbum = (o: any) => o.fail({ errMsg: 'saveImageToPhotosAlbum:fail auth deny' })
    uni.openSetting = vi.fn()
    uni.showModal = vi.fn((o: any) => o.success({ confirm: true }))
    expect(await saveToAlbum('/tmp/a.png')).toBe(false)
    expect(uni.showModal).toHaveBeenCalledWith(expect.objectContaining({ confirmText: '去设置' }))
    expect(uni.openSetting).toHaveBeenCalled()
  })

  it('成功 → toast 已保存', async () => {
    const uni = (globalThis as any).uni
    uni.saveImageToPhotosAlbum = (o: any) => o.success({})
    uni.showToast = vi.fn()
    expect(await saveToAlbum('/tmp/a.png')).toBe(true)
    expect(uni.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已保存到相册' }))
  })
})
