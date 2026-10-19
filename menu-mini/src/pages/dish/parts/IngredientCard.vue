<template>
  <!-- 用料卡（菜谱详情）：每行食材插画 + 用量 + 家里状态（家里有 / 不太够 / 临期 / 加入采购）。 -->
  <view v-if="ingredients.length" class="card">
    <view class="card-head">
      <text class="card-title">用料</text>
      <text v-if="coverage.total" class="cov" :class="coverage.level">家里够 {{ coverage.enough }}/{{ coverage.total }} 样</text>
    </view>
    <view v-for="ing in ingredients" :key="ing.ingredientId" class="ing-row">
      <ui-ing-icon :icon-key="ing.iconKey" :name="ing.ingredientName" :size="40" />
      <view class="ing-main">
        <text class="ing-name">{{ ing.ingredientName || `#${ing.ingredientId}` }}</text>
        <text v-if="amountText(ing)" class="ing-amt">{{ amountText(ing) }}</text>
      </view>
      <text v-if="homeOf(ing).kind === 'expire'" class="st expire">{{ homeOf(ing).text }}</text>
      <view v-else-if="homeOf(ing).kind === 'ok' || homeOf(ing).kind === 'low'" class="st-dot" :class="homeOf(ing).kind">
        <text class="dot" /><text class="st-txt">{{ homeOf(ing).text }}</text>
      </view>
      <view v-else-if="homeOf(ing).kind === 'none'" class="buy" @click="addToShopping(ing)">
        <text class="buy-txt">{{ bought[ing.ingredientId] ? '已加入' : '+ 加入采购' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { amountText, type DishIngredient } from '@/api/dish'
import type { PantryGroupedItem } from '@/api/pantry'

const props = defineProps<{
  ingredients: DishIngredient[]
  stock: Record<number, PantryGroupedItem>
  bought: Record<number, boolean>
}>()
const emit = defineEmits<{ (e: 'buy', ing: DishIngredient): void }>()

function addToShopping(ing: DishIngredient) {
  emit('buy', ing)
}

type Home = { kind: 'ok' | 'low' | 'none' | 'expire' | ''; text: string }
function homeOf(ing: DishIngredient): Home {
  const s = props.stock[ing.ingredientId]
  if (!Object.keys(props.stock).length) return { kind: '', text: '' }
  if (!s || s.level === 'NONE') return { kind: 'none', text: '' }
  if (s.daysLeft != null && s.daysLeft <= 2) {
    const t = s.daysLeft < 0 ? '已过期' : s.daysLeft === 0 ? '今天到期' : s.daysLeft === 1 ? '明天到期' : `剩 ${s.daysLeft} 天`
    return { kind: 'expire', text: t }
  }
  return s.level === 'LOW' ? { kind: 'low', text: '不太够' } : { kind: 'ok', text: '家里有' }
}
const coverage = computed(() => {
  const ings = props.ingredients || []
  if (!ings.length || !Object.keys(props.stock).length) return { enough: 0, total: 0, level: '' }
  const enough = ings.filter((i) => ['ok', 'low', 'expire'].includes(homeOf(i).kind)).length
  const level = enough >= ings.length ? 'full' : enough > 0 ? 'partial' : 'none'
  return { enough, total: ings.length, level }
})
</script>

<style scoped>
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-md) var(--space-lg);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.08);
}
.card-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--space-sm);
}
.card-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.cov {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  font-weight: 700;
  background: var(--warning-soft);
  color: var(--warning-deep);
}
.cov.full {
  background: var(--success-soft);
  color: var(--success-deep);
}
.cov.none {
  background: var(--error-soft);
  color: var(--error-deep);
}
.ing-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-sm) 0;
}
.ing-row + .ing-row {
  border-top: 1px solid var(--border);
}
.ing-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ing-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.ing-amt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.st.expire {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.st-dot {
  display: flex;
  align-items: center;
  gap: 4px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--success);
}
.st-dot.low .dot {
  background: var(--warning);
}
.st-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.buy {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--primary-soft);
  background: var(--warning-soft);
}
.buy-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
</style>
