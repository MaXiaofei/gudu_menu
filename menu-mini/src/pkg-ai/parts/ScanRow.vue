<template>
  <!-- 识别结果一行：勾选 + 食材插画 + 名称（可改）+ 置信度 + 别名提示 / 备选名 + 保质期 + 数量步进。 -->
  <view class="sr" :class="{ off: !row.checked, warn: row.tier === 'confirm' }">
    <view class="chk" :class="{ on: row.checked }" :data-testid="'scan-chk-' + row.name" @click="row.checked = !row.checked">
      <ui-icon v-if="row.checked" name="check" tone="white" :size="12" />
    </view>
    <ui-ing-icon :icon-key="row.iconKey" :name="row.name" :size="40" />
    <view class="main">
      <view class="name-row">
        <input v-model="row.name" class="name" :data-testid="'scan-name-' + row.rawName" />
        <ui-icon name="edit" :size="12" />
        <text class="conf" :class="row.tier">{{ Math.round(row.confidence * 100) }}%</text>
      </view>
      <text v-if="hint" class="alias">{{ hint }}</text>
      <view v-if="row.tier === 'confirm' && row.alternatives.length" class="alts">
        <text class="alts-lb">也可能是</text>
        <text v-for="a in row.alternatives.slice(0, 3)" :key="a" class="alt" @click="pickAlt(a)">{{ a }}</text>
      </view>
      <view class="bb"><ui-icon name="clock" :size="11" /><text class="bb-txt">{{ bestBeforeText(row.suggestedBestBefore) }}</text></view>
    </view>
    <view class="stepper">
      <text class="st" @click="row.amount = Math.max(1, row.amount - 1)">−</text>
      <text class="val">{{ row.amount }}{{ row.unit }}</text>
      <text class="st" @click="row.amount = row.amount + 1">＋</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { aliasHint, bestBeforeText, type ReviewRow } from '@/api/vision'

const props = defineProps<{ row: ReviewRow }>()
const hint = computed(() => aliasHint(props.row))
function pickAlt(a: string) {
  const r = props.row
  r.alternatives = [r.name, ...r.alternatives.filter((x) => x !== a)]
  r.name = a
  r.ingredientId = null // 交给后端按名称重新对齐
  r.checked = true
}
</script>

<style scoped>
.sr {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 12px;
  margin-bottom: var(--space-sm);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.sr.warn {
  border-color: var(--warning);
  background: var(--warning-soft);
}
.sr.off {
  opacity: 0.7;
}
.chk {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-pill);
  border: 2px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.chk.on {
  background: var(--success);
  border-color: var(--success);
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.name {
  width: 5em;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.conf {
  padding: 0 6px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.conf.auto {
  background: var(--success-soft);
  color: var(--success-deep);
}
.conf.confirm {
  background: var(--warning);
  color: var(--on-primary);
}
.alias {
  font-size: var(--fs-xs);
  color: var(--info-deep);
}
.alts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.alts-lb {
  font-size: var(--fs-xs);
  color: var(--body);
}
.alt {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
  font-size: var(--fs-xs);
  color: var(--title);
}
.bb {
  display: flex;
  align-items: center;
  gap: 4px;
}
.bb-txt {
  font-size: var(--fs-xs);
  color: var(--body);
}
.stepper {
  display: flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--card);
}
.st {
  width: 28px;
  text-align: center;
  font-size: var(--fs-lg);
  color: var(--title);
}
.val {
  min-width: 36px;
  text-align: center;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
</style>
