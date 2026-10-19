<template>
  <!-- 待确认动作卡（agent-assistant spec「写操作必须经用户确认」）：预览 + 5 分钟倒计时 + 取消/确认；
       确认后变「已执行 · 撤销」。mark_cooked 交给 CookedCard 渲染大卡。 -->
  <view class="act" :class="[action.status.toLowerCase()]" :data-testid="'action-' + action.actionId">
    <view class="head">
      <view class="ic"><ui-icon :name="icon" tone="primary" :size="16" /></view>
      <text class="title">{{ action.title }}</text>
      <text v-if="action.status === 'PENDING'" class="ttl">{{ ttlText }}</text>
    </view>

    <view v-if="thumbs.length" class="thumbs">
      <ui-dish-image v-for="(t, i) in thumbs" :key="i" :src="t.cover" :name="t.name" :width="40" :height="40" :radius="10" />
      <text class="thumbs-txt">{{ thumbsText }}</text>
    </view>
    <view v-if="lines.length" class="lines">
      <view v-for="l in lines" :key="l.name" class="line">
        <ui-ing-icon :icon-key="l.iconKey" :name="l.name" :size="28" />
        <text class="line-name">{{ l.name }}</text>
        <text class="line-amt">{{ l.amount }}</text>
      </view>
    </view>

    <view v-if="action.status === 'PENDING'" class="btns">
      <view class="btn" data-testid="action-cancel" @click="$emit('cancel', action)"><text class="btn-txt">取消</text></view>
      <view class="btn primary" data-testid="action-confirm" @click="$emit('confirm', action)">
        <text class="btn-txt on">{{ busy ? '执行中…' : '确认' }}</text>
      </view>
    </view>
    <view v-else-if="action.status === 'DONE'" class="done">
      <ui-icon name="check" tone="primary" :size="14" />
      <text class="done-txt">已完成</text>
      <view class="flex1" />
      <text v-if="action.undoToken" class="undo" data-testid="action-undo" @click="$emit('undo', action)">撤销</text>
    </view>
    <text v-else class="gone">{{ action.status === 'EXPIRED' ? '已过期，请重新发起' : '已取消' }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { actionSecondsLeft, type PendingAction } from '@/api/agent'
import { formatClock } from '@/utils/cookTimer'

const props = defineProps<{ action: PendingAction; busy?: boolean }>()
const emit = defineEmits<{
  (e: 'confirm', a: PendingAction): void
  (e: 'cancel', a: PendingAction): void
  (e: 'undo', a: PendingAction): void
  (e: 'expire', a: PendingAction): void
}>()

const now = ref(Date.now())
const timer = setInterval(() => {
  now.value = Date.now()
  if (props.action.status === 'PENDING' && actionSecondsLeft(props.action, now.value) <= 0) emit('expire', props.action)
}, 1000)
onUnmounted(() => clearInterval(timer))

const ttlText = computed(() => `${formatClock(actionSecondsLeft(props.action, now.value))} 内有效`)

const icon = computed(() => ({ add_to_menu: 'calendar', add_to_shopping: 'cart', mark_cooked: 'pot', add_pantry: 'fridge' })[props.action.tool] || 'check')

// 预览结构按工具不同，宽松读取
const p = computed<any>(() => props.action.preview || {})
const thumbs = computed(() =>
  (p.value.dishes || p.value.items?.filter?.((x: any) => x.coverUrl) || [])
    .slice(0, 4)
    .map((d: any) => ({ cover: d.coverUrl ?? null, name: d.name })),
)
const thumbsText = computed(() => {
  const n = (p.value.dishes || []).length
  return [p.value.menuName, n ? `${n} 道菜` : '', p.value.date].filter(Boolean).join(' · ')
})
const lines = computed(() =>
  (props.action.tool === 'add_to_shopping' || props.action.tool === 'add_pantry' ? p.value.items || [] : []).map((x: any) => ({
    name: x.name,
    iconKey: x.iconKey ?? null,
    amount: [x.amount, x.unit].filter(Boolean).join(' '),
  })),
)
</script>

<style scoped>
.act {
  margin-top: var(--space-sm);
  padding: 12px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1.5px solid var(--primary-soft);
}
.act.done {
  border-color: var(--border);
}
.act.expired,
.act.cancelled {
  opacity: 0.6;
  border-style: dashed;
}
.head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.ic {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.title {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.ttl {
  font-size: var(--fs-xs);
  color: var(--warning-deep);
  font-weight: 600;
}
.thumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--space-sm);
}
.thumbs-txt {
  margin-left: 4px;
  font-size: var(--fs-sm);
  color: var(--body);
}
.lines {
  margin-top: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.line-name {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.line-amt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.btns {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}
.btn {
  flex: 1;
  height: 40px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.primary {
  flex: 1.6;
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.btn-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.btn-txt.on {
  color: var(--on-primary);
}
.done {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: var(--space-sm);
}
.done-txt {
  font-size: var(--fs-sm);
  color: var(--success-deep);
  font-weight: 700;
}
.flex1 {
  flex: 1;
}
.undo {
  font-size: var(--fs-sm);
  color: var(--accent);
  font-weight: 700;
  padding: 2px 4px;
}
.gone {
  display: block;
  margin-top: var(--space-sm);
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
