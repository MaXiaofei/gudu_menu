<template>
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <view style="flex: 1" />
      <text class="more">⋯</text>
    </view>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="empty" text="加载失败" />
    <template v-else>
      <scroll-view scroll-y class="body">
        <!-- 头部 -->
        <view class="head">
          <text class="head-name">{{ detail.name || '一顿饭' }}</text>
          <text class="head-sub">{{ mdHm(detail.cookedAt) }} · {{ detail.servingCount ?? 1 }} 人份</text>
        </view>

        <!-- 这顿饭的菜 -->
        <view class="review-box">
          <view class="review-main">
            <text class="review-title">这顿饭的菜 · 吃完别忘了评价</text>
            <text class="review-dishes">{{ dishNames }}</text>
          </view>
          <view class="review-btn" @click="goReview"><text class="review-btn-txt">去评价 ›</text></view>
        </view>

        <!-- 这顿饭用了这些 -->
        <text class="sec-label">这顿饭用了这些</text>
        <view class="use-box">
          <text v-if="detail.usedUp.length" class="use-line">用完 {{ detail.usedUp.length }} 样：{{ detail.usedUp.join(' · ') }}</text>
          <text v-if="detail.partial.length" class="use-line">用了一些 {{ detail.partial.length }} 样：{{ detail.partial.join(' · ') }}</text>
          <text v-if="!detail.usedUp.length && !detail.partial.length" class="use-none">没更新库存（跳过了确认）</text>
        </view>
        <view class="callout">
          库存档位已自动更新（用完→用完，用了一些→降一档）。去库存页随时可改。
        </view>

        <view style="height: 96px" />
      </scroll-view>

      <!-- 再做一次 -->
      <view class="bottom">
        <button class="btn-primary" :disabled="copying" @click="copyAgain">
          {{ copying ? '复制中…' : '再做一次（复制建新食集）' }}
        </button>
        <text class="bottom-hint">复制这 {{ detail.dishes.length }} 道菜 + 份数到新食集，重新走一遍流程</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { foodLogDetail, copyMenu, type FoodLogDetail } from '@/api/foodlog'
import { mdHm } from '@/utils/datetime'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const loading = ref(true)
const detail = ref<FoodLogDetail | null>(null)
const copying = ref(false)

onLoad(async (options) => {
  const menuId = Number(options?.menuId || 0)
  try {
    detail.value = await foodLogDetail(menuId)
  } catch {
    detail.value = null
  }
  loading.value = false
})

const dishNames = computed(() =>
  (detail.value?.dishes ?? []).map((d) => d.dishName || '').filter(Boolean).join(' · '),
)

function goBack() {
  uni.navigateBack()
}

function goReview() {
  const d = detail.value
  if (d?.menuId) uni.navigateTo({ url: `/pages/review/MenuReview?id=${d.menuId}` })
}

async function copyAgain() {
  const d = detail.value
  if (!d?.menuId || copying.value) return
  copying.value = true
  try {
    const newId = await copyMenu(d.menuId)
    uni.showToast({ title: '已复制，进新食集', icon: 'none' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/menu/Detail?id=${newId}` })
    }, 400)
  } catch {
    uni.showToast({ title: '复制失败', icon: 'none' })
  } finally {
    copying.value = false
  }
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
.more {
  font-size: 20px;
  color: var(--caption);
  padding: 4px 6px;
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.head {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 0 16px;
}
.head-name {
  font-size: 18px;
  font-weight: 800;
  color: var(--title);
}
.head-sub {
  font-size: 11px;
  color: var(--caption);
}
.review-box {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.review-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.review-title {
  font-size: 12px;
  color: var(--body);
}
.review-dishes {
  font-size: 12px;
  font-weight: 700;
  color: var(--title);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.review-btn {
  flex-shrink: 0;
  background: var(--primary);
  border-radius: var(--r-sm);
  padding: 7px 10px;
}
.review-btn-txt {
  color: #FFFFFF;
  font-size: 11px;
  font-weight: 700;
}
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 18px 0 8px;
}
.use-box {
  background: var(--card);
  border-radius: var(--r-sm);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.use-line {
  font-size: 12px;
  color: var(--body);
  line-height: 1.5;
}
.use-none {
  font-size: 12px;
  color: var(--caption);
}
.callout {
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: 11px;
  color: var(--body);
  line-height: 1.5;
  margin-top: 8px;
}
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bottom-hint {
  text-align: center;
  font-size: 10px;
  color: var(--caption);
}
</style>
