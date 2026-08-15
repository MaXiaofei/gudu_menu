<template>
  <!-- 统一时间选择胶囊（对齐 Flutter TimeSelectCapsule）：点击弹出逐级选择器
       年 → 月 → 日 → 时→分（按粒度截断）。月份切换 day 自动 clamp。 -->
  <view class="ts">
    <view class="capsule" @click="open">
      <text class="capsule-txt">{{ label }}</text>
      <text class="capsule-caret">▾</text>
    </view>

    <!-- 逐级弹层 -->
    <view v-if="visible" class="mask" @click="visible = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="path">{{ pathText }}</text>
          <text class="done" @click="finish">完成</text>
        </view>
        <scroll-view scroll-y class="grid-wrap">
          <!-- 年（当前 ±10，3 列） -->
          <template v-if="step === 0">
            <text class="grid-title">选择年</text>
            <view class="grid grid3">
              <view
                v-for="y in years"
                :key="y"
                class="cell"
                :class="{ on: y === pickedYear }"
                @click="pickYear(y)"
              >{{ y }}</view>
            </view>
          </template>
          <!-- 月 -->
          <template v-else-if="step === 1">
            <text class="grid-title">选择月</text>
            <view class="grid grid3">
              <view
                v-for="m in 12"
                :key="m"
                class="cell"
                :class="{ on: m === pickedMonth }"
                @click="pickMonth(m)"
              >{{ m }}月</view>
            </view>
          </template>
          <!-- 日（7 列日历，周一起） -->
          <template v-else-if="step === 2">
            <text class="grid-title">选择日</text>
            <view class="week-head">
              <text v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="week-txt">{{ w }}</text>
            </view>
            <view class="grid grid7">
              <view v-for="i in leading" :key="'e' + i" class="cell blank" />
              <view
                v-for="d in daysInMonth"
                :key="d"
                class="cell"
                :class="{ on: d === pickedDay }"
                @click="pickDay(d)"
              >{{ d }}</view>
            </view>
          </template>
          <!-- 时→分（5 分步进） -->
          <template v-else>
            <text class="grid-title">{{ hourChosen ? '选择分' : '选择时' }}</text>
            <view class="grid grid3">
              <view
                v-for="h in 24"
                v-show="!hourChosen"
                :key="'h' + h"
                class="cell"
                :class="{ on: h - 1 === pickedHour }"
                @click="pickHour(h - 1)"
              >{{ String(h - 1).padStart(2, '0') }}</view>
              <view
                v-for="i in 12"
                v-show="hourChosen"
                :key="'m' + i"
                class="cell"
                :class="{ on: (i - 1) * 5 === pickedMinute }"
                @click="pickMinute((i - 1) * 5)"
              >{{ String((i - 1) * 5).padStart(2, '0') }}</view>
            </view>
          </template>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

/** 粒度：year 只选年 / month 年月 / day 年月日 / time 年月日+时分。 */
export type Granularity = 'year' | 'month' | 'day' | 'time'

const props = withDefaults(
  defineProps<{
    value: Date
    granularity?: Granularity
  }>(),
  { granularity: 'month' },
)

const emit = defineEmits<{ (e: 'change', v: Date): void }>()

const visible = ref(false)
const step = ref(0)
const hourChosen = ref(false)

const pickedYear = ref(props.value.getFullYear())
const pickedMonth = ref(props.value.getMonth() + 1)
const pickedDay = ref(props.value.getDate())
const pickedHour = ref(props.value.getHours())
const pickedMinute = ref(Math.floor(props.value.getMinutes() / 5) * 5)

const maxStep = computed(() =>
  props.granularity === 'year' ? 0 : props.granularity === 'month' ? 1 : props.granularity === 'day' ? 2 : 3,
)

const label = computed(() => {
  const v = props.value
  switch (props.granularity) {
    case 'year': return `${v.getFullYear()}年`
    case 'month': return `${v.getFullYear()}年${v.getMonth() + 1}月`
    case 'day': return `${v.getFullYear()}年${v.getMonth() + 1}月${v.getDate()}日`
    default:
      return `${v.getFullYear()}年${v.getMonth() + 1}月${v.getDate()}日 ${String(v.getHours()).padStart(2, '0')}:${String(v.getMinutes()).padStart(2, '0')}`
  }
})

const years = computed(() => {
  const base = pickedYear.value
  return Array.from({ length: 21 }, (_, i) => base - 10 + i)
})

const daysInMonth = computed(() => new Date(pickedYear.value, pickedMonth.value, 0).getDate())
const leading = computed(() => new Date(pickedYear.value, pickedMonth.value - 1, 1).getDay() === 0 ? 6 : new Date(pickedYear.value, pickedMonth.value - 1, 1).getDay() - 1)

const pathText = computed(() => {
  let s = `${pickedYear.value}年`
  if (step.value >= 1) s += `${pickedMonth.value}月`
  if (step.value >= 2) s += `${pickedDay.value}日`
  if (step.value >= 3) s += ` ${hourChosen.value ? String(pickedHour.value).padStart(2, '0') : '--'}:${String(pickedMinute.value).padStart(2, '0')}`
  return s
})

function open() {
  // 从当前 value 重置
  pickedYear.value = props.value.getFullYear()
  pickedMonth.value = props.value.getMonth() + 1
  pickedDay.value = props.value.getDate()
  hourChosen.value = false
  step.value = 0
  visible.value = true
}

function nextStep() {
  if (step.value < maxStep.value) {
    step.value += 1
    if (step.value === 3) hourChosen.value = false
  } else {
    emitAndClose()
  }
}

function emitAndClose() {
  emit('change', new Date(pickedYear.value, pickedMonth.value - 1, pickedDay.value, pickedHour.value, pickedMinute.value))
  visible.value = false
}

function finish() {
  emitAndClose()
}

function pickYear(y: number) {
  pickedYear.value = y
  nextStep()
}
function pickMonth(m: number) {
  pickedMonth.value = m
  pickedDay.value = Math.min(pickedDay.value, new Date(pickedYear.value, m, 0).getDate())
  nextStep()
}
function pickDay(d: number) {
  pickedDay.value = d
  nextStep()
}
function pickHour(h: number) {
  pickedHour.value = h
  if (step.value === 3) hourChosen.value = true
}
function pickMinute(m: number) {
  pickedMinute.value = m
  if (step.value === 3) nextStep()
}
</script>

<style scoped>
.capsule {
  display: flex;
  align-items: center;
  gap: 3px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 3px 9px;
}
.capsule-txt {
  font-size: 12px;
  font-weight: 800;
  color: var(--title);
}
.capsule-caret {
  font-size: 10px;
  color: var(--caption);
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  padding: 14px 16px calc(12px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  height: 60vh;
  box-sizing: border-box;
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.path {
  font-size: 15px;
  font-weight: 800;
  color: var(--title);
}
.done {
  font-size: 12px;
  font-weight: 800;
  color: var(--primary);
}
.grid-wrap {
  flex: 1;
  min-height: 0;
}
.grid-title {
  display: block;
  font-size: 10px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin-bottom: 8px;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.grid3 .cell {
  width: calc((100% - 12px) / 3);
}
.grid7 {
  gap: 4px;
}
.grid7 .cell {
  width: calc((100% - 24px) / 7);
}
.cell {
  height: 36px;
  border-radius: 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--body);
  box-sizing: border-box;
}
.cell.on {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
  color: #FFFFFF;
  font-weight: 800;
}
.cell.blank {
  background: transparent;
  border-color: transparent;
}
.week-head {
  display: flex;
  margin-bottom: 4px;
}
.week-txt {
  flex: 1;
  text-align: center;
  font-size: 10px;
  color: var(--caption);
}
</style>
