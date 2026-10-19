<template>
  <!-- 菜谱列表行（prototype Recipes）：84px 菜品图 + 菜名/右上信息 + 徽章行 + 副信息。 -->
  <view class="udrow" @click="$emit('tap')">
    <ui-dish-image :src="cover" :name="name" :width="size" :height="size" :radius="12" />
    <view class="info">
      <view class="top">
        <text class="name">{{ name }}</text>
        <slot name="right"><text v-if="right" class="right">{{ right }}</text></slot>
      </view>
      <view v-if="coverageLabel || chips.length" class="chips">
        <text v-if="coverageLabel" class="cov" :class="'cov-' + (coverageTone || 'partial')">{{ coverageLabel }}</text>
        <text v-for="c in chips" :key="c" class="chip">{{ c }}</text>
      </view>
      <text v-if="sub" class="sub" :class="{ warn: subWarn }">{{ sub }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import UiDishImage from './ui-dish-image.vue'

withDefaults(
  defineProps<{
    name: string
    cover?: string | null
    right?: string
    coverageLabel?: string
    coverageTone?: 'full' | 'partial' | 'none' | ''
    chips?: string[]
    sub?: string
    subWarn?: boolean
    size?: number
  }>(),
  { chips: () => [], size: 84 },
)
defineEmits<{ (e: 'tap'): void }>()
</script>

<style scoped>
.udrow {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.08);
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
}
.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.right {
  flex-shrink: 0;
  font-size: var(--fs-sm);
  color: var(--body);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cov,
.chip {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
}
.cov {
  font-weight: 700;
}
.cov-full {
  background: var(--success-soft);
  color: var(--success-deep);
}
.cov-partial {
  background: var(--warning-soft);
  color: var(--warning-deep);
}
.cov-none {
  background: var(--error-soft);
  color: var(--error-deep);
}
.chip {
  background: var(--bg);
  border: 1px solid var(--border);
  color: var(--body);
}
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub.warn {
  color: var(--error-deep);
}
</style>
