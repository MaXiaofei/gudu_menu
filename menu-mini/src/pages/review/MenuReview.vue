<template>
  <!-- 食集统一评价页：整体评价 + 逐道评价 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <text class="title">评价</text>
      <view style="width: 30px" />
    </view>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!data" mode="error" text="加载失败" :retry="true" @retry="load" />
    <scroll-view v-else scroll-y class="body">
      <!-- 头部 -->
      <view class="head">
        <ui-avatar :name="data.menuName" :size="44" :fallback="'饭'" />
        <view class="head-main">
          <text class="head-name">{{ data.menuName || '一顿饭' }}</text>
          <text class="head-sub">{{ data.dishCount ?? 0 }} 道菜 · 完成于 {{ finishedAt }}</text>
        </view>
      </view>

      <!-- 整体评价卡 -->
      <view class="card">
        <text class="card-title">这顿饭怎么样？</text>
        <template v-if="data.menuReview?.reviewed">
          <view class="reviewed-row">
            <text class="stars">{{ starsOf(data.menuReview.starRating ?? 0) }}</text>
            <text class="reviewed">已评 ✓</text>
          </view>
          <text v-if="dimSummary" class="dim-summary">{{ dimSummary }}</text>
          <view class="btn-line" @click="openForm(true)"><text class="btn-line-txt">修改</text></view>
        </template>
        <template v-else>
          <text class="not-reviewed">还没有评价</text>
          <view class="btn-line primary" @click="openForm(false)"><text class="btn-line-txt primary">评价 →</text></view>
        </template>
      </view>

      <!-- 菜品列表 -->
      <text class="sec-label">菜品</text>
      <view v-for="d in data.dishes" :key="d.dishId" class="dish-row" @click="goDishReview(d)">
        <image v-if="d.coverUrl" class="dish-cover" :src="thumbOf(d.coverUrl)" mode="aspectFill" lazy-load />
        <ui-avatar v-else :name="d.dishName" :size="40" />
        <view class="dish-main">
          <text class="dish-name">{{ d.dishName || `#${d.dishId}` }}</text>
          <text v-if="d.starRating" class="dish-star">★{{ d.starRating }} <text class="dish-reviewed">已评</text></text>
          <text v-else class="dish-none">未评</text>
        </view>
        <view class="dish-btn" @click.stop="goDishReview(d)"><text class="dish-btn-txt">评价</text></view>
      </view>

      <view class="tip-bar">评过的菜会更新菜谱评分，以后找菜、避雷都用得上。</view>
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { menuReviewOverview, type MenuReviewOverview } from '@/api/review'
import { thumbOf } from '@/utils/image'
import { relativeDate } from '@/utils/datetime'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const menuId = ref(0)
const loading = ref(true)
const data = ref<MenuReviewOverview | null>(null)

const DIM_NAMES: Record<string, string> = { '1': '口味', '2': '难度', '3': '营养均衡', '4': '外观' }

onLoad((options) => {
  menuId.value = Number(options?.id || 0)
})
onShow(() => load())

async function load() {
  loading.value = true
  try {
    data.value = await menuReviewOverview(menuId.value)
  } catch {
    data.value = null
  }
  loading.value = false
}

const finishedAt = computed(() => relativeDate(data.value?.finishedAt) || '近期')
const dimSummary = computed(() => {
  const scores = data.value?.menuReview?.dimensionScores
  if (!scores || !Object.keys(scores).length) return ''
  return Object.entries(scores)
    .map(([id, s]) => `${DIM_NAMES[id] || id} ${'★'.repeat(s)}${'☆'.repeat(5 - s)}`)
    .join(' · ')
})

function starsOf(n: number): string {
  return '★'.repeat(Math.max(0, Math.min(5, n))) + '☆'.repeat(5 - Math.max(0, Math.min(5, n)))
}

function openForm(isEdit: boolean) {
  uni.navigateTo({
    url: `/pages/review/Form?menuId=${menuId.value}&name=${encodeURIComponent(data.value?.menuName || '')}`,
  })
}

function goDishReview(d: { dishId: number; dishName?: string | null }) {
  uni.navigateTo({
    url: `/pages/review/Form?dishId=${d.dishId}&name=${encodeURIComponent(d.dishName || '')}`,
  })
}

function goBack() {
  uni.navigateBack()
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
  padding: 0 14px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 8px;
}
.title {
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0 14px;
}
.head-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.head-name { font-size: 16px; font-weight: 800; color: var(--title); }
.head-sub { font-size: 11px; color: var(--caption); }
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-title { font-size: 14px; font-weight: 800; color: var(--title); }
.reviewed-row { display: flex; align-items: center; gap: 10px; }
.stars { font-size: 20px; color: var(--primary); }
.reviewed { font-size: 12px; color: var(--success); font-weight: 700; }
.dim-summary { font-size: 11px; color: var(--caption); }
.not-reviewed { font-size: 13px; color: var(--caption); }
.btn-line { align-self: flex-start; }
.btn-line-txt {
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
  border: 1px solid var(--primary);
  border-radius: var(--r-pill);
  padding: 5px 14px;
}
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 18px 0 8px;
}
.dish-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 6px;
}
.dish-cover { width: 40px; height: 40px; border-radius: var(--r-md); background: var(--secondary); }
.dish-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.dish-name { font-size: 13px; font-weight: 700; color: var(--title); }
.dish-star { font-size: 11px; color: var(--primary); }
.dish-reviewed { color: var(--success); font-weight: 700; font-size: 10px; }
.dish-none { font-size: 11px; color: var(--caption); }
.dish-btn {
  border: 1px solid var(--primary);
  border-radius: var(--r-pill);
  padding: 4px 12px;
}
.dish-btn-txt { font-size: 11px; font-weight: 700; color: var(--primary); }
.tip-bar {
  margin-top: 16px;
  background: var(--highlight);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: 11px;
  color: var(--body);
}
</style>
