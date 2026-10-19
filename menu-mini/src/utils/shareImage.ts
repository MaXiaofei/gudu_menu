/**
 * 采购清单分享图：纯 Canvas 2D 绘制（小程序 canvas type="2d" 与 H5 HTMLCanvasElement 共用同一套 API）。
 * 布局计算与绘制分离：shareLayout 是纯函数（单测覆盖），drawShareImage 只依赖最小 2D 上下文接口。
 */
import type { ThemePalette } from './theme'

/** 绘制用到的最小 2D 上下文（小程序 / H5 / 单测替身均满足）。 */
export interface Ctx2D {
  fillStyle: any
  strokeStyle: any
  lineWidth: number
  font: string
  textAlign: any
  textBaseline: any
  fillRect(x: number, y: number, w: number, h: number): void
  fillText(text: string, x: number, y: number): void
  measureText(text: string): { width: number }
  beginPath(): void
  moveTo(x: number, y: number): void
  lineTo(x: number, y: number): void
  arcTo(x1: number, y1: number, x2: number, y2: number, r: number): void
  closePath(): void
  fill(): void
  stroke(): void
}

export interface ShareRow {
  name: string
  qty: string
}

export interface ShareContent {
  title: string
  subtitle: string
  rows: ShareRow[]
}

/** 逻辑尺寸（px，导出时再乘像素比）。 */
export const SHARE_W = 750
const PAD = 48
const CARD_PAD = 36
const HEAD_H = 196
const ROW_H = 76
const EMPTY_H = 120
const FOOT_H = 120
/** 单图最多画多少行（超出画「还有 N 项」），防止长清单生成超长图被微信压缩。 */
export const SHARE_MAX_ROWS = 40

export interface ShareLayout {
  width: number
  height: number
  cardTop: number
  cardHeight: number
  rowYs: number[]
  /** 超出 SHARE_MAX_ROWS 未画的行数 */
  overflow: number
  footerY: number
}

export function shareLayout(rowCount: number): ShareLayout {
  const shown = Math.min(rowCount, SHARE_MAX_ROWS)
  const overflow = Math.max(0, rowCount - SHARE_MAX_ROWS)
  const cardTop = PAD + HEAD_H
  const listH = shown === 0 ? EMPTY_H : shown * ROW_H + (overflow > 0 ? ROW_H : 0)
  const cardHeight = CARD_PAD * 2 + listH
  const rowYs = Array.from({ length: shown }, (_, i) => cardTop + CARD_PAD + i * ROW_H)
  const footerY = cardTop + cardHeight + 56
  return {
    width: SHARE_W,
    height: footerY + FOOT_H - 56,
    cardTop,
    cardHeight,
    rowYs,
    overflow,
    footerY,
  }
}

/** 超宽截断加省略号。 */
export function fitText(ctx: Pick<Ctx2D, 'measureText'>, text: string, maxWidth: number): string {
  if (ctx.measureText(text).width <= maxWidth) return text
  const chars = [...text]
  while (chars.length > 1) {
    chars.pop()
    const t = chars.join('') + '…'
    if (ctx.measureText(t).width <= maxWidth) return t
  }
  return '…'
}

function roundRect(ctx: Ctx2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

const FONT = "-apple-system, 'PingFang SC', 'Helvetica Neue', sans-serif"

/** 绘制整张分享图（调用方已按像素比 scale）。 */
export function drawShareImage(ctx: Ctx2D, c: ShareContent, p: ThemePalette): ShareLayout {
  const L = shareLayout(c.rows.length)
  // 背景
  ctx.fillStyle = p.bg
  ctx.fillRect(0, 0, L.width, L.height)

  // 头部：品牌标 + 标题 + 副标题
  ctx.textBaseline = 'middle'
  ctx.textAlign = 'left'
  ctx.fillStyle = p.primary
  roundRect(ctx, PAD, PAD + 8, 8, 32, 4)
  ctx.fill()
  ctx.fillStyle = p.primaryDeep
  ctx.font = `700 26px ${FONT}`
  ctx.fillText('咕嘟小食单 · 采购清单', PAD + 22, PAD + 24)

  ctx.fillStyle = p.title
  ctx.font = `800 44px ${FONT}`
  ctx.fillText(fitText(ctx, c.title, L.width - PAD * 2), PAD, PAD + 100)
  ctx.fillStyle = p.caption
  ctx.font = `400 26px ${FONT}`
  ctx.fillText(c.subtitle, PAD, PAD + 156)

  // 卡片
  ctx.fillStyle = p.card
  roundRect(ctx, PAD, L.cardTop, L.width - PAD * 2, L.cardHeight, 28)
  ctx.fill()
  ctx.strokeStyle = p.border
  ctx.lineWidth = 2
  ctx.stroke()

  const left = PAD + CARD_PAD
  const right = L.width - PAD - CARD_PAD
  if (!c.rows.length) {
    ctx.fillStyle = p.caption
    ctx.font = `400 28px ${FONT}`
    ctx.textAlign = 'center'
    ctx.fillText('清单里的都入库了', L.width / 2, L.cardTop + CARD_PAD + EMPTY_H / 2)
    ctx.textAlign = 'left'
  }
  L.rowYs.forEach((y, i) => {
    const r = c.rows[i]
    const mid = y + ROW_H / 2
    // 勾选框
    ctx.strokeStyle = p.primarySoft
    ctx.lineWidth = 3
    roundRect(ctx, left, mid - 15, 30, 30, 8)
    ctx.stroke()
    // 数量（右对齐）
    ctx.font = `400 28px ${FONT}`
    ctx.fillStyle = p.caption
    ctx.textAlign = 'right'
    const qty = fitText(ctx, r.qty, 200)
    ctx.fillText(qty, right, mid)
    const qtyW = qty ? ctx.measureText(qty).width + 24 : 0
    // 名称
    ctx.textAlign = 'left'
    ctx.font = `700 32px ${FONT}`
    ctx.fillStyle = p.title
    ctx.fillText(fitText(ctx, r.name, right - left - 52 - qtyW), left + 52, mid)
    // 分隔线
    if (i < L.rowYs.length - 1 || L.overflow > 0) {
      ctx.fillStyle = p.border
      ctx.fillRect(left + 52, y + ROW_H - 1, right - left - 52, 2)
    }
  })
  if (L.overflow > 0) {
    const y = L.cardTop + CARD_PAD + L.rowYs.length * ROW_H
    ctx.fillStyle = p.caption
    ctx.font = `400 26px ${FONT}`
    ctx.fillText(`还有 ${L.overflow} 项，打开小程序查看`, left + 52, y + ROW_H / 2)
  }

  // 页脚
  ctx.textAlign = 'center'
  ctx.fillStyle = p.caption
  ctx.font = `400 24px ${FONT}`
  ctx.fillText(`共 ${c.rows.length} 项 · 来自「咕嘟小食单」小程序`, L.width / 2, L.footerY)
  ctx.textAlign = 'left'
  return L
}
