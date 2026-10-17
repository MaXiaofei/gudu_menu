<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- AI 一周餐程（prototype WeekPlan；周一至周日，默认下周）：约束 → 校验结果 → 7 天 × 午/晚 → 换一道 → 一键生成食集与采购。 -->
  <view class="page">
    <ui-back-header title="AI 一周餐程" :subtitle="rangeText">
      <template #action>
        <view class="redo" data-testid="plan-redo" @click="generate"><ui-icon name="undo" :size="14" /><text class="redo-txt">重新排</text></view>
      </template>
    </ui-back-header>

    <scroll-view scroll-x class="chips" :show-scrollbar="false">
      <view class="chip-row">
        <view class="chip k"><ui-avatar-stack :items="faces" :size="16" :max="4" /><text class="chip-txt">{{ servings }} 口人</text></view>
        <view v-for="c in profileChips" :key="c" class="chip k"><text class="chip-txt">{{ c }}</text></view>
        <view class="chip" :class="{ k: fast }" @click="fast = !fast"><text class="chip-txt">工作日 ≤30 分钟</text></view>
        <view class="chip" :class="{ k: preferExpiring }" @click="preferExpiring = !preferExpiring"><text class="chip-txt">优先用快到期</text></view>
        <view class="chip" :class="{ k: budget }" @click="budget = !budget"><text class="chip-txt">预算 ¥400</text></view>
      </view>
    </scroll-view>

    <ui-state v-if="loading" mode="loading" text="咕嘟正在排 7 天的菜…" skeleton="grid" />
    <ui-state v-else-if="!draft" mode="error" text="这次没排出来" :retry="true" @retry="generate" />
    <scroll-view v-else scroll-y class="body">
      <view class="pad">
        <view class="check" :class="{ bad: !draft.check.passed }">
          <view class="check-ic"><ui-icon :name="draft.check.passed ? 'check' : 'info'" tone="white" :size="18" /></view>
          <view class="check-main">
            <text class="check-title">{{ draft.check.passed ? '已排好 7 天 · 校验通过' : '已排好 · 有几天需要你看看' }}</text>
            <text class="check-sub">{{ checkSub }}</text>
          </view>
        </view>

        <view class="grid-head"><text /><text>午餐</text><text>晚餐</text></view>
        <view v-for="d in days" :key="d.date" class="day">
          <view class="dl">
            <text class="dl-w">{{ weekdayLabel(d.date) }}</text>
            <text class="dl-d">{{ shortDate(d.date) }}</text>
            <text v-if="d.note" class="dl-note">{{ d.note }}</text>
          </view>
          <view v-for="meal in MEALS" :key="meal" class="cell" :class="{ sw: swapping?.slotId === slotOf(d, meal)?.slotId }" @click="openSwap(slotOf(d, meal))">
            <template v-if="slotOf(d, meal)">
              <ui-dish-image :src="slotOf(d, meal)!.dish.coverUrl" :name="slotOf(d, meal)!.dish.name" :width="40" :height="40" :radius="8" />
              <view class="cn">
                <text class="dn">{{ slotOf(d, meal)!.dish.name }}</text>
                <text v-if="slotOf(d, meal)!.badges.includes('EXPIRING')" class="bd e">快到期</text>
                <text v-else-if="slotOf(d, meal)!.badges.includes('BABY')" class="bd b">宝宝版</text>
              </view>
            </template>
            <text v-else class="empty">—</text>
          </view>
        </view>
        <view style="height: 150px" />
      </view>
    </scroll-view>

    <!-- 换一道 -->
    <view v-if="swapping" class="mask" @click="swapping = null">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">换掉 {{ swapping.dish.name }}</text>
          <text class="sheet-hint">同样满足少盐、不重样</text>
        </view>
        <ui-state v-if="altLoading" mode="loading" />
        <view v-for="a in alts" :key="a.id" class="alt" :data-testid="'alt-' + a.id" @click="replace(a.id)">
          <ui-dish-image :src="a.coverUrl" :name="a.name" :width="52" :height="52" :radius="10" />
          <view class="alt-main">
            <text class="alt-name">{{ a.name }}</text>
            <text class="alt-sub">{{ a.minutes ? a.minutes + ' 分钟 · ' : '' }}钠 {{ (a.sodiumMg / 1000).toFixed(1) }}g</text>
          </view>
          <ui-icon name="chevron" :size="16" />
        </view>
        <text v-if="!altLoading && !alts.length" class="sheet-hint">暂时没有合适的替换</text>
      </view>
    </view>

    <view v-if="draft" class="bar">
      <view class="sum">
        <view class="sum-it"><text class="sum-v">{{ yuan(draft.summary.estCostFen) }}</text><text class="sum-l">预计采购</text></view>
        <view class="sum-it"><text class="sum-v">{{ draft.summary.newItems }} 样</text><text class="sum-l">需要新买</text></view>
        <view class="sum-it"><text class="sum-v ok">{{ draft.summary.usesExpiring }} 样</text><text class="sum-l">用掉快到期</text></view>
      </view>
      <view class="btn" :class="{ off: applying }" data-testid="plan-apply" @click="apply">
        <text class="btn-txt">{{ applying ? '生成中…' : `生成 ${days.length} 个食集和采购清单` }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  applyDraft,
  createDraft,
  distinctDishCount,
  getDraft,
  replaceSlot,
  slotAlternatives,
  slotOf,
  sortedDays,
  type Meal,
  type MealPlanDraft,
  type PlanSlot,
} from '@/api/mealplan'
import { yuan, type CDishCard } from '@/api/contract'
import { listMembers, type Member } from '@/api/member'
import { defaultPlanWeekStart, isoDate, shortDate, weekRangeLabel, weekdayLabel } from '@/utils/week'

const MEALS: Meal[] = ['LUNCH', 'DINNER']
const draft = ref<MealPlanDraft | null>(null)
const loading = ref(true)
const applying = ref(false)
const members = ref<Member[]>([])
const fast = ref(true)
const preferExpiring = ref(true)
const budget = ref(true)
const swapping = ref<PlanSlot | null>(null)
const alts = ref<(CDishCard & { sodiumMg: number })[]>([])
const altLoading = ref(false)

const weekStart = isoDate(defaultPlanWeekStart())
const rangeText = computed(() => `${weekRangeLabel(draft.value?.weekStart || weekStart)} · 午晚两餐`)
const servings = computed(() => members.value.length || 2)
const faces = computed(() => members.value.map((m) => ({ name: m.name, tags: m.audienceTags })))
/** 档案 → 约束 chip：高血压 → 少盐；宝宝 → 辅食版；高血脂 → 少油 */
const profileChips = computed(() => {
  const out: string[] = []
  for (const m of members.value) {
    const tags = m.audienceTags || []
    if (tags.some((t) => t.includes('高血压'))) out.push(`${m.name}少盐`)
    if (tags.some((t) => t.includes('宝宝'))) out.push(`${m.name}辅食版`)
    if (tags.some((t) => t.includes('高血脂'))) out.push(`${m.name}少油`)
  }
  return out
})
const days = computed(() => (draft.value ? sortedDays(draft.value) : []))
const checkSub = computed(() => {
  const d = draft.value!
  const parts = [...d.check.notes.slice(0, 2), `${distinctDishCount(d)} 道不重样`]
  if (d.check.rounds > 1) parts.push(`第 ${d.check.rounds} 轮通过`)
  return parts.join(' · ')
})

onLoad(async (q: any) => {
  members.value = await listMembers().catch(() => [])
  if (q?.draftId) {
    loading.value = true
    draft.value = await getDraft(Number(q.draftId)).catch(() => null)
    loading.value = false
  } else generate()
})

async function generate() {
  loading.value = true
  try {
    draft.value = await createDraft({
      weekStart,
      meals: MEALS,
      servings: servings.value,
      memberIds: members.value.map((m) => m.id),
      weekdayMaxMinutes: fast.value ? 30 : undefined,
      budgetFen: budget.value ? 40000 : undefined,
      preferExpiring: preferExpiring.value,
    })
  } catch {
    draft.value = null
  } finally {
    loading.value = false
  }
}

async function openSwap(s: PlanSlot | null) {
  if (!s || !draft.value) return
  swapping.value = s
  alts.value = []
  altLoading.value = true
  try {
    alts.value = await slotAlternatives(draft.value.draftId, s.slotId)
  } finally {
    altLoading.value = false
  }
}

async function replace(dishId: number) {
  if (!draft.value || !swapping.value) return
  draft.value = await replaceSlot(draft.value.draftId, swapping.value.slotId, dishId)
  swapping.value = null
}

async function apply() {
  if (!draft.value || applying.value) return
  applying.value = true
  try {
    const r = await applyDraft(draft.value.draftId)
    uni.showToast({ title: `已生成 ${r.menuIds.length} 个食集 · 采购 ${r.shoppingItemCount} 样`, icon: 'none' })
    setTimeout(() => uni.switchTab({ url: '/pages/menu/List' }), 800)
  } finally {
    applying.value = false
  }
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.redo {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
}
.redo-txt {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--title);
}
.chips {
  white-space: nowrap;
  flex-shrink: 0;
}
.chip-row {
  display: flex;
  gap: 6px;
  padding: 0 var(--space-lg) var(--space-sm);
}
.chip {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
}
.chip.k {
  background: var(--highlight);
  border-color: var(--primary-soft);
}
.chip-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.chip.k .chip-txt {
  color: var(--warning-deep);
  font-weight: 600;
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.check {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border-radius: var(--radius-lg);
  background: var(--success-soft);
  border: 1px solid var(--success);
  margin-bottom: var(--space-md);
}
.check.bad {
  background: var(--warning-soft);
  border-color: var(--warning);
}
.check-ic {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--success);
  display: flex;
  align-items: center;
  justify-content: center;
}
.check.bad .check-ic {
  background: var(--warning);
}
.check-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.check-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--success-deep);
}
.check.bad .check-title {
  color: var(--warning-deep);
}
.check-sub {
  font-size: var(--fs-xs);
  color: var(--success-deep);
}
.grid-head,
.day {
  display: grid;
  grid-template-columns: 52px 1fr 1fr;
  gap: 6px;
}
.grid-head {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  padding: 0 2px 6px;
}
.day {
  margin-bottom: 6px;
}
.dl {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.dl-w {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.dl-d {
  font-size: var(--fs-xs);
  color: var(--body);
}
.dl-note {
  margin-top: 2px;
  align-self: flex-start;
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
}
.cell {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--border);
  min-width: 0;
}
.cell.sw {
  border: 1.5px dashed var(--primary);
  background: var(--highlight);
}
.cn {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dn {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bd {
  align-self: flex-start;
  padding: 0 4px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.bd.e {
  background: var(--warning-soft);
  color: var(--warning-deep);
  border: 1px solid var(--warning);
}
.bd.b {
  background: var(--info-soft);
  color: var(--info-deep);
}
.empty {
  font-size: var(--fs-sm);
  color: var(--body);
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: flex-end;
  z-index: 20;
}
.sheet {
  width: 100%;
  padding: var(--space-lg) var(--space-lg) calc(var(--space-xl) + env(safe-area-inset-bottom));
  border-radius: 20px 20px 0 0;
  background: var(--bg);
}
.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--space-md);
}
.sheet-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.sheet-hint {
  font-size: var(--fs-xs);
  color: var(--body);
}
.alt {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.alt-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.alt-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.alt-sub {
  font-size: var(--fs-xs);
  color: var(--body);
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px var(--space-lg) calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid var(--border);
}
.sum {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 10px;
}
.sum-it {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.sum-it + .sum-it {
  border-left: 1px solid var(--border);
}
.sum-v {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.sum-v.ok {
  color: var(--success-deep);
}
.sum-l {
  font-size: var(--fs-xs);
  color: var(--body);
}
.btn {
  height: 50px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.off {
  opacity: 0.6;
}
.btn-txt {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
</style>
