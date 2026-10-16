<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { getOpsStats, type OpsStats } from '@/api/ops'
import { init, type ECharts } from '@/utils/echarts'
import {
  buildLast7dTiles,
  buildTotalTiles,
  buildTrendOption,
  isTrendEmpty,
  normalizeTrend,
  type KpiTile,
} from '@/utils/dashboard'

const DAYS = 14

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const loading = ref(true)
const error = ref('')
const stats = shallowRef<OpsStats | null>(null)

const totalTiles = computed(() => (stats.value ? buildTotalTiles(stats.value) : []))
const last7dTiles = computed(() => (stats.value ? buildLast7dTiles(stats.value) : []))
const trend = computed(() => normalizeTrend(stats.value?.trend))
const trendEmpty = computed(() => isTrendEmpty(trend.value))

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

async function load() {
  disposeChart() // loading 态会卸载图表 DOM，重载前先释放实例
  loading.value = true
  error.value = ''
  try {
    stats.value = await getOpsStats(DAYS)
  } catch (e) {
    error.value = (e as Error)?.message || '加载失败'
    stats.value = null
  } finally {
    loading.value = false
  }
  await nextTick()
  renderChart()
}

function onTile(t: KpiTile) {
  if (t.to) router.push(t.to)
}

// 切换主题：图表颜色全部来自主题 token，重新 setOption
watch(() => themeStore.current, () => nextTick(renderChart))

onMounted(load)
onUnmounted(disposeChart)
</script>

<template>
  <div class="home">
    <div class="welcome">
      <h2>你好，{{ authStore.nickname || '掌柜' }}</h2>
      <p>运营概览 · 近 {{ DAYS }} 天</p>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated class="panel" data-testid="dash-loading" />

    <div v-else-if="error" class="panel">
      <el-result icon="error" title="仪表盘加载失败" :sub-title="error" data-testid="dash-error">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
    </div>

    <template v-else-if="stats">
      <div class="section-label">累计</div>
      <div class="tiles">
        <div
          v-for="t in totalTiles"
          :key="t.key"
          class="tile"
          :class="{ clickable: !!t.to, alert: t.alert }"
          :data-testid="`kpi-total-${t.key}`"
          @click="onTile(t)"
        >
          <div class="tile-value">{{ t.value }}</div>
          <div class="tile-label">{{ t.label }}</div>
        </div>
      </div>

      <div class="section-label">近 7 天</div>
      <div class="tiles">
        <div
          v-for="t in last7dTiles"
          :key="t.key"
          class="tile"
          :class="{ clickable: !!t.to }"
          :data-testid="`kpi-7d-${t.key}`"
          @click="onTile(t)"
        >
          <div class="tile-value">{{ t.value }}</div>
          <div class="tile-label">{{ t.label }}</div>
        </div>
      </div>

      <div class="panel chart-panel">
        <div class="panel-title">近 {{ DAYS }} 天趋势</div>
        <el-empty v-if="trendEmpty" description="近期暂无数据" :image-size="80" data-testid="dash-trend-empty" />
        <div v-else ref="chartRef" class="chart" data-testid="dash-trend-chart"></div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.home {
  max-width: 1200px;
}
.welcome h2 {
  margin: 0 0 6px;
  color: var(--yh-title);
  font-size: 20px;
}
.welcome p {
  color: var(--yh-caption);
  margin: 0 0 16px;
  font-size: 13px;
}
.section-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--yh-caption);
  margin: 4px 0 8px;
  letter-spacing: 0.5px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.tile {
  background: var(--yh-panel);
  border: 1px solid var(--yh-border);
  border-radius: 12px;
  padding: 16px 18px;
  transition: border-color 0.15s;
}
.tile.clickable {
  cursor: pointer;
}
.tile.clickable:hover {
  border-color: var(--el-color-primary);
}
.tile-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--yh-title);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.tile.alert .tile-value {
  color: var(--yh-error);
}
.tile-label {
  font-size: 12px;
  color: var(--yh-caption);
  margin-top: 4px;
}
.panel {
  background: var(--yh-panel);
  border: 1px solid var(--yh-border);
  border-radius: 12px;
  padding: 16px 18px;
}
.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--yh-title);
  margin-bottom: 8px;
}
.chart {
  height: 320px;
  width: 100%;
}
</style>
