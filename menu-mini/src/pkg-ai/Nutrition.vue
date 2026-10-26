<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 营养报告（prototype Nutrition）：成员切换 → 五维雷达（摄入 vs 建议）→ 超标提醒（来源菜 + 替换建议 → 换进餐程）
       → 7 日钠趋势（阈值线）→ 做得好的。图表用 canvas 绘制（小程序不支持内联 SVG），几何计算在 api/mealplan 单测。 -->
  <view class="page">
    <ui-back-header title="营养报告">
      <template #action>
        <view class="range">
          <text class="rg" :class="{ on: range === 'week' }" @click="setRange('week')">本周</text>
          <text class="rg" :class="{ on: range === 'month' }" @click="setRange('month')">本月</text>
        </view>
      </template>
    </ui-back-header>

    <scroll-view scroll-x class="members" :show-scrollbar="false">
      <view class="m-row">
        <view v-for="m in members" :key="m.id" class="m" :class="{ on: m.id === memberId }" @click="pick(m.id)">
          <ui-member-avatar :name="m.name" :tags="m.audienceTags" :size="28" />
          <view class="m-main">
            <text class="m-name">{{ m.name }}</text>
            <text v-if="m.audienceTags?.length" class="m-tag">{{ m.audienceTags[0] }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!report" mode="error" text="报告没生成出来" :retry="true" @retry="load" />
    <scroll-view v-else scroll-y class="body">
      <view class="pad">
        <view class="ai-row" data-testid="nutrition-note"><ui-health-note /></view>
        <view class="card">
          <text class="c-title">{{ report.member.name }}{{ rangeLabel }} {{ okCount }} 项达标<text v-if="report.alerts.length" class="c-bad"> · {{ report.alerts.map((a) => a.label).join('、') }}超标</text></text>
          <text class="c-sub">根据 {{ report.recordCount }} 条食记按每人份估算 · {{ shortDate(report.range.from) }}–{{ shortDate(report.range.to) }}</text>
          <canvas canvas-id="radar" id="radar" class="cv" :style="{ width: cw + 'px', height: '220px' }" />
          <view class="legend">
            <text class="lg"><text class="lg-dot act" />实际摄入</text>
            <text class="lg"><text class="lg-dot tgt" />{{ report.member.profile || '建议量' }}</text>
          </view>
        </view>

        <view v-for="a in report.alerts" :key="a.key" class="card alert">
          <view class="al-head">
            <text class="al-badge">超标</text>
            <text class="al-title">{{ a.label }} {{ rangeLabel }}日均 {{ a.dailyAvg }}{{ a.unit }}</text>
            <text class="al-limit">建议 ≤{{ a.limit }}{{ a.unit }}</text>
          </view>
          <view class="al-bar">
            <view class="al-in" :style="{ width: Math.min(100, (a.dailyAvg / (a.limit * 1.5)) * 100) + '%' }" />
            <view class="al-mark" :style="{ left: (1 / 1.5) * 100 + '%' }" />
          </view>
          <text class="lab">主要来自</text>
          <view class="dishes">
            <view v-for="d in a.sources.slice(0, 3)" :key="d.id" class="dish">
              <ui-dish-image :src="d.coverUrl" :name="d.name" :width="40" :height="40" :radius="8" />
              <text class="dish-name">{{ d.name }}</text>
            </view>
          </view>
          <view v-if="a.suggestions.length" class="swap">
            <text class="swap-txt">下周换成 {{ a.suggestions.map((d) => d.name).join('、') }}</text>
            <view class="swap-btn" data-testid="swap-into-plan" @click="goPlan"><text class="swap-btn-txt">换进餐程</text></view>
          </view>
        </view>

        <view class="card">
          <view class="tr-head">
            <text class="c-title">近 7 天钠摄入</text>
            <text class="c-sub">{{ overDays }} 天超过建议</text>
          </view>
          <canvas canvas-id="trend" id="trend" class="cv" :style="{ width: cw + 'px', height: '150px' }" />
        </view>

        <view v-if="report.goods.length" class="card good">
          <ui-icon name="check" tone="primary" :size="16" />
          <text class="good-txt">做得好：{{ report.goods.join('；') }}</text>
        </view>
        <view style="height: 32px" />
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { linePoints, nutritionReport, radarPoints, type NutritionReport } from '@/api/mealplan'
import { listMembers, type Member } from '@/api/member'
import { palette } from '@/utils/theme'
import { shortDate, weekdayLabel } from '@/utils/week'
import { PAGES } from '@/utils/nav'

const inst = getCurrentInstance()
const members = ref<Member[]>([])
const memberId = ref(0)
const range = ref<'week' | 'month'>('week')
const report = ref<NutritionReport | null>(null)
const loading = ref(true)
const cw = Math.max(260, (uni.getSystemInfoSync().windowWidth || 375) - 64)

const rangeLabel = computed(() => (range.value === 'week' ? '本周' : '本月'))
const okCount = computed(() => report.value?.radar.filter((r) => r.ratio <= 1.05 && r.ratio >= 0.6).length || 0)
const overDays = computed(() => {
  const t = report.value?.trend
  return t ? t.points.filter((p) => p.value > t.limit).length : 0
})

onLoad(async (q: any) => {
  members.value = await listMembers().catch(() => [])
  // 默认选有慢病档案的成员（高血压 / 高血脂），否则第一个
  const has = (re: RegExp) => members.value.find((m) => (m.audienceTags || []).some((t) => re.test(t)))
  const flagged = has(/高血压/) || has(/高血/)
  memberId.value = Number(q?.memberId) || flagged?.id || members.value[0]?.id || 0
  load()
})

function pick(id: number) {
  if (id === memberId.value) return
  memberId.value = id
  load()
}
function setRange(r: 'week' | 'month') {
  if (r === range.value) return
  range.value = r
  load()
}

async function load() {
  if (!memberId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    report.value = await nutritionReport(memberId.value, range.value)
  } catch {
    report.value = null
  } finally {
    loading.value = false
  }
  await nextTick()
  setTimeout(draw, 50)
}

function pts(s: string): [number, number][] {
  return s.split(' ').filter(Boolean).map((p) => p.split(',').map(Number) as [number, number])
}

function poly(ctx: any, list: [number, number][]) {
  list.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)))
  ctx.closePath()
}

function draw() {
  const r = report.value
  if (!r) return
  const p = palette()
  // 雷达
  const ctx: any = uni.createCanvasContext('radar', inst?.proxy as any)
  const cx = cw / 2
  const cy = 112
  const R = 78
  const n = r.radar.length
  ctx.setLineWidth(1)
  for (const level of [0.5, 1, 1.5]) {
    ctx.beginPath()
    poly(ctx, pts(radarPoints(Array(n).fill(level), cx, cy, R)))
    ctx.setStrokeStyle(p.border)
    ctx.stroke()
  }
  // 建议量（ratio=1）虚线
  ctx.beginPath()
  ctx.setLineDash?.([4, 4], 0)
  poly(ctx, pts(radarPoints(Array(n).fill(1), cx, cy, R)))
  ctx.setStrokeStyle(p.caption)
  ctx.stroke()
  ctx.setLineDash?.([], 0)
  // 实际
  ctx.beginPath()
  poly(ctx, pts(radarPoints(r.radar.map((x) => x.ratio), cx, cy, R)))
  ctx.setGlobalAlpha?.(0.55)
  ctx.setFillStyle(p.primarySoft)
  ctx.fill()
  ctx.setGlobalAlpha?.(1)
  ctx.setStrokeStyle(p.primaryDeep)
  ctx.setLineWidth(2)
  ctx.stroke()
  // 标签
  ctx.setFontSize(11)
  ctx.setTextAlign('center')
  pts(radarPoints(Array(n).fill(1.5), cx, cy, R + 18)).forEach(([x, y], i) => {
    const it = r.radar[i]
    ctx.setFillStyle(it.ratio > 1.05 ? p.primaryDeep : p.body)
    ctx.fillText(it.label, x, y)
    ctx.fillText(`${Math.round(it.ratio * 100)}%`, x, y + 13)
  })
  ctx.draw()

  // 趋势
  const t = r.trend
  const tc: any = uni.createCanvasContext('trend', inst?.proxy as any)
  const H = 150
  const values = t.points.map((x) => x.value)
  const yMax = Math.max(t.limit, ...values) * 1.2
  const pad = 22
  const limitY = H - pad - (t.limit / (yMax || 1)) * (H - pad * 2)
  tc.beginPath()
  tc.setLineDash?.([4, 4], 0)
  tc.moveTo(pad, limitY)
  tc.lineTo(cw - pad, limitY)
  tc.setStrokeStyle(p.caption)
  tc.setLineWidth(1)
  tc.stroke()
  tc.setLineDash?.([], 0)
  tc.setFontSize(10)
  tc.setFillStyle(p.body)
  tc.setTextAlign('right')
  tc.fillText(`建议 ${t.limit}`, cw - pad, limitY - 4)
  const line = pts(linePoints(values, cw, H, pad, yMax))
  tc.beginPath()
  line.forEach(([x, y], i) => (i ? tc.lineTo(x, y) : tc.moveTo(x, y)))
  tc.setStrokeStyle(p.primaryDeep)
  tc.setLineWidth(2)
  tc.stroke()
  tc.setTextAlign('center')
  line.forEach(([x, y], i) => {
    const over = values[i] > t.limit
    tc.beginPath()
    tc.arc(x, y, 3.5, 0, Math.PI * 2)
    tc.setFillStyle(over ? p.primaryDeep : p.card)
    tc.fill()
    tc.setStrokeStyle(p.primaryDeep)
    tc.stroke()
    tc.setFillStyle(p.body)
    tc.fillText(weekdayLabel(t.points[i].date).slice(1), x, H - 6)
  })
  tc.draw()
}

function goPlan() {
  uni.navigateTo({ url: PAGES.weekPlan })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.range {
  display: flex;
  padding: 3px;
  border-radius: var(--radius-pill);
  background: var(--secondary);
}
.rg {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-sm);
  color: var(--body);
}
.rg.on {
  background: var(--card);
  color: var(--title);
  font-weight: 700;
}
.members {
  white-space: nowrap;
  flex-shrink: 0;
}
.m-row {
  display: flex;
  gap: var(--space-sm);
  padding: 0 var(--space-lg) var(--space-md);
}
.m {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px 4px 4px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
}
.m.on {
  border-color: var(--primary-deep);
  background: var(--highlight);
}
.m-main {
  display: flex;
  flex-direction: column;
}
.m-name {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.m-tag {
  font-size: var(--fs-tiny);
  color: var(--body);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.card {
  padding: 14px 16px;
  margin-bottom: var(--space-md);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.c-title {
  display: block;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.c-bad {
  color: var(--primary-deep);
}
.c-sub {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--body);
}
.cv {
  display: block;
  margin-top: var(--space-sm);
}
.legend {
  display: flex;
  justify-content: center;
  gap: var(--space-lg);
}
.lg {
  font-size: var(--fs-xs);
  color: var(--body);
}
.lg-dot {
  display: inline-block;
  width: 10px;
  height: 3px;
  margin-right: 4px;
  vertical-align: middle;
}
.lg-dot.act {
  background: var(--primary-deep);
}
.lg-dot.tgt {
  border-top: 2px dashed var(--caption);
}
.alert {
  border-color: var(--error-soft);
}
.al-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.al-badge {
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.al-title {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.al-limit {
  font-size: var(--fs-xs);
  color: var(--body);
}
.al-bar {
  position: relative;
  height: 8px;
  margin: 10px 0;
  border-radius: 4px;
  background: var(--secondary);
}
.al-in {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--warning), var(--error));
}
.al-mark {
  position: absolute;
  top: -3px;
  width: 2px;
  height: 14px;
  background: var(--title);
}
.lab {
  display: block;
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  margin-bottom: 6px;
}
.dishes {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}
.dish {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 4px;
  border-radius: var(--radius-md);
  background: var(--bg);
  border: 1px solid var(--border);
}
.dish-name {
  font-size: var(--fs-sm);
  color: var(--title);
}
.swap {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--success-soft);
}
.swap-txt {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--success-deep);
}
.swap-btn {
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
}
.swap-btn-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
}
.tr-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.good {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--success-soft);
  border-color: var(--success-soft);
}
.good-txt {
  font-size: var(--fs-sm);
  color: var(--success-deep);
}
.ai-row {
  margin-bottom: var(--sp-12);
}
</style>
