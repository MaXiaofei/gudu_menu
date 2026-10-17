<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { User, UserFilled, TrendCharts, Food, MagicStick, WarningFilled, ChatDotRound } from '@element-plus/icons-vue'
import { useThemeStore } from '@/store/theme'
import { useBadgeStore } from '@/store/badges'
import { getOpsStats, type OpsStats } from '@/api/ops'
import { searchDishes, type DishSearchRow } from '@/api/dish'
import { listIngredients } from '@/api/ingredient'
import { listFeedback, type Feedback } from '@/api/feedback'
import { init, type ECharts } from '@/utils/echarts'
import { buildKpiCards, buildTrendOption, formatCount, isTrendEmpty, normalizeTrend, type KpiTile } from '@/utils/dashboard'
import { hasNutrition } from '@/utils/nutrition'
import StatCard from '@/components/StatCard.vue'
import DishThumb from '@/components/DishThumb.vue'
import IngIcon from '@/components/IngIcon.vue'
import AvatarStack from '@/components/AvatarStack.vue'

const DAY_OPTIONS = [7, 14, 30] as const

const router = useRouter()
const themeStore = useThemeStore()
const badgeStore = useBadgeStore()

const days = ref<number>(14)
const loading = ref(true)
const error = ref('')
const stats = shallowRef<OpsStats | null>(null)
const updatedAt = ref('')

const KPI_ICON: Record<string, Component> = {
  accounts: User,
  members: UserFilled,
  activeMembers: TrendCharts,
  cookRecords: Food,
  aiCostYuan: MagicStick,
}
const kpis = computed(() => (stats.value ? buildKpiCards(stats.value) : []))
const trend = computed(() => normalizeTrend(stats.value?.trend))
const trendEmpty = computed(() => isTrendEmpty(trend.value))
const feedbackOpen = computed(() => Number(stats.value?.totals?.feedbackOpen ?? 0))

// —— 待处理 & 菜谱排行（各自独立加载，失败只影响自己的区块）——
const topDishes = ref<DishSearchRow[]>([])
const topLoaded = ref(false)
const hasCookedData = computed(() => topDishes.value.some((d) => (d.cookedCount ?? 0) > 0))
const missingNutrition = ref<{ count: number; sample: string[] } | null>(null)
const recentFeedback = ref<Feedback[]>([])

const chartRef = ref<HTMLDivElement>()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function renderChart() {
  if (!chartRef.value || trendEmpty.value) {
    disposeChart()
    return
  }
  if (!chart) {
    chart = init(chartRef.value)
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartRef.value)
  }
  chart.setOption(buildTrendOption(trend.value, themeStore.currentTheme()), true)
}

function disposeChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

function nowText() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  disposeChart() // loading 态会卸载图表 DOM，重载前先释放实例
  loading.value = true
  error.value = ''
  try {
    stats.value = await getOpsStats(days.value)
    badgeStore.setFeedbackOpen(stats.value?.totals?.feedbackOpen)
    updatedAt.value = nowText()
  } catch (e) {
    error.value = (e as Error)?.message || '加载失败'
    stats.value = null
  } finally {
    loading.value = false
  }
  await nextTick()
  renderChart()
}

async function loadSide() {
  const [top, ings, fb] = await Promise.allSettled([
    searchDishes({ sort: 'cooked', pageNum: 1, pageSize: 5 }),
    listIngredients(),
    listFeedback({ status: 'NEW', pageNum: 1, pageSize: 3 }),
  ])
  if (top.status === 'fulfilled') {
    const rows = Array.isArray(top.value?.records) ? [...top.value.records] : []
    topDishes.value = rows.sort((a, b) => (b.cookedCount ?? 0) - (a.cookedCount ?? 0)).slice(0, 5)
  }
  topLoaded.value = true
  if (ings.status === 'fulfilled' && Array.isArray(ings.value)) {
    const miss = ings.value.filter((i) => i.edible !== false && !hasNutrition(i.nutrition))
    missingNutrition.value = { count: miss.length, sample: miss.slice(0, 3).map((i) => i.name) }
  }
  if (fb.status === 'fulfilled') {
    recentFeedback.value = Array.isArray(fb.value?.records) ? fb.value.records : []
  }
}

function setDays(d: number) {
  if (d === days.value) return
  days.value = d
  load()
}

function onKpi(t: KpiTile) {
  if (t.to) router.push(t.to)
}

// 切换主题：图表颜色全部来自主题 token，重新 setOption
watch(() => themeStore.current, () => nextTick(renderChart))

onMounted(() => {
  load()
  loadSide()
})
onUnmounted(disposeChart)
</script>

<template>
  <div class="yh-page home">
    <div class="yh-head">
      <div>
        <h1>运营仪表盘</h1>
        <div class="yh-sub">{{ updatedAt ? `数据更新于 ${updatedAt}` : '运营概览' }} · 近 {{ days }} 天趋势</div>
      </div>
      <div class="yh-seg" role="group" aria-label="统计天数">
        <button
          v-for="d in DAY_OPTIONS"
          :key="d"
          type="button"
          :class="{ on: d === days }"
          :aria-pressed="d === days"
          @click="setDays(d)"
        >{{ d }} 天</button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated class="yh-card" data-testid="dash-loading" />

    <div v-else-if="error" class="yh-card">
      <el-result icon="error" title="仪表盘加载失败" :sub-title="error" data-testid="dash-error">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
    </div>

    <template v-else-if="stats">
      <div class="kpis">
        <StatCard
          v-for="t in kpis"
          :key="t.key"
          :label="t.label"
          :value="t.value"
          :hint="t.hint"
          :hint-tone="t.hintTone"
          :icon="KPI_ICON[t.key]"
          :clickable="!!t.to"
          :data-testid="`kpi-${t.key}`"
          @click="onKpi(t)"
        />
      </div>

      <div class="grid">
        <section class="yh-card chart-panel">
          <div class="yh-card-title">近 {{ days }} 天趋势</div>
          <el-empty v-if="trendEmpty" description="近期暂无数据" :image-size="80" data-testid="dash-trend-empty" />
          <div v-else ref="chartRef" class="chart" data-testid="dash-trend-chart"></div>
        </section>

        <section class="yh-card todo" aria-label="待处理">
          <div class="yh-card-title">待处理</div>

          <router-link to="/feedback" class="todo-row" data-testid="todo-feedback">
            <AvatarStack
              v-if="recentFeedback.length"
              :people="recentFeedback.map((f) => ({ name: f.memberName }))"
              :size="32"
              :max="3"
            />
            <span v-else class="todo-ico"><el-icon><ChatDotRound /></el-icon></span>
            <span class="todo-txt">
              <b>新反馈</b>
              <span class="yh-muted">{{ recentFeedback.length ? recentFeedback.map((f) => f.memberName || `#${f.memberId}`).join('、') : '暂无待处理反馈' }}</span>
            </span>
            <b class="todo-num" :class="{ hot: feedbackOpen > 0 }">{{ formatCount(feedbackOpen) }}</b>
          </router-link>

          <router-link v-if="missingNutrition" to="/ingredient" class="todo-row" data-testid="todo-nutrition">
            <IngIcon :name="missingNutrition.sample[0] || ''" :size="36" />
            <span class="todo-txt">
              <b>食材缺营养数据</b>
              <span class="yh-muted">{{ missingNutrition.sample.length ? `${missingNutrition.sample.join('、')} 等` : '全部食材已有营养数据' }}</span>
            </span>
            <b class="todo-num">{{ formatCount(missingNutrition.count) }}</b>
          </router-link>

          <div class="todo-row pending" data-testid="todo-library">
            <span class="todo-ico"><el-icon><Food /></el-icon></span>
            <span class="todo-txt">
              <b>公共菜谱投稿待审核</b>
              <span class="yh-muted">投稿审核接口待上线</span>
            </span>
            <span class="yh-tag mute">待接入</span>
          </div>

          <div class="todo-row pending" data-testid="todo-alert">
            <span class="todo-ico"><el-icon><WarningFilled /></el-icon></span>
            <span class="todo-txt">
              <b>系统告警</b>
              <span class="yh-muted">健康检查接口待上线</span>
            </span>
            <span class="yh-tag mute">待接入</span>
          </div>
        </section>
      </div>
    </template>

    <section class="yh-card" aria-label="菜谱排行">
      <div class="panel-head">
        <span class="yh-card-title">{{ hasCookedData || !topLoaded ? '最常做的菜' : '最新上架的菜谱' }}</span>
        <router-link to="/dish" class="yh-link">去菜谱管理 →</router-link>
      </div>
      <div v-if="topDishes.length" class="top-dishes" data-testid="dash-top-dishes">
        <article v-for="(d, i) in topDishes" :key="d.id" class="top-dish">
          <div class="top-cover">
            <DishThumb :src="d.coverUrl" :name="d.name" ratio="4 / 3" :radius="12" />
            <span v-if="hasCookedData" class="rank">{{ i + 1 }}</span>
          </div>
          <b class="top-name">{{ d.name }}</b>
          <span class="yh-muted">{{ (d.cookedCount ?? 0) > 0 ? `做了 ${formatCount(d.cookedCount)} 次` : (d.sourceName ? `来自${d.sourceName}` : '还没人做过') }}</span>
        </article>
      </div>
      <el-empty v-else-if="topLoaded" description="还没有菜谱" :image-size="72" />
      <el-skeleton v-else :rows="3" animated />
    </section>
  </div>
</template>


<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
}
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(320px, 1fr);
  gap: 16px;
}
@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
.chart {
  height: 320px;
  width: 100%;
  margin-top: 8px;
}
.todo {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.todo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--yh-border);
  border-radius: 12px;
  color: var(--yh-title);
  text-decoration: none;
  transition: border-color 0.15s;
}
a.todo-row:hover {
  border-color: var(--el-color-primary);
}
.todo-row.pending {
  background: var(--yh-bg);
}
.todo-ico {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--yh-highlight);
  color: var(--yh-primary-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.todo-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.todo-txt .yh-muted {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-num {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}
.todo-num.hot {
  color: var(--yh-error-text);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.top-dishes {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}
@media (max-width: 1100px) {
  .top-dishes {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
.top-dish {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.top-cover {
  position: relative;
  margin-bottom: 6px;
}
.top-cover :deep(.dish-thumb) {
  display: flex;
}
.rank {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: var(--yh-panel);
  color: var(--yh-title);
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}
.top-name {
  font-size: 15px;
  color: var(--yh-title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
