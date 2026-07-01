<template>
  <view class="page">
    <!-- 顶栏：大标题 -->
    <view class="topbar">
      <view class="title-wrap">
        <view class="title-bar"></view>
        <text class="title">菜库</text>
      </view>
    </view>

    <!-- 搜索框（常驻显示） -->
    <view class="search-box">
      <text class="s-ico">🔍</text>
      <input
        class="s-input"
        v-model="keyword"
        placeholder="搜菜名"
        placeholder-class="s-ph"
        confirm-type="search"
        @confirm="reload"
      />
      <text v-if="keyword" class="s-clear" @click="clearKeyword">✕</text>
    </view>

    <!-- 筛选折叠 -->
    <view class="filter-toggle" @click="showFilter = !showFilter">
      <text>{{ showFilter ? '收起筛选' : '筛选' }}</text>
      <text v-if="activeFilterCount" class="badge">{{ activeFilterCount }}</text>
    </view>

    <!-- 分类标签横滑 -->
    <scroll-view scroll-x class="cat-scroll" :show-scrollbar="false">
      <view class="cat-row">
        <view
          v-for="c in cats"
          :key="c.key"
          :class="['cat-chip', activeCat === c.key ? 'on' : '']"
          @click="onCat(c.key)"
        >
          {{ c.label }}
        </view>
      </view>
    </scroll-view>

    <view v-if="showFilter" class="yh-card filter-panel">
      <view class="f-title">营养上限（每份不超）</view>
      <view class="f-grid">
        <view class="f-cell">
          <text class="f-label">糖≤(g)</text>
          <input class="f-input" type="digit" v-model="filters.sugar" placeholder="如25" />
        </view>
        <view class="f-cell">
          <text class="f-label">热量≤(kcal)</text>
          <input class="f-input" type="digit" v-model="filters.cal" placeholder="如500" />
        </view>
      </view>

      <view class="f-title" style="margin-top: 14px;">分类筛选</view>
      <view class="f-grid">
        <view class="f-cell">
          <text class="f-label">菜系</text>
          <picker mode="selector" :range="cuisineLabels" :value="cuisineIdx" @change="(e:any)=>cuisineIdx=Number(e.detail.value)">
            <view class="f-picker">{{ cuisineLabels[cuisineIdx] }}</view>
          </picker>
        </view>
        <view class="f-cell">
          <text class="f-label">分类</text>
          <picker mode="selector" :range="categoryLabels" :value="categoryIdx" @change="(e:any)=>categoryIdx=Number(e.detail.value)">
            <view class="f-picker">{{ categoryLabels[categoryIdx] }}</view>
          </picker>
        </view>
      </view>
      <view class="f-grid" style="margin-top: 8px;">
        <view class="f-cell">
          <text class="f-label">标签</text>
          <picker mode="selector" :range="tagLabels" :value="tagIdx" @change="(e:any)=>tagIdx=Number(e.detail.value)">
            <view class="f-picker">{{ tagLabels[tagIdx] }}</view>
          </picker>
        </view>
      </view>

      <view class="f-title" style="margin-top: 14px;">其他</view>
      <view class="f-grid">
        <view class="f-cell">
          <text class="f-label">难度≤</text>
          <picker mode="selector" :range="diffNames" :value="diffIdx" @change="(e:any)=>diffIdx=Number(e.detail.value)">
            <view class="f-picker">{{ diffNames[diffIdx] }}</view>
          </picker>
        </view>
        <view class="f-cell">
          <text class="f-label">耗时≤(分)</text>
          <input class="f-input" type="number" v-model="filters.minutes" placeholder="如30" />
        </view>
      </view>

      <view class="f-actions">
        <button class="yh-btn-ghost half sm" @click="resetFilters">重置</button>
        <button class="yh-btn-gradient half sm" @click="reload">应用</button>
      </view>
    </view>

    <!-- 菜品列表（方案D：左圆头像 + 右三行，类似微信列表） -->
    <view v-if="loading && !dishes.length" class="empty">加载中…</view>
    <view v-else-if="!dishes.length" class="empty">
      <text class="empty-ico">🍳</text>
      <text>还没有自己的菜谱，点 + 录一个吧</text>
    </view>
    <view v-else class="dish-list">
      <view
        v-for="d in dishes"
        :key="d.id"
        class="dish-row"
        @click="goDetail(d.id)"
      >
        <!-- 左圆形头像 -->
        <view class="dish-avatar">
          <image v-if="d.coverUrl" class="avatar-img" :src="imgUrl(d.coverUrl)" mode="aspectFill" />
          <text v-else class="avatar-ph">🍽</text>
        </view>
        <!-- 右侧三行信息 -->
        <view class="dish-info">
          <!-- 第1行：菜名 + 来源标记 -->
          <view class="dish-r1">
            <text class="dish-name">{{ d.name }}</text>
            <text v-if="d.source === 'IMPORT'" class="dish-src">🌐</text>
          </view>
          <!-- 第2行：做过·时间·评分（灰字点分隔） -->
          <view class="dish-r2">
            <text v-if="totalMinutes(d)" class="dish-meta">{{ totalMinutes(d) }}分钟</text>
            <text v-if="totalMinutes(d) && d.difficulty" class="dish-dot"> · </text>
            <text v-if="d.difficulty" class="dish-meta">{{ difficultyText(d.difficulty) }}</text>
          </view>
          <!-- 第3行：菜系+分类+标签（横排占一行） -->
          <view class="dish-r3">
            <text v-for="(c, i) in (d.cuisineNames || [])" :key="'c'+i" class="dish-tag">{{ c }}</text>
            <text v-for="(c, i) in (d.categoryNames || [])" :key="'cat'+i" class="dish-tag">{{ c }}</text>
            <text v-for="(t, i) in (d.tagNames || [])" :key="'t'+i" class="dish-tag">{{ t }}</text>
          </view>
        </view>
      </view>
    </view>
    <view v-if="dishes.length" class="end">
      {{ status === 'nomore' ? '— 没有更多了 —' : status === 'loading' ? '加载中…' : '' }}
    </view>

    <!-- 悬浮 + 按钮 -->
    <view class="fab" @click="onCreate">+</view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { searchDishes, searchDishesByNutrition } from '@/api/dish'
import { request } from '@/utils/request'

const dishes = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = 10
const status = ref<'loadmore' | 'loading' | 'nomore'>('loadmore')
const loading = ref(false)

const searchOpen = ref(false)
const showFilter = ref(false)
const activeCat = ref('all')

const cats = [
  { key: 'all', label: '全部' },
  { key: 'done', label: '做过' },
  { key: 'own', label: '自创' },
  { key: 'import', label: '导入' },
  { key: 'star', label: '收藏' },
]

const filters = reactive({ sugar: '', cal: '', minutes: '' })
const diffNames = ['不限', '1', '2', '3', '4', '5']
const diffIdx = ref(0)
const METRIC_SUGAR = 5
const METRIC_CAL = 1

// 菜系/分类/标签字典
const cuisines = ref<any[]>([{ id: 0, name: '不限' }])
const categories = ref<any[]>([{ id: 0, name: '不限' }])
const tags = ref<any[]>([{ id: 0, name: '不限' }])
const cuisineIdx = ref(0)
const categoryIdx = ref(0)
const tagIdx = ref(0)
const cuisineLabels = computed(() => cuisines.value.map((x: any) => x.name))
const categoryLabels = computed(() => categories.value.map((x: any) => x.name))
const tagLabels = computed(() => tags.value.map((x: any) => x.name))

async function loadDicts() {
  try {
    const [cu, cat, tg] = await Promise.all([
      request<any>({ url: '/dict', method: 'GET', data: { group: 'cuisine', pageSize: 1000 } }),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'category', pageSize: 1000 } }),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'tag', pageSize: 1000 } }),
    ])
    const toList = (r: any) => Array.isArray(r) ? r : (r?.records || [])
    cuisines.value = [{ id: 0, name: '不限' }, ...toList(cu)]
    categories.value = [{ id: 0, name: '不限' }, ...toList(cat)]
    tags.value = [{ id: 0, name: '不限' }, ...toList(tg)]
  } catch {}
}
loadDicts()

const activeFilterCount = computed(() => {
  let n = 0
  if (filters.sugar) n++
  if (filters.cal) n++
  if (filters.minutes) n++
  if (diffIdx.value > 0) n++
  if (cuisineIdx.value > 0) n++
  if (categoryIdx.value > 0) n++
  if (tagIdx.value > 0) n++
  return n
})

function toggleSearch() {
  searchOpen.value = !searchOpen.value
}
function onCat(key: string) {
  activeCat.value = key
  // 当前分类仅做 UI 高亮 + 简单筛选（来源/收藏留 P1 真实接口）
  reload()
}
function clearKeyword() {
  keyword.value = ''
  reload()
}
function goSettings() {
  uni.navigateTo({ url: '/pages/profile/Settings' })
}

function buildParams(pn: number) {
  const p: Record<string, any> = {
    keyword: keyword.value,
    pageNum: pn,
    pageSize,
  }
  if (diffIdx.value > 0) p.maxDifficulty = diffIdx.value
  if (filters.minutes) p.maxMinutes = Number(filters.minutes)
  // 来源分类
  if (activeCat.value === 'own') p.source = 'ORIGINAL'
  if (activeCat.value === 'import') p.source = 'IMPORT'
  if (activeCat.value === 'done') p.done = true
  if (activeCat.value === 'star') p.star = true
  // 菜系/分类/标签
  if (cuisineIdx.value > 0) p.cuisineIds = [cuisines.value[cuisineIdx.value].id]
  if (categoryIdx.value > 0) p.categoryIds = [categories.value[categoryIdx.value].id]
  if (tagIdx.value > 0) p.tagIds = [tags.value[tagIdx.value].id]
  const limits: Record<string, number> = {}
  if (filters.sugar) limits[METRIC_SUGAR] = Number(filters.sugar)
  if (filters.cal) limits[METRIC_CAL] = Number(filters.cal)
  if (Object.keys(limits).length) p.nutritionLimits = limits
  return p
}
function resetFilters() {
  filters.sugar = ''
  filters.cal = ''
  filters.minutes = ''
  diffIdx.value = 0
  cuisineIdx.value = 0
  categoryIdx.value = 0
  tagIdx.value = 0
  reload()
}

async function reload() {
  page.value = 1
  dishes.value = []
  await load()
}
async function load() {
  status.value = 'loading'
  loading.value = true
  try {
    const params = buildParams(page.value)
    const r = params.nutritionLimits
      ? await searchDishesByNutrition(params)
      : await searchDishes(params)
    const records = Array.isArray(r) ? r : (r.records || [])
    // 前端兜底过滤（后端字段名差异，保证 UI 不崩）
    dishes.value.push(...records)
    status.value = records.length < pageSize ? 'nomore' : 'loadmore'
  } catch {
    status.value = 'loadmore'
  } finally {
    loading.value = false
  }
}
function totalMinutes(d: any): number {
  return (Number(d.prepTime) || 0) + (Number(d.cookTime) || 0)
}
function difficultyText(d?: number): string {
  if (d == null) return ''
  if (d <= 1) return '简单'
  if (d === 2) return '中等'
  return '有难度'
}
function imgUrl(u: string): string {
  if (!u) return ''
  // 后端返回相对根的路径（如 /gudu/uploads/xxx），H5 经 vite proxy 透传到后端；真机直连已含 host
  return u.startsWith('http') || u.startsWith('/gudu') ? u : '/gudu' + u
}
function onCreate() {
  uni.showActionSheet({
    itemList: ['手动录入菜谱', '从网页链接导入'],
    success: (r) => {
      if (r.tapIndex === 0) {
        uni.navigateTo({ url: '/pages/dish/Create' })
      } else if (r.tapIndex === 1) {
        uni.navigateTo({ url: '/pages/dish/Create?url=1' })
      }
    },
  })
}
function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}

onPullDownRefresh(() => {
  reload().finally(() => uni.stopPullDownRefresh())
})
onReachBottom(() => {
  if (status.value === 'loadmore') {
    page.value++
    load()
  }
})
reload()
</script>

<style scoped>
.page {
  padding: 0 14px 100px;
  min-height: 100vh;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 16px) 4px 8px;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-bar {
  width: 8rpx;
  height: 36rpx;
  background: #E89150;
  border-radius: 4rpx;
}
.title {
  font-size: 24px;
  font-weight: bold;
  color: #4A382A;
}
.top-actions {
  display: flex;
  gap: 12px;
}
.ico-btn {
  font-size: 22px;
  padding: 6px;
}

/* 搜索框 */
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 12rpx 24rpx;
  margin: 12rpx 0;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}
.s-ico { font-size: 16px; color: #9C8C7A; }
.s-input { flex: 1; font-size: 14px; color: #4A382A; }
.s-ph { color: #9C8C7A; }
.s-clear { font-size: 14px; color: #9C8C7A; padding: 0 4px; }

/* 分类横滑 */
.cat-scroll {
  white-space: nowrap;
  margin: 8rpx 0 4rpx;
}
.cat-row {
  display: inline-flex;
  gap: 12rpx;
  padding: 8rpx 0 12rpx;
}
.cat-chip {
  display: inline-block;
  padding: 10rpx 28rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  color: #9C8C7A;
  background: #FFFFFF;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.04);
}
.cat-chip.on {
  background: rgba(232, 145, 80, 0.12);
  color: #E89150;
  font-weight: 600;
}

/* 筛选 */
.filter-toggle {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 6rpx 0 10rpx;
  color: #E89150; font-size: 13px;
}
.badge {
  background: #E89150; color: #fff; font-size: 11px;
  border-radius: 10px; padding: 0 6px; line-height: 16px;
}
.filter-panel { padding: 14px; }
.f-title { font-size: 12px; color: #9C8C7A; margin-bottom: 8px; }
.f-grid { display: flex; gap: 8px; }
.f-cell { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.f-label { font-size: 11px; color: #9C8C7A; }
.f-input {
  background: #FDFAF4;
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  color: #4A382A;
  min-height: 60rpx;
}
.f-picker {
  background: #FDFAF4;
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  color: #4A382A;
  min-height: 60rpx;
}
.f-actions { display: flex; gap: 8px; margin-top: 12px; }
.half { flex: 1; }
.sm { height: 64rpx; line-height: 64rpx; font-size: 13px; padding: 0; }

/* 菜品列表（方案D：左圆头像 + 右三行） */
.dish-list {
  display: flex;
  flex-direction: column;
  padding: 8rpx 0;
}
.dish-row {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.05);
}
.dish-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #F6D9BE, #F6D9BE);
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-img { width: 100%; height: 100%; }
.avatar-ph { font-size: 48rpx; color: rgba(255, 255, 255, 0.8); }
.dish-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
  padding-top: 4rpx;
}
/* 第1行：菜名 + 来源 */
.dish-r1 {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.dish-name {
  font-size: 17px;
  font-weight: bold;
  color: #4A382A;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.dish-src {
  font-size: 24rpx;
  flex-shrink: 0;
}
/* 第2行：做过·时间·评分（灰字） */
.dish-r2 {
  display: flex;
  align-items: center;
}
.dish-meta {
  font-size: 24rpx;
  color: #9C8C7A;
}
.dish-dot {
  font-size: 24rpx;
  color: #9C8C7A;
}
/* 第3行：分类标签（暖橙背景） */
.dish-r3 {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 4rpx;
  overflow: hidden;
  white-space: nowrap;
}
.dish-tag {
  display: inline-block;
  background: rgba(232, 145, 80, 0.1);
  color: #E89150;
  border-radius: 8rpx;
  padding: 4rpx 16rpx;
  font-size: 22rpx;
}

/* 空态/底部 */
.empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 80px 0;
  color: #9C8C7A; font-size: 13px; text-align: center;
}
.empty-ico { font-size: 48px; }
.end { text-align: center; color: #9C8C7A; font-size: 12px; padding: 20rpx 0; }

/* 悬浮 + */
.fab {
  position: fixed;
  right: 36rpx;
  bottom: calc(env(safe-area-inset-bottom) + 140rpx);
  width: 108rpx;
  height: 108rpx;
  border-radius: 54rpx;
  background: linear-gradient(135deg, #E89150, #D17A3C);
  color: #FFFFFF;
  font-size: 56rpx;
  font-weight: 300;
  line-height: 108rpx;
  text-align: center;
  box-shadow: 0 8rpx 24rpx rgba(232, 145, 80, 0.4);
  z-index: 99;
}
</style>
