<template>
  <!-- 游客占位（微信审核：未登录也能浏览）：底部 Tab 里需要登录的页（食集 / 余量 / 我的）不直接跳登录页，
       展示「登录后查看你家的…」+ 登录按钮；点按钮才进登录页，登录后回到本页。 -->
  <view class="gate" data-testid="login-gate">
    <view class="gate-ic"><ui-icon :name="icon" :size="40" /></view>
    <text class="gate-title">{{ title }}</text>
    <text v-if="desc" class="gate-desc">{{ desc }}</text>
    <button class="gate-btn" data-testid="login-gate-btn" @click="toLogin">去登录</button>
    <text class="gate-hint">不登录也可以先看看菜谱</text>
  </view>
</template>

<script setup lang="ts">
import { currentPageTarget, loginUrl } from '@/utils/loginRedirect'

withDefaults(defineProps<{ title: string; desc?: string; icon?: string }>(), { desc: '', icon: 'users' })

function toLogin() {
  uni.navigateTo({ url: loginUrl(currentPageTarget()) })
}
</script>

<style scoped>
.gate {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 72px var(--space-xl) var(--space-xl);
  gap: var(--space-md);
}
.gate-ic {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-pill);
  background: var(--highlight);
  display: flex;
  align-items: center;
  justify-content: center;
}
.gate-title {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
  text-align: center;
}
.gate-desc {
  font-size: var(--fs-md);
  color: var(--body);
  text-align: center;
  line-height: 1.6;
}
.gate-btn {
  margin-top: var(--space-md);
  min-width: 200px;
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-weight: 700;
  font-size: var(--fs-md);
}
.gate-btn::after {
  border: none;
}
.gate-hint {
  font-size: var(--fs-sm);
  color: var(--caption);
}
</style>
