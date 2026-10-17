<template>
  <!-- Tab 主页大标题顶栏（prototype）：24/700 标题 + 右侧操作；顶距用统一导航安全距（避开胶囊）。 -->
  <view class="th" :style="{ paddingTop: sb + 'px' }">
    <view v-if="eyebrow || $slots.eyebrow" class="eyebrow-row">
      <slot name="eyebrow"><text class="eyebrow">{{ eyebrow }}</text></slot>
    </view>
    <view class="row">
      <text class="title">{{ title }}</text>
      <view class="right"><slot /></view>
    </view>
    <text v-if="sub" class="sub">{{ sub }}</text>
  </view>
</template>

<script setup lang="ts">
import { navInset } from '@/utils/token'

defineProps<{ title: string; sub?: string; eyebrow?: string }>()
const sb = navInset()
</script>

<style scoped>
.th {
  background: var(--bg);
  padding-left: var(--space-lg);
  padding-right: var(--space-lg);
  padding-bottom: var(--space-sm);
}
.eyebrow-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}
.eyebrow {
  font-size: var(--fs-sm);
  color: var(--body);
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  min-height: 40px;
}
.title {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
}
.right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.sub {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
