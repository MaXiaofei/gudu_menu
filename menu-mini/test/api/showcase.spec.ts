import { describe, it, expect, vi } from 'vitest'
import { installHttpMock } from '../helpers/http'
import {
  applyAgentEvent,
  emptyAssistantMessage,
  actionSecondsLeft,
  toolsSeconds,
  confirmAction,
  undoAction,
  rateMessage,
  agentSuggestions,
  listSessions,
  parseVoiceCommand,
  reviseAction,
} from '@/api/agent'
import {
  feed,
  createPost,
  validatePost,
  countText,
  toggleLikeLocal,
  react,
  addComment,
  coverHeightRatio,
  toCoverRatio,
  listTopics,
  weekNoText,
} from '@/api/community'
import { toReviewRows, confidenceLevel, aliasHint, applyJob, pollJob, bestBeforeText } from '@/api/vision'
import { createDraft, sortedDays, distinctDishCount, radarPoints, linePoints, type MealPlanDraft } from '@/api/mealplan'
import { coverageLabel, yuan } from '@/api/contract'

describe('api/agent SSE 事件归并（contracts §1）', () => {
  it('meta → tool_start/tool_end → card → token → action → done', () => {
    const m = emptyAssistantMessage()
    const evs = [
      { event: 'meta', data: { messageId: 'm9', traceId: 't9' } },
      { event: 'tool_start', data: { name: 'get_pantry', label: '查余量' } },
      { event: 'tool_end', data: { name: 'get_pantry', summary: '29 样 · 4 样快到期', ms: 300, ok: true } },
      { event: 'card', data: { type: 'recipes', items: [] } },
      { event: 'token', data: { text: '鲈鱼明天到期，' } },
      { event: 'token', data: { text: '先吃它。' } },
      { event: 'action', data: { actionId: 'a1', tool: 'add_to_menu', title: '加入今晚的饭', preview: {}, expiresAt: '2026-10-16T18:45:00', status: 'PENDING' } },
      { event: 'done', data: { usage: {}, degraded: false } },
    ]
    evs.forEach((e) => applyAgentEvent(m, e))
    expect(m.messageId).toBe('m9')
    expect(m.traceId).toBe('t9')
    expect(m.tools).toEqual([{ name: 'get_pantry', label: '查余量', summary: '29 样 · 4 样快到期', ms: 300, ok: true, running: false }])
    expect(m.cards[0].type).toBe('recipes')
    expect(m.text).toBe('鲈鱼明天到期，先吃它。')
    expect(m.actions[0].status).toBe('PENDING')
    expect(m.streaming).toBe(false)
  })

  it('同一 action 再次推送 → 原地更新状态', () => {
    const m = emptyAssistantMessage()
    applyAgentEvent(m, { event: 'action', data: { actionId: 'a1', status: 'PENDING', title: 'x' } })
    applyAgentEvent(m, { event: 'action', data: { actionId: 'a1', status: 'EXPIRED' } })
    expect(m.actions).toHaveLength(1)
    expect(m.actions[0]).toMatchObject({ status: 'EXPIRED', title: 'x' })
  })

  it('error：停止流式并记录报错编号；degraded 标记', () => {
    const m = emptyAssistantMessage()
    applyAgentEvent(m, { event: 'error', data: { code: 500, message: '超时', errorId: '7f3a-91c2' } })
    expect(m.streaming).toBe(false)
    expect(m.error?.errorId).toBe('7f3a-91c2')
    const m2 = emptyAssistantMessage()
    applyAgentEvent(m2, { event: 'done', data: { degraded: true } })
    expect(m2.degraded).toBe(true)
  })

  it('剩余秒数 / 工具总耗时', () => {
    const now = Date.parse('2026-10-16T18:40:00Z')
    expect(actionSecondsLeft({ expiresAt: '2026-10-16T18:44:32Z' } as any, now)).toBe(272)
    expect(actionSecondsLeft({ expiresAt: '2026-10-16T18:39:00Z' } as any, now)).toBe(0)
    expect(toolsSeconds([{ ms: 1200 }, { ms: 600 }] as any)).toBe('1.8')
  })

  it('确认 / 撤销 / 点踩 路径与参数', async () => {
    const http = installHttpMock({
      'POST /agent/actions/a1/confirm': { status: 'DONE', result: {}, undoToken: 'u1' },
      'POST /agent/actions/undo': { status: 'UNDONE' },
      'POST /agent/messages/m1/feedback': {},
    })
    expect((await confirmAction('a1')).undoToken).toBe('u1')
    await undoAction('u1')
    await rateMessage('m1', -1)
    expect(http.calls[1].data).toEqual({ undoToken: 'u1' })
    expect(http.calls[2].data).toEqual({ rating: -1 })
  })

  it('首屏建议兜底；会话列表游标', async () => {
    installHttpMock({ 'GET /agent/suggestions': null, 'GET /agent/sessions': { items: [{ sessionId: 's1' }], nextCursor: null } })
    expect(await agentSuggestions()).toEqual({ greeting: '', tonight: null, expiring: [], prompts: [] })
    expect((await listSessions()).items[0].sessionId).toBe('s1')
  })
})

describe('api/community（contracts §2）', () => {
  it('feed 参数：tab + cursor + size', async () => {
    const http = installHttpMock({ 'GET /community/feed': { items: [], nextCursor: null } })
    await feed('follow', 'c1')
    expect(http.calls[0].data).toEqual({ tab: 'follow', cursor: 'c1', size: 20 })
  })

  it('发布校验与 trim；图片最多 9 张', async () => {
    expect(validatePost({ title: ' ', text: '', imageUrls: ['a'] })).toBe('写个标题吧')
    expect(validatePost({ title: 'x', text: '', imageUrls: [] })).toBe('至少放 1 张照片')
    expect(validatePost({ title: '一'.repeat(31), text: '', imageUrls: ['a'] })).toBe('标题最多 30 字')
    expect(validatePost({ title: '滑蛋虾仁', text: '嫩', imageUrls: ['a'] })).toBe('')
    const http = installHttpMock({ 'POST /community/posts': { postId: 1, status: 'PUBLISHED' } })
    await createPost({ title: ' 嫩到宝宝抢着吃 ', text: ' 好 ', imageUrls: Array(10).fill('u'), topicIds: [1], visibility: 'PUBLIC' })
    expect(http.calls[0].data.title).toBe('嫩到宝宝抢着吃')
    expect(http.calls[0].data.imageUrls).toHaveLength(9)
  })

  it('点赞乐观更新 + 接口参数；评论 parentId 可选', async () => {
    const p = { liked: false, likeCount: 1299 }
    toggleLikeLocal(p)
    expect(p).toEqual({ liked: true, likeCount: 1300 })
    toggleLikeLocal(p)
    expect(p.likeCount).toBe(1299)
    const http = installHttpMock({ 'POST /community/posts/3/reactions': {}, 'POST /community/posts/3/comments': {} })
    await react(3, 'LIKE', true)
    await addComment(3, ' 好看 ')
    await addComment(3, '谢谢', 8)
    expect(http.calls[0].data).toEqual({ type: 'LIKE', on: true })
    expect(http.calls[1].data).toEqual({ text: '好看' })
    expect(http.calls[2].data).toEqual({ text: '谢谢', parentId: 8 })
  })

  it('countText', () => {
    expect(countText(856)).toBe('856')
    expect(countText(1200)).toBe('1.2k')
    expect(countText(21000)).toBe('2.1w')
    expect(countText(3000)).toBe('3k')
  })
})

describe('api/vision（contracts §3）', () => {
  const item = (name: string, confidence: number, rawName = name) =>
    ({ name, rawName, confidence, ingredientId: 1, iconKey: null, amount: 1, unit: '个', level: 'AUTO', alternatives: [], suggestedBestBefore: null }) as any

  it('置信度分级：≥0.8 自动、0.5–0.8 待确认、<0.5 不展示', () => {
    expect(confidenceLevel(0.98)).toBe('auto')
    expect(confidenceLevel(0.8)).toBe('auto')
    expect(confidenceLevel(0.72)).toBe('confirm')
    expect(confidenceLevel(0.49)).toBe('hidden')
    const rows = toReviewRows([item('鸡蛋', 0.98), item('菠菜', 0.72), item('不明', 0.3), item('香菇', 0.64)])
    expect(rows.map((r) => r.name)).toEqual(['菠菜', '香菇', '鸡蛋'])
    expect(rows.map((r) => r.checked)).toEqual([false, false, true])
  })

  it('别名提示与保质期文案', () => {
    expect(aliasHint({ rawName: '西红柿', name: '番茄' })).toBe('西红柿 → 番茄')
    expect(aliasHint({ rawName: '番茄', name: '番茄' })).toBe('')
    const today = new Date(2026, 9, 16)
    expect(bestBeforeText('2026-10-19', today)).toBe('3 天后到期')
    expect(bestBeforeText('2026-10-16', today)).toBe('今天到期')
    expect(bestBeforeText(null, today)).toBe('保质期未知')
  })

  it('apply 只传勾选字段，空 ingredientId 不传', async () => {
    const http = installHttpMock({ 'POST /vision/jobs/7/apply': { added: 1 } })
    await applyJob(7, [{ name: ' 番茄 ', ingredientId: null, amount: 3, unit: '个', bestBefore: '2026-10-23' }])
    expect(http.calls[0].data).toEqual({ items: [{ name: '番茄', amount: 3, unit: '个', bestBefore: '2026-10-23' }] })
  })

  it('轮询直到 DONE', async () => {
    let n = 0
    installHttpMock({ 'GET /vision/jobs/5': () => ({ jobId: 5, status: n++ < 2 ? 'PENDING' : 'DONE', items: [] }) })
    const sleep = vi.fn(async () => {})
    const j = await pollJob(5, { sleep })
    expect(j.status).toBe('DONE')
    expect(sleep).toHaveBeenCalledTimes(2)
  })
})

describe('api/mealplan（contracts §4）', () => {
  it('未指定 weekStart：周五生成下周一', async () => {
    const http = installHttpMock({ 'POST /mealplan/drafts': { draftId: 1 } })
    await createDraft({ meals: ['LUNCH', 'DINNER'], servings: 4, memberIds: [1], preferExpiring: true }, new Date(2026, 9, 16))
    expect(http.calls[0].data.weekStart).toBe('2026-10-19')
  })

  it('按日期排序、不重样计数', () => {
    const dish = (id: number) => ({ id }) as any
    const d = {
      days: [
        { date: '2026-10-20', slots: [{ dish: dish(1) }, { dish: dish(2) }] },
        { date: '2026-10-19', slots: [{ dish: dish(3) }, { dish: dish(1) }] },
      ],
    } as unknown as MealPlanDraft
    expect(sortedDays(d).map((x) => x.date)).toEqual(['2026-10-19', '2026-10-20'])
    expect(distinctDishCount(d)).toBe(3)
  })

  it('雷达 / 折线几何', () => {
    expect(radarPoints([1.5, 1.5, 1.5, 1.5], 50, 50, 40)).toBe('50,10 90,50 50,90 10,50')
    expect(radarPoints([3], 0, 0, 10)).toBe('0,-10') // 超出截断
    expect(linePoints([0, 10], 100, 50, 0, 10)).toBe('0,50 100,0')
  })
})

describe('api/contract', () => {
  it('coverageLabel / yuan', () => {
    expect(coverageLabel({ have: 4, total: 4, missing: [] })).toBe('家里够')
    expect(coverageLabel({ have: 4, total: 5, missing: ['小葱'] })).toBe('缺 1 样 · 小葱')
    expect(yuan(32600)).toBe('¥326')
    expect(yuan(1850)).toBe('¥18.50')
  })
})

describe('api/agent 本地语音指令', () => {
  it('下一步 / 上一步 / 暂停 / 计时；长句交给模型', () => {
    expect(parseVoiceCommand('下一步。')).toBe('next')
    expect(parseVoiceCommand('上一步')).toBe('prev')
    expect(parseVoiceCommand('暂停')).toBe('pause')
    expect(parseVoiceCommand('开始计时')).toBe('timer')
    expect(parseVoiceCommand('没有郫县豆瓣酱用什么代替')).toBeNull()
    expect(parseVoiceCommand('')).toBeNull()
  })
})

describe('对接已合并后端（厨房圈 / revise）', () => {
  it('coverRatio 夹到 0.5–2：卡片高宽比 = 1 / coverRatio', () => {
    expect(coverHeightRatio(0.75)).toBeCloseTo(1.333, 3)
    expect(coverHeightRatio(0.2)).toBe(2)
    expect(coverHeightRatio(5)).toBe(0.5)
    expect(coverHeightRatio(undefined)).toBe(1)
    expect(toCoverRatio(1080, 1440)).toBe(0.75)
    expect(toCoverRatio(4000, 1000)).toBe(2)
    expect(toCoverRatio(0, 10)).toBeUndefined()
  })

  it('发布：fromFoodLogId + coverRatio 夹取；不再传 fromCookRecordId', async () => {
    const http = installHttpMock({ 'POST /community/posts': { postId: 1, status: 'PUBLISHED' } })
    await createPost({ title: '滑蛋虾仁', text: '', imageUrls: ['u'], topicIds: [], visibility: 'PUBLIC', fromFoodLogId: 88, coverRatio: 3 })
    expect(http.calls[0].data.fromFoodLogId).toBe(88)
    expect(http.calls[0].data.coverRatio).toBe(2)
    expect(http.calls[0].data).not.toHaveProperty('fromCookRecordId')
  })

  it('话题列表 GET /community/topics?status=ACTIVE；weekNo 文案', async () => {
    const http = installHttpMock({ 'GET /community/topics': [{ topicId: 7, name: '一周不重样', weekNo: 3 }] })
    const ts = await listTopics()
    expect(http.calls[0].data).toEqual({ status: 'ACTIVE', size: 10 })
    expect(weekNoText(ts[0])).toBe('第 3 周')
    expect(weekNoText({ weekNo: null })).toBe('')
  })

  it('revise：POST /agent/actions/{id}/revise { args } → 新 PendingAction', async () => {
    const http = installHttpMock({ 'POST /agent/actions/a-1/revise': { actionId: 'a-2', status: 'PENDING' } })
    const next = await reviseAction('a-1', { servings: 4, eaterIds: [1, 3] })
    expect(next.actionId).toBe('a-2')
    expect(http.calls[0].data).toEqual({ args: { servings: 4, eaterIds: [1, 3] } })
  })
})
