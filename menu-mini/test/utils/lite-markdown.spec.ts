import { describe, it, expect } from 'vitest'
import { parseLiteMarkdown, type MdPart } from '@/utils/liteMarkdown'

const plain = (p: MdPart[]) => p.map((x) => x.text).join('')
const bolds = (p: MdPart[]) => p.filter((x) => x.bold).map((x) => x.text)

describe('utils/liteMarkdown', () => {
  it('粗体闭合后加粗，标记不显示', () => {
    const p = parseLiteMarkdown('推荐**清蒸鲈鱼**，少盐')
    expect(plain(p)).toBe('推荐清蒸鲈鱼，少盐')
    expect(bolds(p)).toEqual(['清蒸鲈鱼'])
  })

  it('流式中未闭合的 ** 去掉标记、不加粗', () => {
    expect(plain(parseLiteMarkdown('推荐**清蒸'))).toBe('推荐清蒸')
    expect(bolds(parseLiteMarkdown('推荐**清蒸'))).toEqual([])
    const q = parseLiteMarkdown('**番茄**和**鸡')
    expect(plain(q)).toBe('番茄和鸡')
    expect(bolds(q)).toEqual(['番茄'])
  })

  it('列表项与标题', () => {
    const p = parseLiteMarkdown('推荐：\n- **清蒸鲈鱼** 30 分钟\n* 凉拌菠菜\n· 菠菜蛋汤\n## 小贴士')
    expect(plain(p)).toBe('推荐：\n• 清蒸鲈鱼 30 分钟\n• 凉拌菠菜\n• 菠菜蛋汤\n小贴士')
    expect(bolds(p)).toEqual(['清蒸鲈鱼', '小贴士'])
  })

  it('乘号、负数与空串原样', () => {
    expect(plain(parseLiteMarkdown('2*3 个鸡蛋'))).toBe('2*3 个鸡蛋')
    expect(plain(parseLiteMarkdown('-5 度冷冻'))).toBe('-5 度冷冻')
    expect(parseLiteMarkdown('')).toEqual([])
  })
})
