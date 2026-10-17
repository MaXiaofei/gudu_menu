<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <ui-tab-header title="我家余量" :sub="`我家的厨房 · ${totalCount} 样`">
      <view class="shop-btn" data-testid="go-shopping" @click="goShopping">
        <ui-icon name="cart" :size="16" />
        <text class="shop-txt">采购清单</text>
      </view>
    </ui-tab-header>

    <ui-offline-bar />
    <scroll-view class="body" scroll-y @scrolltolower="searching && loadMoreSearch()">
      <view class="pad">
        <ui-stat-grid :items="stats" @tap="onStatTap" />

        <view class="search-box">
          <ui-icon name="search" :size="16" />
          <input v-model="kwInput" class="search-ipt" placeholder="搜家里的食材" placeholder-class="ph" @input="onInput" />
          <text v-if="kwInput" class="search-clear" @click="clearKw">✕</text>
        </view>

        <view v-if="!searching" class="chips">
          <view class="chip" :class="{ on: filter === 'all' }" @click="filter = 'all'">全部 {{ totalCount }}</view>
          <view class="chip" :class="{ on: filter === 'none' }" @click="filter = 'none'">用完 {{ summary.none }}</view>
          <view class="chip" :class="{ on: filter === 'low' }" @click="filter = 'low'">不足 {{ summary.low }}</view>
          <view class="chip" :class="{ on: filter === 'enough' }" @click="filter = 'enough'">充足 {{ summary.enough }}</view>
        </view>
      </view>

      <ui-state v-if="loading && first" mode="loading" />

      <view v-else-if="searching" class="pad list">
        <view class="found">找到 {{ searchTotal }} 个</view>
        <view v-if="!searchItems.length" class="no-hit">家里没有「{{ searchingKw }}」</view>
        <stock-row v-for="it in searchItems" :key="it.ingredientId" :item="it" :sub="subText(it)" @open="goDetail(it)" @level="(v) => changeLevel(it, v)" />
        <ui-load-more v-if="searchItems.length && searchRemain > 0" :remain="searchRemain" :loading="searchLoadingMore" @more="loadMoreSearch" />
      </view>

      <view v-else class="pad list">
        <!-- 先吃这些（3 天内到期） -->
        <template v-if="filter === 'all' && expiring.length">
          <view class="sec-head"><text class="dot-red" /><text class="sec-title">先吃这些</text></view>
          <view class="eat-first" data-testid="eat-first">
            <view v-for="e in expiring" :key="e.ingredientId" class="eat-row" @click="goDetail(e)">
              <ui-ing-icon :icon-key="e.iconKey" :name="e.ingredientName" :size="40" />
              <view class="info">
                <text class="name">{{ e.ingredientName }}</text>
                <text class="sub">{{ e.bestBefore ? `${mdLabel(e.bestBefore)}到期` : '' }}</text>
              </view>
              <ui-expiry-badge :days-left="e.daysLeft" />
            </view>
            <view v-if="pick" class="pick" @click="goDish(pick.dishId)">
              <ui-dish-image :src="cardCover(pick)" :name="pick.name" :width="44" :height="44" :radius="10" />
              <view class="info">
                <text class="pick-name">今晚做「{{ pick.name }}」，刚好用掉</text>
                <text class="sub">{{ pickSub }}</text>
              </view>
              <ui-icon name="chevron" :size="16" />
            </view>
          </view>
        </template>

        <template v-for="sec in sections" :key="sec.key">
          <view v-if="items[sec.key].length" class="sec-head">
            <text class="sec-title">{{ sec.label }}</text>
            <text class="sec-count">{{ sec.total }}</text>
          </view>
          <stock-row v-for="it in items[sec.key]" :key="it.ingredientId" :item="it" :sub="subText(it)" @open="goDetail(it)" @level="(v) => changeLevel(it, v)" />
          <ui-load-more v-if="remain(sec.key) > 0" :remain="remain(sec.key)" :loading="loadingMore[sec.key]" @more="loadMore(sec.key)" />
        </template>
        <ui-state
          v-if="totalCount === 0"
          mode="empty"
          text="冰箱里还是空的"
          hint="记下家里有什么，智荐才知道先帮你用掉哪些"
          action-text="手动添加"
          :illus="['egg', 'tomato', 'milk']"
          @action="goAdd"
        />
      </view>
      <view style="height: 96px" />
    </scroll-view>

    <view class="fab" data-testid="fab-add" @click="goAdd">
      <ui-icon name="plus" tone="white" :size="18" />
      <text class="fab-txt">入库</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { listGroupedPage, listExpiring, pantrySummary, setPantryLevel, sourceLabel, sourceSub, type PantryGroupedItem, type ExpiringItem, type PantrySummary } from '@/api/pantry'
import { recommendHome } from '@/api/recommend'
import { cardCover, coverageText, type DishCard } from '@/api/dish'
import StockRow from './parts/StockRow.vue'
import { mdLabel } from '@/utils/freshness'

const kwInput = ref('')
const searchingKw = ref('')
const searching = computed(() => searchingKw.value.length > 0)

const filter = ref('all')
const summary = reactive({ enough: 0, low: 0, none: 0 })
const totalCount = computed(() => summary.enough + summary.low + summary.none)

// 三组定义（筛选态过滤显示）
const sections = computed(() => {
  const defs = [
    { key: 'NONE', tag: 'none', label: '用完了', total: summary.none },
    { key: 'LOW', tag: 'low', label: '不太够', total: summary.low },
    { key: 'ENOUGH', tag: 'enough', label: '充足', total: summary.enough },
  ]
  return defs.filter((d) => filter.value === 'all' || filter.value === d.tag)
})

// 三组独立分页
const items = reactive<Record<string, PantryGroupedItem[]>>({ NONE: [], LOW: [], ENOUGH: [] })
const page = reactive<Record<string, number>>({ NONE: 1, LOW: 1, ENOUGH: 1 })
const loadingMore = reactive<Record<string, boolean>>({ NONE: false, LOW: false, ENOUGH: false })

// 搜索分页
const searchItems = ref<PantryGroupedItem[]>([])
const searchPage = ref(1)
const searchTotal = ref(0)
const searchLoadingMore = ref(false)
const searchRemain = computed(() => searchTotal.value - searchItems.value.length)

const loading = ref(false)
const first = ref(true)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let reloadToken = 0

const expiring = ref<ExpiringItem[]>([])
const pick = ref<DishCard | null>(null)
const sum = ref<PantrySummary | null>(null)
const stats = computed(() => [
  { value: summary.enough, label: '充足', tone: 'success' as const },
  { value: summary.low, label: '不足', tone: 'warning' as const },
  { value: summary.none, label: '用完', tone: 'error' as const },
  { value: sum.value?.expiring3d ?? expiring.value.length, label: '3 天内到期', tone: 'dark' as const },
])
const pickSub = computed(() => {
  const p = pick.value
  if (!p) return ''
  return [coverageText(p.coverage), p.cookMinutes ? `${p.cookMinutes} 分钟` : ''].filter(Boolean).join(' · ')
})

/** 增强信息（临期、关联菜、统计）：失败不影响列表。 */
function loadExtras() {
  listExpiring(3).then((r) => (expiring.value = r)).catch(() => (expiring.value = []))
  pantrySummary().then((r) => (sum.value = r)).catch(() => {})
  recommendHome().then((h) => (pick.value = h.expiringPicks[0] || null)).catch(() => {})
}

function onStatTap(i: number) {
  filter.value = ['enough', 'low', 'none', 'all'][i]
}

async function changeLevel(it: PantryGroupedItem, level: string) {
  const prev = it.level
  it.level = level // 乐观更新
  try {
    await setPantryLevel(it.ingredientId, level)
    reload()
  } catch {
    it.level = prev
  }
}

function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}

onShow(() => {
  reload()
  loadExtras()
})
onPullDownRefresh(async () => {
  await reload()
  uni.stopPullDownRefresh()
})

function onInput() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => reload(), 300)
}

function clearKw() {
  if (debounceTimer) clearTimeout(debounceTimer)
  kwInput.value = ''
  reload()
}

async function reload() {
  const token = ++reloadToken
  loading.value = true
  searchingKw.value = kwInput.value.trim() // 先同步搜索词再分流
  try {
    if (searching.value) {
      const r = await listGroupedPage({ keyword: searchingKw.value, pageNum: 1 })
      if (token !== reloadToken) return
      applySummary(r.summary)
      searchItems.value = r.items
      searchPage.value = 1
      searchTotal.value = r.summary.enough + r.summary.low + r.summary.none
    } else {
      const results = await Promise.all([
        listGroupedPage({ level: 'NONE', pageNum: 1 }),
        listGroupedPage({ level: 'LOW', pageNum: 1 }),
        listGroupedPage({ level: 'ENOUGH', pageNum: 1 }),
      ])
      if (token !== reloadToken) return
      applySummary(results[0].summary)
      items.NONE = results[0].items
      items.LOW = results[1].items
      items.ENOUGH = results[2].items
      page.NONE = page.LOW = page.ENOUGH = 1
    }
  } catch {
    // request 已 toast
  }
  if (token === reloadToken) {
    loading.value = false
    first.value = false
  }
}

function applySummary(s: { enough: number; low: number; none: number }) {
  summary.enough = s.enough
  summary.low = s.low
  summary.none = s.none
}

function remain(level: string): number {
  const total = level === 'NONE' ? summary.none : level === 'LOW' ? summary.low : summary.enough
  return total - (items[level]?.length ?? 0)
}

async function loadMore(level: string) {
  if (loadingMore[level]) return
  loadingMore[level] = true
  try {
    const r = await listGroupedPage({ level, pageNum: page[level] + 1 })
    page[level] += 1
    items[level].push(...r.items)
  } catch {}
  loadingMore[level] = false
}

async function loadMoreSearch() {
  if (searchLoadingMore.value || searchRemain.value <= 0) return
  searchLoadingMore.value = true
  try {
    const r = await listGroupedPage({ keyword: searchingKw.value, pageNum: searchPage.value + 1 })
    searchPage.value += 1
    searchItems.value.push(...r.items)
  } catch {}
  searchLoadingMore.value = false
}

function subText(it: PantryGroupedItem): string {
  const label = sourceLabel(it)
  if (!label) return it.level === 'NONE' ? '本来就没有' : '无变动记录'
  return `${label} ${sourceSub(it)}`.trim()
}

function goDetail(it: { ingredientId: number }) {
  uni.navigateTo({ url: `/pages/pantry/Detail?id=${it.ingredientId}` })
}
function goAdd() {
  uni.navigateTo({ url: '/pages/pantry/Add' })
}
function goShopping() {
  uni.navigateTo({ url: '/pages/shopping/List' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.shop-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
}
.shop-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.search-box {
  margin-top: var(--space-md);
  height: 44px;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 var(--space-md);
}
.search-ipt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--caption);
}
.search-clear {
  color: var(--body);
  font-size: var(--fs-md);
  padding: 4px;
}
.chips {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-md) 0;
}
.chip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-sm);
}
.chip.on {
  background: var(--title);
  border-color: var(--title);
  color: var(--on-primary);
  font-weight: 700;
}
.list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.sec-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--space-md);
}
.sec-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.sec-count {
  font-size: var(--fs-sm);
  color: var(--body);
}
.dot-red {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--error);
}
.eat-first {
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--error-soft);
  overflow: hidden;
}
.eat-row,
.pick {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
}
.eat-row + .eat-row {
  border-top: 1px solid var(--border);
}
.pick {
  background: var(--highlight);
  border-top: 1px solid var(--border);
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.pick-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.found {
  font-size: var(--fs-sm);
  color: var(--body);
}
.no-hit {
  padding: var(--space-xl) 0;
  text-align: center;
  font-size: var(--fs-md);
  color: var(--body);
}
.fab {
  position: fixed;
  right: var(--space-lg);
  bottom: calc(var(--window-bottom, 0px) + var(--space-lg) + env(safe-area-inset-bottom));
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 6px 18px rgba(var(--sh), 0.35);
  z-index: 10;
}
.fab-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
</style>
