<template>
  <!-- Tab 主页顶栏（DESIGN.md §13.1）：奶油底无标题，action 右对齐独占一行；
       无 action 时只占状态栏高度（内容顶到状态栏下）。 -->
  <view class="ab" :style="{ paddingTop: sb + 'px' }">
    <view class="ab-row">
      <slot />
    </view>
  </view>
</template>

<script setup lang="ts">
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
</script>

<style scoped>
.ab {
  background: var(--bg);
}
.ab-row {
  min-height: 36px;
  padding: 8px 16px 4px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
</style>
