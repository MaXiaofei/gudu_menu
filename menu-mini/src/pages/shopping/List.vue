<template>
  <view class="page">
    <!-- 顶栏：大标题 + 暖橙竖条 -->
    <view class="topbar">
      <view class="title-wrap">
        <view class="title-bar"></view>
        <text class="title">采购</text>
      </view>
    </view>

    <!-- 列表 -->
    <view v-if="loading && !lists.length" class="empty">加载中…</view>
    <view v-else-if="!lists.length" class="empty">
      <text class="empty-ico">🛒</text>
      <text>还没有采购记录</text>
    </view>
    <view v-else class="list">
      <view
        v-for="l in lists"
        :key="l.id"
        class="yh-card row"
        @click="goDetail(l.id)"
      >
        <view class="row-main">
          <text class="row-title">采购单·{{ sourceTag(l) }}·{{ dailySeq(l) }}</text>
          <text class="row-date">{{ timeText(l) }}</text>
        </view>
        <text class="row-range">{{ rangeTag(l) }}</text>
        <text class="row-arrow">›</text>
      </view>
    </view>

    <!-- 悬浮 + 新建按钮 -->
    <view class="fab" @click="onCreate">+</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { listShopping, createShopping, type ShoppingList } from '@/api/shopping'

const lists = ref<ShoppingList[]>([])
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    lists.value = await listShopping()
  } catch {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

function sourceTag(l: ShoppingList): string {
  const r = l.timeRange
  if (!r) return '自定义'
  if (r === 'custom') return '自定义'
  if (r === 'plan') return '周计划'
  if (r === 'menu') return '菜单'
  if (r === 'dish') return '菜品'
  return r
}

function timeText(l: ShoppingList): string {
  const d = l.createdAt || l.startDate
  if (!d) return ''
  const s = String(d)
  // "2026-06-24T12:30:00" → "06-24 12:30"
  const date = s.slice(5, 10)
  const time = s.slice(11, 16)
  return `${date} ${time}`
}

function dailySeq(l: ShoppingList): string {
  // 当天序号：用 id 末位+1 作为简单序号（同一天内递增）
  return `第${(l.id % 100) + 1}单`
}

function rangeTag(l: ShoppingList): string {
  const r = l.timeRange
  if (!r) return ''
  if (r === 'custom') return '自定义'
  if (r === 'plan') return '周计划'
  if (r === 'menu') return '菜单'
  if (r === 'dish') return '菜品'
  return r
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/shopping/Detail?id=${id}` })
}

function onCreate() {
  uni.showActionSheet({
    itemList: ['自定义采购', '从菜品生成'],
    success: async (r) => {
      if (r.tapIndex === 0) {
        // 自定义采购：先建空采购单，再进详情手动添加
        uni.showLoading({ title: '创建中…' })
        try {
          const newId = await createShopping()
          uni.hideLoading()
          uni.navigateTo({ url: `/pages/shopping/Detail?id=${newId}` })
        } catch (e: any) {
          uni.hideLoading()
          uni.showToast({ title: e?.msg || '创建失败', icon: 'none' })
        }
      } else if (r.tapIndex === 1) {
        uni.navigateTo({ url: '/pages/shopping/Detail?mode=generate' })
      }
    },
  })
}

onShow(() => { reload() })
onLoad(() => { reload() })
</script>

<style scoped>
.page {
  padding: 0 14px 120rpx;
  min-height: 100vh;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 16px) 4px 8px;
}
.title-wrap { display: flex; align-items: center; gap: 8px; }
.title-bar {
  width: 8rpx; height: 36rpx;
  background: #E89150; border-radius: 4rpx;
}
.title {
  font-size: 24px; font-weight: bold; color: #4A382A;
}

/* 列表卡片 */
.list { display: flex; flex-direction: column; padding-top: 8rpx; }
.row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 28rpx 32rpx;
}
.row-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}
.row-title {
  font-size: 17px; font-weight: 600; color: #4A382A;
}
.row-date {
  font-size: 24rpx; color: #9C8C7A;
}
.row-range {
  background: rgba(232, 145, 80, 0.1);
  color: #E89150;
  border-radius: 8rpx;
  padding: 4rpx 16rpx;
  font-size: 22rpx;
  flex-shrink: 0;
}
.row-arrow {
  font-size: 40rpx; color: #9C8C7A;
  flex-shrink: 0;
}

/* 空态 */
.empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 100px 0;
  color: #9C8C7A; font-size: 13px; text-align: center;
}
.empty-ico { font-size: 48px; }

/* 悬浮 + */
.fab {
  position: fixed;
  right: 36rpx;
  bottom: calc(env(safe-area-inset-bottom) + 60rpx);
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
