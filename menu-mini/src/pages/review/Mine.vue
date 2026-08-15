<template>
  <!-- 我的评价：待评价区 + 历史评价 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我的评价</text>
      <view style="width: 30px" />
    </view>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!data" mode="error" text="加载失败" :retry="true" @retry="load" />
    <scroll-view v-else scroll-y class="body">
      <!-- 待评价 -->
      <template v-if="data.pendingMenus.length">
        <text class="sec-label">待评价</text>
        <view v-for="p in data.pendingMenus" :key="p.menuId" class="row" @click="goMenuReview(p.menuId)">
          <text class="row-name">{{ p.menuName || '一顿饭' }}</text>
          <text class="row-status">{{ p.menuReviewed ? '部分已评' : '待评价' }}</text>
          <text class="row-go">去评价 →</text>
        </view>
      </template>

      <!-- 我的评价 -->
      <text class="sec-label">我的评价</text>
      <view v-if="!data.reviews.length" class="empty">还没有评价，做完一顿饭顺手评一下</view>
      <view v-for="r in data.reviews" :key="r.id" class="row" @click="goReview(r)">
        <view class="type-chip" :class="{ menu: isMenuReview(r) }">
          <text class="type-chip-txt">{{ isMenuReview(r) ? '食集' : '菜' }}</text>
        </view>
        <view class="row-main">
          <text class="row-name">{{ r.name || '未命名' }}</text>
          <text class="row-time">{{ relativeDate(r.createTime) }}</text>
        </view>
        <text class="row-stars">{{ '★'.repeat(r.starRating) }}{{ '☆'.repeat(5 - r.starRating) }}</text>
      </view>
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { myReviews, type MyReviewsData } from '@/api/review'
import { relativeDate } from '@/utils/datetime'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const loading = ref(true)
const data = ref<MyReviewsData | null>(null)

onShow(() => load())

async function load() {
  loading.value = true
  try {
    data.value = await myReviews()
  } catch {
    data.value = null
  }
  loading.value = false
}

function isMenuReview(r: { dishId?: number | null; menuId?: number | null }): boolean {
  return !r.dishId && !!r.menuId
}

function goMenuReview(menuId: number) {
  uni.navigateTo({ url: `/pages/review/MenuReview?id=${menuId}` })
}
function goReview(r: { dishId?: number | null; menuId?: number | null; name?: string | null }) {
  if (isMenuReview(r)) {
    uni.navigateTo({ url: `/pages/review/MenuReview?id=${r.menuId}` })
  } else {
    uni.navigateTo({
      url: `/pages/review/Form?dishId=${r.dishId}&name=${encodeURIComponent(r.name || '')}`,
    })
  }
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
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 14px 0 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 11px 12px;
  margin-bottom: 6px;
}
.row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.row-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-time { font-size: 10px; color: var(--caption); }
.row-status { font-size: 11px; color: var(--warning-text); }
.row-go { font-size: 11px; color: var(--primary); font-weight: 700; }
.row-stars { font-size: 11px; color: var(--primary); flex-shrink: 0; }
.type-chip {
  padding: 2px 8px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
}
.type-chip.menu { background: var(--highlight); border-color: var(--primary-soft); }
.type-chip-txt { font-size: 10px; color: var(--caption); }
.type-chip.menu .type-chip-txt { color: var(--primary); font-weight: 700; }
.empty {
  background: var(--card);
  border-radius: var(--r-md);
  padding: 32px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
</style>
