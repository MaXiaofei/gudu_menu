<template>
  <!-- 详情/录入页顶栏（DESIGN.md §13.2/13.3）：箭头行在上（与胶囊按钮对齐）、
       标题行在下（间距 8px）。录入页不传 title → 只渲染箭头行。 -->
  <view class="bh" :style="{ paddingTop: sb + 'px' }">
    <view class="bh-arrow-row">
      <text class="bh-arrow" @click="goBack">‹</text>
    </view>
    <view v-if="title || $slots.action || subtitle" class="bh-title-row">
      <view class="bh-main">
        <text v-if="title" class="bh-title">{{ title }}</text>
        <text v-if="subtitle" class="bh-subtitle">{{ subtitle }}</text>
        <slot name="subtitle" />
      </view>
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { statusBarHeight } from '@/utils/token'

defineProps<{
  title?: string
  subtitle?: string
}>()

const sb = statusBarHeight()

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/dish/List' })
  }
}
</script>

<style scoped>
.bh {
  background: var(--bg);
}
.bh-arrow-row {
  height: 32px;
  padding: 0 14px;
  display: flex;
  align-items: center;
}
.bh-arrow {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 0 6px;
}
.bh-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 8px;
}
.bh-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.bh-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--title);
}
.bh-subtitle {
  font-size: 12px;
  color: var(--caption);
}
</style>
