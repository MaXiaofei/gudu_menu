<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 跟做模式（prototype CookMode；cook-mode spec）：深色沉浸、屏幕常亮、分段进度、大图大字、
       步骤时长 → 计时器（可并行，到点震动提示）、问咕嘟（带菜谱与步骤上下文）、上一步 / 下一步、最后一步进做完确认。 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: inset + 'px' }">
      <view class="circle" data-testid="cook-exit" @click="exit"><ui-icon name="x" tone="white" :size="18" /></view>
      <view class="top-main">
        <text class="top-title">{{ dish?.name || '跟做' }}</text>
        <text class="top-sub">第 {{ idx + 1 }} / {{ steps.length || 1 }} 步{{ leftText }}</text>
      </view>
      <view class="awake"><ui-icon name="sun" tone="white" :size="12" /><text class="awake-txt">屏幕常亮</text></view>
    </view>
    <view class="segs">
      <view v-for="(s, i) in steps" :key="i" class="seg" :class="{ done: i < idx, cur: i === idx }" @click="goStep(i)" />
    </view>

    <ui-state v-if="loading" mode="loading" />
    <scroll-view v-else scroll-y class="body">
      <view class="pad">
        <view v-if="stepImage" class="hero">
          <image class="hero-img" :src="stepImage" mode="aspectFill" />
          <text class="hero-n">STEP {{ idx + 1 }}</text>
        </view>
        <text class="step-txt" data-testid="cook-step-text">{{ step?.text || '这道菜还没有步骤' }}</text>

        <view class="timer-row">
          <view v-for="t in stepTimers" :key="t.text" class="tm-btn" :data-testid="'start-timer-' + t.seconds" @click="startTimer(t.seconds, t.text)">
            <view class="tm-play"><ui-icon name="play" tone="white" :size="12" /></view>
            <text class="tm-btn-txt">{{ t.text }} · {{ formatClock(t.seconds) }}</text>
          </view>
          <text v-if="nextText" class="next">下一步：{{ nextText }}</text>
        </view>

        <!-- 问咕嘟 -->
        <view class="qa">
          <text class="qa-lab">问咕嘟 · 带着这一步的上下文</text>
          <template v-for="(m, i) in chat.messages" :key="i">
            <view v-if="m.role === 'user'" class="q"><text class="q-txt">{{ m.text }}</text></view>
            <view v-else class="a">
              <image class="a-av" src="/static/brand/logo.png" mode="aspectFit" />
              <text class="a-txt">{{ m.text || (m.streaming ? '想一想…' : m.error?.message || '') }}<text v-if="m.streaming" class="cursor">▍</text></text>
            </view>
          </template>
          <view class="qa-ipt">
            <input v-model="question" class="ipt" placeholder="比如：没有郫县豆瓣酱用什么代替？" placeholder-class="ph" confirm-type="send" data-testid="cook-ask" @confirm="ask" />
            <view class="ask-btn" @click="ask"><ui-icon name="send" tone="white" :size="14" /></view>
          </view>
        </view>
        <view style="height: 24px" />
      </view>
    </scroll-view>

    <view class="foot">
      <view v-if="timers.length" class="timers">
        <view v-for="t in timers" :key="t.id" class="tm" :class="{ hot: isHot(t, now), end: remaining(t, now) === 0 }" @click="removeTimer(t.id)">
          <view class="ring">
            <view class="ring-in" :style="{ height: progress(t, now) * 100 + '%' }" />
          </view>
          <view class="tm-main">
            <text class="tm-time">{{ remaining(t, now) ? formatClock(remaining(t, now)) : '到点了' }}</text>
            <text class="tm-label">{{ t.label }}{{ isHot(t, now) ? ' · 快到点' : '' }}</text>
          </view>
        </view>
      </view>
      <view class="voice" @click="focusAsk"><view class="wv"><view v-for="i in 7" :key="i" class="wb" :style="{ animationDelay: i * 0.1 + 's' }" /></view><text class="voice-txt">说「下一步」或问咕嘟</text></view>
      <view class="nav">
        <view class="prev" :class="{ off: idx === 0 }" data-testid="cook-prev" @click="prev"><text class="nav-txt">‹ 上一步</text></view>
        <view class="nextb" data-testid="cook-next" @click="next"><text class="nav-txt">{{ idx >= steps.length - 1 ? '做好了' : '下一步 ›' }}</text></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { dishDetail, type Dish, type DishStep } from '@/api/dish'
import { parseVoiceCommand } from '@/api/agent'
import { useAgentChat } from '@/composables/useAgentChat'
import { formatClock, isHot, parseStepTimers, progress, remaining, type RunningTimer } from '@/utils/cookTimer'
import { toAbsolute } from '@/utils/image'
import { navInset } from '@/utils/token'
import { COOK_AFTER_MODE_KEY } from '@/utils/nav'

const inset = navInset()
const dishId = ref(0)
const dish = ref<Dish | null>(null)
const steps = ref<DishStep[]>([])
const idx = ref(0)
const loading = ref(true)
const question = ref('')
const timers = ref<RunningTimer[]>([])
const now = ref(Date.now())
const ctxRef = { page: 'cook_mode', dishId: 0, step: 1 }
const chat = useAgentChat({ context: ctxRef })
let tick: ReturnType<typeof setInterval> | null = null
const rang = new Set<string>()

onLoad(async (q: any) => {
  dishId.value = Number(q?.id) || 0
  ctxRef.dishId = dishId.value
  try {
    const d = await dishDetail(dishId.value)
    dish.value = d.dish
    steps.value = d.steps || []
  } finally {
    loading.value = false
  }
})
onShow(() => {
  uni.setKeepScreenOn({ keepScreenOn: true })
  tick = setInterval(onTick, 1000)
})
onHide(stopTick)
onUnload(() => {
  stopTick()
  chat.stop()
})
function stopTick() {
  uni.setKeepScreenOn({ keepScreenOn: false })
  if (tick) clearInterval(tick)
  tick = null
}

const step = computed(() => steps.value[idx.value])
const stepImage = computed(() => {
  const first = (step.value?.images || '').split(',').filter(Boolean)[0]
  return first ? toAbsolute(first) : dish.value?.coverUrl ? toAbsolute(dish.value.coverUrl) : ''
})
const stepTimers = computed(() => parseStepTimers(step.value?.text || ''))
const nextText = computed(() => {
  const t = steps.value[idx.value + 1]?.text || ''
  return t.length > 14 ? t.slice(0, 14) + '…' : t
})
const leftText = computed(() => {
  const secs = steps.value.slice(idx.value).reduce((s, x) => s + parseStepTimers(x.text).reduce((a, b) => a + b.seconds, 0), 0)
  return secs ? ` · 还剩约 ${Math.ceil(secs / 60)} 分钟` : ''
})

function goStep(i: number) {
  idx.value = Math.max(0, Math.min(steps.value.length - 1, i))
  ctxRef.step = idx.value + 1
}
function prev() {
  if (idx.value > 0) goStep(idx.value - 1)
}
function next() {
  if (idx.value < steps.value.length - 1) return goStep(idx.value + 1)
  // 最后一步：回详情打开做完确认（回写余量）
  uni.setStorageSync(COOK_AFTER_MODE_KEY, dishId.value)
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.redirectTo({ url: `/pages/dish/Detail?id=${dishId.value}` })
}

function startTimer(seconds: number, text: string) {
  const label = `第 ${idx.value + 1} 步 · ${text}`
  timers.value.push({ id: `${Date.now()}`, label, total: seconds, endAt: Date.now() + seconds * 1000 })
  uni.showToast({ title: `${formatClock(seconds)} 计时开始`, icon: 'none' })
}
function removeTimer(id: string) {
  const t = timers.value.find((x) => x.id === id)
  if (t && remaining(t, now.value) > 0) {
    uni.showModal({ title: '取消计时？', content: t.label, success: ({ confirm }) => confirm && (timers.value = timers.value.filter((x) => x.id !== id)) })
  } else timers.value = timers.value.filter((x) => x.id !== id)
}
function onTick() {
  now.value = Date.now()
  for (const t of timers.value) {
    if (remaining(t, now.value) === 0 && !rang.has(t.id)) {
      rang.add(t.id)
      uni.vibrateLong({})
      uni.showToast({ title: `到点了：${t.label}`, icon: 'none', duration: 3000 })
    }
  }
}

async function ask() {
  const q = question.value.trim()
  if (!q) return
  question.value = ''
  // 本地指令不走模型
  const cmd = parseVoiceCommand(q)
  if (cmd === 'next') return next()
  if (cmd === 'prev') return prev()
  if (cmd === 'timer' && stepTimers.value[0]) return startTimer(stepTimers.value[0].seconds, stepTimers.value[0].text)
  if (cmd === 'pause') return uni.showToast({ title: '已暂停，说「继续」接着做', icon: 'none' })
  await chat.send(q)
}
function focusAsk() {
  uni.showToast({ title: '在下方输入，或说「下一步」', icon: 'none' })
}
function exit() {
  if (timers.value.some((t) => remaining(t, now.value) > 0)) {
    uni.showModal({ title: '退出跟做？', content: '还有计时器在走', success: ({ confirm }) => confirm && uni.navigateBack() })
  } else uni.navigateBack()
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1c1511;
  color: var(--on-primary);
}
.top {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 var(--space-lg) var(--space-sm);
}
.circle {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}
.top-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.top-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
.top-sub {
  font-size: var(--fs-xs);
  color: var(--primary-soft);
}
.awake {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: rgba(232, 145, 80, 0.25);
}
.awake-txt {
  font-size: var(--fs-xs);
  color: var(--primary-soft);
}
.segs {
  display: flex;
  gap: 4px;
  padding: 0 var(--space-lg) var(--space-sm);
}
.seg {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.18);
}
.seg.done {
  background: var(--primary);
}
.seg.cur {
  background: var(--primary-soft);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.hero {
  position: relative;
  height: 170px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
}
.hero-img {
  width: 100%;
  height: 100%;
}
.hero-n {
  position: absolute;
  left: 12px;
  top: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: rgba(28, 21, 17, 0.7);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 1px;
}
.step-txt {
  display: block;
  margin: var(--space-lg) 0 var(--space-md);
  font-size: var(--fs-h3);
  font-weight: 800;
  line-height: 1.6;
  color: var(--on-primary);
}
.timer-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.tm-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 16px 0 6px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  box-shadow: 0 6px 16px rgba(209, 122, 60, 0.35);
}
.tm-play {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
}
.tm-btn-txt {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
.next {
  font-size: var(--fs-sm);
  color: var(--primary-soft);
}
.qa {
  margin-top: var(--space-lg);
  padding: 12px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.qa-lab {
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--primary-soft);
}
.q {
  align-self: flex-end;
  max-width: 84%;
  padding: 8px 12px;
  border-radius: 14px 14px 4px 14px;
  background: var(--secondary);
}
.q-txt {
  font-size: var(--fs-md);
  color: var(--title);
}
.a {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-start;
}
.a-av {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  background: var(--card);
  flex-shrink: 0;
}
.a-txt {
  flex: 1;
  padding: 8px 12px;
  border-radius: 4px 14px 14px 14px;
  background: rgba(255, 255, 255, 0.1);
  font-size: var(--fs-md);
  line-height: 1.5;
  color: var(--on-primary);
}
.cursor {
  color: var(--primary);
}
.qa-ipt {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.ipt {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.1);
  color: var(--on-primary);
  font-size: var(--fs-sm);
}
.ph {
  color: rgba(255, 255, 255, 0.5);
}
.ask-btn {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.foot {
  flex-shrink: 0;
  padding: 10px var(--space-lg) calc(16px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #1c1511;
}
.timers {
  display: flex;
  gap: var(--space-sm);
}
.tm {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.07);
}
.tm.hot {
  background: rgba(229, 169, 56, 0.18);
  box-shadow: inset 0 0 0 1.5px var(--warning);
}
.tm.end {
  background: rgba(219, 90, 78, 0.25);
}
.ring {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-pill);
  border: 3px solid rgba(255, 255, 255, 0.16);
  overflow: hidden;
  flex-shrink: 0;
}
.ring-in {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--primary);
  opacity: 0.8;
}
.tm-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.tm-time {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--on-primary);
}
.tm.hot .tm-time {
  color: var(--warning);
}
.tm-label {
  font-size: var(--fs-xs);
  color: var(--primary-soft);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.voice {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.1);
}
.wv {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 18px;
}
.wb {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--primary);
  animation: wv 1s ease-in-out infinite alternate;
}
@keyframes wv {
  to {
    transform: scaleY(0.4);
  }
}
.voice-txt {
  font-size: var(--fs-sm);
  color: var(--on-primary);
}
.nav {
  display: flex;
  gap: var(--space-sm);
}
.prev {
  flex: 1;
  height: 54px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.prev.off {
  opacity: 0.4;
}
.nextb {
  flex: 1.6;
  height: 54px;
  border-radius: var(--radius-lg);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-txt {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
</style>
