<template>
  <!-- 进行中主卡（prototype Menus）：菜品拼图封面 + 名称/时间 + 备菜 / 一起吃 进度。 -->
  <view class="ac" data-testid="active-menu" @click="$emit('tap')">
    <ui-dish-collage :items="dishes" :height="156" :radius="0">
      <text class="state">{{ prepTotal && prepReady >= prepTotal ? '备好了' : '进行中' }}</text>
    </ui-dish-collage>
    <view class="body">
      <view class="row">
        <view class="main">
          <text class="name">{{ menu.name }}</text>
          <text class="meta">{{ mdHm(menu.createTime) }} · {{ menu.servingCount ?? 1 }} 人 · {{ menu.dishCount ?? dishes.length }} 道菜</text>
        </view>
        <ui-icon name="chevron" :size="18" />
      </view>
      <view class="bars">
        <view class="bar">
          <view class="bar-top"><text class="bar-l">备菜</text><text class="bar-v">{{ prepReady }}/{{ prepTotal }}</text></view>
          <view class="track"><view class="fill p" :style="{ width: pct(prepReady, prepTotal) }" /></view>
        </view>
        <view class="bar">
          <view class="bar-top"><text class="bar-l">一起吃</text><text class="bar-v">{{ together }} 人点了</text></view>
          <view class="track"><view class="fill i" :style="{ width: together ? '100%' : '0%' }" /></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { togetherCount, type Menu } from '@/api/menu'
import { getPrep } from '@/api/prep'
import { mdHm } from '@/utils/datetime'

const props = defineProps<{ menu: Menu; dishes: { cover?: string | null; name?: string | null }[] }>()
defineEmits<{ (e: 'tap'): void }>()

const prepReady = ref(0)
const prepTotal = ref(0)
const together = ref(0)

watch(
  () => props.menu.id,
  (id) => {
    getPrep(id)
      .then((p) => {
        prepReady.value = p.readyCount ?? 0
        prepTotal.value = p.totalCount ?? 0
      })
      .catch(() => {})
    togetherCount(id).then((n) => (together.value = Number(n) || 0)).catch(() => {})
  },
  { immediate: true },
)

function pct(a: number, b: number) {
  return b ? `${Math.round((a / b) * 100)}%` : '0%'
}
</script>

<style scoped>
.ac {
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--card);
  border: 2px solid var(--primary-soft);
  box-shadow: 0 8px 22px rgba(var(--sh), 0.14);
}
.state {
  position: absolute;
  left: var(--space-md);
  top: var(--space-md);
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.body {
  padding: var(--space-md) var(--space-lg) var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.meta {
  font-size: var(--fs-sm);
  color: var(--body);
}
.bars {
  display: flex;
  gap: var(--space-lg);
}
.bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bar-top {
  display: flex;
  justify-content: space-between;
}
.bar-l,
.bar-v {
  font-size: var(--fs-sm);
  color: var(--body);
}
.bar-v {
  font-weight: 700;
  color: var(--title);
}
.track {
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--secondary);
  overflow: hidden;
}
.fill {
  height: 100%;
  border-radius: var(--radius-pill);
}
.fill.p { background: var(--primary-deep); }
.fill.i { background: var(--info); }
</style>
