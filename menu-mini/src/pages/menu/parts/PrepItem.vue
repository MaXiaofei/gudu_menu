<template>
  <!-- 备菜行：食材插画 + 名称/用量 + 哪几道菜要用 + 状态。 -->
  <view class="prep-row" :class="{ ready: item.status === 'READY' }" @click="$emit('toggle')" @longpress="$emit('more')">
    <view class="check" :class="{ on: item.status === 'READY' }"><ui-icon v-if="item.status === 'READY'" name="check" tone="white" :size="14" /></view>
    <ui-ing-icon :icon-key="item.iconKey" :name="item.ingredientName" :size="40" />
    <view class="main">
      <text class="name">{{ item.ingredientName }}<template v-if="usage"> · {{ usage }}</template></text>
      <text class="sub">{{ dishes }}</text>
    </view>
    <text class="st" :class="'st-' + item.status">{{ prepLabel(item.status) }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PrepItem } from '@/api/prep'
import { prepLabel } from '@/utils/token'

const props = defineProps<{ item: PrepItem }>()
defineEmits<{ (e: 'toggle'): void; (e: 'more'): void }>()
const usage = computed(() => (props.item.usageTexts ?? []).join(' + '))
const dishes = computed(() => {
  const names = props.item.dishNames ?? []
  if ((props.item.dishCount ?? names.length) >= 2) return `${names.join(' + ')} · ${props.item.dishCount ?? names.length} 道菜要用`
  return names.join('、')
})
</script>

<style scoped>
.prep-row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-12);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.prep-row.ready {
  background: var(--bg);
}
.check {
  width: 22px;
  height: 22px;
  border-radius: var(--r-pill);
  border: 2px solid var(--border);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.check.on {
  background: var(--success);
  border-color: var(--success);
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.ready .name {
  color: var(--body);
  text-decoration: line-through;
}
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.st {
  font-size: var(--fs-xs);
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--r-pill);
  background: var(--secondary);
  color: var(--body);
}
.st-READY { background: var(--success-soft); color: var(--success-deep); }
.st-THAWING { background: var(--info-soft); color: var(--info-deep); }
.st-MARINATING { background: var(--warning-soft); color: var(--warning-deep); }
</style>
