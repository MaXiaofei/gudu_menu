<template>
  <view class="page">
    <!-- 顶栏：食集名 + 状态胶囊 + 副信息（§13.2） -->
    <ui-back-header :title="detail?.menu.name" :subtitle="headSub">
      <template #action>
        <view v-if="detail" class="pill" :class="isDone ? 'done' : 'active'">
          {{ isDone ? '已完成' : '进行中' }}
        </view>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="empty" text="加载详情失败" />
    <template v-else>
      <!-- Tab 栏 -->
      <view class="tabs">
        <view class="tab" :class="{ on: tab === 0 }" @click="tab = 0">菜 · {{ detail.dishes.length }}</view>
        <view class="tab" :class="{ on: tab === 1 }" @click="tab = 1">备菜 · {{ prepTotalCount }}</view>
        <view class="tab" :class="{ on: tab === 2 }" @click="tab = 2">聚餐 · {{ togetherCnt }}</view>
      </view>

      <scroll-view scroll-y class="body">
        <!-- ======== 菜 Tab ======== -->
        <view v-if="tab === 0" class="pane">
          <view class="sec-label">包含菜品</view>
          <view v-for="d in detail.dishes" :key="d.dishId" class="dish-card">
            <view class="dish-main" @click="tapDish(d)">
              <image v-if="d.coverUrl" class="dish-cover" :src="thumbOf(d.coverUrl)" mode="aspectFill" lazy-load />
              <ui-avatar v-else :name="d.dishName || '菜'" :size="38" />
              <view class="dish-info">
                <view class="dish-name-row">
                  <text class="dish-name">{{ d.dishName || (d.dishId ? `菜 #${d.dishId}` : '自定义菜') }}</text>
                  <text v-if="d.addedBy" class="dish-added">{{ d.addedBy }} 点的</text>
                </view>
                <text class="dish-factor">× {{ fmtFactor(d.servingFactor) }} 份</text>
              </view>
              <text v-if="d.dishId" class="arrow">›</text>
            </view>
            <!-- 备注行 -->
            <view class="note-row" @click="editNote(d)">
              <text class="note-label">备注</text>
              <text v-if="d.note" class="note-chip">{{ d.note }}</text>
              <text v-else class="note-placeholder">加备注/忌口…</text>
              <text v-if="!isDone && d.dishId" class="note-edit">✎</text>
              <text v-if="!isDone && d.dishId" class="note-del" @click.stop="confirmRemoveDish(d)">✕</text>
            </view>
          </view>
          <!-- 加菜（未完成态） -->
          <view v-if="!isDone" class="add-dish" @click="goPicker">＋ 加菜（去菜谱找）</view>
        </view>

        <!-- ======== 备菜 Tab ======== -->
        <view v-if="tab === 1" class="pane">
          <ui-state v-if="prepLoading" mode="loading" />
          <template v-else-if="prep">
            <!-- 进度区 + 一键加采购 -->
            <view class="prep-head">
              <view v-if="!isDone" class="shop-btn" @click="openAddShopping">一键加采购</view>
            </view>
            <view class="prep-progress">
              <view class="prep-progress-row">
                <text class="prep-title">备料进度</text>
                <text class="prep-count">已备 {{ prep.readyCount }} / 共 {{ prep.totalCount }} 样</text>
              </view>
              <view class="bar"><view class="bar-fill" :style="{ width: barWidth }" /></view>
            </view>

            <view class="sec-label">备料清单</view>
            <view v-for="it in prep.items" :key="it.ingredientId" class="prep-row" @click="togglePrep(it)" @longpress="longPressPrep(it)">
              <view class="prep-main">
                <view class="prep-name-row">
                  <text class="prep-name" :class="{ ready: it.status === 'READY' }">{{ it.ingredientName }}</text>
                  <view v-if="(it.dishCount ?? 0) >= 2" class="prep-shared">{{ it.dishCount }} 道菜共用</view>
                </view>
                <view class="prep-sub">
                  <text class="stock-badge" :style="{ color: stockColor(it.stockLevel), background: stockColor(it.stockLevel) + '1A' }">
                    家里：{{ stockLabel(it.stockLevel) }}
                  </text>
                  <text v-if="usageText(it)" class="prep-usage">{{ usageText(it) }}</text>
                </view>
              </view>
              <view class="prep-chip" :style="prepChipStyle(it.status)">{{ prepLabel(it.status) }}</view>
            </view>

            <!-- 调料折叠 -->
            <view v-if="prep.condiments.length" class="condiment-head" @click="condimentOpen = !condimentOpen">
              <text class="sec-label condiment-label">调料 {{ prep.condiments.length }} 样</text>
              <text class="condiment-toggle">{{ condimentOpen ? '收起 ▴' : '展开 ▾' }}</text>
            </view>
            <template v-if="condimentOpen">
              <view v-for="it in prep.condiments" :key="it.ingredientId" class="prep-row" @click="togglePrep(it)" @longpress="longPressPrep(it)">
                <view class="prep-main">
                  <view class="prep-name-row">
                    <text class="prep-name" :class="{ ready: it.status === 'READY' }">{{ it.ingredientName }}</text>
                  </view>
                  <view class="prep-sub">
                    <text class="stock-badge" :style="{ color: stockColor(it.stockLevel), background: stockColor(it.stockLevel) + '1A' }">
                      家里：{{ stockLabel(it.stockLevel) }}
                    </text>
                  </view>
                </view>
                <view class="prep-chip" :style="prepChipStyle(it.status)">{{ prepLabel(it.status) }}</view>
              </view>
            </template>
            <view style="height: 24px" />
          </template>
          <ui-state v-else mode="empty" text="加载备菜失败" />
        </view>

        <!-- ======== 聚餐 Tab ======== -->
        <view v-if="tab === 2" class="pane">
          <ui-state v-if="togetherLoading" mode="loading" />
          <template v-else-if="together">
            <!-- 邀请卡（完成态隐藏） -->
            <view v-if="!isDone" class="invite-card">
              <template v-if="invite">
                <view class="invite-info">
                  <text class="invite-title">邀请朋友 · 口令 {{ invite.code }}</text>
                  <text class="invite-sub">点分享发给微信好友，或把口令发给他</text>
                </view>
                <view class="invite-ops">
                  <view class="invite-op" @click="copyCode"><text class="invite-op-txt">复制口令</text></view>
                  <button class="invite-op share-btn" open-type="share"><text class="invite-op-txt">分享链接</text></button>
                </view>
              </template>
              <template v-else>
                <text class="invite-title">邀请朋友一起点菜</text>
                <text class="invite-sub">把清单发给朋友，大家各自加想吃的菜</text>
                <view class="invite-create" @click="doCreateInvite">
                  <text class="invite-create-txt">{{ inviting ? '生成中…' : '生成邀请' }}</text>
                </view>
              </template>
            </view>

            <!-- 成员区 -->
            <text class="sec-label">成员 · {{ together.members.length }}</text>
            <view v-if="!together.members.length" class="together-empty">还没有人加入，先邀请朋友吧</view>
            <view v-else class="member-chips">
              <view v-for="(m, i) in together.members" :key="i" class="member-chip">
                <view class="member-avatar"><text class="member-avatar-txt">{{ (m.nickname || '友')[0] }}</text></view>
                <text class="member-name">{{ m.nickname || '朋友' }}</text>
                <text class="member-time">{{ relativeTime(m.lastActiveAt) }}</text>
              </view>
            </view>

            <!-- 动态区 -->
            <text class="sec-label">动态</text>
            <view v-if="!together.activities.length" class="together-empty">暂无动态</view>
            <view v-else class="act-list">
              <view v-for="(a, i) in together.activities" :key="i" class="act-row">
                <text class="act-txt">{{ a.nickname || '朋友' }} {{ a.action === 'remove' ? '删了' : '点了' }}「{{ a.dishName }}」</text>
                <text class="act-time">{{ relativeTime(a.createTime) }}</text>
              </view>
            </view>

            <text class="together-note">朋友点分享进来就能加入，加菜直接进菜 Tab（标「XX 点的」），谁都能删，会记下谁删的。清单每 10 秒自动刷新。</text>
          </template>
          <ui-state v-else mode="error" text="加载聚餐失败" :retry="true" @retry="loadTogether" />
        </view>

        <view style="height: 88px" />
      </scroll-view>

      <!-- 底部操作（仅菜 Tab） -->
      <view v-if="tab === 0" class="bottom">
        <button v-if="!isDone" class="btn-cook" :disabled="cooking" @click="startCook">
          {{ cooking ? '处理中…' : '开始做饭' }}
        </button>
        <template v-else>
          <button class="btn-cook done" disabled>已完成</button>
          <button class="btn-review" @click="goReview">去评价</button>
        </template>
      </view>
    </template>

    <!-- 备注编辑弹窗 -->
    <view v-if="noteDialog" class="mask" @click="noteDialog = false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">菜备注</text>
        <input
          v-model="noteInput"
          class="dialog-ipt"
          maxlength="255"
          placeholder="如：宝宝那份少盐"
          placeholder-class="ph"
          :focus="noteDialog"
          @confirm="confirmNote"
        />
        <view class="dialog-actions">
          <text class="dialog-btn" @click="noteDialog = false">取消</text>
          <text class="dialog-btn primary" @click="confirmNote">确定</text>
        </view>
      </view>
    </view>

    <!-- 一键加采购弹层 -->
    <view v-if="shopSheet" class="mask" @click="shopSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">加入采购清单</text>
          <text class="sheet-sub">默认勾选家里没有/不足的，可改</text>
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view v-for="it in allPrepItems" :key="it.ingredientId" class="shop-row" @click="toggleShopSel(it.ingredientId)">
            <view class="checkbox" :class="{ on: shopSelected.has(it.ingredientId) }">
              <text v-if="shopSelected.has(it.ingredientId)" class="checkbox-txt">✓</text>
            </view>
            <text class="shop-name">{{ it.ingredientName }}</text>
            <text class="stock-badge" :style="{ color: stockColor(it.stockLevel) }">家里：{{ stockLabel(it.stockLevel) }}</text>
          </view>
        </scroll-view>
        <view class="sheet-bottom">
          <button class="btn-primary" :disabled="shopSelected.size === 0 || shopping" @click="confirmAddShopping">
            {{ shopping ? '加入中…' : `加入采购清单（${shopSelected.size}）` }}
          </button>
        </view>
      </view>
    </view>

    <!-- 做菜确认弹层（确认 → 结果 两态） -->
    <view v-if="cookSheet" class="mask mask-top" @click="cancelCook">
      <view class="sheet cook-sheet" @click.stop>
        <!-- 确认层 -->
        <template v-if="cookResult === null">
          <view class="drag-bar" />
          <text class="cook-title">这顿饭用了什么</text>
          <text class="cook-sub">{{ cookSubText }}</text>
          <view class="cook-tip">每项选一个状态，库存会自动更新</view>
          <scroll-view scroll-y class="cook-list">
            <view v-for="m in cookItems" :key="m.ingredientId" class="cook-row">
              <view class="cook-row-main">
                <text class="cook-name">{{ m.ingredientName }}</text>
                <text class="cook-meta">家里：{{ stockLabel(m.level) }}<template v-if="cookUsage(m)"> · {{ cookUsage(m) }}</template></text>
              </view>
              <view class="cook-chips">
                <view
                  v-for="opt in cookOptions"
                  :key="opt.value"
                  class="cook-chip"
                  :class="{ on: cookSel[m.ingredientId] === opt.value }"
                  :style="cookSel[m.ingredientId] === opt.value ? `background:${opt.color};border-color:${opt.color}` : ''"
                  @click="cookSel[m.ingredientId] = opt.value"
                >{{ opt.label }}</view>
              </view>
            </view>
          </scroll-view>
          <view class="cook-note">
            食材默认「用完了」，调料默认「用了一些」（降一档）。点一下就能改。没有库存的食材也不拦着你做饭。
          </view>
          <view class="cook-actions">
            <button class="btn-ghost cook-skip" @click="skipCook">跳过，不更新库存</button>
            <button class="btn-primary cook-ok" @click="confirmCook">确认已做完 · {{ usedUpCount }} 样用完</button>
          </view>
        </template>
        <!-- 结果层 -->
        <template v-else>
          <view class="result-head">
            <view class="result-badge"><text class="result-badge-txt">✓</text></view>
            <text class="result-title">做好了，库存已更新</text>
            <text class="result-sub">食集 → <text class="result-done">已完成</text></text>
          </view>
          <view class="result-box">
            <text class="result-box-title">库存已更新</text>
            <view v-for="m in cookedUsedUp" :key="'u' + m.ingredientId" class="result-row">
              <text class="result-name">{{ m.ingredientName }}</text>
              <text class="result-pill red">用完</text>
            </view>
            <view v-for="m in cookedPartial" :key="'p' + m.ingredientId" class="result-row">
              <text class="result-name">{{ m.ingredientName }}</text>
              <text class="result-pill yellow">用了一些</text>
            </view>
            <text v-if="!cookedUsedUp.length && !cookedPartial.length" class="result-none">本次没有更新库存</text>
          </view>
          <view class="result-review" @click="goReview">
            <text class="result-review-txt">这顿饭的菜 · 吃完别忘了评价</text>
            <text class="result-review-btn">去评价 ›</text>
          </view>
          <view class="cook-actions">
            <button class="btn-primary" @click="closeCookResult">返回食集</button>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { onLoad, onShow, onHide, onShareAppMessage } from '@dcloudio/uni-app'
import {
  menuDetail, updateDishNote, removeDishFromMenu, togetherCount,
  cookMaterials, cookMenu, shoppingFromPrep,
  type MenuDetail, type MenuDish, type CookMaterial,
} from '@/api/menu'
import { getPrep, updatePrepStatus, type MenuPrep, type PrepItem } from '@/api/prep'
import { getTogether, createInvite, type TogetherVO } from '@/api/together'
import { thumbOf } from '@/utils/image'
import { relativeDate } from '@/utils/datetime'
import { stockColor, stockLabel, prepLabel, T } from '@/utils/token'

const menuId = ref(0)
const loading = ref(true)
const detail = ref<MenuDetail | null>(null)
const tab = ref(0)
const togetherCnt = ref(0)

// 备菜
const prep = ref<MenuPrep | null>(null)
const prepLoading = ref(false)
const condimentOpen = ref(false)

// 备注
const noteDialog = ref(false)
const noteInput = ref('')
let noteDish: MenuDish | null = null

// 一键加采购
const shopSheet = ref(false)
const shopSelected = reactive(new Set<number>())
const shopping = ref(false)

// 做菜确认
const cooking = ref(false)
const cookSheet = ref(false)
const cookItems = ref<CookMaterial[]>([])
const cookSel = reactive<Record<number, number>>({})
const cookResult = ref<{ usedUp: number[]; partiallyUsed: number[] } | null>(null)

// ---- 聚餐（一起吃） ----
const together = ref<TogetherVO | null>(null)
const togetherLoading = ref(false)
const invite = ref<{ code: string; token: string } | null>(null)
const inviting = ref(false)
let togetherTimer: ReturnType<typeof setInterval> | null = null

/** 分享给微信好友：落地页带免登录 token（小程序码/分享卡片方案）。 */
onShareAppMessage(() => {
  if (invite.value) {
    return {
      title: `一起来点菜：${detail.value?.menu.name || '聚餐清单'}`,
      path: `/pages/together/Pick?menuId=${menuId.value}&token=${invite.value.token}`,
    }
  }
  return { title: '咕嘟小食单', path: '/pages/dish/List' }
})

const cookOptions = [
  { value: 0, label: '用完了', color: T.error },
  { value: 1, label: '用了一些', color: T.warning },
  { value: 2, label: '这次没用', color: T.caption },
]

onLoad((options) => {
  menuId.value = Number(options?.id || 0)
  load()
})

// 从选菜页返回刷新
onShow(() => {
  if (menuId.value && detail.value) load(false)
})

async function load(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    detail.value = await menuDetail(menuId.value)
    loadPrep()
    togetherCount(menuId.value).then((n) => (togetherCnt.value = n)).catch(() => {})
  } catch {
    detail.value = null
  }
  loading.value = false
}

async function loadPrep() {
  prepLoading.value = true
  try {
    prep.value = await getPrep(menuId.value)
  } catch {
    prep.value = null
  }
  prepLoading.value = false
}

const isDone = computed(() => detail.value?.menu.status === 'DONE')
const headSub = computed(() => {
  const m = detail.value?.menu
  if (!m) return undefined
  return `${relativeDate(m.createTime)} · 份数 ${m.servingCount ?? 1} · 关联 ${detail.value!.dishes.length} 道菜 · 约 ${detail.value!.totalMinutes ?? 0} 分钟`
})
const prepTotalCount = computed(() => prep.value?.totalCount ?? 0)
const barWidth = computed(() => {
  const p = prep.value
  if (!p || !p.totalCount) return '0%'
  return `${Math.round((p.readyCount / p.totalCount) * 100)}%`
})
const allPrepItems = computed(() => [
  ...(prep.value?.items ?? []),
  ...(prep.value?.condiments ?? []),
])
const cookSubText = computed(() => {
  const names = cookItems.value.slice(0, 3).map((m) => m.ingredientName)
  const n = cookItems.value.length
  if (!n) return ''
  return names.length < n ? `${names.join(' + ')} 等 ${n} 样` : names.join(' + ')
})
const usedUpCount = computed(() =>
  Object.values(cookSel).filter((v) => v === 0).length,
)
const cookedUsedUp = computed(() => cookItems.value.filter((m) => cookSel[m.ingredientId] === 0))
const cookedPartial = computed(() => cookItems.value.filter((m) => cookSel[m.ingredientId] === 1))

function fmtFactor(f?: number | null): string {
  const v = f ?? 1
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}
function usageText(it: PrepItem): string {
  return (it.usageTexts ?? []).join(' + ')
}
function cookUsage(m: CookMaterial): string {
  return (m.usageTexts ?? []).join(' + ')
}
function prepChipStyle(status: string): Record<string, string> {
  const color = status === 'READY' ? T.success : status === 'THAWING' ? T.info : status === 'MARINATING' ? T.warning : T.caption
  return { color, borderColor: color, background: 'transparent' }
}

// ---- 菜 Tab ----
function tapDish(d: MenuDish) {
  if (isDone.value || !d.dishId) return
  uni.navigateTo({ url: `/pages/dish/Detail?id=${d.dishId}&showActions=0` })
}
function goPicker() {
  uni.navigateTo({ url: `/pages/dish/List?selectForMenuId=${menuId.value}` })
}
function goReview() {
  uni.navigateTo({ url: `/pages/review/MenuReview?id=${menuId.value}` })
}

function editNote(d: MenuDish) {
  if (isDone.value || !d.dishId) return
  noteDish = d
  noteInput.value = d.note || ''
  noteDialog.value = true
}
async function confirmNote() {
  if (!noteDish) return
  try {
    await updateDishNote(menuId.value, noteDish.dishId, noteInput.value)
    noteDialog.value = false
    load(false)
  } catch {
    uni.showToast({ title: '备注更新失败', icon: 'none' })
  }
}

function confirmRemoveDish(d: MenuDish) {
  uni.showModal({
    title: '移出食集',
    content: `确认将「${d.dishName || '这道菜'}」移出食集？`,
    confirmText: '移出',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm || !d.dishId) return
      try {
        await removeDishFromMenu(menuId.value, d.dishId)
        load(false)
      } catch {
        uni.showToast({ title: '移出失败', icon: 'none' })
      }
    },
  })
}

// ---- 备菜 Tab ----
async function togglePrep(it: PrepItem) {
  if (isDone.value) return
  const next = it.status === 'READY' ? 'PENDING' : 'READY'
  await updatePrep(it, next)
}

function longPressPrep(it: PrepItem) {
  if (isDone.value) return
  uni.showActionSheet({
    itemList: ['化冻中', '腌制中', '重置为待备'],
    success: async ({ tapIndex }) => {
      const next = ['THAWING', 'MARINATING', 'PENDING'][tapIndex]
      if (next && next !== it.status) await updatePrep(it, next)
    },
  })
}

/** 乐观更新 + 失败回滚（对齐 APP）。 */
async function updatePrep(it: PrepItem, next: string) {
  const prev = prep.value
  const rebuilt = (): MenuPrep | null => {
    if (!prev) return null
    const map = (list: PrepItem[]) =>
      list.map((x) => (x.ingredientId === it.ingredientId ? { ...x, status: next } : x))
    const items = map(prev.items)
    const condiments = map(prev.condiments)
    const readyCount = [...items, ...condiments].filter((x) => x.status === 'READY').length
    return { items, condiments, readyCount, totalCount: prev.totalCount }
  }
  prep.value = rebuilt()
  try {
    await updatePrepStatus(menuId.value, it.ingredientId, next)
  } catch {
    prep.value = prev
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

// ---- 一键加采购 ----
function openAddShopping() {
  const items = allPrepItems.value
  if (!items.length) {
    uni.showToast({ title: '没有可加入的备菜', icon: 'none' })
    return
  }
  shopSelected.clear()
  items.forEach((it) => {
    if (it.stockLevel === 'NONE' || it.stockLevel === 'LOW') shopSelected.add(it.ingredientId)
  })
  shopSheet.value = true
}
function toggleShopSel(id: number) {
  shopSelected.has(id) ? shopSelected.delete(id) : shopSelected.add(id)
}
async function confirmAddShopping() {
  shopping.value = true
  try {
    const listId = await shoppingFromPrep(menuId.value, [...shopSelected])
    shopSheet.value = false
    uni.showToast({ title: '已加入采购清单', icon: 'none' })
    // 采购页阶段 4 落地后跳转 /pages/shopping/List?listId=
    uni.navigateTo({ url: `/pages/shopping/List?listId=${listId}` })
  } catch {
    uni.showToast({ title: '加入采购清单失败', icon: 'none' })
  } finally {
    shopping.value = false
  }
}

// ---- 做菜确认 ----
async function startCook() {
  if (cooking.value) return
  cooking.value = true
  try {
    cookItems.value = await cookMaterials(menuId.value)
    cookResult.value = null
    Object.keys(cookSel).forEach((k) => delete cookSel[Number(k)])
    // 食材默认「用完了」，调料默认「用了一些」
    cookItems.value.forEach((m) => {
      cookSel[m.ingredientId] = m.isCondiment ? 1 : 0
    })
    cookSheet.value = true
  } catch {
    uni.showToast({ title: '做菜失败', icon: 'none' })
  } finally {
    cooking.value = false
  }
}
function cancelCook() {
  if (cookResult.value !== null) return // 结果层不通过遮罩关
  cookSheet.value = false
}
async function confirmCook() {
  const usedUp: number[] = []
  const partiallyUsed: number[] = []
  cookItems.value.forEach((m) => {
    const v = cookSel[m.ingredientId]
    if (v === 0) usedUp.push(m.ingredientId)
    else if (v === 1) partiallyUsed.push(m.ingredientId)
  })
  try {
    await cookMenu(menuId.value, usedUp, partiallyUsed)
    cookResult.value = { usedUp, partiallyUsed }
  } catch {
    uni.showToast({ title: '做菜失败', icon: 'none' })
  }
}
async function skipCook() {
  try {
    await cookMenu(menuId.value, [], [])
    cookResult.value = { usedUp: [], partiallyUsed: [] }
  } catch {
    uni.showToast({ title: '做菜失败', icon: 'none' })
  }
}
function closeCookResult() {
  cookSheet.value = false
  load(false)
}

// ---- 聚餐逻辑 ----
async function loadTogether() {
  try {
    const r = await getTogether(menuId.value)
    together.value = r
    // 已有邀请：本地持有（优先于轮询返回值）
    if (!invite.value && r.invite?.token) invite.value = { code: r.invite.code || '', token: r.invite.token }
  } catch {
    together.value = null
    stopPolling()
  }
}

watch(tab, (t) => {
  if (t === 2) {
    togetherLoading.value = !together.value
    loadTogether().finally(() => (togetherLoading.value = false))
    startPolling()
  } else {
    stopPolling()
  }
})

function startPolling() {
  if (togetherTimer) return
  togetherTimer = setInterval(() => loadTogether(), 10000)
}
function stopPolling() {
  if (togetherTimer) {
    clearInterval(togetherTimer)
    togetherTimer = null
  }
}
onHide(stopPolling)
onShow(() => {
  if (tab.value === 2) startPolling()
})

async function doCreateInvite() {
  if (inviting.value) return
  inviting.value = true
  try {
    const r = await createInvite(menuId.value)
    invite.value = r
    uni.showToast({ title: `邀请已生成，口令 ${r.code}`, icon: 'none' })
  } catch {
    uni.showToast({ title: '生成邀请失败', icon: 'none' })
  } finally {
    inviting.value = false
  }
}

function copyCode() {
  if (!invite.value) return
  uni.setClipboardData({
    data: `聚餐口令：${invite.value.code}（打开小程序，输入口令加入）`,
    success: () => uni.showToast({ title: '口令已复制', icon: 'none' }),
  })
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / M/D。 */
function relativeTime(s?: string | null): string {
  if (!s) return ''
  const d = new Date(s.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.pill {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  font-size: 10px;
  font-weight: 700;
  color: #FFFFFF;
}
.pill.active { background: var(--warning); }
.pill.done { background: var(--success); }

/* Tab 栏 */
.tabs {
  display: flex;
  background: var(--card);
  border-bottom: 1px solid var(--border);
}
.tab {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  font-size: 14px;
  color: var(--body);
  border-bottom: 2px solid transparent;
}
.tab.on {
  color: var(--primary);
  font-weight: 700;
  border-bottom-color: var(--primary);
}
.body {
  flex: 1;
  min-height: 0;
}
.pane {
  padding: 0 16px;
}

/* 菜 Tab */
.sec-label {
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 12px 2px 8px;
  display: block;
}
.dish-card {
  background: var(--card);
  border-radius: var(--r-md);
  margin-bottom: 8px;
  overflow: hidden;
}
.dish-main {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}
.dish-cover {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
  background: var(--secondary);
}
.dish-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.dish-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dish-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-added {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--secondary);
  color: var(--primary-deep);
  font-size: 9px;
}
.dish-factor {
  font-size: 11px;
  color: var(--caption);
}
.arrow {
  color: var(--caption);
  font-size: 16px;
  font-weight: 700;
}
.note-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px dashed var(--border);
}
.note-label {
  font-size: 10px;
  color: var(--caption);
  flex-shrink: 0;
}
.note-chip {
  flex: 1;
  min-width: 0;
  background: var(--highlight);
  border-radius: var(--r-sm);
  padding: 3px 8px;
  font-size: 11px;
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.note-placeholder {
  flex: 1;
  font-size: 11px;
  color: var(--caption);
}
.note-edit {
  color: var(--caption);
  font-size: 13px;
  padding: 0 2px;
}
.note-del {
  color: var(--caption);
  font-size: 12px;
  padding: 0 2px;
}
.add-dish {
  margin-top: 8px;
  border: 1.5px solid var(--primary);
  border-radius: var(--r-md);
  padding: 13px 0;
  text-align: center;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
}

/* 备菜 Tab */
.prep-head {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.shop-btn {
  padding: 5px 12px;
  border-radius: var(--r-pill);
  background: var(--primary);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 800;
}
.prep-progress {
  background: var(--card);
  border-radius: var(--r-md);
  padding: 12px;
  margin-top: 8px;
}
.prep-progress-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}
.prep-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
}
.prep-count {
  font-size: 12px;
  color: var(--caption);
}
.bar {
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--bg);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: var(--r-pill);
  background: var(--success);
  transition: width 0.2s;
}
.prep-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 6px;
}
.prep-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.prep-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.prep-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.prep-name.ready {
  color: var(--caption);
  text-decoration: line-through;
}
.prep-shared {
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--secondary);
  color: var(--primary-deep);
  font-size: 9px;
}
.prep-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stock-badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: var(--r-sm);
  font-size: 9px;
  font-weight: 700;
  background: var(--bg);
}
.prep-usage {
  font-size: 10px;
  color: var(--caption);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prep-chip {
  flex-shrink: 0;
  border: 1px solid;
  border-radius: var(--r-pill);
  padding: 4px 10px;
  font-size: 10px;
  font-weight: 700;
  background: var(--card);
}
.condiment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.condiment-label {
  margin: 0;
}
.condiment-toggle {
  font-size: 11px;
  color: var(--primary);
}

/* 底部操作 */
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  display: flex;
  gap: 10px;
}
.btn-cook {
  flex: 1;
  background: var(--success);
  color: #FFFFFF;
  border-radius: var(--r-md);
  font-size: 15px;
  font-weight: 700;
  padding: 13px 0;
}
.btn-cook.done {
  flex: 1;
  background: var(--success);
  opacity: 0.6;
}
.btn-review {
  flex: 1;
  background: var(--card);
  color: var(--warning);
  border: 1px solid var(--warning);
  border-radius: var(--r-md);
  font-size: 15px;
  font-weight: 700;
  padding: 12px 0;
}

/* 弹层通用 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.mask-top { align-items: stretch; }
.sheet {
  width: 100%;
  height: 85vh;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  display: flex;
  flex-direction: column;
  padding-top: 8px;
}
.drag-bar {
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: var(--border);
  margin: 0 auto 10px;
}
.sheet-head {
  padding: 0 18px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sheet-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
}
.sheet-sub {
  font-size: 12px;
  color: var(--caption);
}
.sheet-list {
  flex: 1;
  min-height: 0;
}
.sheet-bottom {
  padding: 10px 18px calc(12px + env(safe-area-inset-bottom));
}
.shop-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
}
.checkbox {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 1.5px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.checkbox.on {
  background: var(--success);
  border-color: var(--success);
}
.checkbox-txt {
  color: #FFFFFF;
  font-size: 12px;
  font-weight: 800;
}
.shop-name {
  flex: 1;
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}

/* 做菜确认 */
.cook-sheet {
  height: 85vh;
}
.cook-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--title);
  text-align: center;
}
.cook-sub {
  font-size: 12px;
  color: var(--caption);
  text-align: center;
  margin-top: 2px;
}
.cook-tip {
  font-size: 11px;
  color: var(--caption);
  margin: 10px 18px 0;
}
.cook-list {
  flex: 1;
  min-height: 0;
  margin-top: 6px;
}
.cook-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
}
.cook-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cook-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.cook-meta {
  font-size: 10px;
  color: var(--caption);
}
.cook-chips {
  display: flex;
  gap: 5px;
  flex-shrink: 0;
}
.cook-chip {
  padding: 4px 8px;
  border-radius: var(--r-pill);
  border: 1px solid var(--border);
  font-size: 10px;
  color: var(--body);
}
.cook-chip.on {
  color: #FFFFFF;
  font-weight: 700;
}
.cook-note {
  margin: 8px 18px 0;
  padding: 10px;
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  font-size: 11px;
  color: var(--body);
  line-height: 1.5;
}
.cook-actions {
  display: flex;
  gap: 10px;
  padding: 10px 18px calc(14px + env(safe-area-inset-bottom));
}
.cook-skip { flex: 1; }
.cook-ok { flex: 2; }
/* 结果层 */
.result-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0 12px;
  gap: 6px;
}
.result-badge {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--success);
  display: flex;
  align-items: center;
  justify-content: center;
}
.result-badge-txt {
  color: #FFFFFF;
  font-size: 28px;
  font-weight: 800;
}
.result-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--title);
}
.result-sub {
  font-size: 12px;
  color: var(--caption);
}
.result-done {
  color: var(--success);
  font-weight: 700;
}
.result-box {
  margin: 0 18px;
  background: var(--bg);
  border-radius: var(--r-sm);
  padding: 10px 14px;
}
.result-box-title {
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
}
.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}
.result-name {
  font-size: 13px;
  color: var(--title);
}
.result-pill {
  padding: 1px 8px;
  border-radius: var(--r-pill);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 700;
}
.result-pill.red { background: var(--error); }
.result-pill.yellow { background: var(--warning); }
.result-none {
  display: block;
  padding: 6px 0;
  font-size: 12px;
  color: var(--caption);
}
.result-review {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 18px 0;
  padding: 10px 14px;
  background: var(--bg);
  border-radius: var(--r-sm);
}
.result-review-txt {
  font-size: 12px;
  color: var(--body);
}
.result-review-btn {
  font-size: 12px;
  color: var(--primary);
  font-weight: 700;
}

/* 弹窗 */
.dialog {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 78vw;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 20px 18px 12px;
  z-index: 110;
}
.dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
}
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: 14px;
  color: var(--title);
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
}
.dialog-btn {
  font-size: 14px;
  color: var(--caption);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary);
  font-weight: 700;
}
.ph {
  color: var(--caption);
}


/* 聚餐 Tab */
.invite-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 14px;
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.invite-info { display: flex; flex-direction: column; gap: 3px; }
.invite-title { font-size: 14px; font-weight: 800; color: var(--title); }
.invite-sub { font-size: 11px; color: var(--caption); }
.invite-ops { display: flex; gap: 10px; }
.invite-op {
  flex: 1;
  background: var(--primary);
  border-radius: var(--r-pill);
  padding: 8px 0;
  text-align: center;
}
.share-btn { line-height: normal; }
.invite-op-txt { color: #FFFFFF; font-size: 12px; font-weight: 700; }
.invite-create {
  align-self: center;
  background: var(--primary);
  border-radius: var(--r-pill);
  padding: 8px 28px;
  margin-top: 4px;
}
.invite-create-txt { color: #FFFFFF; font-size: 13px; font-weight: 700; }
.together-empty {
  background: var(--card);
  border-radius: var(--r-sm);
  padding: 14px;
  font-size: 11px;
  color: var(--caption);
  text-align: center;
}
.member-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.member-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-pill);
  padding: 5px 12px 5px 5px;
}
.member-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.member-avatar-txt { font-size: 12px; font-weight: 700; color: var(--primary-deep); }
.member-name { font-size: 12px; font-weight: 700; color: var(--title); }
.member-time { font-size: 9px; color: var(--caption); }
.act-list { background: var(--card); border-radius: var(--r-sm); padding: 4px 12px; }
.act-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
}
.act-row + .act-row { border-top: 1px solid var(--bg); }
.act-txt { font-size: 11px; color: var(--body); }
.act-time { font-size: 9px; color: var(--caption); }
.together-note {
  display: block;
  margin-top: 14px;
  font-size: 10px;
  color: var(--caption);
  line-height: 1.6;
}

</style>
