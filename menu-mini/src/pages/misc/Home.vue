<template>
  <view class="page">
    <!-- 顶栏 -->
    <view class="topbar">
      <view class="title-wrap">
        <view class="title-bar"></view>
        <text class="title">更多</text>
      </view>
      <text class="ico-btn" @click="goSettings">⚙️</text>
    </view>

    <view class="sub">厨房里的得力小工具</view>

    <!-- 功能入口（单列卡片） -->
    <view class="entry-list">
      <view class="yh-card entry" @click="go('/pages/shopping/List')">
        <view class="entry-ico" style="background: rgba(232,163,61,0.15); color:#E8A33D;">🛒</view>
        <view class="entry-body">
          <text class="entry-name">采购</text>
          <text class="entry-sub">从菜品/菜单生成采购清单</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/shopping/List?custom=1')">
        <view class="entry-ico" style="background: rgba(232,163,61,0.15); color:#E8A33D;">✏️</view>
        <view class="entry-body">
          <text class="entry-name">自定义采购</text>
          <text class="entry-sub">手动添加要买的东西</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/pantry/List')">
        <view class="entry-ico" style="background: rgba(111,191,142,0.15); color:#6FBF8E;">🧊</view>
        <view class="entry-body">
          <text class="entry-name">家里有啥</text>
          <text class="entry-sub">冰箱食材库存、临期提醒</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/dailylog/Index')">
        <view class="entry-ico" style="background: rgba(176,123,216,0.15); color:#B07BD8;">📝</view>
        <view class="entry-body">
          <text class="entry-name">今天吃了啥</text>
          <text class="entry-sub">饮食日记，记一笔安心</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/ai/Recommend')">
        <view class="entry-ico" style="background: rgba(224,123,123,0.15); color:#E07B7B;">✨</view>
        <view class="entry-body">
          <text class="entry-name">AI 帮我</text>
          <text class="entry-sub">换菜单、算热量</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/cookbook/FindByIngredients')">
        <view class="entry-ico" style="background: rgba(42,157,143,0.15); color:#2A9D8F;">🔎</view>
        <view class="entry-body">
          <text class="entry-name">食材找菜</text>
          <text class="entry-sub">手里有啥，能做啥</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>

      <view class="yh-card entry" @click="go('/pages/mealplan/Calendar')">
        <view class="entry-ico" style="background: rgba(107,168,232,0.15); color:#6BA8E8;">📅</view>
        <view class="entry-body">
          <text class="entry-name">本周排菜</text>
          <text class="entry-sub">一周菜单排起来</text>
        </view>
        <text class="entry-arrow">›</text>
      </view>
    </view>

    <!-- 今日推荐入口（跳 index） -->
    <view class="yh-card home-entry" @click="go('/pages/index/Index')">
      <view class="home-left">
        <text class="home-title">今天给全家做点啥？</text>
        <text class="home-sub">看看今日推荐和人间烟火 →</text>
      </view>
      <text class="home-emoji">🍳</text>
    </view>

    <view style="height: 60rpx;"></view>
  </view>
</template>

<script setup lang="ts">
function go(url: string) {
  uni.navigateTo({ url })
}
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
  padding: calc(env(safe-area-inset-top) + 32rpx) 8rpx 12rpx;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.title-bar {
  width: 8rpx;
  height: 36rpx;
  background: #FF8C42;
  border-radius: 4rpx;
}
.title {
  font-size: 24px;
  font-weight: bold;
  color: #2D2A26;
}
.ico-btn {
  font-size: 22px;
  padding: 6px;
}
.sub {
  font-size: 24rpx;
  color: #9B958C;
  padding: 0 8rpx 24rpx;
}

/* 入口列表 */
.entry-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.entry {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  margin-bottom: 0;
}
.entry-ico {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  flex-shrink: 0;
}
.entry-body {
  flex: 1;
  margin-left: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.entry-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}
.entry-sub {
  font-size: 24rpx;
  color: #9B958C;
}
.entry-arrow {
  font-size: 40rpx;
  color: #B8B2A7;
}

/* 今日推荐入口（渐变底） */
.home-entry {
  margin-top: 32rpx;
  background: linear-gradient(135deg, #FF8C42, #FFA45C);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36rpx 40rpx;
  border-radius: 36rpx;
}
.home-left {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.home-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #FFFFFF;
}
.home-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
}
.home-emoji {
  font-size: 64rpx;
}

.logout {
  margin-top: 24rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 28rpx;
}
</style>
