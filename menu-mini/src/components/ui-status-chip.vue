<template>
  <!-- 状态胶囊（对齐 Flutter StatusChip）：实底白字小胶囊，色值走 props。 -->
  <view class="chip" :style="{ background: color }">
    <slot>{{ label }}</slot>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  label?: string
  color?: string
}>()
</script>

<style scoped>
.chip {
  padding: 2px 8px;
  border-radius: var(--r-pill);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.5;
}
</style>
