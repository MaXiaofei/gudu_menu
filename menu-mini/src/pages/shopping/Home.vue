<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">买点啥</text>
      <text class="hero-sub">排菜、采购、库存、记录，一处搞定</text>
    </view>

    <view class="entry card" @click="go('/pages/mealplan/Calendar')">
      <text class="e-icon">📅</text>
      <view class="e-body">
        <text class="e-title">本周排菜</text>
        <text class="e-desc">安排这一周每天吃啥</text>
      </view>
      <text class="e-arrow">›</text>
    </view>

    <view class="entry card" @click="go('/pages/shopping/List')">
      <text class="e-icon">🛒</text>
      <view class="e-body">
        <text class="e-title">采购单</text>
        <text class="e-desc">从菜单/菜品生成，也能手动加</text>
      </view>
      <text class="e-arrow">›</text>
    </view>

    <view class="entry card" @click="go('/pages/pantry/List')">
      <text class="e-icon">🧊</text>
      <view class="e-body">
        <text class="e-title">还剩啥</text>
        <text class="e-desc">冰箱里有哪些、快过期了没</text>
      </view>
      <text class="e-arrow">›</text>
    </view>

    <view class="entry card" @click="go('/pages/dailylog/Index')">
      <text class="e-icon">📝</text>
      <view class="e-body">
        <text class="e-title">今天吃了啥</text>
        <text class="e-desc">记一笔，看看营养够不够</text>
      </view>
      <text class="e-arrow">›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
function go(url: string) {
  uni.navigateTo({ url })
}
</script>

<style scoped>
.page { padding: 0 14px 24px; }

.hero { padding: 18px 2px 10px; }
.hero-title { display: block; font-size: 22px; font-weight: 700; color: #222; }
.hero-sub { display: block; font-size: 13px; color: #999; margin-top: 4px; }

.entry {
  display: flex; align-items: center; gap: 12px;
  padding: 16px 14px;
}
.e-icon { font-size: 24px; }
.e-body { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.e-title { font-size: 16px; font-weight: 600; color: #222; }
.e-desc { font-size: 12px; color: #999; }
.e-arrow { font-size: 20px; color: #ccc; }
</style>
