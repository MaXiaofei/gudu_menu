<template>
  <!-- 异常态三件套（DESIGN.md §14）：loading=骨架闪烁（禁 spinner）/
       empty=空态（纯文字，可带副文案）/ error=错误重试。 -->
  <view class="state">
    <!-- 骨架闪烁 -->
    <view v-if="mode === 'loading'" class="sk">
      <view v-for="i in 4" :key="i" class="sk-row" />
    </view>

    <!-- 空态 -->
    <view v-else-if="mode === 'empty'" class="txt">
      <text class="txt-main">{{ text || '暂无数据' }}</text>
      <text v-if="hint" class="txt-hint">{{ hint }}</text>
    </view>

    <!-- 错误态 -->
    <view v-else class="txt">
      <text class="txt-main">{{ text || '加载失败' }}</text>
      <view v-if="retry" class="retry" @click="$emit('retry')">重试</view>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  mode: 'loading' | 'empty' | 'error'
  text?: string
  hint?: string
  retry?: boolean
}>()
defineEmits<{ (e: 'retry'): void }>()
</script>

<style scoped>
.state {
  padding: 24px 16px;
}
.sk {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sk-row {
  height: 56px;
  border-radius: var(--r-md);
  background: linear-gradient(90deg, var(--card) 25%, var(--secondary) 45%, var(--card) 65%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
}
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
.txt {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 48px 24px;
}
.txt-main {
  font-size: 13px;
  color: var(--caption);
}
.txt-hint {
  font-size: 11px;
  color: var(--caption);
  opacity: 0.8;
  text-align: center;
}
.retry {
  margin-top: 8px;
  padding: 6px 24px;
  border: 1px solid var(--primary);
  border-radius: var(--r-pill);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}
</style>
