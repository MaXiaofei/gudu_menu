<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AvatarStack from '@/components/AvatarStack.vue'
import {
  createEvalCase, listEvalCases, listEvalFailures, listEvalRuns, listTraces, startEvalRun,
  type EvalFailure, type EvalRun, type TraceSummary,
} from '@/api/agentAdmin'
import {
  CATEGORY_DROP_LIMIT, TOOL_ACCURACY_GATE, categoryBars, compareRuns, diffTools, gateOf, hardGateFails, intentLabel,
  lastRuns, polyline, summarizeCases, trendGeometry,
} from '@/utils/agentEval'
import { toPct, traceTime } from '@/utils/agentTrace'

/**
 * Agent 评测（原型 AdminAgentEval）：改提示词或换模型后跑全量评测集，工具选择正确率低于 90% 时不允许发布。
 * 数据来自 /admin/agent/eval/**（contracts.md §1 后台）。
 */
const runs = ref<EvalRun[]>([])
const candId = ref<string>('')
const baseId = ref<string>('')
const cases = ref<ReturnType<typeof summarizeCases>>({ total: 0, testTotal: null, byIntent: [] })
const onlyNew = ref(false)
const failures = ref<EvalFailure[]>([])
const failIntent = ref<string>('')
const downvotes = ref<TraceSummary[]>([])
const addedTraceIds = ref<Set<string>>(new Set())
const loading = ref(false)
const runDialog = ref(false)
const runVersion = ref('')
const starting = ref(false)

const sortedRuns = computed(() => lastRuns(runs.value, runs.value.length))
const recent = computed(() => lastRuns(runs.value, 10))
const cand = computed(() => sortedRuns.value.find((r) => String(r.runId) === candId.value) ?? null)
const base = computed(() => sortedRuns.value.find((r) => String(r.runId) === baseId.value) ?? null)
const gate = computed(() => gateOf(cand.value))
const hardFails = computed(() => hardGateFails(cand.value))
const catBars = computed(() => categoryBars(cand.value))
const newFailCount = computed(() => failures.value.filter((f) => f.newSinceBaseline).length)
const rows = computed(() => compareRuns(base.value, cand.value))

const CHART_W = 640
const CHART_H = 220
const trend = computed(() => {
  const tool = recent.value.map((r) => toPct(r.toolAccuracy) ?? 0)
  const pts = recent.value.map((r) => toPct(r.pointCoverage) ?? 0)
  return trendGeometry([tool, pts], CHART_W, CHART_H)
})

const failIntents = computed(() => {
  const m = new Map<string, number>()
  for (const f of failures.value) m.set(f.intent, (m.get(f.intent) ?? 0) + 1)
  return [...m].map(([intent, count]) => ({ intent, count, label: intentLabel(intent) })).sort((a, b) => b.count - a.count)
})
const shownFailures = computed(() =>
  failures.value.filter((f) => (!failIntent.value || f.intent === failIntent.value) && (!onlyNew.value || f.newSinceBaseline)),
)

// 14 个类别：查 / 写 / 组合 / 安全 等分组用同一色系的深浅区分
const PALETTE = ['#E89150', '#F2B27E', '#4FAE6E', '#7CC493', '#A8D8B5', '#C9E8D1', '#D17A3C', '#B8762E', '#4FA0D0', '#8EC3E3', '#DB5A4E', '#E99088', '#8C7B6B', '#BDB1A4']
const caseBars = computed(() => {
  const total = cases.value.total || 1
  return cases.value.byIntent.map((b, i) => ({ ...b, pct: (b.count / total) * 100, color: PALETTE[i % PALETTE.length] }))
})

function runLabel(r: EvalRun) {
  return `#${r.runId} · ${r.promptVersion}${r.passed ? '' : '（未通过）'}`
}

async function load() {
  loading.value = true
  try {
    const [r, c, d] = await Promise.allSettled([listEvalRuns(), listEvalCases(), listTraces({ rating: -1, size: 5 })])
    runs.value = r.status === 'fulfilled' && Array.isArray(r.value) ? r.value : []
    cases.value = summarizeCases(c.status === 'fulfilled' ? c.value : null)
    downvotes.value = d.status === 'fulfilled' && Array.isArray(d.value?.items) ? d.value.items : []
    const sorted = sortedRuns.value
    const latest = sorted[sorted.length - 1]
    candId.value = latest ? String(latest.runId) : ''
    // 基线默认取候选之前最近一次通过的运行（即线上版本）
    const prevPassed = [...sorted].reverse().find((x) => x !== latest && x.passed)
    baseId.value = prevPassed ? String(prevPassed.runId) : ''
  } finally {
    loading.value = false
  }
}

watch(candId, async (id) => {
  failures.value = []
  failIntent.value = ''
  onlyNew.value = false
  if (!id) return
  try {
    const f = await listEvalFailures(id)
    failures.value = Array.isArray(f) ? f : []
  } catch {
    failures.value = []
  }
})

function openRun() {
  runVersion.value = cand.value?.promptVersion ?? ''
  runDialog.value = true
}

async function onStartRun() {
  const v = runVersion.value.trim()
  if (!v) {
    ElMessage.warning('请填写提示词版本')
    return
  }
  starting.value = true
  try {
    const res = await startEvalRun(v)
    ElMessage.success(`已发起评测运行 #${res?.runId ?? ''}，完成后刷新查看`)
    runDialog.value = false
  } finally {
    starting.value = false
  }
}

async function addDownvote(t: TraceSummary) {
  await createEvalCase({ fromTraceId: t.traceId })
  addedTraceIds.value = new Set([...addedTraceIds.value, t.traceId])
  ElMessage.success('已转为评测用例')
}

onMounted(load)
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>Agent 评测</h1>
        <div class="yh-sub">改提示词或换模型后跑全量评测集；工具选择正确率低于 {{ TOOL_ACCURACY_GATE }}% 时不允许发布</div>
      </div>
      <div class="head-tools">
        <el-select v-model="candId" placeholder="候选版本" style="width: 220px" aria-label="候选版本" data-testid="cand-select">
          <el-option v-for="r in [...sortedRuns].reverse()" :key="String(r.runId)" :label="runLabel(r)" :value="String(r.runId)" />
        </el-select>
        <span class="yh-muted">对比</span>
        <el-select v-model="baseId" placeholder="基线版本" clearable style="width: 200px" aria-label="基线版本">
          <el-option v-for="r in [...sortedRuns].reverse()" :key="String(r.runId)" :label="runLabel(r)" :value="String(r.runId)" />
        </el-select>
        <el-button type="primary" data-testid="start-run" @click="openRun">发起评测</el-button>
      </div>
    </div>

    <section
      v-if="cand"
      class="gate"
      :class="gate.passed ? 'ok' : 'err'"
      role="status"
      data-testid="gate"
    >
      <span class="gate-ico" aria-hidden="true">{{ gate.passed ? '✓' : '⨯' }}</span>
      <div class="gate-txt">
        <b>{{ cand.promptVersion }} {{ gate.passed ? '可以发布' : '不可发布' }}</b>
        <span>
          运行 #{{ cand.runId }} · {{ traceTime(cand.createdAt) }} · {{ cases.total }} 条用例 · {{ gate.reason }}
          <template v-if="newFailCount">；{{ newFailCount }} 条相对基线新失败</template>
        </span>
      </div>
    </section>
    <section v-else-if="!loading" class="yh-card empty yh-muted">还没有评测运行记录，点右上角「发起评测」</section>

    <section v-if="hardFails.length" class="hard" role="alert" data-testid="hard-gates">
      <b>硬门禁未通过（必须 100%）</b>
      <div v-for="h in hardFails" :key="h.gate" class="hard-row">
        <span class="hard-name">{{ h.label }}</span>
        <span>{{ h.failed }} 条失败<template v-if="h.total"> / {{ h.total }}</template></span>
        <span v-if="h.caseIds?.length" class="hard-ids"><code v-for="id in h.caseIds" :key="id">{{ id }}</code></span>
      </div>
    </section>

    <div class="grid2">
      <section class="yh-card" aria-label="评测集" data-testid="cases">
        <div class="card-head"><span class="yh-card-title">评测集</span></div>
        <div class="big">
          <b>{{ cases.total }}</b>
          <span class="yh-muted">条用例<template v-if="cases.testTotal != null">（test 集 {{ cases.testTotal }}）</template> · 每条含输入、期望工具序列、期望要点</span>
        </div>
        <div class="stackbar" aria-hidden="true">
          <span v-for="b in caseBars" :key="b.intent" :style="{ width: `${b.pct}%`, background: b.color }" />
        </div>
        <ul class="intents">
          <li v-for="b in caseBars" :key="b.intent">
            <i :style="{ background: b.color }" />{{ b.label }}
            <b>{{ b.count }}<span v-if="b.testCount != null" class="yh-muted"> · test {{ b.testCount }}</span></b>
          </li>
        </ul>
        <div class="yh-muted note">评分 = 工具选择（序列匹配）+ 参数正确（服务端校验器）+ 要点覆盖（LLM 裁判，抽检 10% 人工复核）</div>
      </section>

      <section class="yh-card" aria-label="近 10 次运行" data-testid="trend">
        <div class="card-head">
          <span class="yh-card-title">近 10 次运行</span>
          <span class="legend"><i class="l1" />工具选择正确率 <i class="l2" />要点覆盖</span>
        </div>
        <svg v-if="recent.length" :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="chart" role="img" aria-label="近 10 次评测趋势">
          <g v-for="t in trend.yTicks" :key="t.v">
            <line x1="44" :x2="CHART_W - 16" :y1="t.y" :y2="t.y" class="gl" />
            <text x="38" :y="t.y + 4" text-anchor="end" class="ax">{{ t.v }}%</text>
          </g>
          <line x1="44" :x2="CHART_W - 16" :y1="trend.gateY" :y2="trend.gateY" class="gate-line" />
          <text :x="CHART_W - 18" :y="trend.gateY - 6" text-anchor="end" class="gate-lbl">发布门禁 {{ TOOL_ACCURACY_GATE }}%</text>
          <polyline :points="polyline(trend.series[1])" class="s2" />
          <polyline :points="polyline(trend.series[0])" class="s1" />
          <circle
            v-for="(p, i) in trend.series[0]"
            :key="i"
            :cx="p.x"
            :cy="p.y"
            r="4"
            class="pt"
            :class="{ bad: p.v < TOOL_ACCURACY_GATE }"
          />
          <text v-for="(r, i) in recent" :key="`x${i}`" :x="trend.series[0][i]?.x" :y="CHART_H - 8" text-anchor="middle" class="ax">#{{ r.runId }}</text>
        </svg>
        <div v-else class="empty yh-muted">暂无运行记录</div>
      </section>
    </div>

    <section v-if="catBars.length" class="yh-card" aria-label="分类得分 vs 基线" data-testid="categories">
      <div class="card-head">
        <span class="yh-card-title">分类得分 vs 基线</span>
        <span class="legend"><i class="l1" />{{ cand?.promptVersion }} <i class="lb" />基线 · 任一类别下降超过 {{ CATEGORY_DROP_LIMIT }} 个百分点标红；安全类须 100%</span>
      </div>
      <div class="cats">
        <div v-for="c in catBars" :key="c.category" class="cat" :class="{ bad: c.regressed }" :data-category="c.category">
          <span class="cat-name">{{ c.label }}<code>{{ c.category }}</code></span>
          <span class="cat-track">
            <span class="cat-fill" :style="{ width: `${Math.min(100, c.score)}%` }" />
            <span v-if="c.baseline != null" class="cat-base" :style="{ left: `${Math.min(100, c.baseline)}%` }" :title="`基线 ${c.baseline.toFixed(1)}%`" />
          </span>
          <span class="cat-val">{{ c.score.toFixed(1) }}%</span>
          <span class="cat-delta" :class="c.delta == null ? '' : c.delta >= 0 ? 'good' : c.regressed ? 'bad' : 'warnc'">
            {{ c.delta == null ? '无基线' : c.delta > 0 ? `+${c.delta}` : c.delta === 0 ? '0' : `−${Math.abs(c.delta)}` }}
          </span>
        </div>
      </div>
    </section>

    <div class="grid2 wide-left">
      <section class="yh-card" aria-label="版本对比">
        <div class="card-head"><span class="yh-card-title">版本对比</span></div>
        <table class="cmp" data-testid="compare">
          <thead>
            <tr>
              <th>指标</th>
              <th class="r">{{ base ? `${base.promptVersion}（基线）` : '基线' }}</th>
              <th class="r">{{ cand ? `${cand.promptVersion}（候选）` : '候选' }}</th>
              <th class="r">变化</th>
              <th>门禁</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.key">
              <td><b>{{ r.label }}</b></td>
              <td class="r">{{ r.base }}</td>
              <td class="r" :class="{ bad: r.gateTone === 'err' }">{{ r.cand }}</td>
              <td class="r" :class="r.better === null ? '' : r.better ? 'good' : 'bad'">{{ r.delta }}</td>
              <td><span class="yh-tag" :class="r.gateTone">{{ r.gate }}</span></td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="yh-card" aria-label="差评回流" data-testid="downvotes">
        <div class="card-head">
          <span class="yh-card-title">差评回流</span>
          <span v-if="downvotes.length" class="yh-tag warn">{{ downvotes.length }} 条待转用例</span>
        </div>
        <div v-for="t in downvotes" :key="t.traceId" class="dv">
          <AvatarStack :people="[{ name: t.user?.nickname, avatarUrl: t.user?.avatarUrl }]" :size="30" />
          <div class="dv-main">
            <span>「{{ t.firstText }}」</span>
            <span class="yh-muted"><code>{{ t.traceId }}</code> · {{ traceTime(t.createdAt) }}</span>
          </div>
          <el-button size="small" :disabled="addedTraceIds.has(t.traceId)" @click="addDownvote(t)">
            {{ addedTraceIds.has(t.traceId) ? '已加入' : '加入' }}
          </el-button>
        </div>
        <div v-if="!downvotes.length" class="empty yh-muted">暂无差评对话</div>
        <div class="yh-muted note">加入时自动带上原始输入和当轮工具调用，运营补充期望后进入评测集</div>
      </section>
    </div>

    <section class="yh-card" aria-label="退化用例" data-testid="failures">
      <div class="card-head">
        <span class="yh-card-title">
          失败用例（{{ failures.length }}）
          <span v-if="newFailCount" class="yh-tag err">新失败 {{ newFailCount }}</span>
        </span>
        <el-checkbox v-if="newFailCount" v-model="onlyNew" data-testid="only-new">只看新失败</el-checkbox>
        <div v-if="failIntents.length" class="yh-seg" role="group" aria-label="按意图筛选">
          <button type="button" :class="{ on: !failIntent }" @click="failIntent = ''">全部</button>
          <button
            v-for="f in failIntents"
            :key="f.intent"
            type="button"
            :class="{ on: failIntent === f.intent }"
            @click="failIntent = f.intent"
          >{{ f.label }} {{ f.count }}</button>
        </div>
      </div>
      <el-table :data="shownFailures" empty-text="候选版本没有退化用例">
        <el-table-column label="用例" width="110">
          <template #default="{ row }">
            <code>{{ row.caseId }}</code>
            <div v-if="row.newSinceBaseline"><span class="yh-tag err" data-testid="new-fail">新失败</span></div>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="110">
          <template #default="{ row }">{{ intentLabel(row.intent) }}</template>
        </el-table-column>
        <el-table-column label="输入" min-width="220" prop="input" />
        <el-table-column label="期望工具序列" min-width="240">
          <template #default="{ row }">
            <span class="seq">
              <template v-for="(t, i) in diffTools(row.expectedTools, row.actualTools).expected" :key="i">
                <span v-if="i" class="arrow">→</span>
                <code class="tool" :class="t.mark">{{ t.tool }}</code>
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="实际" min-width="240">
          <template #default="{ row }">
            <span class="seq">
              <template v-for="(t, i) in diffTools(row.expectedTools, row.actualTools).actual" :key="i">
                <span v-if="i" class="arrow">→</span>
                <code class="tool" :class="t.mark">{{ t.tool }}</code>
              </template>
              <span v-if="!row.actualTools?.length" class="tool extra">只回复文本</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异" min-width="160">
          <template #default="{ row }">{{ row.note || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="yh-muted note">
        <code class="tool missing">漏调</code> 期望有、实际没调；<code class="tool extra">多调</code> 实际多出的工具；<code class="tool arg">参数</code> 同一工具参数不同
      </div>
    </section>

    <el-dialog v-model="runDialog" title="发起评测运行" width="420px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="提示词版本">
          <el-input v-model="runVersion" placeholder="如 v14" data-testid="run-version" />
        </el-form-item>
        <div class="yh-muted">将对 {{ cases.total }} 条用例全量运行，约消耗与一次线上高峰小时相当的模型调用。</div>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="starting" data-testid="run-confirm" @click="onStartRun">开始运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-tools {
  display: flex;
  gap: 10px;
  align-items: center;
}
.gate {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 16px;
  border: 1px solid;
}
.gate.err {
  background: var(--yh-error-soft);
  border-color: var(--yh-error);
  color: var(--yh-error-text);
}
.gate.ok {
  background: var(--yh-success-soft);
  border-color: var(--yh-success);
  color: var(--yh-success-text);
}
.gate-ico {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 900;
  color: var(--yh-on-primary);
  background: currentColor;
  flex-shrink: 0;
}
.gate.err .gate-ico {
  background: var(--yh-error);
}
.gate.ok .gate-ico {
  background: var(--yh-success);
}
.gate-txt {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.gate-txt b {
  font-size: 18px;
}
.hard {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 18px;
  border-radius: 14px;
  background: var(--yh-error);
  color: var(--yh-on-primary);
  font-size: 13px;
}
.hard b {
  font-size: 15px;
}
.hard-row {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
}
.hard-name {
  font-weight: 700;
  min-width: 80px;
}
.hard-ids {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}
.hard-ids code {
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.2);
}
.cats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 32px;
}
.cat {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 56px 60px;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  padding: 5px 0;
  color: var(--yh-title);
}
.cat-name {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.cat-name code {
  font-size: 11px;
  color: var(--yh-body);
}
.cat-track {
  position: relative;
  height: 10px;
  border-radius: 5px;
  background: var(--yh-secondary);
}
.cat-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 5px;
  background: var(--yh-primary-deep);
}
.cat.bad .cat-fill {
  background: var(--yh-error);
}
.cat-base {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  background: var(--yh-title);
  transform: translateX(-1px);
}
.cat-val {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.cat-delta {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.warnc {
  color: var(--yh-warning-text);
}
.legend .lb {
  width: 2px;
  height: 12px;
  border-radius: 0;
  background: var(--yh-title);
  margin-left: 8px;
}
.grid2 {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 16px;
}
.grid2.wide-left {
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.big {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.big b {
  font-size: 34px;
  color: var(--yh-title);
}
.stackbar {
  display: flex;
  gap: 3px;
  height: 12px;
  margin: 14px 0;
  border-radius: 6px;
  overflow: hidden;
}
.stackbar span {
  display: block;
  height: 100%;
}
.intents {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}
.intents li {
  display: flex;
  align-items: center;
  gap: 8px;
}
.intents li b {
  margin-left: auto;
}
.intents i,
.legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
.note {
  margin-top: 12px;
  line-height: 1.6;
}
.legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--yh-body);
}
.legend .l1 {
  background: var(--yh-primary-deep);
}
.legend .l2 {
  background: var(--yh-info);
  margin-left: 8px;
}
.chart {
  width: 100%;
  height: auto;
}
.gl {
  stroke: var(--yh-border);
}
.ax {
  font-size: 11px;
  fill: var(--yh-body);
}
.gate-line {
  stroke: var(--yh-error);
  stroke-dasharray: 5 4;
}
.gate-lbl {
  font-size: 11px;
  fill: var(--yh-error-text);
  font-weight: 700;
}
.s1,
.s2 {
  fill: none;
  stroke-width: 2.5;
}
.s1 {
  stroke: var(--yh-primary-deep);
}
.s2 {
  stroke: var(--yh-info);
}
.pt {
  fill: var(--yh-panel);
  stroke: var(--yh-primary-deep);
  stroke-width: 2;
}
.pt.bad {
  fill: var(--yh-error);
  stroke: var(--yh-error);
}
.cmp {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.cmp th {
  text-align: left;
  font-size: 12px;
  color: var(--yh-body);
  font-weight: 700;
  padding: 8px 10px;
  border-bottom: 1px solid var(--yh-border);
}
.cmp td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--yh-border);
  color: var(--yh-title);
  font-variant-numeric: tabular-nums;
}
.cmp .r {
  text-align: right;
}
.good {
  color: var(--yh-success-text);
  font-weight: 700;
}
.bad {
  color: var(--yh-error-text);
  font-weight: 700;
}
.dv {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--yh-border);
}
.dv-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: var(--yh-title);
}
.seq {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.arrow {
  color: var(--yh-body);
}
.tool {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 6px;
  font-size: 12px;
  border: 1px solid var(--yh-border);
  background: var(--yh-panel);
  color: var(--yh-title);
}
.tool.missing {
  background: var(--yh-error-soft);
  color: var(--yh-error-text);
  text-decoration: line-through;
  border-color: transparent;
}
.tool.extra {
  background: var(--yh-warning-soft);
  color: var(--yh-warning-text);
  border-color: var(--yh-primary-soft);
}
.tool.arg {
  background: var(--yh-info-soft);
  color: var(--yh-info-text);
  border-color: transparent;
}
.empty {
  padding: 24px;
  text-align: center;
}
@media (max-width: 1200px) {
  .grid2,
  .grid2.wide-left,
  .cats {
    grid-template-columns: 1fr;
  }
}
</style>
