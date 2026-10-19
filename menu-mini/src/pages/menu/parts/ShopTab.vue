<template>
  <!-- 食集 · 采购（prototype MenuShop）：本食集全部用料按 家里没有 / 有但不够 / 家里够 分组，勾选生成采购清单。 -->
  <view class="pane">
    <ui-state v-if="loading" mode="loading" />
    <template v-else-if="all.length">
      <view class="tip"><text class="tip-txt">这里列出 {{ dishCount }} 道菜的全部用料，并标出家里有没有。默认勾选要买的，家里够的不用管。</text></view>
      <ui-stat-grid :items="stats" />
      <template v-for="g in groups" :key="g.key">
        <template v-if="g.items.length">
          <text class="sec-label">{{ g.label }} · {{ g.items.length }}</text>
          <view v-for="it in g.items" :key="it.ingredientId" class="row" :class="{ dim: g.key === 'ENOUGH' }" @click="toggle(it.ingredientId)">
            <view class="check" :class="{ on: sel.has(it.ingredientId) }"><ui-icon v-if="sel.has(it.ingredientId)" name="check" tone="white" :size="14" /></view>
            <ui-ing-icon :icon-key="it.iconKey" :name="it.ingredientName" :size="40" :out="g.key === 'ENOUGH'" />
            <view class="main">
              <text class="name">{{ it.ingredientName }}<template v-if="usage(it)"> · {{ usage(it) }}</template></text>
              <text class="sub">{{ g.sub }}<template v-if="(it.dishCount ?? 0) >= 2"> · {{ it.dishCount }} 道菜要用</template></text>
            </view>
          </view>
        </template>
      </template>
      <view class="bar">
        <button class="btn" :disabled="!sel.size || busy" data-testid="menu-shop-make" @click="make">
          {{ busy ? '生成中…' : `生成采购清单 · ${sel.size} 样` }}
        </button>
      </view>
    </template>
    <ui-state v-else mode="empty" text="还没有要买的" hint="先在「菜」里加几道菜，用料会自动汇总到这里" :illus="['tomato', 'egg', 'scallion']" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { MenuPrep, PrepItem } from '@/api/prep'
import { shoppingFromPrep } from '@/api/menu'

const props = defineProps<{ prep: MenuPrep | null; loading: boolean; menuId: number; dishCount: number }>()

const all = computed<PrepItem[]>(() => [...(props.prep?.items ?? []), ...(props.prep?.condiments ?? [])])
const groups = computed(() => [
  { key: 'NONE', label: '家里没有', sub: '家里没有', items: all.value.filter((i) => !i.stockLevel || i.stockLevel === 'NONE') },
  { key: 'LOW', label: '有但不够', sub: '家里不太够', items: all.value.filter((i) => i.stockLevel === 'LOW') },
  { key: 'ENOUGH', label: '家里够', sub: '不用买', items: all.value.filter((i) => i.stockLevel === 'ENOUGH') },
])
const stats = computed(() => [
  { value: groups.value[0].items.length, label: '没有', tone: 'error' as const },
  { value: groups.value[1].items.length, label: '有但不够', tone: 'warning' as const },
  { value: groups.value[2].items.length, label: '家里够', tone: 'success' as const },
])

const sel = reactive(new Set<number>())
watch(
  all,
  (list) => {
    sel.clear()
    list.forEach((i) => i.stockLevel !== 'ENOUGH' && sel.add(i.ingredientId))
  },
  { immediate: true },
)
function toggle(id: number) {
  sel.has(id) ? sel.delete(id) : sel.add(id)
}
function usage(it: PrepItem) {
  return (it.usageTexts ?? []).join(' + ')
}

const busy = ref(false)
async function make() {
  busy.value = true
  try {
    const listId = await shoppingFromPrep(props.menuId, [...sel])
    uni.showToast({ title: '已生成采购清单', icon: 'none' })
    uni.navigateTo({ url: `/pages/shopping/List?listId=${listId}` })
  } catch {
    // request.ts 已提示
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.tip {
  padding: var(--sp-12);
  border-radius: var(--r-md);
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
}
.tip-txt {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
.sec-label {
  margin-top: var(--sp-8);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-12);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.row.dim {
  background: var(--bg);
}
.check {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid var(--border);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.check.on {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
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
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.bar {
  margin-top: var(--sp-12);
}
.btn {
  height: 48px;
  line-height: 48px;
  border-radius: var(--r-md);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.btn::after {
  border: none;
}
.btn[disabled] {
  background: var(--primary-soft);
  color: var(--on-primary);
}
</style>
