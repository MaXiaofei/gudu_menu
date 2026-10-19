<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 关于小食单：版本号 + 协议 / 隐私 / 意见反馈入口 -->
  <view class="page">
    <ui-back-header />

    <view class="body">
      <view class="brand">
        <image class="logo" src="/static/logo.png" mode="aspectFit" />
        <text class="name">{{ appName }}</text>
        <text class="version" data-testid="app-version">版本 v{{ version }}</text>
        <text class="slogan">小火慢炖，咕嘟出家的味道</text>
      </view>

      <view class="card">
        <view v-for="l in links" :key="l.label" class="row" @click="go(l.url)">
          <text class="row-label">{{ l.label }}</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { APP_NAME, APP_VERSION } from '@/config/app'

const appName = APP_NAME
const version = APP_VERSION
const links = [
  { label: '用户协议', url: '/pages/profile/Legal?type=terms' },
  { label: '隐私政策', url: '/pages/profile/Legal?type=privacy' },
  { label: '意见反馈', url: '/pages/profile/Feedback' },
]

function go(url: string) {
  uni.navigateTo({ url })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.body {
  padding: 0 var(--sp-16) var(--sp-24);
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-6);
  padding: var(--sp-24) 0;
}
.logo {
  width: 72px;
  height: 72px;
  border-radius: var(--r-lg);
}
.name {
  margin-top: var(--sp-6);
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.version {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.slogan {
  font-size: var(--fs-sm);
  color: var(--body);
}
.card {
  background: var(--card);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.row {
  display: flex;
  align-items: center;
  padding: 13px 14px;
}
.row + .row {
  border-top: 1px solid var(--bg);
}
.row-label {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.row-arrow {
  color: var(--caption);
  font-size: var(--fs-lg);
}
</style>
