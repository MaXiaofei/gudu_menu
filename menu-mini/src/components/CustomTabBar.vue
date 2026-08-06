<template>
  <view class="ctb">
    <view class="ctb-item" :class="{ on: cur === 'dish' }" @click="go('/pages/dish/List')">
      <u-icon name="bookmark" :size="22" :color="cur === 'dish' ? '#D17A3C' : '#9C8C7A'" />
      <text class="ctb-txt">菜谱</text>
    </view>
    <view class="ctb-item" :class="{ on: cur === 'menu' }" @click="go('/pages/menu/Home')">
      <u-icon name="bag" :size="22" :color="cur === 'menu' ? '#D17A3C' : '#9C8C7A'" />
      <text class="ctb-txt">食集</text>
    </view>
    <view class="ctb-fab-wrap" @click="go('/pages/index/Index')">
      <view class="ctb-fab" :class="{ on: cur === 'index' }">
        <u-icon name="star-fill" :size="22" color="#FFFFFF" />
      </view>
      <text class="ctb-fab-txt" :class="{ on: cur === 'index' }">智荐</text>
    </view>
    <view class="ctb-item" :class="{ on: cur === 'pantry' }" @click="go('/pages/pantry/List')">
      <u-icon name="home-fill" :size="22" :color="cur === 'pantry' ? '#D17A3C' : '#9C8C7A'" />
      <text class="ctb-txt">我家余量</text>
    </view>
    <view class="ctb-item" :class="{ on: cur === 'profile' }" @click="go('/pages/profile/Settings')">
      <u-icon name="account" :size="22" :color="cur === 'profile' ? '#D17A3C' : '#9C8C7A'" />
      <text class="ctb-txt">我的</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const cur = ref('')

function detect() {
  const pages = getCurrentPages()
  const route = pages[pages.length - 1]?.route || ''
  if (route.includes('dish/List')) cur.value = 'dish'
  else if (route.includes('menu/Home')) cur.value = 'menu'
  else if (route.includes('index/Index')) cur.value = 'index'
  else if (route.includes('pantry/List')) cur.value = 'pantry'
  else if (route.includes('profile/Settings')) cur.value = 'profile'
  else cur.value = ''
}

function go(url: string) {
  const pages = getCurrentPages()
  const curRoute = pages[pages.length - 1]?.route || ''
  const target = url.replace(/^\//, '')
  if (curRoute === target) return
  uni.switchTab({ url, fail: () => uni.reLaunch({ url }) })
}

onMounted(() => {
  // 双保险隐藏原生 tabBar（H5 CSS 已隐藏；API 兜底小程序端）
  try { uni.hideTabBar?.({ animation: false }) } catch {}
  detect()
})
</script>

<style scoped>
.ctb {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  z-index: 999;
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  padding: 10rpx 0 calc(env(safe-area-inset-bottom) + 8rpx);
  background: #FFFFFF;
  border-top: 1px solid #F0E6D6;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.04);
}
.ctb-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  padding: 4rpx 0;
}
.ctb-txt { font-size: 18rpx; color: #9C8C7A; }
.ctb-item.on .ctb-txt { color: #D17A3C; font-weight: 800; }

/* 中间凸起智荐 FAB */
.ctb-fab-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ctb-fab {
  width: 88rpx; height: 88rpx;
  border-radius: 50%;
  background: #E89150;
  display: flex; align-items: center; justify-content: center;
  margin-top: -44rpx;
  box-shadow: 0 8rpx 20rpx rgba(232, 145, 80, 0.4);
  border: 4rpx solid #FFFFFF;
}
.ctb-fab.on { background: #D17A3C; }
.ctb-fab-txt { font-size: 18rpx; color: #9C8C7A; margin-top: 4rpx; }
.ctb-fab-txt.on { color: #D17A3C; font-weight: 800; }
</style>
