<template>
  <!-- 做完回写确认卡（mark_cooked，prototype AgentVoice）：成品图 + 人份 + 谁吃了 + 用料去向；
       改了人份/吃的人 → 「按修改重算」调 POST /agent/actions/{id}/revise，新动作替换本卡（确认接口不收参数，预览即执行）。 -->
  <view class="cooked" :data-testid="'cooked-' + action.actionId">
    <view class="top">
      <ui-dish-image :src="pv.dish?.coverUrl" :name="pv.dish?.name" :width="64" :height="64" :radius="14" />
      <view class="top-main">
        <text class="name">{{ pv.dish?.name }}做好了</text>
        <text class="sub">{{ statusText }}</text>
      </view>
    </view>

    <view class="row">
      <view class="row-main">
        <text class="row-title">做了几人份</text>
        <text class="row-sub">按你说的「{{ pv.servings }} 个人」</text>
      </view>
      <view class="stepper">
        <view class="st-btn" @click="servings = Math.max(1, servings - 1)"><text class="st-txt">−</text></view>
        <text class="st-val" data-testid="servings">{{ servings }}</text>
        <view class="st-btn" @click="servings = Math.min(20, servings + 1)"><text class="st-txt">＋</text></view>
      </view>
    </view>

    <view class="row col">
      <text class="row-title">谁吃了</text>
      <view class="eaters">
        <view v-for="m in members" :key="m.name" class="eater" :class="{ on: picked.has(m.name) }" @click="toggle(m.name)">
          <ui-member-avatar :name="m.name" :avatar-url="m.avatarUrl" :tags="m.tags" :size="44" ring />
          <view v-if="picked.has(m.name)" class="tick"><ui-icon name="check" tone="white" :size="10" /></view>
          <text class="eater-name">{{ m.name }}</text>
        </view>
      </view>
      <text v-if="warn" class="warn">{{ warn }}</text>
    </view>

    <view class="row col">
      <text class="row-title">用料去向</text>
      <view v-for="l in pv.lines || []" :key="l.name" class="line">
        <ui-ing-icon :icon-key="l.iconKey" :name="l.name" :size="36" :out="l.status === 'USED_UP'" />
        <view class="line-main">
          <text class="line-name">{{ l.name }}</text>
          <text class="line-sub">{{ l.after }}</text>
        </view>
        <text class="line-delta" :class="l.status.toLowerCase()">{{ l.delta }}</text>
        <text v-if="l.status === 'USED_UP'" class="pill err">用完了</text>
        <text v-else-if="l.status === 'SKIP'" class="pill">不扣</text>
      </view>
    </view>

    <view v-if="action.status === 'PENDING'" class="btns">
      <view class="btn" data-testid="cooked-edit" @click="onEdit"><ui-icon name="edit" :size="16" /><text class="btn-txt">{{ busy ? '重算中…' : changed ? '按修改重算' : '改一下' }}</text></view>
      <view class="btn primary" data-testid="cooked-confirm" @click="$emit('confirm', action)">
        <text class="btn-txt on">{{ busy ? '回写中…' : '确认回写' }}</text>
      </view>
    </view>
    <view v-else-if="action.status === 'DONE'" class="done">
      <ui-icon name="check" tone="primary" :size="14" />
      <text class="done-txt">已回写余量，记进食记</text>
      <view class="flex1" />
      <text v-if="action.undoToken" class="undo" @click="$emit('undo', action)">撤销</text>
    </view>
    <text v-else class="gone">{{ action.status === 'EXPIRED' ? '已过期，请重新说一遍' : '已取消' }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { MarkCookedPreview, PendingAction } from '@/api/agent'

const props = defineProps<{
  action: PendingAction
  /** 家庭成员（含未吃的人），用于多选；id = memberId */
  family: { id?: number; name: string; avatarUrl?: string | null; tags?: string[] | null }[]
  busy?: boolean
}>()
const emit = defineEmits<{
  (e: 'confirm', a: PendingAction): void
  (e: 'undo', a: PendingAction): void
  (e: 'revise', a: PendingAction, args: { servings: number; eaterIds: number[] }): void
}>()

const pv = computed<MarkCookedPreview>(() => props.action.preview || ({} as MarkCookedPreview))
const servings = ref(pv.value.servings || 1)
const picked = reactive(new Set<string>((pv.value.eaters || []).map((e) => e.name)))
watch(pv, (v) => {
  servings.value = v.servings || 1
  picked.clear()
  ;(v.eaters || []).forEach((e) => picked.add(e.name))
})

const members = computed(() => {
  const list = props.family.map((m) => ({ ...m }))
  for (const e of pv.value.eaters || []) {
    const hit = list.find((m) => m.name === e.name)
    if (hit) hit.id = hit.id ?? e.memberId
    else list.push({ id: e.memberId, name: e.name, avatarUrl: e.avatarUrl })
  }
  return list
})
function toggle(n: string) {
  if (props.action.status !== 'PENDING') return
  picked.has(n) ? picked.delete(n) : picked.add(n)
}

const changed = computed(() => {
  const orig = new Set((pv.value.eaters || []).map((e) => e.name))
  return servings.value !== pv.value.servings || orig.size !== picked.size || [...picked].some((n) => !orig.has(n))
})

/** 档案提醒：宝宝辅食成员未选时提示原因（来自菜的标签）。 */
const warn = computed(() => {
  const baby = members.value.find((m) => (m.tags || []).some((t) => t.includes('宝宝')))
  if (!baby || picked.has(baby.name)) return ''
  const spicy = (pv.value.dish?.tags || []).some((t) => /辣|咸/.test(t))
  return spicy ? `${baby.name}没选：这道菜偏辣偏咸，默认没算她` : ''
})

/** 修改参数：人份 + 吃的人（memberId）。 */
function reviseArgs() {
  const ids = members.value.filter((m) => picked.has(m.name) && m.id).map((m) => m.id as number)
  return { servings: servings.value, eaterIds: ids }
}

const statusText = computed(() =>
  props.action.status === 'PENDING' ? '待确认 · 确认后回写余量，5 分钟内可撤销' : props.action.status === 'DONE' ? '已回写' : '',
)

function onEdit() {
  if (!changed.value) return uni.showToast({ title: '改人份或点头像，再点这里', icon: 'none' })
  emit('revise', props.action, reviseArgs())
}
</script>

<style scoped>
.cooked {
  margin-top: var(--space-sm);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: 0 4px 16px rgba(var(--sh), 0.1);
  overflow: hidden;
}
.top {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  padding: 14px;
  background: var(--highlight);
}
.top-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  font-size: var(--fs-subtitle);
  font-weight: 800;
  color: var(--title);
}
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 12px 14px;
  border-top: 1px solid var(--border);
}
.row.col {
  flex-direction: column;
  align-items: stretch;
}
.row-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.row-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.row-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.stepper {
  display: flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
}
.st-btn {
  width: 36px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.st-txt {
  font-size: var(--fs-lg);
  color: var(--title);
}
.st-val {
  min-width: 28px;
  text-align: center;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.eaters {
  display: flex;
  gap: var(--space-lg);
  flex-wrap: wrap;
}
.eater {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  opacity: 0.5;
}
.eater.on {
  opacity: 1;
}
.tick {
  position: absolute;
  right: -2px;
  top: 30px;
  width: 16px;
  height: 16px;
  border-radius: var(--radius-pill);
  background: var(--success);
  border: 2px solid var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.eater-name {
  font-size: var(--fs-sm);
  color: var(--title);
}
.warn {
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--warning-soft);
  color: var(--warning-deep);
  font-size: var(--fs-sm);
}
.line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.line-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.line-name {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--title);
}
.line-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.line-delta {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.line-delta.skip {
  color: var(--body);
  font-weight: 400;
}
.pill {
  padding: 1px 8px;
  border-radius: var(--radius-xs);
  background: var(--secondary);
  color: var(--body);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.pill.err {
  background: var(--error-soft);
  color: var(--error-deep);
}
.btns {
  display: flex;
  gap: var(--space-sm);
  padding: 12px 14px 14px;
  border-top: 1px solid var(--border);
}
.btn {
  flex: 1;
  height: 46px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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
  padding: 12px 14px;
  border-top: 1px solid var(--border);
}
.done-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--success-deep);
}
.flex1 {
  flex: 1;
}
.undo {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--accent);
}
.gone {
  display: block;
  padding: 12px 14px;
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
