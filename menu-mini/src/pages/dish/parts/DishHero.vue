<template>
  <!-- 菜谱首推大图卡：16:9 原图 + 底部渐变 + 覆盖率徽章 + 菜名。 -->
  <view class="hero" data-testid="dish-hero" @click="$emit('tap')">
    <ui-dish-image :src="dish.coverUrl" :name="dish.name" ratio="16:9" :radius="16" original>
      <view class="hero-shade" />
      <text v-if="coverageText(dish.coverage)" class="hero-badge" :class="'cov-' + coverageLevel(dish.coverage)">
        {{ coverageText(dish.coverage) }}{{ coverageLevel(dish.coverage) === 'full' ? ' · 现在就能做' : '' }}
      </text>
      <view class="hero-info">
        <text class="hero-name">{{ dish.name }}</text>
        <text v-if="sub" class="hero-sub">{{ sub }}</text>
      </view>
    </ui-dish-image>
  </view>
</template>

<script setup lang="ts">
import { coverageText, coverageLevel, type Dish } from '@/api/dish'

defineProps<{ dish: Dish; sub?: string }>()
defineEmits<{ (e: 'tap'): void }>()
</script>

<style scoped>
.hero {
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(var(--sh), 0.16);
}
.hero-shade {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 40%, rgba(0, 0, 0, 0.55));
}
.hero-badge {
  position: absolute;
  left: var(--space-md);
  top: var(--space-md);
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-xs);
  font-weight: 700;
  background: var(--card);
  color: var(--success-deep);
}
.hero-badge.cov-partial {
  color: var(--warning-deep);
}
.hero-badge.cov-none {
  color: var(--error-deep);
}
.hero-info {
  position: absolute;
  left: var(--space-lg);
  right: var(--space-lg);
  bottom: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hero-name {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--on-primary);
}
.hero-sub {
  font-size: var(--fs-sm);
  color: var(--on-primary);
  opacity: 0.92;
}
</style>
