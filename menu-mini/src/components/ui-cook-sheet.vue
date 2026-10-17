<template>
  <!-- 做完确认（prototype CookSheet）：确认 → 结果 两态。
       食材默认「用完」，调料默认「剩一些」（降一档），点一下可改；父组件负责调接口，成功后置 done。 -->
  <view class="mask" @click="onMask">
    <view class="sheet" @click.stop>
      <view class="drag-bar" />
      <template v-if="!done">
        <view class="head">
          <ui-dish-image v-if="cover !== undefined" :src="cover" :name="resultSub" :width="56" :height="56" :radius="12" />
          <view class="head-main">
            <text class="title">{{ title }}</text>
            <text class="sub">确认一下用掉了什么，家里的余量会跟着更新</text>
          </view>
        </view>

        <scroll-view scroll-y class="list">
          <view v-if="showServings" class="block">
            <text class="label">做了几人份</text>
            <view class="stepper">
              <view class="st-btn" aria-label="减少" @click="servings = Math.max(1, servings - 1)"><text class="st-ic">−</text></view>
              <text class="st-num">{{ servings }}</text>
              <view class="st-btn" aria-label="增加" @click="servings = Math.min(20, servings + 1)"><text class="st-ic">+</text></view>
            </view>
          </view>

          <text class="label pad">用料去向</text>
          <ui-state v-if="!items.length" mode="empty" text="这道菜还没有登记用料" hint="直接确认也会记到食记" :illus="['rice']" />
          <view v-for="m in items" :key="m.ingredientId" class="row">
            <ui-ing-icon :icon-key="m.iconKey" :name="m.ingredientName" :size="40" :out="m.level === 'NONE'" />
            <view class="row-main">
              <text class="name">{{ m.ingredientName }}</text>
              <text class="meta">{{ metaOf(m) }}</text>
            </view>
            <view class="chips">
              <view
                v-for="opt in options"
                :key="opt.value"
                class="chip"
                :class="[{ on: sel[m.ingredientId] === opt.value }, 'c' + opt.value]"
                @click="sel[m.ingredientId] = opt.value"
              ><text class="chip-txt">{{ opt.label }}</text></view>
            </view>
          </view>

          <view v-if="allowNote" class="block col">
            <text class="label">写一句食记（可选）</text>
            <textarea v-model="note" class="note-ipt" maxlength="200" placeholder="比如：蒸 8 分钟刚好，下次少放点豉油" placeholder-class="ph" :auto-height="true" />
          </view>
        </scroll-view>

        <view class="actions">
          <button class="btn-skip skip" :disabled="submitting" @click="emitSubmit(true)">跳过</button>
          <button class="btn-ok ok" :disabled="submitting" @click="emitSubmit(false)">
            {{ submitting ? '提交中…' : '记一笔，更新余量' }}
          </button>
        </view>
        <text class="foot">记错了可以在食记里撤销，余量会一起恢复</text>
      </template>

      <template v-else>
        <view class="result-head">
          <view class="badge"><text class="badge-txt">✓</text></view>
          <text class="result-title">做好了，已记到食记</text>
          <text v-if="resultSub" class="sub">{{ resultSub }}</text>
        </view>
        <view class="result-box">
          <text class="result-box-title">我家余量已更新</text>
          <view v-for="m in doneUsedUp" :key="'u' + m.ingredientId" class="result-row">
            <ui-ing-icon :icon-key="m.iconKey" :name="m.ingredientName" :size="28" bare />
            <text class="result-name">{{ m.ingredientName }}</text>
            <text class="pill red">用完</text>
          </view>
          <view v-for="m in donePartial" :key="'p' + m.ingredientId" class="result-row">
            <ui-ing-icon :icon-key="m.iconKey" :name="m.ingredientName" :size="28" bare />
            <text class="result-name">{{ m.ingredientName }}</text>
            <text class="pill yellow">剩一些</text>
          </view>
          <text v-if="!doneUsedUp.length && !donePartial.length" class="result-none">本次没有更新余量</text>
        </view>
        <view class="actions">
          <button class="btn-skip skip" @click="$emit('close')">返回</button>
          <button class="btn-ok ok" @click="$emit('foodlog')">去食记看看</button>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, watch, ref } from 'vue'
import type { CookMaterial } from '@/api/menu'
import { stockLabel } from '@/utils/token'
import UiDishImage from './ui-dish-image.vue'
import UiIngIcon from './ui-ing-icon.vue'

const props = withDefaults(
  defineProps<{
    items: CookMaterial[]
    title?: string
    submitting?: boolean
    done?: boolean
    resultSub?: string
    /** 成品图（undefined = 不显示头图） */
    cover?: string | null
    /** 显示「做了几人份」（单菜直做） */
    showServings?: boolean
    /** 显示「写一句食记」 */
    allowNote?: boolean
  }>(),
  { showServings: false, allowNote: false },
)
const emit = defineEmits<{
  (e: 'submit', p: { usedUp: number[]; partiallyUsed: number[]; servings: number; note: string }): void
  (e: 'close'): void
  (e: 'foodlog'): void
}>()

const title = computed(() => props.title || (props.resultSub ? `${props.resultSub}做好了` : '这顿饭用了什么'))
// 0 用完 / 1 剩一些 / 2 没动
const sel = reactive<Record<number, number>>({})
const options = [
  { value: 0, label: '用完' },
  { value: 1, label: '剩一些' },
  { value: 2, label: '没动' },
]
const servings = ref(1)
const note = ref('')

watch(
  () => props.items,
  (list) => {
    Object.keys(sel).forEach((k) => delete sel[Number(k)])
    list.forEach((m) => (sel[m.ingredientId] = m.level === 'NONE' ? 2 : m.isCondiment ? 1 : 0))
  },
  { immediate: true },
)

const usedUp = computed(() => props.items.filter((m) => sel[m.ingredientId] === 0).map((m) => m.ingredientId))
const partial = computed(() => props.items.filter((m) => sel[m.ingredientId] === 1).map((m) => m.ingredientId))

const AFTER = ['用完', '变不足', '不变']
function metaOf(m: CookMaterial): string {
  if (!m.level || m.level === 'NONE') return '家里没有，已跳过也没关系'
  const usage = (m.usageTexts ?? []).join(' + ')
  const flow = `家里：${stockLabel(m.level)} → ${AFTER[sel[m.ingredientId] ?? 2]}`
  return usage ? `${usage} · ${flow}` : flow
}

const submitted = ref<{ usedUp: number[]; partiallyUsed: number[] }>({ usedUp: [], partiallyUsed: [] })
const doneUsedUp = computed(() => props.items.filter((m) => submitted.value.usedUp.includes(m.ingredientId)))
const donePartial = computed(() => props.items.filter((m) => submitted.value.partiallyUsed.includes(m.ingredientId)))

function emitSubmit(skip: boolean) {
  if (props.submitting) return
  const p = skip ? { usedUp: [], partiallyUsed: [] } : { usedUp: usedUp.value, partiallyUsed: partial.value }
  submitted.value = p
  emit('submit', { ...p, servings: servings.value, note: note.value })
}

function onMask() {
  if (!props.done && !props.submitting) emit('close')
}
</script>

<style scoped>
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--mask);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 88vh;
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
.head {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: 0 18px var(--sp-8);
}
.head-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.list {
  flex: 1;
  min-height: 0;
  max-height: 56vh;
}
.block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--sp-8) 18px;
}
.block.col {
  flex-direction: column;
  align-items: stretch;
  gap: var(--sp-8);
}
.label {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.label.pad {
  display: block;
  padding: var(--sp-8) 18px 0;
}
.stepper {
  display: flex;
  align-items: center;
  gap: var(--sp-16);
}
.st-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
  background: var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.st-ic {
  font-size: var(--fs-lg);
  color: var(--title);
}
.st-num {
  font-size: var(--fs-h3);
  font-weight: 700;
  color: var(--title);
  min-width: 20px;
  text-align: center;
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
  font-size: var(--fs-xs);
  color: var(--body);
}
.chips {
  display: flex;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.chip {
  padding: 6px 8px;
}
.chip-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.chip.on .chip-txt {
  color: var(--on-primary);
  font-weight: 700;
}
.chip.on.c0 { background: var(--error); }
.chip.on.c1 { background: var(--warning); }
.chip.on.c2 { background: var(--body); }
.note-ipt {
  width: 100%;
  min-height: 56px;
  box-sizing: border-box;
  padding: var(--sp-10);
  border-radius: var(--r-md);
  background: var(--bg);
  border: 1px solid var(--border);
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--caption);
}
.actions {
  display: flex;
  gap: var(--sp-10);
  padding: var(--sp-10) 18px var(--sp-6);
}
.btn-skip,
.btn-ok {
  height: 48px;
  line-height: 48px;
  margin: 0;
  border-radius: var(--r-md);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.btn-skip::after,
.btn-ok::after {
  border: none;
}
.btn-skip {
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
}
.btn-ok {
  background: var(--primary-deep);
  color: var(--on-primary);
}
.btn-ok[disabled] {
  background: var(--primary-soft);
  color: var(--on-primary);
}
.skip { flex: 1; }
.ok { flex: 2.2; }
.foot {
  display: block;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--body);
  padding: 0 18px calc(12px + env(safe-area-inset-bottom));
}
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
  border-radius: var(--r-md);
  padding: var(--sp-10) 14px;
}
.result-box-title {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.result-row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  padding: var(--sp-6) 0;
}
.result-name {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.pill {
  padding: 1px var(--sp-8);
  border-radius: var(--r-pill);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.pill.red { background: var(--error); }
.pill.yellow { background: var(--warning); }
.result-none {
  display: block;
  padding: var(--sp-6) 0;
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
