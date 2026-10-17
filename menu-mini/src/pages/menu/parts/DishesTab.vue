<template>
  <!-- 食集 · 菜（prototype MenuDetail）：76px 菜品图 + 份数/谁点的 + 备注忌口；完成态盖绿勾。 -->
  <view class="pane">
    <view v-if="noteHint" class="callout"><text class="callout-txt">{{ noteHint }}</text></view>
    <view v-for="(d, i) in dishes" :key="d.dishId || d.customName || i" class="dish-card">
      <view class="dish-main" @click="tapDish(d)">
        <ui-dish-image :src="d.coverUrl" :name="d.dishName || d.customName" :width="76" :height="76" :radius="12">
          <view v-if="isDone" class="done-mask"><ui-icon name="check" tone="white" :size="28" /></view>
        </ui-dish-image>
        <view class="dish-info">
          <text class="dish-name">{{ d.dishName || d.customName || (d.dishId ? `菜 #${d.dishId}` : '自定义菜') }}</text>
          <text class="dish-meta">{{ fmtFactor(d.servingFactor) }} 份<template v-if="d.addedByNickname"> · {{ d.addedByNickname }}点的</template></text>
          <view class="note-row" @click.stop="$emit('note', d)">
            <text v-if="d.note" class="note-chip">{{ d.note }}</text>
            <text v-else-if="!isDone && d.dishId" class="note-ph">+ 加备注 / 忌口</text>
          </view>
        </view>
        <view v-if="!isDone && d.dishId" class="del" aria-label="移出" @click.stop="$emit('remove', d)"><ui-icon name="x" :size="16" /></view>
      </view>
    </view>
    <ui-state v-if="!dishes.length" mode="empty" text="还没有菜" hint="去菜谱里挑几道，家里够不够一眼就知道" :illus="['broccoli', 'egg', 'fish']" />
    <view v-if="!isDone" class="add-dish" @click="$emit('pick')">
      <ui-icon name="plus" tone="primary" :size="16" /><text class="add-txt">从菜谱加菜</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MenuDish } from '@/api/menu'

const props = defineProps<{ dishes: MenuDish[]; isDone: boolean }>()
const emit = defineEmits<{ (e: 'note', d: MenuDish): void; (e: 'remove', d: MenuDish): void; (e: 'pick'): void; (e: 'open', d: MenuDish): void }>()

const noteHint = computed(() => {
  const withNote = props.dishes.filter((d) => d.note)
  return withNote.length ? `${withNote.length} 道菜带了忌口 / 备注，做的时候留意` : ''
})
function fmtFactor(f?: number | null): string {
  const v = f ?? 1
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}
function tapDish(d: MenuDish) {
  if (d.dishId) emit('open', d)
}
</script>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  gap: var(--sp-10);
}
.callout {
  padding: var(--sp-12);
  border-radius: var(--r-md);
  background: var(--info-soft);
}
.callout-txt {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--info-deep);
}
.dish-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-12);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.08);
}
.dish-main {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.done-mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(79, 174, 110, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dish-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dish-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.dish-meta {
  font-size: var(--fs-sm);
  color: var(--body);
}
.note-row {
  display: flex;
}
.note-chip {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--warning-soft);
  color: var(--warning-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.note-ph {
  font-size: var(--fs-sm);
  color: var(--primary-deep);
}
.del {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.6;
}
.add-dish {
  height: 48px;
  border-radius: var(--r-lg);
  border: 1px dashed var(--primary-soft);
  background: var(--highlight);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.add-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--primary-deep);
}
</style>
