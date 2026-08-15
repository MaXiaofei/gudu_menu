import { describe, it, expect } from 'vitest'
import { sourceTypeLabel, amountText, stockBadge, restockItems } from '@/api/shopping'
import { installHttpMock } from '../helpers/http'

// api/shopping：来源文案 / 数量格式 / 库存徽章映射 / 批量入库。
describe('api/shopping', () => {
  it('sourceTypeLabel：来源映射', () => {
    expect(sourceTypeLabel('menu')).toBe('菜单')
    expect(sourceTypeLabel('custom_text')).toBe('文本录入')
    expect(sourceTypeLabel(null)).toBe('采购')
  })

  it('amountText：整数去小数、单位拼接', () => {
    expect(amountText({ id: 1, purchaseAmount: 2, purchaseUnitName: '斤' })).toBe('2 斤')
    expect(amountText({ id: 1, purchaseAmount: 1.5 })).toBe('1.5')
    expect(amountText({ id: 1 })).toBe('')
  })

  it('stockBadge：三色档位 + 手动加兜底', () => {
    expect(stockBadge({ id: 1, stockStatus: 'RED_NONE' })!.text).toBe('家里：用完')
    expect(stockBadge({ id: 1, stockStatus: 'YELLOW_SHORT' })!.text).toBe('家里：不足')
    expect(stockBadge({ id: 1, stockStatus: 'GREEN_ENOUGH' })!.text).toBe('家里：充足')
    expect(stockBadge({ id: 1 })!.text).toBe('手动加')
  })

  it('restockItems：POST /shopping/restock 带 itemIds', async () => {
    const http = installHttpMock({ 'POST /shopping/restock': { restocked: 2, markedOnly: 1 } })
    const r = await restockItems([1, 2, 3])
    expect(r).toEqual({ restocked: 2, markedOnly: 1 })
    expect(http.calls[0].data).toEqual({ itemIds: [1, 2, 3] })
  })
})
