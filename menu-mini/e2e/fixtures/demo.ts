import type { Page, Route } from '@playwright/test'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 原型演示数据（与 docs/design/prototype 一致）+ 接口 mock。
 * 图片走 https://mock.gudu.test/img/**，由 route 回放 docs/design/prototype/img 下的文件。
 */
const IMG_ROOT = resolve(process.cwd(), '../docs/design/prototype/img')
export const IMG = (p: string) => `https://mock.gudu.test/img/${p}`
const dishImg = (slug: string) => IMG(`dish/${slug}.jpg`)

export const DISHES = [
  { id: 1, slug: 'huadan-xiaren', name: '滑蛋虾仁', prepTime: 5, cookTime: 5, cookedCount: 12, tagNames: ['宝宝可吃'], coverage: { enough: 3, total: 3, missing: [] }, lastCookedAt: '2026-10-13 18:30:00', lastCookedBy: { memberId: 3, name: '妈妈' } },
  { id: 2, slug: 'qingzheng-luyu', name: '清蒸鲈鱼', prepTime: 10, cookTime: 15, cookedCount: 4, tagNames: ['适合高血压'], coverage: { enough: 4, total: 5, missing: [{ ingredientId: 15, name: '葱' }] }, lastCookedAt: '2026-10-09 18:30:00' },
  { id: 3, slug: 'lianou-paigu', name: '莲藕排骨汤', prepTime: 20, cookTime: 70, cookedCount: 0, sourceName: '公共库', coverage: { enough: 2, total: 4, missing: [{ ingredientId: 20, name: '莲藕' }, { ingredientId: 21, name: '排骨' }] } },
  { id: 4, slug: 'hongshao-rou', name: '红烧肉', prepTime: 10, cookTime: 50, cookedCount: 2, coverage: { enough: 1, total: 5, missing: [] } },
  { id: 5, slug: 'suanrong-xilanhua', name: '蒜蓉西兰花', prepTime: 3, cookTime: 5, cookedCount: 9, tagNames: ['少油'], coverage: { enough: 2, total: 2, missing: [] } },
  { id: 6, slug: 'mapo-doufu', name: '麻婆豆腐', prepTime: 5, cookTime: 15, cookedCount: 6, coverage: { enough: 3, total: 4, missing: [{ ingredientId: 30, name: '牛肉末' }] } },
  { id: 7, slug: 'kele-jichi', name: '可乐鸡翅', prepTime: 10, cookTime: 25, cookedCount: 7, tagNames: ['宝宝可吃'], coverage: { enough: 3, total: 4, missing: [{ ingredientId: 31, name: '可乐' }] } },
  { id: 8, slug: 'xihongshi-jidan-tang', name: '西红柿鸡蛋汤', prepTime: 3, cookTime: 5, cookedCount: 15, coverage: { enough: 2, total: 2, missing: [] } },
  { id: 9, slug: 'liangban-bocai', name: '凉拌菠菜', prepTime: 5, cookTime: 3, cookedCount: 3, coverage: { enough: 2, total: 2, missing: [] } },
  { id: 10, slug: 'tudou-niurou', name: '土豆炖牛肉', prepTime: 15, cookTime: 60, cookedCount: 5, coverage: { enough: 3, total: 4, missing: [] } },
  { id: 11, slug: 'nangua-geng', name: '牛奶南瓜羹', prepTime: 5, cookTime: 15, cookedCount: 8, tagNames: ['宝宝辅食'], coverage: { enough: 2, total: 2, missing: [] } },
  { id: 12, slug: 'tangcu-paigu', name: '糖醋排骨', prepTime: 10, cookTime: 40, cookedCount: 3, coverage: { enough: 2, total: 5, missing: [] } },
  { id: 13, slug: 'xiaoji-mogu', name: '小鸡炖蘑菇', prepTime: 15, cookTime: 50, cookedCount: 0, coverage: { enough: 2, total: 4, missing: [] } },
  { id: 14, slug: 'xiezi-niangao', name: '梭子蟹炒年糕', prepTime: 15, cookTime: 15, cookedCount: 0, coverage: { enough: 1, total: 4, missing: [] } },
  { id: 15, slug: 'shanyao-ni', name: '蓝莓山药泥', prepTime: 10, cookTime: 20, cookedCount: 0, coverage: { enough: 1, total: 3, missing: [] } },
].map((d) => ({ ...d, coverUrl: dishImg(d.slug) }))

export const dishCard = (id: number) => {
  const d = DISHES.find((x) => x.id === id)!
  return { dishId: d.id, name: d.name, coverImage: d.coverUrl, coverUrl: d.coverUrl, cookMinutes: (d.prepTime || 0) + (d.cookTime || 0), difficulty: 1, cookedCount: d.cookedCount, coverage: d.coverage }
}

export const PANTRY = [
  { ingredientId: 11, ingredientName: '鲈鱼', iconKey: 'fish', level: 'ENOUGH', daysLeft: 1, bestBefore: '2026-10-17', categoryName: '水产', lastChange: { source: 'purchase', createTime: '2026-10-14 10:00:00' } },
  { ingredientId: 12, ingredientName: '菠菜', iconKey: 'spinach', level: 'LOW', daysLeft: 2, bestBefore: '2026-10-18', categoryName: '蔬菜' },
  { ingredientId: 13, ingredientName: '北豆腐', iconKey: 'tofu', level: 'ENOUGH', daysLeft: 2, bestBefore: '2026-10-18', categoryName: '豆制品' },
  { ingredientId: 14, ingredientName: '番茄', iconKey: 'tomato', level: 'ENOUGH', daysLeft: 6, bestBefore: '2026-10-22', categoryName: '蔬菜' },
  { ingredientId: 15, ingredientName: '小葱', iconKey: 'scallion', level: 'NONE', categoryName: '蔬菜', lastChange: { source: 'cook', createTime: '2026-10-15 19:00:00' } },
  { ingredientId: 16, ingredientName: '土豆', iconKey: 'potato', level: 'ENOUGH', daysLeft: 12, bestBefore: '2026-10-28', categoryName: '蔬菜' },
  { ingredientId: 17, ingredientName: '鸡蛋', iconKey: 'egg', level: 'LOW', daysLeft: 20, bestBefore: '2026-11-05', categoryName: '肉禽蛋' },
  { ingredientId: 18, ingredientName: '姜', iconKey: 'ginger', level: 'ENOUGH', categoryName: '调料' },
  { ingredientId: 19, ingredientName: '蒸鱼豉油', iconKey: 'sauce', level: 'ENOUGH', categoryName: '调料' },
  { ingredientId: 22, ingredientName: '西兰花', iconKey: 'broccoli', level: 'ENOUGH', daysLeft: 4, categoryName: '蔬菜' },
  { ingredientId: 23, ingredientName: '虾仁', iconKey: 'shrimp', level: 'ENOUGH', daysLeft: 30, categoryName: '水产' },
  { ingredientId: 25, ingredientName: '食用油', iconKey: 'oil', level: 'ENOUGH', categoryName: '调料' },
  { ingredientId: 24, ingredientName: '五花肉', iconKey: 'pork', level: 'NONE', categoryName: '肉禽蛋' },
]

export const DETAIL_LUYU = {
  dish: { ...DISHES[1], difficulty: 2, note: '蒸 8 分钟刚好，关火焖 2 分钟更嫩。', categoryNames: ['清淡'], tagNames: ['适合高血压', '少油'] },
  ingredients: [
    { ingredientId: 11, ingredientName: '鲈鱼', amount: 1, unitName: '条', iconKey: 'fish' },
    { ingredientId: 18, ingredientName: '姜', amount: 1, unitName: '块', iconKey: 'ginger' },
    { ingredientId: 19, ingredientName: '蒸鱼豉油', amount: 2, unitName: '勺', iconKey: 'sauce' },
    { ingredientId: 25, ingredientName: '食用油', amount: 1, unitName: '勺', iconKey: 'oil' },
    { ingredientId: 15, ingredientName: '小葱', amount: 3, unitName: '根', iconKey: 'scallion' },
  ],
  steps: [
    { text: '鲈鱼洗净，两面各划三刀，铺姜片腌 10 分钟。', images: IMG('step/qingzheng-luyu-2.jpg') },
    { text: '水开后上锅，大火蒸 8 分钟，关火焖 2 分钟。', images: IMG('step/qingzheng-luyu-5.jpg') },
    { text: '倒掉蒸出的汤汁，铺上葱丝。', images: IMG('step/qingzheng-luyu-7.jpg') },
    { text: '淋蒸鱼豉油，浇一勺热油激香。', images: IMG('step/qingzheng-luyu-9.jpg') },
  ],
}

export const MEMBERS = [
  { id: 1, name: '小王', audienceTags: [] },
  { id: 2, name: '林林', audienceTags: ['高血脂'] },
  { id: 3, name: '妈妈', audienceTags: ['高血压'] },
  { id: 4, name: '朵朵', audienceTags: ['宝宝辅食'] },
]

type Handler = (url: URL, method: string) => unknown | undefined

/** 统一 mock：handlers 按顺序匹配，返回 undefined 则交给下一个；都不匹配回 {}。 */
export async function installMocks(page: Page, extra: Handler[] = []) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'h5-token')
    localStorage.setItem('legal_consent', '2026-10')
  })
  await page.route('https://mock.gudu.test/**', async (route: Route) => {
    const p = resolve(IMG_ROOT, new URL(route.request().url()).pathname.replace(/^\/img\//, ''))
    if (!existsSync(p)) return route.fulfill({ status: 404 })
    const type = p.endsWith('.svg') ? 'image/svg+xml' : 'image/jpeg'
    await route.fulfill({ status: 200, contentType: type, body: readFileSync(p) })
  })
  const handlers: Handler[] = [...extra, ...BASE_HANDLERS]
  await page.route(/\/gudu\//, async (route: Route) => {
    const req = route.request()
    const url = new URL(req.url())
    let data: unknown = {}
    for (const h of handlers) {
      const r = h(url, req.method())
      if (r !== undefined) {
        data = r
        break
      }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'X-Trace-Id': '7f3a91c2e5d04b1a', 'Access-Control-Expose-Headers': 'X-Trace-Id' }, body: JSON.stringify({ code: 0, msg: 'ok', data }) })
  })
}

const page = <T>(records: T[]) => ({ records, total: records.length, current: 1, size: records.length })

export const BASE_HANDLERS: Handler[] = [
  (u) => (u.pathname.endsWith('/dish/search') ? page(DISHES) : undefined),
  (u) => (/\/dish\/2$/.test(u.pathname) ? DETAIL_LUYU : undefined),
  (u) => {
    const m = u.pathname.match(/\/dish\/(\d+)$/)
    if (!m) return undefined
    const d = DISHES.find((x) => x.id === Number(m[1])) || DISHES[0]
    return { dish: d, ingredients: DETAIL_LUYU.ingredients.slice(0, 3), steps: DETAIL_LUYU.steps.slice(0, 2) }
  },
  (u) => (u.pathname.endsWith('/dict') ? [] : undefined),
  (u) => (u.pathname.endsWith('/member') ? MEMBERS : undefined),
  (u) => (u.pathname.endsWith('/member/current') ? 1 : undefined),
  (u) => (u.pathname.endsWith('/member/me') ? { id: 1, name: '小王', isAdmin: true } : undefined),
  (u) => (u.pathname.endsWith('/me/stats') ? { cookedThisMonth: 21, familyDishes: 32, savedFromWaste: 4 } : undefined),
  (u) =>
    u.pathname.endsWith('/pantry/grouped')
      ? { summary: { enough: 24, low: 6, none: 3 }, items: u.searchParams.get('level') ? PANTRY.filter((p) => p.level === u.searchParams.get('level')) : PANTRY }
      : undefined,
  (u) =>
    u.pathname.endsWith('/pantry/summary')
      ? { enough: 24, low: 6, out: 3, expiring3d: 3, byCategory: [{ categoryId: 1, name: '蔬菜', count: 9 }, { categoryId: 2, name: '肉禽蛋', count: 8 }, { categoryId: 3, name: '水产', count: 3 }, { categoryId: 4, name: '调料', count: 13 }] }
      : undefined,
  (u) => (u.pathname.endsWith('/pantry/expiring') ? PANTRY.filter((p) => p.daysLeft != null && p.daysLeft <= 3) : undefined),
  (u) =>
    u.pathname.endsWith('/recommend/home')
      ? {
          expiring: PANTRY.filter((p) => p.daysLeft != null && p.daysLeft <= 3),
          expiringPicks: [dishCard(2), dishCard(9), dishCard(6)],
          frequent: [dishCard(1), dishCard(5), dishCard(7), dishCard(8)],
          seasonal: [dishCard(13), dishCard(14), dishCard(15)],
        }
      : undefined,
  (u) =>
    u.pathname.endsWith('/cookbook/recent')
      ? [{ dishId: 10, name: '土豆炖牛肉', coverImage: DISHES[9].coverUrl, lastCookedAt: '2026-10-15 18:40:00', cookedCount: 5 }]
      : undefined,
  (u) =>
    /\/cook\/dish\/\d+\/materials$/.test(u.pathname)
      ? DETAIL_LUYU.ingredients.map((i, k) => ({ ingredientId: i.ingredientId, ingredientName: i.ingredientName, iconKey: i.iconKey, level: k === 4 ? 'NONE' : 'ENOUGH', isCondiment: k === 2, usageTexts: [`${i.amount} ${i.unitName}`] }))
      : undefined,
  (u) => (u.pathname.endsWith('/notification/unread-count') ? { count: 2 } : undefined),
  (u) =>
    u.pathname.endsWith('/notification')
      ? [
          { id: 1, type: 'expiry', title: '鲈鱼明天到期', content: '今晚做「清蒸鲈鱼」刚好用掉，家里够 4/5 样', isRead: 0, createTime: '2026-10-16 08:00:00' },
          { id: 2, type: 'shopping', title: '采购清单还有 3 样没买', content: '西兰花、小葱、鸡蛋', isRead: 0, createTime: '2026-10-15 18:00:00' },
          { id: 3, type: 'feedback', title: '你的反馈已处理', content: '「放进余量没反应」已在 1.0.1 修复，感谢告诉我们。', isRead: 1, createTime: '2026-10-14 10:00:00' },
        ]
      : undefined,
  (u) =>
    u.pathname.endsWith('/menu')
      ? page([
          { id: 21, name: '今晚的饭', status: 'ACTIVE', servingCount: 4, dishCount: 4, createTime: '2026-10-16 18:30:00' },
          { id: 22, name: '周六 · 爸妈来吃饭', status: 'ACTIVE', servingCount: 6, dishCount: 3, createTime: '2026-10-19 18:00:00' },
          { id: 23, name: '朵朵一周辅食', status: 'ACTIVE', servingCount: 1, dishCount: 3, createTime: '2026-10-20 10:00:00' },
          { id: 24, name: '上周日家宴', status: 'DONE', servingCount: 5, dishCount: 4, createTime: '2026-10-12 18:00:00' },
        ])
      : undefined,
  (u) => {
    const m = u.pathname.match(/\/menu\/(\d+)$/)
    if (!m) return undefined
    const sets: Record<string, number[]> = { '21': [2, 1, 5, 8], '22': [12, 4, 7], '23': [11, 9, 1], '24': [10, 6, 3, 13] }
    const ids = sets[m[1]] || [2, 1]
    return {
      menu: { id: Number(m[1]), name: '今晚的饭', status: m[1] === '24' ? 'DONE' : 'ACTIVE', servingCount: 4, createTime: '2026-10-16 18:30:00' },
      totalMinutes: 45,
      dishes: ids.map((id, k) => {
        const d = DISHES.find((x) => x.id === id)!
        return { dishId: d.id, dishName: d.name, coverUrl: d.coverUrl, servingFactor: 1, addedByNickname: ['', '朵朵', '林林', '妈妈'][k] || '', note: k === 2 ? '少油' : k === 3 ? '少盐' : '' }
      }),
    }
  },
  (u) =>
    /\/menu\/\d+\/prep$/.test(u.pathname)
      ? {
          readyCount: 5,
          totalCount: 8,
          items: [
            { ingredientId: 17, ingredientName: '鸡蛋', iconKey: 'egg', stockLevel: 'LOW', usageTexts: ['5 个'], dishCount: 2, dishNames: ['滑蛋虾仁', '西红柿鸡蛋汤'], status: 'READY' },
            { ingredientId: 26, ingredientName: '蒜', iconKey: 'garlic', stockLevel: 'ENOUGH', usageTexts: ['6 瓣'], dishCount: 2, dishNames: ['蒜蓉西兰花', '清蒸鲈鱼'], status: 'PENDING' },
            { ingredientId: 22, ingredientName: '西兰花', iconKey: 'broccoli', stockLevel: 'NONE', usageTexts: ['1 颗'], dishCount: 1, dishNames: ['蒜蓉西兰花'], status: 'PENDING' },
            { ingredientId: 23, ingredientName: '虾仁', iconKey: 'shrimp', stockLevel: 'ENOUGH', usageTexts: ['1 盒'], dishCount: 1, dishNames: ['滑蛋虾仁'], status: 'THAWING' },
            { ingredientId: 11, ingredientName: '鲈鱼', iconKey: 'fish', stockLevel: 'ENOUGH', usageTexts: ['1 条'], dishCount: 1, dishNames: ['清蒸鲈鱼'], status: 'READY' },
            { ingredientId: 14, ingredientName: '番茄', iconKey: 'tomato', stockLevel: 'ENOUGH', usageTexts: ['2 个'], dishCount: 1, dishNames: ['西红柿鸡蛋汤'], status: 'READY' },
            { ingredientId: 15, ingredientName: '小葱', iconKey: 'scallion', stockLevel: 'NONE', usageTexts: ['3 根'], dishCount: 2, dishNames: ['滑蛋虾仁', '清蒸鲈鱼'], status: 'PENDING' },
          ],
          condiments: [{ ingredientId: 19, ingredientName: '蒸鱼豉油', iconKey: 'sauce', stockLevel: 'ENOUGH', usageTexts: ['2 勺'], dishCount: 1, dishNames: ['清蒸鲈鱼'], status: 'READY' }],
        }
      : undefined,
  (u) => (/\/menu\/\d+\/together-count$/.test(u.pathname) ? 2 : undefined),
  (u) =>
    /\/menu\/\d+\/together$/.test(u.pathname)
      ? {
          members: [{ nickname: '林林', lastActiveAt: '2026-10-16 17:00:00' }, { nickname: '妈妈', lastActiveAt: '2026-10-16 16:00:00' }],
          dishes: [],
          activities: [{ nickname: '林林', action: 'add', dishName: '蒜蓉西兰花', createTime: '2026-10-16 17:00:00' }],
          invite: { code: 'K7P2QM', token: 't' },
        }
      : undefined,
  (u) =>
    /\/food-log\/month/.test(u.pathname)
      ? {
          summary: { meals: 21, dishes: 38, cookDays: 18, topDishes: ['滑蛋虾仁', '西红柿鸡蛋汤', '蒜蓉西兰花'] },
          total: 3,
          records: [
            { menuId: 21, name: '今晚的饭', cookedAt: '2026-10-16 18:42:00', dishCount: 2, servingCount: 4, dishNames: ['清蒸鲈鱼', '滑蛋虾仁'], usedUpCount: 2, partialCount: 1 },
            { menuId: 20, name: '周二晚饭', cookedAt: '2026-10-14 18:30:00', dishCount: 2, servingCount: 4, dishNames: ['土豆炖牛肉', '蒜蓉西兰花'], usedUpCount: 1, reviewed: true },
            { menuId: 19, name: '朵朵的辅食', cookedAt: '2026-10-14 10:00:00', dishCount: 1, servingCount: 1, dishNames: ['牛奶南瓜羹'] },
          ],
        }
      : undefined,
  (u) =>
    /\/shopping\/\d+$/.test(u.pathname)
      ? (() => {
          const items = [
            { id: 1, ingredientName: '西兰花', iconKey: 'broccoli', purchaseAmount: 1, purchaseUnitName: '颗', stockStatus: 'RED_NONE', purchased: 0 },
            { id: 2, ingredientName: '小葱', iconKey: 'scallion', purchaseAmount: 3, purchaseUnitName: '根', stockStatus: 'RED_NONE', purchased: 0 },
            { id: 3, ingredientName: '鸡蛋', iconKey: 'egg', purchaseAmount: 2, purchaseUnitName: '个', stockStatus: 'YELLOW_SHORT', purchased: 0 },
            { id: 4, customName: '纯牛奶', iconKey: 'milk', purchaseAmount: 1, purchaseUnitName: '箱', purchased: 0 },
            { id: 5, customName: '抽纸', iconKey: 'tissue', purchaseAmount: 1, purchaseUnitName: '提', purchased: 1 },
          ]
          return { id: 7, name: '今晚的饭', items, grouped: { '1': items.slice(0, 2), '2': items.slice(2, 3), '9': items.slice(3) }, categoryNames: { '1': '蔬菜', '2': '肉禽蛋', '9': '其他' } }
        })()
      : undefined,
  (u) => (u.pathname.endsWith('/shopping') ? page([{ id: 7, name: '今晚的饭', sourceType: 'menu', createTime: '2026-10-16 10:00:00' }]) : undefined),
  (u) => (u.pathname.endsWith('/ingredient') ? page(PANTRY.map((p) => ({ id: p.ingredientId, name: p.ingredientName }))) : undefined),
]
