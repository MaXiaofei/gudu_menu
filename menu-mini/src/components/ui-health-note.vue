<template>
  <!-- 健康类免责提示（非 AI 内容用：营养报告、家有特殊饮食人群时的智荐）：「仅供参考，不能替代医生或营养师建议」。
       AI 生成的内容用 ui-ai-badge disclaimer。颜色全部走主题 token。 -->
  <view class="hn" data-testid="health-note">
    <ui-icon name="info" :size="14" />
    <text class="hn-txt">{{ HEALTH_DISCLAIMER }}</text>
  </view>
</template>

<script setup lang="ts">
import { HEALTH_DISCLAIMER } from '@/utils/aiLabel'
</script>

<style scoped>
.hn {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  padding: var(--sp-8) var(--sp-10);
  border-radius: var(--r-sm);
  background: var(--info-soft);
}
.hn-txt {
  flex: 1;
  min-width: 0;
  color: var(--caption);
  font-size: var(--fs-xs);
  line-height: 1.5;
}
</style>
