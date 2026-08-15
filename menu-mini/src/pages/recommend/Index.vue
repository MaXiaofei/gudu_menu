<template>
  <!-- 推荐 Tab：占位（APP 端推荐功能开发中，定稿后按同一套流程迁移） -->
  <view class="page">
    <ui-action-bar />
    <ui-state mode="empty" text="推荐功能建设中" hint="APP 端推荐定稿后同步到小程序" />
  </view>
</template>

<script setup lang="ts">
// 占位页：不迁移 APP 推荐代码（开发中无参考意义）
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
</style>
