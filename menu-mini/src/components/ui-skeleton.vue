<template>
  <!-- 骨架屏（状态规范）：主色浅底 0.3↔0.7 呼吸 1.2s；不用转圈。variant 决定版式。 -->
  <view class="usk" :class="variant">
    <template v-if="variant === 'hero'">
      <view class="b hero-img" />
      <view class="b line w60" />
      <view class="b line w40" />
    </template>
    <template v-else-if="variant === 'grid'">
      <view class="grid">
        <view v-for="i in count" :key="i" class="cell"><view class="b cell-img" /><view class="b line w80" /></view>
      </view>
    </template>
    <template v-else>
      <view v-for="i in count" :key="i" class="row">
        <view class="b thumb" />
        <view class="col"><view class="b line w60" /><view class="b line w40" /><view class="b line w80 thin" /></view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'list' | 'hero' | 'grid'; count?: number }>(), { variant: 'list', count: 4 })
</script>

<style scoped>
.usk {
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.b {
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  animation: breathe 1.2s ease-in-out infinite;
}
@keyframes breathe {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.7; }
}
.row {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}
.thumb {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding-top: var(--space-xs);
}
.line {
  height: 12px;
}
.line.thin {
  height: 10px;
}
.w40 { width: 40%; }
.w60 { width: 60%; }
.w80 { width: 80%; }
.hero-img {
  height: 200px;
  border-radius: var(--radius-xl);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
}
.cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.cell-img {
  height: 110px;
  border-radius: var(--radius-md);
}
</style>
