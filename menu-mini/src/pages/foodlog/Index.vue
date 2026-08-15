<template>
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

    <!-- 统计卡 -->
    <view class="stat-card">
      <view class="stat-row">
        <view class="stat">
          <text class="stat-num">{{ summary.meals }}</text>
          <text class="stat-label">顿饭</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ summary.dishes }}</text>
          <text class="stat-label">道菜</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ summary.cookDays }}</text>
          <text class="stat-label">做饭天数</text>
        </view>
      </view>
      <view v-if="topDishes.length" class="top-line">
        {{ yearMode ? '全年' : '本月' }}最常：{{ topDishes.join(' · ') }}
      </view>
    </view>

    <!-- Tab -->
    <view class="tabs">
      <view class="tab" :class="{ on: tab === 'timeline' }" @click="tab = 'timeline'">时间轴</view>
      <view class="tab" :class="{ on: tab === 'byDish' }" @click="switchByDish">按菜汇总</view>
    </view>

    <!-- 时间轴 -->
    <scroll-view v-if="tab === 'timeline'" scroll-y class="body" @scrolltolower="loadMore">
      <view
        v-for="r in records"
        :key="r.menuId ?? r.cookedAt"
        class="tl-row"
        @click="goDetail(r)"
      >
        <view class="tl-dot" />
        <view class="tl-main">
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
      <ui-state v-if="!loading && !records.length" mode="empty" :text="`${yearMode ? '本年' : '本月'}还没有做菜记录`" />
      <view v-if="records.length && !hasMore && total >= 10" class="footer">共 {{ total }} 顿</view>
      <view style="height: 24px" />
    </scroll-view>

    <!-- 按菜汇总 -->
    <scroll-view v-else scroll-y class="body">
      <view v-if="byDish.length" class="hint-bar">本月做过 {{ totalKinds }} 种菜 · 按次数排序，点一行看全部记录</view>
      <view v-for="d in byDish" :key="d.dishId" class="dish-row" @click="backToTimeline">
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
import { mdHm } from '@/utils/datetime'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()

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
  font-size: 26px;
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
  font-size: 18px;
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
  font-size: 11px;
  font-weight: 700;
  color: var(--body);
}
.seg-item.on {
  background: var(--primary-deep);
  color: #FFFFFF;
}
/* 统计卡 */
.stat-card {
  margin: 10px 14px 0;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  padding: 14px 16px;
}
.stat-row {
  display: flex;
}
.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-num {
  color: #FFFFFF;
  font-size: 22px;
  font-weight: 800;
}
.stat-label {
  color: rgba(255, 255, 255, 0.85);
  font-size: 11px;
}
.top-line {
  margin-top: 10px;
  background: rgba(255, 255, 255, 0.24);
  border-radius: var(--r-sm);
  padding: 6px 10px;
  color: #FFFFFF;
  font-size: 11px;
}
/* Tab */
.tabs {
  display: flex;
  gap: 8px;
  padding: 12px 14px 8px;
}
.tab {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  font-size: 12px;
  color: var(--body);
}
.tab.on {
  background: var(--title);
  border-color: var(--title);
  color: #FFFFFF;
  font-weight: 700;
}
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
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 6px;
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
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-time {
  font-size: 10px;
  color: var(--caption);
}
.tl-sub {
  font-size: 10px;
  color: var(--caption);
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
  font-size: 9px;
  font-weight: 700;
}
.tl-tag.red { color: var(--error); }
.tl-tag.yellow { color: var(--warning-text); }
.tl-tag.green { color: var(--success); }
.footer {
  padding: 12px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
/* 按菜 */
.hint-bar {
  font-size: 10px;
  color: var(--caption);
  margin: 2px 2px 8px;
}
.dish-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 6px;
}
.dish-name {
  flex: 1;
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.dish-count {
  font-size: 11px;
  color: var(--primary);
  font-weight: 700;
}
.dish-last {
  font-size: 10px;
  color: var(--caption);
}
.dish-star {
  font-size: 11px;
  color: var(--primary);
  font-weight: 700;
}
</style>
