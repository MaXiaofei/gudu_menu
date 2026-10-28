<template>
  <!-- 「我家」小标签：本家私有食材的标记，颜色走主题 token -->
  <text class="own-tag" data-testid="own-tag">{{ OWN_TAG }}</text>
</template>

<script setup lang="ts">
import { OWN_TAG } from '@/utils/ingredientSearch'
</script>

<style scoped>
.own-tag {
  display: inline-block;
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: var(--fs-tiny);
  font-weight: 700;
  line-height: 1.4;
}
</style>
