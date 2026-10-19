<template>
  <!-- 统计格（余量四格、我的三格）：大号数字 + 标签；tone 决定底色。 -->
  <view class="ustat" :style="{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }">
    <view v-for="(it, i) in items" :key="i" class="cell" :class="it.tone || 'plain'" @click="$emit('tap', i)">
      <text class="v">{{ it.value }}</text>
      <text class="l">{{ it.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface StatItem {
  value: string | number
  label: string
  tone?: 'plain' | 'success' | 'warning' | 'error' | 'dark'
}
defineProps<{ items: StatItem[] }>()
defineEmits<{ (e: 'tap', index: number): void }>()
</script>

<style scoped>
.ustat {
  display: grid;
  gap: var(--space-sm);
}
.cell {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--card);
  border: 1px solid var(--border);
}
.v {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.l {
  font-size: var(--fs-xs);
  color: var(--body);
}
.success { background: var(--success-soft); border-color: transparent; }
.success .v { color: var(--success-deep); }
.warning { background: var(--warning-soft); border-color: transparent; }
.warning .v { color: var(--warning-deep); }
.error { background: var(--error-soft); border-color: transparent; }
.error .v { color: var(--error-deep); }
.dark { background: var(--title); border-color: transparent; }
.dark .v, .dark .l { color: var(--on-primary); }
</style>
