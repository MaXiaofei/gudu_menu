<template>
  <!-- 食集 · 备菜（prototype MenuPrep）：进度卡 + 筛选 + 几道菜共用 / 按菜 + 调料折叠；点一下切已备，长按化冻/腌制。 -->
  <view class="pane">
    <ui-state v-if="loading" mode="loading" />
    <template v-else-if="prep">
      <view class="prog prep-progress">
        <view class="prog-top">
          <text class="prog-title">已备 {{ prep.readyCount }} / {{ prep.totalCount }}</text>
          <text class="prog-pct">{{ pct }}</text>
        </view>
        <view class="track"><view class="fill" :style="{ width: pct }" /></view>
        <text class="prog-tip">点一下标记已备好，长按可选「化冻中 / 腌制中」</text>
      </view>

      <view class="chips">
        <view v-for="f in FILTERS" :key="f.k" class="chip" :class="{ on: filter === f.k }" @click="filter = f.k">
          {{ f.label }} {{ count(f.k) }}
        </view>
      </view>

      <template v-if="shared.length">
        <text class="sec-label">几道菜共用</text>
        <prep-item v-for="it in shared" :key="it.ingredientId" :item="it" @toggle="$emit('toggle', it)" @more="$emit('more', it)" />
      </template>
      <template v-if="single.length">
        <text class="sec-label">按菜</text>
        <prep-item v-for="it in single" :key="it.ingredientId" :item="it" @toggle="$emit('toggle', it)" @more="$emit('more', it)" />
      </template>

      <view v-if="prep.condiments.length" class="condiment-head" @click="open = !open">
        <text class="sec-label">调料 {{ prep.condiments.length }} 样</text>
        <text class="toggle">{{ open ? '收起' : '展开' }}</text>
      </view>
      <template v-if="open">
        <prep-item v-for="it in prep.condiments" :key="it.ingredientId" :item="it" @toggle="$emit('toggle', it)" @more="$emit('more', it)" />
      </template>
      <ui-state v-if="!prep.totalCount" mode="empty" text="这个食集还没有要备的料" hint="先在「菜」里加几道菜" :illus="['garlic', 'ginger', 'scallion']" />
    </template>
    <ui-state v-else mode="error" text="备菜没加载出来" :retry="true" @retry="$emit('retry')" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MenuPrep, PrepItem as Item } from '@/api/prep'
import PrepItem from './PrepItem.vue'

const props = defineProps<{ prep: MenuPrep | null; loading: boolean }>()
defineEmits<{ (e: 'toggle', it: Item): void; (e: 'more', it: Item): void; (e: 'retry'): void }>()

const FILTERS = [
  { k: 'all', label: '全部' },
  { k: 'pending', label: '待备' },
  { k: 'thaw', label: '需化冻' },
] as const
const filter = ref<'all' | 'pending' | 'thaw'>('all')
const open = ref(false)

function match(it: Item, k: string) {
  if (k === 'pending') return it.status !== 'READY'
  if (k === 'thaw') return it.status === 'THAWING'
  return true
}
function count(k: string) {
  return (props.prep?.items ?? []).filter((it) => match(it, k)).length
}
const visible = computed(() => (props.prep?.items ?? []).filter((it) => match(it, filter.value)))
const shared = computed(() => visible.value.filter((it) => (it.dishCount ?? 0) >= 2))
const single = computed(() => visible.value.filter((it) => (it.dishCount ?? 0) < 2))
const pct = computed(() => {
  const p = props.prep
  return p && p.totalCount ? `${Math.round((p.readyCount / p.totalCount) * 100)}%` : '0%'
})
</script>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.prog {
  padding: var(--sp-16);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.prog-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.prog-title {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.prog-pct {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--primary-deep);
}
.track {
  height: 8px;
  border-radius: var(--r-pill);
  background: var(--secondary);
  overflow: hidden;
}
.fill {
  height: 100%;
  border-radius: var(--r-pill);
  background: var(--success);
}
.prog-tip {
  font-size: var(--fs-sm);
  color: var(--body);
}
.chips {
  display: flex;
  gap: var(--sp-8);
  padding: var(--sp-4) 0;
}
.chip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-sm);
}
.chip.on {
  background: var(--title);
  border-color: var(--title);
  color: var(--on-primary);
  font-weight: 700;
}
.sec-label {
  margin-top: var(--sp-8);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.condiment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toggle {
  font-size: var(--fs-sm);
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
