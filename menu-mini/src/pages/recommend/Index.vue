<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 推荐：想吃什么（自然语言）→ 菜谱向量库语义召回 → 组合推荐
       （2026-08 向量化版，对齐 APP AiRecommendPage：menu-flutter/lib/pages/ai/recommend_page.dart） -->
  <view class="page">
    <ui-action-bar />

    <view class="body">
      <!-- 页面标题（Tab 主页 ActionBar 下直接内容，DESIGN.md §13，无花哨头部卡） -->
      <text class="title">智荐</text>

      <!-- ===== 首页聚合（/recommend/home + /cookbook/recent）：空分区隐藏 ===== -->
      <view v-if="homeLoading" class="home-sk" data-testid="home-skeleton">
        <view class="sk-block sk-tall" />
        <view class="sk-row">
          <view v-for="i in 3" :key="i" class="sk-block sk-mini" />
        </view>
      </view>
      <ui-state v-else-if="homeError" mode="error" text="推荐加载失败" :retry="true" @retry="loadHome" />
      <template v-else>
        <!-- 临期主推 -->
        <view v-if="home.expiring.length" class="exp-card" data-testid="sec-expiring">
          <view class="exp-head">
            <text class="exp-title">临期主推</text>
            <text class="exp-more" @click="goPantry">我家余量 ›</text>
          </view>
          <view class="exp-items">
            <view v-for="e in home.expiring" :key="e.ingredientId" class="exp-item">
              <text class="exp-name">{{ e.ingredientName }}</text>
              <ui-expiry-badge :days-left="e.daysLeft" />
            </view>
          </view>
          <template v-if="home.expiringPicks.length">
            <text class="exp-sub">先把它们用掉</text>
            <view v-for="d in home.expiringPicks" :key="d.dishId" class="pick-row" @click="goDish(d.dishId)">
              <image v-if="d.coverImage" class="pick-cover" :src="thumbOf(d.coverImage)" mode="aspectFill" lazy-load />
              <ui-avatar v-else :name="d.name" :size="40" />
              <view class="pick-main">
                <text class="pick-name">{{ d.name }}</text>
                <text class="pick-meta">{{ cardMeta(d) }}</text>
              </view>
              <text v-if="coverageText(d.coverage)" class="pick-cov">{{ coverageText(d.coverage) }}</text>
              <text class="arrow">›</text>
            </view>
          </template>
        </view>

        <!-- 横滑分区：最近做过 / 你常做 / 季节鲜 -->
        <template v-for="sec in hSections" :key="sec.key">
          <view v-if="sec.items.length" class="hsec" :data-testid="`sec-${sec.key}`">
            <text class="section-label">{{ sec.label }}</text>
            <scroll-view scroll-x class="hscroll" :show-scrollbar="false">
              <view class="hrow">
                <ui-dish-mini
                  v-for="it in sec.items"
                  :key="it.dishId"
                  :name="it.name"
                  :cover="it.cover"
                  :meta="it.meta"
                  :coverage="it.coverage"
                  @tap="goDish(it.dishId)"
                />
              </view>
            </scroll-view>
          </view>
        </template>
      </template>

      <text class="section-label ask-label">想吃点别的</text>
      <text class="desc">说说想吃的口味，帮你找菜、搭好一桌</text>

      <!-- 想吃什么（语义主入口）：自然语言 + 快捷口味 chips -->
      <view class="search-row">
        <view class="search-box">
          <text class="search-ico">⌕</text>
          <input
            v-model="pref"
            class="search-ipt"
            placeholder="想吃什么？如：清淡下饭、酸甜开胃"
            placeholder-class="ph"
            confirm-type="search"
            :cursor="pref.length"
            cursor-color="#E89150"
            @confirm="onSemanticSearch"
            @input="onInput"
          />
          <text v-if="pref" class="search-clear" @click="onClear">✕</text>
        </view>
      </view>

      <view class="chip-wrap">
        <view v-for="s in quickChips" :key="s" class="chip" @click="onQuickChip(s)">{{ s }}</view>
      </view>

      <!-- 语义找菜即时结果（列表行样式，对齐菜谱列表） -->
      <view v-if="semanticLoading" class="loading">
        <view class="spin spin-caption" />
      </view>
      <template v-if="hits && hits.length">
        <text class="section-label">找菜结果</text>
        <view v-for="h in hits || []" :key="h.dishId" class="hit-row" @click="goDish(h.dishId)">
          <text class="hit-name">{{ h.name }}</text>
          <text v-if="h.cookTime != null" class="hit-time">{{ h.cookTime }} 分钟</text>
          <text class="arrow">›</text>
        </view>
      </template>

      <!-- 组合推荐结果（展示在按钮上方） -->
      <template v-if="groups && groups.length">
        <text class="section-label">{{ isDefault ? '为你推荐' : '推荐组合' }}</text>
        <view v-for="(g, i) in groups" :key="i" class="group-card">
          <view class="group-head">
            <text class="group-no">{{ i + 1 }}</text>
            <text class="group-title">推荐组合</text>
          </view>
          <view class="group-chips">
            <view v-for="d in g.dishes || []" :key="d.dishId" class="dish-chip" @click="goDish(d.dishId)">{{ d.name }}</view>
          </view>
          <view v-for="(r, ri) in g.reasons || []" :key="ri" class="reason">
            <text class="dot">·</text>
            <text class="reason-txt">{{ r }}</text>
          </view>
        </view>
      </template>

      <!-- 操作按钮（相似菜 = 语义单菜列表 / 组合推荐 = 画像+搭配组合） -->
      <view class="btns">
        <view class="btn outline" :class="{ disabled: semanticLoading }" @click="onSemanticSearch">相似菜</view>
        <view class="btn fill" :class="{ disabled: loading }" @click="onRecommend">
          <view v-if="loading" class="spin spin-white" />
          <text>组合推荐</text>
        </view>
      </view>

      <!-- 错误/空态 -->
      <view v-if="error" class="error-box">{{ error }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { semanticSearch, coverageText, type SemanticHit, type DishCard } from '@/api/dish'
import { recommendHome, recentCooked, type RecommendHome, type RecentCooked } from '@/api/recommend'
import { thumbOf } from '@/utils/image'
import { relativeDate } from '@/utils/datetime'
import { recommendDefault, recommendMenu, type RecommendGroup } from '@/api/ai'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'

const memberStore = useMemberStore()

const pref = ref('')
const hits = ref<SemanticHit[] | null>(null) // 语义找菜即时结果
const semanticLoading = ref(false)
const loading = ref(false)
const groups = ref<RecommendGroup[] | null>(null)
const error = ref('')

const quickChips = ['清淡下饭', '酸甜开胃', '快手菜', '来点硬菜', '暖暖的汤']
const isDefault = ref(false) // 当前的 groups 是否为进页默认推荐（标题区分）

/** 进页面默认出菜（对齐 APP）：无历史→最新录入；有历史→做过次数/画像召回。失败静默。 */
async function loadDefault() {
  try {
    const list = await recommendDefault(memberStore.currentId, 3)
    groups.value = list
    isDefault.value = true
  } catch (_) {
    // 默认推荐失败静默（用户可手动触发）
  }
}
onShow(() => {
  loadDefault()
  loadHome()
})

// ---- 首页聚合 ----
const homeLoading = ref(true)
const homeError = ref(false)
const homeLoaded = ref(false)
const home = reactive<RecommendHome>({ expiring: [], expiringPicks: [], frequent: [], seasonal: [] })
const recent = ref<RecentCooked[]>([])

/** 首次骨架；再次 onShow 静默刷新。两个接口都失败才进错误态。 */
async function loadHome() {
  if (!homeLoaded.value) homeLoading.value = true
  homeError.value = false
  const [h, r] = await Promise.allSettled([recommendHome(), recentCooked(10)])
  if (h.status === 'fulfilled') Object.assign(home, h.value)
  if (r.status === 'fulfilled') recent.value = r.value
  if (h.status === 'rejected' && r.status === 'rejected') {
    homeError.value = !homeLoaded.value
  } else {
    homeLoaded.value = true
  }
  homeLoading.value = false
}

interface MiniItem { dishId: number; name: string; cover: string; meta: string; coverage: string }
function toMini(d: DishCard): MiniItem {
  return { dishId: d.dishId, name: d.name, cover: d.coverImage ? thumbOf(d.coverImage) : '', meta: cardMeta(d), coverage: coverageText(d.coverage) }
}
const hSections = computed(() => [
  {
    key: 'recent',
    label: '最近做过',
    items: recent.value.map((r) => ({
      dishId: r.dishId,
      name: r.name,
      cover: r.coverImage ? thumbOf(r.coverImage) : '',
      meta: `${relativeDate(r.lastCookedAt)} · 做过 ${r.cookedCount} 次`,
      coverage: '',
    })),
  },
  { key: 'frequent', label: '你常做', items: home.frequent.map(toMini) },
  { key: 'seasonal', label: '季节鲜', items: home.seasonal.map(toMini) },
])

function cardMeta(d: DishCard): string {
  const parts: string[] = []
  if (d.cookMinutes) parts.push(`${d.cookMinutes} 分钟`)
  parts.push(d.cookedCount > 0 ? `做过 ${d.cookedCount} 次` : '没做过')
  return parts.join(' · ')
}

function goPantry() {
  uni.switchTab({ url: '/pages/pantry/List' })
}

/** 输入变化即清空旧结果（相似菜/组合均随新输入失效，对齐 APP）。 */
function onInput() {
  if (hits.value != null || groups.value != null || error.value) {
    hits.value = null
    groups.value = null
    error.value = ''
  }
}

function onClear() {
  pref.value = ''
  onInput()
}

/** 语义找菜（即时）：自然语言 → 向量相似 Top8，列表行展示点进详情。 */
async function onSemanticSearch() {
  const q = pref.value.trim()
  if (!q) return
  semanticLoading.value = true
  try {
    hits.value = await semanticSearch(q)
  } catch {
    hits.value = []
  } finally {
    semanticLoading.value = false
  }
}

/** 快捷口味：填入输入框并直接搜索。 */
function onQuickChip(s: string) {
  pref.value = s
  onSemanticSearch()
}

/** 组合推荐：口味画像 + 偏好 → 后端规则引擎组合（不排除做过的菜，完全按口味来）。 */
async function onRecommend() {
  loading.value = true
  error.value = ''
  groups.value = null
  try {
    const list = await recommendMenu(memberStore.currentId, pref.value)
    groups.value = list
    isDefault.value = false
    if (!list.length) {
      error.value = '暂无推荐，菜库菜品较少时建议先录入更多菜品'
    }
  } catch (e: any) {
    error.value = e?.message ? String(e.message).replace(/^Exception: /, '') : '推荐失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.body {
  padding: 2px 16px 24px;
}
.title {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--title);
  line-height: 1.35;
}
.desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--caption);
}
/* 首页聚合 */
.home-sk {
  margin-top: var(--sp-12);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.sk-row {
  display: flex;
  gap: var(--sp-10);
}
.sk-block {
  border-radius: var(--r-md);
  background: linear-gradient(90deg, var(--card) 25%, var(--secondary) 45%, var(--card) 65%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
}
.sk-tall {
  height: 132px;
}
.sk-mini {
  width: 112px;
  height: 120px;
}
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
.exp-card {
  margin-top: var(--sp-12);
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
  border-radius: var(--r-lg);
  padding: var(--sp-12);
}
.exp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.exp-title {
  font-size: var(--fs-md);
  font-weight: 800;
  color: var(--title);
}
.exp-more {
  font-size: var(--fs-xs);
  color: var(--primary);
  font-weight: 700;
}
.exp-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-6);
  margin-top: var(--sp-8);
}
.exp-item {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  background: var(--card);
  border-radius: var(--r-pill);
  padding: 3px var(--sp-6) 3px var(--sp-10);
}
.exp-name {
  font-size: var(--fs-sm);
  color: var(--title);
  font-weight: 700;
}
.exp-sub {
  display: block;
  margin: var(--sp-12) 0 var(--sp-6);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--caption);
}
.pick-row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  background: var(--card);
  border-radius: var(--r-md);
  padding: var(--sp-8) var(--sp-10);
  margin-bottom: var(--sp-6);
}
.pick-cover {
  width: 40px;
  height: 40px;
  border-radius: var(--r-sm);
  flex-shrink: 0;
  background: var(--secondary);
}
.pick-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pick-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-meta {
  font-size: var(--fs-tiny);
  color: var(--caption);
}
.pick-cov {
  flex-shrink: 0;
  font-size: var(--fs-tiny);
  font-weight: 700;
  color: var(--success);
}
.hsec {
  margin-top: var(--sp-4);
}
.hscroll {
  white-space: nowrap;
}
.hrow {
  display: inline-flex;
  gap: var(--sp-10);
  padding-bottom: var(--sp-4);
}
.ask-label {
  margin-top: var(--sp-20);
}
/* 搜索（DESIGN.md §SearchInput：白底 + 主色描边 + rMd + ✕ 清除） */
.search-row {
  margin-top: 14px;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1.5px solid var(--primary);
  border-radius: var(--r-md);
  padding: 0 12px;
}
.search-ico {
  color: var(--caption);
  font-size: 15px;
}
.search-ipt {
  flex: 1;
  font-size: 13px;
  color: var(--title);
  padding: 9px 0;
}
.ph {
  color: var(--caption);
}
.search-clear {
  color: var(--caption);
  font-size: 13px;
  padding: 4px;
}
/* 快捷口味 chips（secondary 底 + primary-soft 描边胶囊） */
.chip-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 6px;
  margin-top: 10px;
}
.chip {
  padding: 5px 12px;
  border-radius: var(--r-sm);
  background: var(--secondary);
  border: 1px solid var(--primary-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}
/* 语义加载（小 spinner） */
.loading {
  display: flex;
  justify-content: center;
  padding: 12px;
}
.spin {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-top-color: currentColor;
  animation: spin 0.8s linear infinite;
}
.spin-caption {
  color: var(--caption);
}
.spin-white {
  color: #FFFFFF;
  border-color: rgba(255, 255, 255, 0.35);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
/* 分区标签 */
.section-label {
  display: block;
  margin: 12px 0 6px;
  font-size: 11px;
  font-weight: 700;
  color: var(--caption);
}
/* 找菜结果行（对齐菜谱列表卡片行） */
.hit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 7px;
}
.hit-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-time {
  font-size: 12px;
  color: var(--caption);
}
.arrow {
  color: var(--caption);
  font-size: 16px;
  font-weight: 700;
}
/* 组合推荐卡片 */
.group-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px;
  margin-bottom: 12px;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.group-no {
  font-size: 16px;
  font-weight: 800;
  color: var(--primary);
}
.group-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
}
.group-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 4px;
  margin-top: 12px;
}
.dish-chip {
  padding: 4px 12px;
  border-radius: var(--r-md);
  background: rgba(232, 145, 80, 0.15);
  color: var(--primary);
  font-size: 12px;
  text-decoration: underline;
  text-decoration-color: rgba(232, 145, 80, 0.2);
}
.reason {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 4px;
}
.dot {
  font-size: 12px;
  color: var(--caption);
}
.reason-txt {
  flex: 1;
  font-size: 12px;
  color: var(--caption);
  line-height: 1.5;
}
/* 操作按钮（相似菜 outline + 组合推荐实心，1:2 宽） */
.btns {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
.btn {
  height: 44px;
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
}
.btn.outline {
  flex: 1;
  background: transparent;
  border: 1px solid var(--primary);
  color: var(--primary);
}
.btn.fill {
  flex: 2;
  background: var(--primary);
  color: #FFFFFF;
}
.btn.disabled {
  opacity: 0.6;
}
/* 错误/空态（红底提示卡） */
.error-box {
  margin-top: 16px;
  padding: 12px;
  background: #FFF3F0;
  border-radius: var(--r-md);
  font-size: 12px;
  color: var(--error);
  line-height: 1.5;
}
</style>
