<template>
  <view class="page">
    <!-- 顶栏 -->
    <view class="topbar">
      <view class="title-wrap">
        <view class="title-bar"></view>
        <text class="title">食集</text>
      </view>
      <text class="ico-btn" @click="onNewMenu">＋</text>
    </view>

    <view class="sub">把一餐/一周的菜排到一起，点开就能整集做菜、自动扣库存</view>

    <!-- 食集卡片列表 -->
    <view v-if="loading && !menus.length" class="empty">加载中…</view>
    <view v-else-if="!menus.length" class="empty">
      <text class="empty-ico">📝</text>
      <text>还没有食集，点 ＋ 排个本周菜单吧</text>
    </view>
    <view v-else>
      <view
        v-for="mn in menus"
        :key="mn.id"
        class="yh-card menu-card"
        @click="goDetail(mn.id)"
      >
        <view class="menu-head">
          <view :class="['type-chip', mn.status === 'DONE' ? 'done' : 'active']">
            {{ mn.status === 'DONE' ? '✓ 已完成' : '🍽 进行中' }}
          </view>
          <text class="menu-name">{{ mn.name }}</text>
        </view>
        <view class="menu-meta">
          <text class="m-item">👥 {{ mn.servingCount || 1 }} 人份</text>
          <text class="m-item">🕒 {{ fmtTime(mn.createTime) }}</text>
        </view>
      </view>

      <!-- 分页：上拉加载更多 -->
      <view v-if="hasMore" class="more-hint" @click="loadMore">上拉或点此加载更多</view>
      <view v-else class="more-hint">没有更多了</view>
    </view>

    <view style="height: 60rpx;"></view>

    <!-- 新建食集按钮（仍跳排菜日历，待新建页落地） -->
    <button class="yh-btn-gradient new-btn" @click="onNewMenu">+ 新建食集</button>
    <view style="height: 180rpx;"></view>
    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listMenus, type Menu } from '@/api/menu'
import CustomTabBar from '@/components/CustomTabBar.vue'

const menus = ref<Menu[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const hasMore = ref(true)

async function reload() {
  page.value = 1
  hasMore.value = true
  loading.value = true
  try {
    const p = await listMenus({ pageNum: page.value, pageSize })
    menus.value = p.records || []
    hasMore.value = (p.records || []).length >= pageSize
  } catch {
    /* 静默，request.ts 已 toast */
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  page.value++
  try {
    const p = await listMenus({ pageNum: page.value, pageSize })
    const list = p.records || []
    menus.value.push(...list)
    hasMore.value = list.length >= pageSize
  } catch {
    page.value-- // 回滚
  } finally {
    loading.value = false
  }
}

onReachBottom(() => loadMore())

function fmtTime(s?: string): string {
  if (!s) return ''
  // "2026-06-24T12:30:00" → "06-24 12:30"
  const d = String(s)
  return `${d.slice(5, 10)} ${d.slice(11, 16)}`
}

function onNewMenu() {
  uni.navigateTo({ url: '/pages/mealplan/Calendar' })
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/menu/Detail?id=${id}` })
}

onShow(() => reload())
</script>

<style scoped>
.page {
  padding: 0 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
  min-height: 100vh;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 32rpx) 8rpx 16rpx;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 16rpx;
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
.ico-btn {
  font-size: 32px;
  color: #E89150;
  padding: 0 8rpx;
  line-height: 1;
}
.sub {
  font-size: 24rpx;
  color: #9C8C7A;
  padding: 0 8rpx 24rpx;
}

/* 食集卡 */
.menu-card {
  padding: 32rpx;
}
.menu-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.type-chip {
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 600;
}
.type-chip.active {
  background: rgba(79, 174, 110, 0.12);
  color: #4FAE6E;
}
.type-chip.done {
  background: rgba(156, 140, 122, 0.15);
  color: #9C8C7A;
}
.menu-name {
  flex: 1;
  font-size: 32rpx;
  font-weight: bold;
  color: #4A382A;
}
.menu-meta {
  display: flex;
  gap: 32rpx;
  margin-top: 20rpx;
}
.m-item { font-size: 26rpx; color: #9C8C7A; }

/* 空态 */
.empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 16px; padding: 100px 0;
  color: #9C8C7A; font-size: 13px; text-align: center;
}
.empty-ico { font-size: 56px; }

.more-hint {
  text-align: center;
  padding: 24rpx 0;
  color: #9C8C7A;
  font-size: 24rpx;
}

.new-btn {
  margin-top: 24rpx;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
}
</style>
