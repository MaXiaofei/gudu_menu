<template>
  <view class="page">
    <!-- 顶栏 -->
    <view class="topbar">
      <view class="title-wrap">
        <view class="title-bar"></view>
        <text class="title">菜单</text>
      </view>
      <text class="ico-btn" @click="onNewMenu">＋</text>
    </view>

    <view class="sub">把一餐/一周的菜排到一起，备菜买菜更省心</view>

    <!-- 菜单卡片列表 -->
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!menus.length" class="empty">
      <text class="empty-ico">📝</text>
      <text>还没有菜单，点 ＋ 排个本周菜单吧</text>
    </view>
    <view v-else>
      <view
        v-for="mn in menus"
        :key="mn.id"
        class="yh-card menu-card"
        @click="goPlan(mn.id)"
      >
        <view class="menu-head">
          <view :class="['type-chip', mn.type === 'feast' ? 'feast' : 'daily']">
            {{ mn.type === 'feast' ? '🎉 宴请' : '🏠 日常' }}
          </view>
          <text class="menu-name">{{ mn.name }}</text>
        </view>
        <view class="menu-meta">
          <text class="m-item">📅 {{ mn.dateText }}</text>
          <text class="m-item">👥 {{ mn.people }} 人</text>
        </view>
        <view class="menu-foot">
          <text class="m-item">🍳 {{ mn.dishCount }} 道菜</text>
          <text class="m-item price">¥{{ mn.totalPrice }}</text>
        </view>
      </view>
    </view>

    <view style="height: 60rpx;"></view>

    <!-- 新建菜单按钮 -->
    <button class="yh-btn-gradient new-btn" @click="onNewMenu">+ 新建菜单</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'

// 菜单 = 排菜计划的一组。后端目前无"菜单列表"接口，先做占位 + 入口到排菜。
// 占位数据：方便 UI 验证；真实数据待后端 /menu/list（P1）。
const menus = ref<any[]>([
  {
    id: 0,
    type: 'daily',
    name: '本周日常',
    dateText: '6/22 - 6/28',
    people: 3,
    dishCount: 12,
    totalPrice: 186,
  },
])
const loading = ref(false)

function onNewMenu() {
  uni.navigateTo({ url: '/pages/mealplan/Calendar' })
}
function goPlan(id: number) {
  uni.navigateTo({ url: '/pages/mealplan/Calendar' })
}

onShow(() => {
  // P1：接真实菜单列表
})
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

/* 菜单卡 */
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
.type-chip.daily {
  background: rgba(232, 145, 80, 0.12);
  color: #E89150;
}
.type-chip.feast {
  background: rgba(230, 162, 60, 0.15);
  color: #E5A938;
}
.menu-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #4A382A;
}
.menu-meta {
  display: flex;
  gap: 32rpx;
  margin-top: 20rpx;
}
.menu-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid #F0E6D6;
}
.m-item { font-size: 26rpx; color: #9C8C7A; }
.m-item.price {
  font-size: 32rpx;
  font-weight: bold;
  color: #E89150;
}

/* 空态 */
.empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 16px; padding: 100px 0;
  color: #9C8C7A; font-size: 13px; text-align: center;
}
.empty-ico { font-size: 56px; }

.new-btn {
  margin-top: 24rpx;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
}
</style>
