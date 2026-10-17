<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <!-- 顶栏：返回 + 时间胶囊（含步进）+ 月|年切换 -->
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <view class="time-group">
        <text class="step" @click="stepBy(-1)">‹</text>
        <ui-time-select :value="current" granularity="month" @change="onPick" />
        <text class="step" :class="{ dim: false }" @click="stepBy(1)">›</text>
      </view>
      <view class="seg">
        <view class="seg-item" :class="{ on: !yearMode }" @click="setYearMode(false)">月</view>
        <view class="seg-item" :class="{ on: yearMode }" @click="setYearMode(true)">年</view>
      </view>
    </view>

    <!-- 统计（prototype CookLog） -->
    <view class="stats">
      <ui-stat-grid :items="statItems" />
      <view v-if="topDishes.length" class="top-line">
        <ui-thumb-stack :items="topDishes.map((n) => ({ name: n, cover: coverByName[n] }))" :size="28" />
        <text class="top-txt">{{ yearMode ? '全年' : '本月' }}最常做：{{ topDishes.join(' · ') }}</text>
      </view>
    </view>

    <view class="tabs-wrap">
      <ui-segment :model-value="tab" :items="[{ key: 'timeline', label: '时间轴' }, { key: 'byDish', label: '按菜汇总' }]" @update:model-value="onTab" />
    </view>

    <!-- 时间轴 -->
    <scroll-view v-if="tab === 'timeline'" scroll-y class="body" @scrolltolower="loadMore">
      <view
        v-for="r in records"
        :key="r.menuId ?? r.cookedAt ?? undefined"
        class="tl-row"
        @click="goDetail(r)"
      >
        <view class="tl-dot" />
        <view class="tl-main">
          <ui-dish-collage
            v-if="(r.dishNames ?? []).some((n) => coverByName[n])"
            class="tl-cover"
            :items="(r.dishNames ?? []).map((n) => ({ name: n, cover: coverByName[n] }))"
            :height="120"
            :radius="12"
          />
          <view class="tl-head">
            <text class="tl-name">{{ r.name || '一顿饭' }}</text>
            <text class="tl-time">{{ mdHm(r.cookedAt) }}</text>
          </view>
          <text class="tl-sub">{{ r.dishCount ?? 0 }} 道菜 · {{ r.servingCount ?? 1 }} 人份 · {{ (r.dishNames ?? []).join('/') }}</text>
          <view v-if="r.usedUpCount || r.partialCount || r.reviewed" class="tl-tags">
            <text v-if="r.usedUpCount" class="tl-tag red">用完 {{ r.usedUpCount }} 样</text>
            <text v-if="r.partialCount" class="tl-tag yellow">用了一些 {{ r.partialCount }} 样</text>
            <text v-if="r.reviewed" class="tl-tag green">已评价</text>
          </view>
        </view>
      </view>
      <ui-state v-if="loading && !records.length" mode="loading" />
      <ui-state
        v-else-if="!records.length"
        mode="empty"
        :text="`${yearMode ? '今年' : '这个月'}还没有做饭记录`"
        hint="做完一道菜点「记一笔」，这里会自动记下来"
        :illus="['rice', 'egg', 'tomato']"
      />
      <view v-if="records.length && !hasMore && total >= 10" class="footer">共 {{ total }} 顿</view>
      <view style="height: 24px" />
    </scroll-view>

    <!-- 按菜汇总 -->
    <scroll-view v-else scroll-y class="body">
      <view v-if="byDish.length" class="hint-bar">本月做过 {{ totalKinds }} 种菜 · 按次数排序，点一行看全部记录</view>
      <view v-for="d in byDish" :key="d.dishId" class="dish-row" @click="backToTimeline">
        <ui-dish-image :src="coverById[d.dishId]" :name="d.dishName" :width="44" :height="44" :radius="10" />
        <text class="dish-name">{{ d.dishName || `#${d.dishId}` }}</text>
        <text class="dish-count">{{ d.count }} 次</text>
        <text class="dish-last">{{ lastCooked(d.lastCookedAt) }}</text>
        <text v-if="d.avgStar != null" class="dish-star">★{{ d.avgStar.toFixed(1) }}</text>
      </view>
      <ui-state v-if="!byDishLoading && !byDish.length" mode="empty" :text="`${yearMode ? '本年' : '本月'}还没有做菜记录`" />
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { foodLogMonth, foodLogByDish, type FoodLogMeal, type FoodLogSummary, type FoodLogByDish } from '@/api/foodlog'
import { recentCooked } from '@/api/recommend'
import { mdHm } from '@/utils/datetime'
import { navInset } from '@/utils/token'

const sb = navInset()

const yearMode = ref(false)
const current = ref(startOfMonth())
const tab = ref<'timeline' | 'byDish'>('timeline')

const summary = ref<FoodLogSummary>({ meals: 0, dishes: 0, cookDays: 0 })
const records = ref<FoodLogMeal[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)

const byDish = ref<FoodLogByDish[]>([])
const totalKinds = ref(0)
const byDishLoading = ref(false)

const topDishes = computed(() => summary.value.topDishes ?? [])
const statItems = computed(() => [
  { value: summary.value.meals, label: '顿饭', tone: 'plain' as const },
  { value: summary.value.dishes, label: '道菜', tone: 'plain' as const },
  { value: summary.value.cookDays, label: '做饭天数', tone: 'success' as const },
])

// 食记接口不带封面：用「最近做过」按菜名 / 菜 id 补图（取不到退首字占位）
const coverByName = ref<Record<string, string>>({})
const coverById = ref<Record<number, string>>({})
recentCooked(30)
  .then((list) => {
    const n: Record<string, string> = {}
    const i: Record<number, string> = {}
    list.forEach((r) => {
      if (r.coverImage) {
        n[r.name] = r.coverImage
        i[r.dishId] = r.coverImage
      }
    })
    coverByName.value = n
    coverById.value = i
  })
  .catch(() => {})

function onTab(k: string) {
  if (k === 'byDish') switchByDish()
  else tab.value = 'timeline'
}

function startOfMonth(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1)
}

/** 接口参数：月模式 yyyy-MM / 年模式 yyyy。 */
const monthParam = computed(() =>
  yearMode.value
    ? String(current.value.getFullYear())
    : `${current.value.getFullYear()}-${String(current.value.getMonth() + 1).padStart(2, '0')}`,
)

reload()
async function reload() {
  page.value = 1
  hasMore.value = true
  loading.value = true
  try {
    const r = await foodLogMonth(monthParam.value)
    summary.value = r.summary
    records.value = r.records
    total.value = r.total
    hasMore.value = r.records.length >= 10
  } catch {
    records.value = []
  }
  loading.value = false
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const r = await foodLogMonth(monthParam.value, page.value + 1)
    page.value += 1
    records.value.push(...r.records)
    hasMore.value = r.records.length >= 10
  } catch {}
  loading.value = false
}

async function switchByDish() {
  tab.value = 'byDish'
  if (byDish.value.length) return
  byDishLoading.value = true
  try {
    const r = await foodLogByDish(monthParam.value)
    byDish.value = r.items
    totalKinds.value = r.totalKinds
  } catch {}
  byDishLoading.value = false
}

function backToTimeline() {
  tab.value = 'timeline'
}

// ---- 时间切换 ----
function onPick(d: Date) {
  current.value = new Date(d.getFullYear(), d.getMonth(), 1)
  yearMode.value = false
  reload()
}
function stepBy(delta: number) {
  const d = current.value
  if (yearMode.value) {
    current.value = new Date(d.getFullYear() + delta, 0, 1)
  } else {
    current.value = new Date(d.getFullYear(), d.getMonth() + delta, 1)
  }
  reload()
}
function setYearMode(y: boolean) {
  if (yearMode.value === y) return
  yearMode.value = y
  reload()
}

function goDetail(r: FoodLogMeal) {
  if (!r.menuId) return
  uni.navigateTo({ url: `/pages/foodlog/Detail?menuId=${r.menuId}` })
}
function goBack() {
  uni.navigateBack()
}
function lastCooked(s?: string | null): string {
  if (!s) return ''
  const d = new Date(s.replace(/-/g, '/'))
  return isNaN(d.getTime()) ? '' : `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 6px;
  padding-right: 14px;
}
.back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 8px;
}
.time-group {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
}
.step {
  font-size: var(--fs-subtitle);
  color: var(--title);
  font-weight: 700;
  padding: 2px 6px;
}
.step.dim {
  color: var(--border);
}
.seg {
  display: flex;
  background: var(--secondary);
  border-radius: var(--r-md);
  padding: 2px;
}
.seg-item {
  padding: 4px 12px;
  border-radius: 10px;
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
}
.seg-item.on {
  background: var(--primary-deep);
  color: var(--on-primary);
}
/* 统计卡 */
.body {
  flex: 1;
  min-height: 0;
  padding: 0 14px;
  box-sizing: border-box;
}
/* 时间轴 */
.tl-row {
  display: flex;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-12);
  margin-bottom: var(--sp-10);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.06);
}
.tl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  margin-top: 6px;
  flex-shrink: 0;
}
.tl-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.tl-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.tl-name {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-time {
  font-size: var(--fs-sm);
  color: var(--body);
}
.tl-sub {
  font-size: var(--fs-sm);
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-tags {
  display: flex;
  gap: 6px;
  margin-top: 1px;
}
.tl-tag {
  padding: 1px 8px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.tl-tag.red { background: var(--error-soft); color: var(--error-deep); }
.tl-tag.yellow { background: var(--warning-soft); color: var(--warning-deep); }
.tl-tag.green { background: var(--success-soft); color: var(--success-deep); }
.footer {
  padding: 12px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--body);
}
/* 按菜 */
.hint-bar {
  font-size: var(--fs-sm);
  color: var(--body);
  margin: 2px 2px 8px;
}
.dish-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: var(--sp-10) var(--sp-12);
  margin-bottom: var(--sp-8);
}
.dish-name {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.dish-count {
  font-size: var(--fs-xs);
  color: var(--primary);
  font-weight: 700;
}
.dish-last {
  font-size: var(--fs-sm);
  color: var(--body);
}
.dish-star {
  font-size: var(--fs-xs);
  color: var(--primary);
  font-weight: 700;
}
.stats {
  padding: var(--sp-8) var(--sp-16) 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-10);
}
.top-line {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.top-txt {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--body);
}
.tabs-wrap {
  padding: var(--sp-12) var(--sp-16);
}
.tl-cover {
  margin-bottom: var(--sp-8);
}
</style>
