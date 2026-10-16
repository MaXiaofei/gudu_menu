<template>
  <!-- 做菜确认弹层（确认 → 结果 两态）。交互对齐食集详情「这顿饭用了什么」：
       食材默认「用完了」，调料默认「用了一些」，点一下可改。父组件负责调接口，成功后置 done。 -->
  <view class="mask" @click="onMask">
    <view class="sheet" @click.stop>
      <view class="drag-bar" />
      <!-- 确认层 -->
      <template v-if="!done">
        <text class="title">{{ title }}</text>
        <text v-if="subText" class="sub">{{ subText }}</text>
        <view class="tip">每项选一个状态，我家余量会自动更新</view>
        <scroll-view scroll-y class="list">
          <ui-state v-if="!items.length" mode="empty" text="这道菜还没有登记用料" hint="直接确认也会记到食记" />
          <view v-for="m in items" :key="m.ingredientId" class="row">
            <view class="row-main">
              <text class="name">{{ m.ingredientName }}</text>
              <text class="meta">家里：{{ stockLabel(m.level) }}<template v-if="usage(m)"> · {{ usage(m) }}</template></text>
            </view>
            <view class="chips">
              <view
                v-for="opt in options"
                :key="opt.value"
                class="chip"
                :class="{ on: sel[m.ingredientId] === opt.value }"
                :style="sel[m.ingredientId] === opt.value ? `background:${opt.color};border-color:${opt.color}` : ''"
                @click="sel[m.ingredientId] = opt.value"
              >{{ opt.label }}</view>
            </view>
          </view>
        </scroll-view>
        <view class="note">食材默认「用完了」，调料默认「用了一些」（降一档）。没有余量的食材也不拦着你做饭。</view>
        <view class="actions">
          <button class="btn-ghost skip" :disabled="submitting" @click="emitSubmit(true)">跳过，不更新余量</button>
          <button class="btn-primary ok" :disabled="submitting" @click="emitSubmit(false)">
            {{ submitting ? '提交中…' : `确认已做完 · ${usedUp.length} 样用完` }}
          </button>
        </view>
      </template>
      <!-- 结果层 -->
      <template v-else>
        <view class="result-head">
          <view class="badge"><text class="badge-txt">✓</text></view>
          <text class="result-title">做好了，已记到食记</text>
          <text v-if="resultSub" class="sub">{{ resultSub }}</text>
        </view>
        <view class="result-box">
          <text class="result-box-title">我家余量已更新</text>
          <view v-for="m in doneUsedUp" :key="'u' + m.ingredientId" class="result-row">
            <text class="result-name">{{ m.ingredientName }}</text>
            <text class="pill red">用完</text>
          </view>
          <view v-for="m in donePartial" :key="'p' + m.ingredientId" class="result-row">
            <text class="result-name">{{ m.ingredientName }}</text>
            <text class="pill yellow">用了一些</text>
          </view>
          <text v-if="!doneUsedUp.length && !donePartial.length" class="result-none">本次没有更新余量</text>
        </view>
        <view class="actions">
          <button class="btn-ghost skip" @click="$emit('close')">返回</button>
          <button class="btn-primary ok" @click="$emit('foodlog')">去食记看看</button>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, watch, ref } from 'vue'
import type { CookMaterial } from '@/api/menu'
import { stockLabel, T } from '@/utils/token'

const props = defineProps<{
  items: CookMaterial[]
  title?: string
  submitting?: boolean
  done?: boolean
  resultSub?: string
}>()
const emit = defineEmits<{
  (e: 'submit', p: { usedUp: number[]; partiallyUsed: number[] }): void
  (e: 'close'): void
  (e: 'foodlog'): void
}>()

const title = computed(() => props.title || '这道菜用了什么')
// 0 用完了 / 1 用了一些 / 2 这次没用
const sel = reactive<Record<number, number>>({})
const options = [
  { value: 0, label: '用完了', color: T.error },
  { value: 1, label: '用了一些', color: T.warning },
  { value: 2, label: '这次没用', color: T.caption },
]

watch(
  () => props.items,
  (list) => {
    Object.keys(sel).forEach((k) => delete sel[Number(k)])
    list.forEach((m) => (sel[m.ingredientId] = m.isCondiment ? 1 : 0))
  },
  { immediate: true },
)

const usedUp = computed(() => props.items.filter((m) => sel[m.ingredientId] === 0).map((m) => m.ingredientId))
const partial = computed(() => props.items.filter((m) => sel[m.ingredientId] === 1).map((m) => m.ingredientId))
const subText = computed(() => {
  const n = props.items.length
  if (!n) return ''
  const names = props.items.slice(0, 3).map((m) => m.ingredientName)
  return names.length < n ? `${names.join(' + ')} 等 ${n} 样` : names.join(' + ')
})

// 结果层展示「提交时」的选择（skip 时为空）
const submitted = ref<{ usedUp: number[]; partiallyUsed: number[] }>({ usedUp: [], partiallyUsed: [] })
const doneUsedUp = computed(() => props.items.filter((m) => submitted.value.usedUp.includes(m.ingredientId)))
const donePartial = computed(() => props.items.filter((m) => submitted.value.partiallyUsed.includes(m.ingredientId)))

function usage(m: CookMaterial): string {
  return (m.usageTexts ?? []).join(' + ')
}

function emitSubmit(skip: boolean) {
  if (props.submitting) return
  const p = skip ? { usedUp: [], partiallyUsed: [] } : { usedUp: usedUp.value, partiallyUsed: partial.value }
  submitted.value = p
  emit('submit', p)
}

function onMask() {
  if (!props.done && !props.submitting) emit('close')
}
</script>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: var(--mask);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 85vh;
  min-height: 50vh;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  display: flex;
  flex-direction: column;
  padding-top: var(--sp-8);
}
.drag-bar {
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: var(--border);
  margin: 0 auto 10px;
}
.title {
  font-size: var(--fs-subtitle);
  font-weight: 800;
  color: var(--title);
  text-align: center;
}
.sub {
  font-size: var(--fs-sm);
  color: var(--caption);
  text-align: center;
  margin-top: 2px;
}
.tip {
  font-size: var(--fs-xs);
  color: var(--caption);
  margin: var(--sp-10) 18px 0;
}
.list {
  flex: 1;
  min-height: 0;
  max-height: 50vh;
  margin-top: var(--sp-6);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-10) 18px;
}
.row-main {
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
.meta {
  font-size: var(--fs-tiny);
  color: var(--caption);
}
.chips {
  display: flex;
  gap: 5px;
  flex-shrink: 0;
}
.chip {
  padding: var(--sp-4) var(--sp-8);
  border-radius: var(--r-pill);
  border: 1px solid var(--border);
  font-size: var(--fs-tiny);
  color: var(--body);
}
.chip.on {
  color: var(--on-primary);
  font-weight: 700;
}
.note {
  margin: var(--sp-8) 18px 0;
  padding: var(--sp-10);
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  font-size: var(--fs-xs);
  color: var(--body);
  line-height: 1.5;
}
.actions {
  display: flex;
  gap: var(--sp-10);
  padding: var(--sp-10) 18px calc(14px + env(safe-area-inset-bottom));
}
.skip { flex: 1; }
.ok { flex: 2; }
.result-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--sp-20) 0 var(--sp-12);
  gap: var(--sp-6);
}
.badge {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--success);
  display: flex;
  align-items: center;
  justify-content: center;
}
.badge-txt {
  color: var(--on-primary);
  font-size: var(--fs-h2);
  font-weight: 800;
}
.result-title {
  font-size: var(--fs-lg);
  font-weight: 800;
  color: var(--title);
}
.result-box {
  margin: 0 18px;
  background: var(--bg);
  border-radius: var(--r-sm);
  padding: var(--sp-10) 14px;
}
.result-box-title {
  font-size: var(--fs-xs);
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
}
.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-6) 0;
}
.result-name {
  font-size: var(--fs-md);
  color: var(--title);
}
.pill {
  padding: 1px var(--sp-8);
  border-radius: var(--r-pill);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.pill.red { background: var(--error); }
.pill.yellow { background: var(--warning); }
.result-none {
  display: block;
  padding: var(--sp-6) 0;
  font-size: var(--fs-sm);
  color: var(--caption);
}
</style>
