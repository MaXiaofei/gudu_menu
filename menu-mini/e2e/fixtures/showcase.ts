import type { Page, Route } from '@playwright/test'
import { IMG, DISHES } from './demo'

/**
 * showcase-ai-community 新接口 mock（契约 openspec/changes/showcase-ai-community/contracts.md）。
 * 与 demo.ts 的 installMocks 组合：installMocks(page, SHOWCASE_HANDLERS) 后再 installAgentSse(page)。
 */
type Handler = (url: URL, method: string) => unknown | undefined

const card = (id: number, extra: Record<string, unknown> = {}) => {
  const d = DISHES.find((x) => x.id === id)!
  return {
    id: d.id,
    name: d.name,
    coverUrl: d.coverUrl,
    minutes: (d.prepTime || 0) + (d.cookTime || 0),
    difficulty: '简单',
    coverage: { have: d.coverage.enough, total: d.coverage.total, missing: d.coverage.missing.map((m) => m.name) },
    tags: (d as any).tagNames || [],
    ...extra,
  }
}
const user = (userId: number, nickname: string, avatar: string) => ({ userId, nickname, avatarUrl: IMG(`avatar/${avatar}.svg`) })
const AUTHORS = [user(101, '糖糖酱', 'c-tangtang'), user(102, '陈厨', 'c-chenchu'), user(103, '小满', 'c-xiaoman'), user(104, 'Amay', 'c-amay'), user(105, '大白', 'c-dabai'), user(106, '老王', 'laowang')]
const POSTS = [
  [2, '第一次做清蒸鲈鱼就成功了！火候和时间都记下来了', 0.75, 1260, 128, ['控盐']],
  [11, '1 岁宝宝的牛奶南瓜羹，不加糖也很甜', 1, 856, 0, ['宝宝辅食']],
  [7, '可乐鸡翅零失败，收汁这一步最关键', 0.8, 2100, 562, []],
  [1, '10 分钟的滑蛋虾仁，嫩到宝宝抢着吃', 1.2, 932, 309, ['快手菜']],
  [12, '糖醋排骨，酸甜比例 1:2:3 记住就行', 0.9, 640, 0, []],
  [5, '蒜蓉西兰花，焯水 30 秒颜色最好看', 1.25, 418, 47, ['控脂']],
  [6, '麻婆豆腐，花椒油最后淋', 0.85, 1530, 211, []],
  [10, '周末炖一锅土豆牛肉，周一带饭', 1, 377, 0, []],
].map(([id, title, ratio, likes, copies, tags], i) => {
  const d = DISHES.find((x) => x.id === id)!
  return {
    postId: 300 + i,
    author: AUTHORS[i % AUTHORS.length],
    title,
    coverUrl: d.coverUrl,
    coverRatio: ratio,
    imageCount: i % 3 === 0 ? 3 : 1,
    likeCount: likes,
    liked: i === 1,
    copyCount: copies,
    tags,
  }
})
export const TOPIC = {
  topicId: 7,
  name: '一周不重样',
  intro: '连续 7 天，每天做一道不重样的家常菜并发布。',
  rule: '每天发布 1 条带 #一周不重样 的作品算打卡；全勤得「不重样」徽章，前 10 名上首页推荐。',
  startDate: '2026-10-12',
  endDate: '2026-10-18',
  participantCount: 2130,
  postCount: 16200,
  coverUrls: [DISHES[1].coverUrl, DISHES[6].coverUrl, DISHES[0].coverUrl],
}

export const SHOWCASE_HANDLERS: Handler[] = [
  // ---- Agent ----
  (u) =>
    u.pathname.endsWith('/agent/suggestions')
      ? {
          greeting: '我看了下家里：29 样食材，鲈鱼明天到期。妈妈要少盐，朵朵吃软的——今晚这样安排怎么样？',
          tonight: card(2, { tags: ['适合高血压'] }),
          expiring: [
            { name: '鲈鱼', iconKey: 'fish', daysLeft: 1 },
            { name: '菠菜', iconKey: 'spinach', daysLeft: 2 },
            { name: '北豆腐', iconKey: 'tofu', daysLeft: 2 },
          ],
          prompts: ['冰箱里的能做什么', '给朵朵安排这周辅食', '周六爸妈来吃饭怎么安排', '拍冰箱入库'],
        }
      : undefined,
  (u, m) => (u.pathname.endsWith('/agent/sessions') && m === 'POST' ? { sessionId: 's-demo' } : undefined),
  (u) =>
    u.pathname.endsWith('/agent/sessions')
      ? { items: [{ sessionId: 's-1', title: '朵朵这周辅食', lastMessage: '已排好 5 天，周三换成了蒸水蛋', updatedAt: '2026-10-15 20:10:00', coverUrl: DISHES[10].coverUrl }], nextCursor: null }
      : undefined,
  (u) => (/\/agent\/actions\/[^/]+\/confirm$/.test(u.pathname) ? { status: 'DONE', result: {}, undoToken: 'u-1' } : undefined),
  (u) => (/\/agent\/actions\/[^/]+\/cancel$/.test(u.pathname) ? { status: 'CANCELLED' } : undefined),
  (u) => (u.pathname.endsWith('/agent/actions/undo') ? { status: 'UNDONE' } : undefined),
  (u) => (/\/agent\/messages\/[^/]+\/feedback$/.test(u.pathname) ? {} : undefined),
  // ---- 厨房圈 ----
  (u) => (u.pathname.endsWith('/community/banner') ? { topic: TOPIC, followingUpdates: AUTHORS.slice(0, 5) } : undefined),
  (u) =>
    u.pathname.endsWith('/community/feed')
      ? u.searchParams.get('tab') === 'follow'
        ? { items: [], nextCursor: null }
        : u.searchParams.get('cursor')
          ? { items: POSTS.slice(6).map((p) => ({ ...p, postId: p.postId + 100 })), nextCursor: null }
          : { items: POSTS.slice(0, 6), nextCursor: 'c-2' }
      : undefined,
  (u) =>
    /\/community\/posts\/\d+$/.test(u.pathname)
      ? {
          ...POSTS[0],
          text: '一斤二两的鲈鱼，背上划刀摆成孔雀开屏，水开后大火蒸 8 分钟，关火再焖 2 分钟。倒掉盘里的腥水，铺葱丝淋热油，「滋啦」一声就成了。',
          images: [DISHES[1].coverUrl, IMG('step/qingzheng-luyu-5.jpg'), IMG('step/qingzheng-luyu-9.jpg')],
          topics: [{ topicId: 7, name: '一周不重样' }],
          recipe: { ...card(2), copyCount: 128 },
          commentCount: 2,
          collectCount: 386,
          collected: false,
          following: false,
          createdAt: '2026-10-14 19:20:00',
          city: '上海',
          status: 'PUBLISHED',
        }
      : undefined,
  (u) => (/\/community\/posts\/\d+\/copy-recipe$/.test(u.pathname) ? { dishId: 2 } : undefined),
  (u) => (/\/community\/posts\/\d+\/copies$/.test(u.pathname) ? { items: [{ user: AUTHORS[2], imageUrl: IMG('step/qingzheng-luyu-9.jpg') }, { user: AUTHORS[3], imageUrl: DISHES[1].coverUrl }] } : undefined),
  (u, m) =>
    /\/community\/posts\/\d+\/comments$/.test(u.pathname)
      ? m === 'POST'
        ? { commentId: 99, author: user(1, '小王', 'xiaowang'), text: '马上去试', isAuthor: false, likeCount: 0, createdAt: '2026-10-16 19:00:00', replies: [] }
        : {
            items: [
              {
                commentId: 1,
                author: AUTHORS[1],
                text: '蒸好以后盘里的水一定要倒掉，不然腥',
                isAuthor: false,
                likeCount: 12,
                createdAt: '2026-10-15 09:00:00',
                replies: [{ commentId: 2, author: AUTHORS[0], text: '对！我写进步骤了', isAuthor: true, likeCount: 3, createdAt: '2026-10-15 10:00:00', replies: [] }],
              },
            ],
            nextCursor: null,
          }
      : undefined,
  (u) => (/\/community\/posts\/\d+\/reactions$/.test(u.pathname) ? { likeCount: 1261, collectCount: 386, liked: true, collected: false } : undefined),
  (u) => (/\/community\/users\/\d+\/follow$/.test(u.pathname) ? { following: true } : undefined),
  (u) =>
    /\/community\/topics\/\d+$/.test(u.pathname)
      ? {
          ...TOPIC,
          myProgress: {
            days: [
              { date: '2026-10-12', done: true, coverUrl: DISHES[0].coverUrl },
              { date: '2026-10-13', done: true, coverUrl: DISHES[4].coverUrl },
              { date: '2026-10-14', done: true, coverUrl: DISHES[12].coverUrl },
              { date: '2026-10-15', done: true, coverUrl: DISHES[5].coverUrl },
            ],
            doneCount: 4,
          },
          ranking: AUTHORS.slice(0, 5).map((a, i) => ({ user: a, days: 5, likeCount: 3400 - i * 600, rank: i + 1 })),
          myRank: 238,
        }
      : undefined,
  (u) => (u.pathname.endsWith('/community/drafts/ai-text') ? { text: '早上赶时间做的，鸡蛋要小火推，看到七成熟就关火，余温会把它焖熟。' } : undefined),
  (u) => (u.pathname.endsWith('/community/posts') ? { postId: 300, status: 'PUBLISHED' } : undefined),
  // ---- 拍照识别 ----
  (u) =>
    /\/vision\/jobs\/\d+$/.test(u.pathname)
      ? {
          jobId: 5,
          mode: 'FRIDGE',
          status: 'DONE',
          imageExpiresAt: '2026-10-17 19:00:00',
          items: [
            { name: '鸡蛋', rawName: '鸡蛋', ingredientId: 17, iconKey: 'egg', amount: 6, unit: '个', confidence: 0.98, level: 'AUTO', alternatives: [], suggestedBestBefore: '2026-11-05' },
            { name: '番茄', rawName: '西红柿', ingredientId: 14, iconKey: 'tomato', amount: 3, unit: '个', confidence: 0.94, level: 'AUTO', alternatives: [], suggestedBestBefore: '2026-10-23' },
            { name: '菠菜', rawName: '菠菜', ingredientId: 12, iconKey: 'spinach', amount: 1, unit: '把', confidence: 0.72, level: 'CONFIRM', alternatives: ['油麦菜', '小白菜'], suggestedBestBefore: '2026-10-19' },
            { name: '香菇', rawName: '香菇', ingredientId: null, iconKey: 'mushroom', amount: 4, unit: '朵', confidence: 0.64, level: 'CONFIRM', alternatives: ['杏鲍菇', '口蘑'], suggestedBestBefore: '2026-10-21' },
            { name: '牛奶', rawName: '牛奶', ingredientId: 40, iconKey: 'milk', amount: 1, unit: '盒', confidence: 0.96, level: 'AUTO', alternatives: [], suggestedBestBefore: '2026-10-22' },
            { name: '不明', rawName: '不明', ingredientId: null, iconKey: null, amount: 1, unit: '个', confidence: 0.3, level: 'CONFIRM', alternatives: [], suggestedBestBefore: null },
          ],
        }
      : undefined,
  (u) => (/\/vision\/jobs\/\d+\/apply$/.test(u.pathname) ? { added: 3 } : undefined),
  // ---- 餐程 / 营养 ----
  (u) => (u.pathname.endsWith('/mealplan/drafts') || /\/mealplan\/drafts\/\d+(\/slots\/\d+)?$/.test(u.pathname) ? DRAFT : undefined),
  (u) => (/\/alternatives$/.test(u.pathname) ? { items: [card(5, { sodiumMg: 600 }), card(8, { sodiumMg: 500 }), card(9, { sodiumMg: 400 })] } : undefined),
  (u) => (/\/mealplan\/drafts\/\d+\/apply$/.test(u.pathname) ? { menuIds: [1, 2, 3, 4, 5, 6, 7], shoppingItemCount: 18 } : undefined),
  (u) =>
    u.pathname.endsWith('/nutrition/report')
      ? {
          member: { memberId: 3, name: '妈妈', avatarUrl: null, profile: '高血压人群建议量' },
          range: { from: '2026-10-12', to: '2026-10-16' },
          recordCount: 11,
          radar: [
            { key: 'energy', label: '能量', actual: 1650, target: 1800, unit: 'kcal', ratio: 0.92 },
            { key: 'protein', label: '蛋白质', actual: 63, target: 60, unit: 'g', ratio: 1.05 },
            { key: 'fat', label: '脂肪', actual: 58, target: 54, unit: 'g', ratio: 1.08 },
            { key: 'sodium', label: '钠', actual: 2.6, target: 2, unit: 'g', ratio: 1.3 },
            { key: 'fiber', label: '膳食纤维', actual: 25.5, target: 25, unit: 'g', ratio: 1.02 },
          ],
          alerts: [{ key: 'sodium', label: '钠', dailyAvg: 2.6, limit: 2, unit: 'g', sources: [card(4), card(6)], suggestions: [card(2), card(5)] }],
          trend: { key: 'sodium', points: ['10', '11', '12', '13', '14', '15', '16'].map((d, i) => ({ date: `2026-10-${d}`, value: [2.1, 1.8, 3.1, 2.4, 2.8, 1.9, 2.2][i] })), limit: 2 },
          goods: ['膳食纤维达标', '蛋白质充足'],
        }
      : undefined,
]

const DAYS = ['2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24', '2026-10-25']
const PLAN: [number, number, string?, string?][] = [
  [11, 5, 'BABY', 'EXPIRING'],
  [8, 9],
  [1, 2],
  [11, 6, 'BABY'],
  [10, 7],
  [3, 13],
  [15, 12, 'BABY'],
]
export const DRAFT = {
  draftId: 9,
  weekStart: '2026-10-19',
  weekEnd: '2026-10-25',
  constraints: ['4 口人', '妈妈少盐', '朵朵辅食版'],
  check: { passed: true, rounds: 2, notes: ['钠日均 1.8g（≤2g）'] },
  days: DAYS.map((date, i) => ({
    date,
    weekday: '',
    note: null,
    slots: [
      { slotId: i * 2 + 1, meal: 'LUNCH', dish: card(PLAN[i][0]), badges: PLAN[i][2] ? [PLAN[i][2]] : [] },
      { slotId: i * 2 + 2, meal: 'DINNER', dish: card(PLAN[i][1]), badges: PLAN[i][3] ? [PLAN[i][3]] : [] },
    ],
  })),
  summary: { estCostFen: 32600, newItems: 18, usesExpiring: 3 },
}

/** 咕嘟 SSE：按问题回放事件流（在 installMocks 之后注册，优先匹配）。 */
export async function installAgentSse(page: Page) {
  await page.route(/\/gudu\/agent\/sessions\/[^/]+\/messages$/, async (route: Route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    const body = JSON.parse(route.request().postData() || '{}')
    const text: string = body.text || ''
    const ev = (event: string, data: unknown) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
    let out = ev('meta', { messageId: 'm-' + Date.now(), traceId: '7f3a91c2e5d04b1a' })
    if (/做完|做好/.test(text)) {
      out += ev('tool_start', { name: 'get_recipe', label: '认出菜谱' }) + ev('tool_end', { name: 'get_recipe', summary: '麻婆豆腐', ms: 210, ok: true })
      out += ev('token', { text: '好嘞，按 3 人份算的，看看对不对：' })
      out += ev('action', {
        actionId: 'a-cook',
        tool: 'mark_cooked',
        title: '做完回写 · 麻婆豆腐',
        expiresAt: new Date(Date.now() + 300000).toISOString(),
        status: 'PENDING',
        preview: {
          dish: card(6, { tags: ['偏辣'] }),
          servings: 3,
          eaters: [
            { memberId: 1, name: '小王', avatarUrl: null },
            { memberId: 2, name: '林林', avatarUrl: null },
            { memberId: 3, name: '妈妈', avatarUrl: null },
          ],
          lines: [
            { name: '北豆腐', iconKey: 'tofu', delta: '−1 块', after: '余量 0 · 用完了', status: 'USED_UP' },
            { name: '肉末', iconKey: 'pork', delta: '−100g', after: '剩 200g', status: 'LEFT' },
            { name: '郫县豆瓣酱', iconKey: 'sauce', delta: '适量', after: '调料不计量', status: 'SKIP' },
          ],
        },
      })
    } else if (/加进|食集/.test(text)) {
      out += ev('tool_start', { name: 'add_to_menu', label: '准备加入食集' }) + ev('tool_end', { name: 'add_to_menu', summary: '今晚的饭 · 3 道', ms: 120, ok: true })
      out += ev('token', { text: '确认后我就加进去，缺的两样放进采购清单。' })
      out += ev('action', {
        actionId: 'a-menu',
        tool: 'add_to_menu',
        title: '加入「今晚的饭」· 3 道菜',
        expiresAt: new Date(Date.now() + 272000).toISOString(),
        status: 'PENDING',
        preview: { menuName: '今晚的饭', date: '周五晚餐', dishes: [card(2), card(9), card(6)] },
      })
    } else {
      out += ev('tool_start', { name: 'get_pantry', label: '查了余量' }) + ev('tool_end', { name: 'get_pantry', summary: '29 样，4 样快到期', ms: 300, ok: true })
      out += ev('tool_start', { name: 'get_family_profile', label: '读取家庭档案' }) + ev('tool_end', { name: 'get_family_profile', summary: '妈妈高血压', ms: 100, ok: true })
      out += ev('tool_start', { name: 'search_recipes', label: '检索菜谱' }) + ev('tool_end', { name: 'search_recipes', summary: '按「少盐、用掉鲈鱼菠菜」', ms: 1400, ok: true })
      out += ev('card', { type: 'recipes', items: [card(2), card(9), card(6)] })
      out += ev('token', { text: '鲈鱼明天到期，菠菜、北豆腐剩 2 天。按妈妈少盐挑了三道，' })
      out += ev('token', { text: '都只用一点点调味。' })
    }
    out += ev('done', { usage: { promptTokens: 1200, completionTokens: 180, costFen: 2 }, degraded: false })
    await route.fulfill({ status: 200, contentType: 'text/event-stream', body: out })
  })
}
