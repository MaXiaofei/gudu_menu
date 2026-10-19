<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { MagicStick, Food, DataAnalysis, Bowl } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { getAiProvider, getAiUsage, listAiCallLogs, type AiCallLog, type AiProviderInfo } from '@/api/ai'
import {
  formatMs,
  percentile,
  sampleStats,
  sceneLabel,
  summarizeUsage,
  withinDays,
  type UsageRow,
} from '@/utils/aiStats'
import { formatCount, formatYuan } from '@/utils/dashboard'
import StatCard from '@/components/StatCard.vue'
import PendingApi from '@/components/PendingApi.vue'

/** 成本 / P95 由最近一批明细计算（/ai/usage 不含成本与分位数） */
const SAMPLE_SIZE = 500
const DAY_OPTIONS = [
  { value: 1, label: '今天' },
  { value: 7, label: '近 7 天' },
  { value: 30, label: '近 30 天' },
]
const SCENE_ICON: Record<string, Component> = {
  menu_recommend: Bowl,
  nutrition_fill: Food,
  dish_estimate: DataAnalysis,
}

const days = ref(7)
const usage = ref<UsageRow[]>([])
const usageError = ref('')
const sample = ref<AiCallLog[]>([])
const provider = ref<AiProviderInfo | null>(null)

const summary = computed(() => summarizeUsage(usage.value))
const windowed = computed(() => withinDays(sample.value, days.value))
const stats = computed(() => sampleStats(windowed.value))
/** 明细样本是否完整覆盖了时间窗口（样本满 500 条且最早一条仍在窗口内 → 不完整） */
const sampleTruncated = computed(() => sample.value.length >= SAMPLE_SIZE && windowed.value.length === sample.value.length)

const sceneRows = computed(() => {
  const max = Math.max(1, ...usage.value.map((r) => Number(r.totalCalls) || 0))
  return [...usage.value]
    .sort((a, b) => b.totalCalls - a.totalCalls)
    .map((r) => {
      const lat = windowed.value.filter((l) => l.scene === r.scene && l.status !== 'fail' && l.latencyMs != null).map((l) => l.latencyMs as number)
      return {
        ...r,
        pct: Math.round(((Number(r.totalCalls) || 0) / max) * 100),
        cost: stats.value.costByScene[r.scene] ?? 0,
        p95: percentile(lat, 95),
      }
    })
})

async function loadUsage() {
  usageError.value = ''
  try {
    const rows = await getAiUsage(days.value)
    usage.value = Array.isArray(rows) ? rows : []
  } catch (e) {
    usageError.value = (e as Error)?.message || '加载失败'
    usage.value = []
  }
}

async function loadSample() {
  try {
    const page = await listAiCallLogs({ pageNum: 1, pageSize: SAMPLE_SIZE })
    sample.value = Array.isArray(page?.records) ? page.records : []
  } catch {
    sample.value = []
  }
}

async function loadProvider() {
  try {
    const p = await getAiProvider()
    provider.value = p && typeof p.current === 'string' ? p : null
  } catch {
    provider.value = null
  }
}

function setDays(d: number) {
  if (d === days.value) return
  days.value = d
  loadUsage()
}

// ===== 明细列表 =====
const logs = ref<AiCallLog[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref({ pageNum: 1, pageSize: 15, scene: '', status: '' })

async function loadLogs() {
  loading.value = true
  try {
    const res = await listAiCallLogs(query.value)
    logs.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total || 0
  } catch {
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFilter() {
  query.value.pageNum = 1
  loadLogs()
}

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailLog = ref<AiCallLog | null>(null)

function showDetail(row: AiCallLog) {
  detailLog.value = row
  detailVisible.value = true
}

function formatJson(str: string | null) {
  if (!str) return '(空)'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function timeOf(s: string) {
  return (s || '').replace('T', ' ').slice(5, 19)
}

const providerText = computed(() => {
  const p = provider.value
  if (!p) return '模型配置读取中或不可用'
  const readyList = (p.ready ?? []).filter((r) => r.ready && r.provider !== p.current).map((r) => r.provider)
  return `当前模型 ${p.current}${readyList.length ? `，可切换：${readyList.join('、')}` : ''}；失败时降级到规则推荐`
})

onMounted(() => {
  loadUsage()
  loadSample()
  loadProvider()
  loadLogs()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>AI 用量与限额</h1>
        <div class="yh-sub" data-testid="ai-provider">{{ providerText }}</div>
      </div>
      <div class="yh-seg" role="group" aria-label="统计范围" data-testid="ai-days">
        <button
          v-for="d in DAY_OPTIONS"
          :key="d.value"
          type="button"
          :class="{ on: d.value === days }"
          :aria-pressed="d.value === days"
          @click="setDays(d.value)"
        >{{ d.label }}</button>
      </div>
    </div>

    <div class="kpis">
      <StatCard
        label="调用次数"
        :value="formatCount(summary.totalCalls)"
        :hint="summary.successRate === null ? '暂无调用' : `成功率 ${(summary.successRate * 100).toFixed(1)}% · 失败 ${summary.failCalls}`"
        :hint-tone="summary.failCalls > 0 ? 'warn' : 'muted'"
        data-testid="ai-kpi-calls"
      />
      <StatCard
        label="成本"
        :value="formatYuan(stats.costYuan)"
        :hint="`基于最近 ${stats.sampleSize} 条明细${sampleTruncated ? '（不足以覆盖整个范围）' : ''}`"
        data-testid="ai-kpi-cost"
      />
      <StatCard
        label="P95 耗时"
        :value="formatMs(stats.p95Ms)"
        :hint="stats.slowestScene ? `${sceneLabel(stats.slowestScene)}最慢` : '暂无成功调用'"
        data-testid="ai-kpi-p95"
      />
      <StatCard label="触发每日限额" value="—" hint="限额命中统计待上线" data-testid="ai-kpi-limit" />
    </div>

    <div class="grid">
      <section class="yh-card" aria-label="按功能">
        <div class="yh-card-title">按功能</div>
        <el-empty v-if="usageError" :description="`加载失败：${usageError}`" :image-size="64">
          <el-button type="primary" @click="loadUsage">重试</el-button>
        </el-empty>
        <el-empty v-else-if="!sceneRows.length" description="这个时间段没有 AI 调用" :image-size="64" />
        <div v-else class="scenes" data-testid="ai-scenes">
          <div v-for="(r, i) in sceneRows" :key="r.scene" class="scene">
            <span class="s-ico"><el-icon><component :is="SCENE_ICON[r.scene] || MagicStick" /></el-icon></span>
            <span class="s-name">{{ sceneLabel(r.scene) }}</span>
            <span class="s-bar"><i :style="{ width: `${r.pct}%`, background: `var(--chart-${i % 4})` }" /></span>
            <span class="s-num">{{ formatCount(r.totalCalls) }} 次</span>
            <span class="s-meta yh-muted">P95 {{ formatMs(r.p95) }} · {{ formatYuan(r.cost) }}<template v-if="r.failCalls"> · <span class="fail">失败 {{ r.failCalls }}</span></template></span>
          </div>
        </div>
      </section>

      <section class="yh-card" aria-label="限额设置">
        <div class="yh-card-title">限额设置</div>
        <PendingApi
          compact
          needs="每人每日上限、每周成本预算、80% 告警与超额降级的读写接口"
          task="prototype-ui-rollout 3.8"
        >
          <span class="yh-muted">当前每人每日上限由服务端配置 gudu.ai.daily-limit（默认 50 次）决定</span>
        </PendingApi>
      </section>
    </div>

    <section class="yh-card logs" aria-label="调用明细">
      <div class="logs-head">
        <span class="yh-card-title">调用明细</span>
        <div class="filters">
          <el-select v-model="query.scene" placeholder="全部功能" clearable size="small" style="width: 130px" @change="onFilter">
            <el-option value="menu_recommend" label="首页智荐" />
            <el-option value="nutrition_fill" label="营养补全" />
            <el-option value="dish_estimate" label="菜品估营养" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部结果" clearable size="small" style="width: 110px" @change="onFilter">
            <el-option value="ok" label="成功" />
            <el-option value="fail" label="失败" />
          </el-select>
        </div>
      </div>
      <el-table :data="logs" v-loading="loading" data-testid="ai-logs" @row-click="showDetail">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ timeOf(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="功能" width="120">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column label="成员" width="100">
          <template #default="{ row }">{{ row.memberId ? `#${row.memberId}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="模型" width="110">
          <template #default="{ row }">{{ row.provider || '—' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ formatMs(row.latencyMs) }}</template>
        </el-table-column>
        <el-table-column label="Token" width="100">
          <template #default="{ row }">{{ formatCount((row.tokensIn ?? 0) + (row.tokensOut ?? 0)) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <span class="yh-tag" :class="row.status === 'ok' ? 'ok' : 'err'">{{ row.status === 'ok' ? '成功' : '失败' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ fail: row.status === 'fail' }">{{ row.errorMsg ? row.errorMsg : (row.request || '').substring(0, 80) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无调用记录" :image-size="64" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[15, 50, 100]"
        layout="total, sizes, prev, pager, next"
        small
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadLogs"
        @size-change="onFilter"
      />
    </section>

    <el-dialog v-model="detailVisible" title="调用详情" width="700px" top="5vh">
      <template v-if="detailLog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间">{{ detailLog.createTime }}</el-descriptions-item>
          <el-descriptions-item label="功能">{{ sceneLabel(detailLog.scene) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ detailLog.provider || '—' }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <span class="yh-tag" :class="detailLog.status === 'ok' ? 'ok' : 'err'">{{ detailLog.status === 'ok' ? '成功' : '失败' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="输入 Token">{{ detailLog.tokensIn ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="输出 Token">{{ detailLog.tokensOut ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatMs(detailLog.latencyMs) }}</el-descriptions-item>
          <el-descriptions-item label="成本">{{ formatYuan(detailLog.cost) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailLog.errorMsg" label="错误信息" :span="2">
            <span class="fail">{{ detailLog.errorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">请求内容（发送给模型的）</el-divider>
        <el-input type="textarea" :model-value="formatJson(detailLog.request)" readonly :rows="5" />
        <el-divider content-position="left">响应内容（模型返回的）</el-divider>
        <el-input type="textarea" :model-value="formatJson(detailLog.response)" readonly :rows="5" />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
.scenes {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
  /* 图表色板来自主题：cream/matcha chart 色（tokens.json），用主色/信息色/成功色/标题色 */
  --chart-0: var(--el-color-primary);
  --chart-1: var(--yh-info);
  --chart-2: var(--yh-success);
  --chart-3: var(--yh-title);
}
.scene {
  display: grid;
  grid-template-columns: 30px 100px minmax(80px, 1fr) 80px minmax(150px, auto);
  align-items: center;
  gap: 10px;
}
.s-ico {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--yh-highlight);
  color: var(--yh-primary-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.s-name {
  color: var(--yh-title);
  font-size: 14px;
}
.s-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--yh-border);
  overflow: hidden;
}
.s-bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
}
.s-num {
  font-variant-numeric: tabular-nums;
  color: var(--yh-title);
  text-align: right;
}
.fail {
  color: var(--yh-error-text);
}
.logs {
  padding: 14px 8px 12px;
}
.logs-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 10px 8px;
}
.filters {
  display: flex;
  gap: 8px;
}
.logs :deep(.el-table__row) {
  cursor: pointer;
}
</style>
