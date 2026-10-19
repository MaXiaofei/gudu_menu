<template>
  <!-- 余量行（prototype Pantry）：食材插画 + 名称/来源 + 临期徽标 + 行内三档（充足/不足/用完）。 -->
  <view class="row" :class="{ out: item.level === 'NONE' }" @click="$emit('open')">
    <ui-ing-icon :icon-key="item.iconKey" :name="item.ingredientName" :category="item.categoryName" :size="44" :out="item.level === 'NONE'" />
    <view class="info">
      <view class="name-row">
        <text class="name">{{ item.ingredientName || `#${item.ingredientId}` }}</text>
        <ui-expiry-badge v-if="showExpiryBadge(item.daysLeft)" :days-left="item.daysLeft" />
      </view>
      <text class="sub">{{ sub }}</text>
    </view>
    <view class="seg" @click.stop>
      <view
        v-for="o in LEVELS"
        :key="o.v"
        class="seg-it"
        :class="[o.cls, { on: item.level === o.v }]"
        :data-testid="`lv-${item.ingredientId}-${o.v}`"
        @click="item.level !== o.v && $emit('level', o.v)"
      ><text class="seg-txt">{{ o.label }}</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { PantryGroupedItem } from '@/api/pantry'
import { showExpiryBadge } from '@/utils/freshness'

defineProps<{ item: PantryGroupedItem; sub: string }>()
defineEmits<{ (e: 'open'): void; (e: 'level', v: string): void }>()

const LEVELS = [
  { v: 'ENOUGH', label: '充足', cls: 'ok' },
  { v: 'LOW', label: '不足', cls: 'low' },
  { v: 'NONE', label: '用完', cls: 'none' },
]
</script>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.name-row {
  display: flex;
  align-items: center;
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
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.seg {
  display: flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
}
.seg-it {
  padding: 6px 8px;
}
.seg-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.seg-it.on .seg-txt {
  color: var(--on-primary);
  font-weight: 700;
}
.seg-it.on.ok { background: var(--success); }
.seg-it.on.low { background: var(--warning); }
.seg-it.on.none { background: var(--error); }
</style>
