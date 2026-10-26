<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AvatarStack from '@/components/AvatarStack.vue'
import StatCard from '@/components/StatCard.vue'
import {
  createEvalCase, getAgentStats, getTrace, getTraceFull, getTraceMessages, listTraces,
  type AgentMessage, type AgentStats, type Span, type TraceDetail, type TraceQuery, type TraceSummary,
} from '@/api/agentAdmin'
import { reasonError } from '@/utils/backoffice'
import {
  SPAN_TYPE_LABEL, TRACE_STATUS, cardSummary, countMaskedPhones, fenToYuan, formatInt, formatMs, formatPct, formatTick,
  layoutWaterfall, safeJson, traceTime,
} from '@/utils/agentTrace'

/**
 * Agent 调用链（原型 AdminAgentTrace）：每轮对话一条 trace，模型调用 / 检索 / 工具 / 护栏 / 写操作各为一个 span。
 * 数据来自 /admin/agent/**（contracts.md §1 后台），输入输出由后端脱敏后返回。
 */
type Filter = 'ALL' | 'DOWNVOTED' | 'GUARDED' | 'DEGRADED'
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'DOWNVOTED', label: '差评' },
  { value: 'GUARDED', label: '拦截' },
  { value: 'DEGRADED', label: '降级' },
]

const today = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

const date = ref(today)
const stats = ref<AgentStats | null>(null)
const filter = ref<Filter>('ALL')
const list = ref<TraceSummary[]>([])
const nextCursor = ref<string | null>(null)
const listLoading = ref(false)
const listError = ref('')
const selectedId = ref<string | null>(null)
const detail = ref<TraceDetail | null>(null)
const detailLoading = ref(false)
const selectedSpanId = ref<string | null>(null)
const search = ref('')
const adding = ref(false)
const convOpen = ref(false)
const convLoading = ref(false)
const messages = ref<AgentMessage[]>([])
/** 当前选中调用链是否已查看全文（默认脱敏摘要；换调用链后重置）。 */
const fullMode = ref(false)
const fullLoading = ref(false)

function sortMessages(res: AgentMessage[] | undefined) {
  return Array.isArray(res) ? [...res].sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1)) : []
}

/** 查看全文：弹框要求填写原因（≤200 字），后端校验并写操作日志后返回未脱敏内容。 */
async function viewFull() {
  if (!selectedId.value) return
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('查看对话全文会记录到操作日志，请填写查看原因（如：用户投诉回答异常）', '查看全文', {
      confirmButtonText: '查看', cancelButtonText: '取消', inputPlaceholder: '查看原因（必填，≤200 字）',
      inputValidator: (v: string) => reasonError(v) ?? true,
    })
    reason = r.value.trim()
  } catch {
    return
  }
  fullLoading.value = true
  try {
    const f = await getTraceFull(selectedId.value, reason)
    if (f?.detail && Array.isArray(f.detail.spans)) detail.value = f.detail
    messages.value = sortMessages(f?.messages)
    fullMode.value = true
    ElMessage.success('已显示全文，本次查看已记录到操作日志')
  } catch {
    // request.ts 已弹出错误提示
  } finally {
    fullLoading.value = false
  }
}

const ACTION_STATUS: Record<string, string> = { PENDING: '待确认', DONE: '已执行', CANCELLED: '已取消', EXPIRED: '已过期' }

async function openConversation() {
  if (!selectedId.value) return
  convOpen.value = true
  if (fullMode.value) return // 已查看全文：直接展示，不再用摘要覆盖
  convLoading.value = true
  try {
    const res = await getTraceMessages(selectedId.value)
    messages.value = sortMessages(res)
  } catch {
    messages.value = []
  } finally {
    convLoading.value = false
  }
}


const layout = computed(() => layoutWaterfall(detail.value?.spans ?? []))
const selectedSpan = computed<Span | null>(
  () => detail.value?.spans.find((s) => s.spanId === selectedSpanId.value) ?? null,
)
const parentName = computed(() => {
  const pid = selectedSpan.value?.parentId
  return pid ? detail.value?.spans.find((s) => s.spanId === pid)?.name ?? pid : '—'
})
const totals = computed(() => {
  const spans = detail.value?.spans ?? []
  const sum = (f: (s: Span) => number | null) => spans.reduce((a, s) => a + (f(s) ?? 0), 0)
  return {
    tokens: sum((s) => s.tokens),
    costFen: sum((s) => s.costFen),
    llm: spans.filter((s) => s.type === 'llm').length,
    tools: spans.filter((s) => s.type === 'tool' || s.type === 'retrieval').length,
    pending: layout.value.rows.filter((r) => r.pending).length,
  }
})
const maskedCount = computed(() =>
  selectedSpan.value ? countMaskedPhones(selectedSpan.value.input, selectedSpan.value.output) : 0,
)
const statsHint = computed(() => {
  const s = stats.value
  if (!s) return { turns: '', cost: '' }
  return {
    turns: '',
    cost: s.turns ? `每轮 ${fenToYuan(s.costFen / s.turns, 3)}` : '',
  }
})

function queryOf(f: Filter): TraceQuery {
  if (f === 'DOWNVOTED') return { rating: -1 }
  if (f === 'GUARDED' || f === 'DEGRADED') return { status: f }
  return {}
}

async function loadStats() {
  try {
    stats.value = await getAgentStats(date.value)
  } catch {
    stats.value = null
  }
}

async function loadList(append = false) {
  listLoading.value = true
  listError.value = ''
  try {
    const res = await listTraces({ ...queryOf(filter.value), cursor: append ? nextCursor.value : null, size: 20 })
    const items = Array.isArray(res?.items) ? res.items : []
    list.value = append ? [...list.value, ...items] : items
    nextCursor.value = res?.nextCursor ?? null
    if (!append && !list.value.some((t) => t.traceId === selectedId.value)) {
      if (list.value[0]) openTrace(list.value[0].traceId)
      else {
        selectedId.value = null
        detail.value = null
      }
    }
  } catch (e) {
    listError.value = (e as Error)?.message || '加载失败'
    if (!append) list.value = []
  } finally {
    listLoading.value = false
  }
}

async function openTrace(traceId: string) {
  selectedId.value = traceId
  fullMode.value = false
  messages.value = []
  detailLoading.value = true
  try {
    const d = await getTrace(traceId)
    detail.value = d && Array.isArray(d.spans) ? d : null
    const spans = detail.value?.spans ?? []
    selectedSpanId.value = (spans.find((s) => !s.ok) ?? spans.find((s) => s.type === 'retrieval') ?? spans[0])?.spanId ?? null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function onFilter(f: Filter) {
  if (filter.value === f) return
  filter.value = f
  loadList()
}

function onSearch() {
  const id = search.value.trim()
  if (!id) return
  openTrace(id)
}

async function addToEval() {
  if (!selectedId.value) return
  adding.value = true
  try {
    await createEvalCase({ fromTraceId: selectedId.value })
    ElMessage.success('已加入评测集，可到「Agent 评测」补充期望要点')
  } finally {
    adding.value = false
  }
}

function onDate() {
  loadStats()
}

function userOf(t: TraceSummary) {
  return [{ name: t.user?.nickname, avatarUrl: t.user?.avatarUrl }]
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>Agent 调用链</h1>
        <div class="yh-sub">每轮对话一条 trace，模型调用、检索、工具、护栏各为一个 span；输入输出已脱敏</div>
      </div>
      <div class="head-tools">
        <el-input
          v-model="search"
          class="search"
          placeholder="按 traceId 打开，如 tr_8f3a21"
          clearable
          data-testid="trace-search"
          @keyup.enter="onSearch"
        />
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 150px"
          aria-label="统计日期"
          @change="onDate"
        />
      </div>
    </div>

    <div class="kpis" data-testid="trace-kpis">
      <StatCard label="对话轮数" :value="stats ? formatInt(stats.turns) : '—'" />
      <StatCard
        label="工具调用成功率"
        :value="stats ? formatPct(stats.toolSuccessRate) : '—'"
        :tone="stats && (stats.toolSuccessRate <= 1 ? stats.toolSuccessRate * 100 : stats.toolSuccessRate) < 95 ? 'warn' : 'default'"
      />
      <StatCard label="P95 首字" :value="stats ? formatMs(stats.p95FirstTokenMs) : '—'" />
      <StatCard label="成本" :value="stats ? fenToYuan(stats.costFen, 2) : '—'" :hint="statsHint.cost" />
      <StatCard label="护栏拦截" :value="stats ? `${stats.guardBlocks} 次` : '—'" :tone="stats?.guardBlocks ? 'warn' : 'default'" />
    </div>

    <div class="split">
      <section class="yh-card list-card" aria-label="会话列表">
        <div class="card-head">
          <span class="yh-card-title">会话</span>
          <div class="yh-seg" role="group" aria-label="会话筛选" data-testid="trace-filter">
            <button
              v-for="f in FILTERS"
              :key="f.value"
              type="button"
              :class="{ on: filter === f.value }"
              :aria-pressed="filter === f.value"
              @click="onFilter(f.value)"
            >{{ f.label }}</button>
          </div>
        </div>
        <div v-loading="listLoading" class="trace-list" data-testid="trace-list">
          <button
            v-for="t in list"
            :key="t.traceId"
            type="button"
            class="trace-row"
            :class="{ on: t.traceId === selectedId }"
            :aria-current="t.traceId === selectedId ? 'true' : undefined"
            @click="openTrace(t.traceId)"
          >
            <AvatarStack :people="userOf(t)" :size="36" />
            <span class="tr-main">
              <span class="tr-top">
                <b>{{ t.user?.nickname || `用户 ${t.user?.userId}` }}</b>
                <code>{{ t.traceId }}</code>
                <span class="tr-ms">{{ formatMs(t.ms) }}</span>
              </span>
              <span class="tr-text">{{ t.firstText }}</span>
              <span class="tr-meta">
                <span class="yh-tag" :class="TRACE_STATUS[t.status]?.tone || 'mute'">{{ TRACE_STATUS[t.status]?.label || t.status }}</span>
                <span class="yh-muted">{{ traceTime(t.createdAt) }}</span>
              </span>
            </span>
          </button>
          <div v-if="!listLoading && !list.length" class="empty yh-muted">{{ listError || '该筛选下没有会话' }}</div>
          <el-button v-if="nextCursor" text class="more" :loading="listLoading" @click="loadList(true)">加载更多</el-button>
        </div>
      </section>

      <div class="right">
        <section v-loading="detailLoading" class="yh-card" aria-label="调用链" data-testid="trace-detail">
          <template v-if="detail">
            <div class="d-head">
              <AvatarStack :people="userOf(detail.trace)" :size="40" />
              <div class="d-title">
                <b>{{ detail.trace.firstText }}</b>
                <span class="yh-muted">
                  {{ detail.trace.user?.nickname }}
                  <template v-if="detail.trace.phoneMasked"> · {{ detail.trace.phoneMasked }}</template>
                  <template v-if="detail.trace.familyName"> · {{ detail.trace.familyName }}</template>
                  · {{ traceTime(detail.trace.createdAt) }} · <code>{{ detail.trace.traceId }}</code>
                  <template v-if="detail.trace.promptVersion"> · prompt {{ detail.trace.promptVersion }}</template>
                  <template v-if="detail.trace.model"> · {{ detail.trace.model }}</template>
                </span>
              </div>
              <el-button data-testid="open-conv" @click="openConversation">{{ fullMode ? '查看完整对话' : '查看对话摘要' }}</el-button>
              <el-button v-if="!fullMode" :loading="fullLoading" data-testid="view-full" @click="viewFull">查看全文</el-button>
              <span v-else class="yh-muted" data-testid="full-tip">已查看全文（已记录）</span>
              <el-button type="primary" :loading="adding" data-testid="add-eval" @click="addToEval">加入评测集</el-button>
            </div>

            <div v-if="detail.trace.status === 'DOWNVOTED' || detail.trace.feedback?.rating === -1" class="banner err" role="note">
              用户差评<template v-if="detail.trace.feedback?.comment">：「{{ detail.trace.feedback.comment }}」</template>
            </div>

            <div class="sum">
              <span>总耗时 <b>{{ formatMs(detail.trace.ms ?? layout.totalMs) }}</b></span>
              <span v-if="detail.trace.firstTokenMs">首字 <b>{{ formatMs(detail.trace.firstTokenMs) }}</b></span>
              <span>Tokens <b>{{ formatInt(detail.trace.tokens ?? totals.tokens) }}</b></span>
              <span>成本 <b>{{ fenToYuan(detail.trace.costFen ?? totals.costFen) }}</b></span>
              <span>模型调用 <b>{{ totals.llm }}</b></span>
              <span>工具 <b>{{ totals.tools }}</b></span>
              <span v-if="totals.pending">待确认动作 <b>{{ totals.pending }}</b></span>
            </div>

            <div class="wf" data-testid="waterfall">
              <div class="wf-axis">
                <span class="wf-name yh-muted">span</span>
                <span class="wf-track">
                  <span v-for="(t, i) in layout.ticks" :key="i" class="tick" :style="{ left: `${(i / 4) * 100}%` }">{{ formatTick(t) }}</span>
                </span>
                <span class="wf-ms yh-muted">耗时</span>
              </div>
              <button
                v-for="r in layout.rows"
                :key="r.span.spanId"
                type="button"
                class="wf-row"
                :class="{ on: r.span.spanId === selectedSpanId, fail: !r.span.ok }"
                :data-type="r.span.type"
                @click="selectedSpanId = r.span.spanId"
              >
                <span class="wf-name"><i class="dot" :class="r.span.type" /><code>{{ r.span.name }}</code></span>
                <span class="wf-track">
                  <span v-for="i in 3" :key="i" class="grid" :style="{ left: `${(i / 4) * 100}%` }" />
                  <span
                    class="bar"
                    :class="[r.span.type, { pending: r.pending }]"
                    :style="{ left: `${r.leftPct}%`, width: `${r.widthPct}%` }"
                    :data-testid="`bar-${r.span.spanId}`"
                  />
                </span>
                <span class="wf-ms">
                  <span v-if="r.pending" class="yh-tag warn">待确认</span>
                  <template v-else>{{ formatMs(r.span.ms) }}</template>
                </span>
              </button>
              <div class="legend">
                <span v-for="(label, k) in SPAN_TYPE_LABEL" :key="k"><i class="dot" :class="k" />{{ label }}</span>
              </div>
            </div>
          </template>
          <div v-else-if="!detailLoading" class="empty yh-muted">选择左侧会话查看调用链</div>
        </section>

        <section v-if="selectedSpan" class="yh-card" aria-label="span 详情" data-testid="span-detail">
          <div class="card-head">
            <code class="span-title">{{ selectedSpan.name }}</code>
            <span class="yh-muted">{{ formatMs(selectedSpan.startMs) }} → {{ formatMs(selectedSpan.startMs + selectedSpan.ms) }} · {{ selectedSpan.costFen ? fenToYuan(selectedSpan.costFen) : '无模型费用' }}</span>
          </div>
          <div class="io">
            <div>
              <div class="io-lbl">输入</div>
              <pre class="json">{{ safeJson(selectedSpan.input, 2) || '—' }}</pre>
            </div>
            <div>
              <div class="io-lbl">输出</div>
              <pre class="json">{{ safeJson(selectedSpan.output, 2) || '—' }}</pre>
            </div>
          </div>
          <dl class="info">
            <dt>类型</dt><dd>{{ SPAN_TYPE_LABEL[selectedSpan.type] || selectedSpan.type }}</dd>
            <dt>结果</dt><dd><span class="yh-tag" :class="selectedSpan.ok ? 'ok' : 'err'">{{ selectedSpan.ok ? '成功' : '失败' }}</span></dd>
            <dt>Tokens</dt><dd>{{ selectedSpan.tokens != null ? formatInt(selectedSpan.tokens) : '—' }}</dd>
            <dt>成本</dt><dd>{{ selectedSpan.costFen != null ? fenToYuan(selectedSpan.costFen) : '—' }}</dd>
            <dt>脱敏</dt>
            <dd>
              <span v-if="maskedCount" class="yh-tag info" data-testid="masked">手机号 {{ maskedCount }} 处已掩码</span>
              <span v-else class="yh-muted">未检出手机号</span>
            </dd>
            <dt>父 span</dt><dd><code>{{ parentName }}</code></dd>
          </dl>
        </section>

        <el-drawer v-model="convOpen" :title="fullMode ? '完整对话（已记录查看原因）' : '对话摘要（已脱敏，正文已截断）'" size="520px" data-testid="conv-drawer">
          <div v-loading="convLoading" class="conv">
            <div v-for="m in messages" :key="m.messageId" class="msg" :class="m.role" :data-current="m.traceId === selectedId ? 'true' : undefined">
              <div class="bubble">
                <div v-if="m.tools?.length" class="m-tools">
                  <span v-for="(t, i) in m.tools" :key="i" class="m-tool" :class="{ fail: !t.ok }">
                    {{ t.ok ? '✓' : '✗' }} {{ t.label || t.name }}<template v-if="t.summary"> · {{ t.summary }}</template> <em>{{ formatMs(t.ms) }}</em>
                  </span>
                </div>
                <div class="m-text">{{ m.text }}</div>
                <div v-for="(c, i) in m.cards ?? []" :key="`c${i}`" class="m-card">{{ cardSummary(c) }}</div>
                <div v-for="a in m.actions ?? []" :key="a.actionId" class="m-card act">
                  {{ a.title }} · <b>{{ ACTION_STATUS[a.status] || a.status }}</b>
                </div>
              </div>
              <span class="yh-muted m-meta">
                {{ m.role === 'user' ? '用户' : '咕嘟' }} · {{ traceTime(m.createdAt) }}
                <template v-if="m.traceId === selectedId"> · 当前轮</template>
              </span>
            </div>
            <div v-if="!convLoading && !messages.length" class="empty yh-muted">没有对话记录</div>
          </div>
        </el-drawer>

        <section class="yh-card" aria-label="按工具统计">
          <div class="card-head">
            <span class="yh-card-title">按工具（{{ date === today ? '今天' : date }}）</span>
          </div>
          <el-table :data="stats?.byTool ?? []" data-testid="tool-stats" empty-text="暂无工具调用">
            <el-table-column label="工具" min-width="160">
              <template #default="{ row }"><code>{{ row.name }}</code></template>
            </el-table-column>
            <el-table-column label="调用" width="100" align="right">
              <template #default="{ row }">{{ formatInt(row.calls) }}</template>
            </el-table-column>
            <el-table-column label="成功率" width="110" align="right">
              <template #default="{ row }">
                <span :class="{ low: (row.successRate <= 1 ? row.successRate * 100 : row.successRate) < 95 }">{{ formatPct(row.successRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="P95" width="110" align="right">
              <template #default="{ row }">{{ formatMs(row.p95Ms) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-tools {
  display: flex;
  gap: 10px;
  align-items: center;
}
.search {
  width: 280px;
}
.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}
.split {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.list-card {
  padding: 14px 0 6px;
}
.list-card .card-head {
  padding: 0 14px;
}
.trace-list {
  display: flex;
  flex-direction: column;
  min-height: 120px;
}
.trace-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border: none;
  border-top: 1px solid var(--yh-border);
  border-left: 3px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--yh-title);
}
.trace-row:hover {
  background: var(--yh-highlight);
}
.trace-row.on {
  background: var(--yh-highlight);
  border-left-color: var(--yh-primary-deep);
}
.tr-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tr-top {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 14px;
}
.tr-top code {
  font-size: 12px;
  color: var(--yh-body);
}
.tr-ms {
  margin-left: auto;
  font-size: 12px;
  color: var(--yh-body);
  font-variant-numeric: tabular-nums;
}
.tr-text {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tr-meta {
  display: flex;
  gap: 8px;
  align-items: center;
}
.empty {
  padding: 28px 16px;
  text-align: center;
}
.more {
  margin: 6px auto;
}
.d-head {
  display: flex;
  gap: 12px;
  align-items: center;
}
.d-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.d-title b {
  font-size: 16px;
  color: var(--yh-title);
}
.banner {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
}
.banner.err {
  background: var(--yh-error-soft);
  color: var(--yh-error-text);
}
.sum {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin: 14px 0 10px;
  font-size: 13px;
  color: var(--yh-body);
}
.sum b {
  color: var(--yh-title);
}
.wf {
  display: flex;
  flex-direction: column;
}
.wf-axis,
.wf-row {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 84px;
  gap: 12px;
  align-items: center;
}
.wf-axis {
  height: 24px;
  font-size: 11px;
}
.wf-row {
  height: 36px;
  border: none;
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  border-radius: 8px;
  padding: 0;
  color: var(--yh-title);
}
.wf-row:hover,
.wf-row.on {
  background: var(--yh-highlight);
}
.wf-row.fail .wf-name code {
  color: var(--yh-error-text);
}
.wf-name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-left: 6px;
}
.wf-name code {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-track {
  position: relative;
  height: 100%;
}
.wf-axis .tick {
  position: absolute;
  top: 4px;
  transform: translateX(-50%);
  color: var(--yh-body);
  white-space: nowrap;
}
.wf-axis .tick:first-child {
  transform: none;
}
.wf-axis .tick:last-child {
  transform: translateX(-100%);
}
.grid {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--yh-border);
}
.bar {
  position: absolute;
  top: 10px;
  height: 16px;
  border-radius: 4px;
  min-width: 3px;
}
.bar.llm,
.dot.llm {
  background: var(--yh-primary-soft);
  background: color-mix(in srgb, var(--yh-primary-deep) 80%, white);
}
.bar.retrieval,
.dot.retrieval {
  background: var(--yh-info);
}
.bar.tool,
.dot.tool {
  background: var(--yh-success);
}
.bar.guard,
.dot.guard {
  background: var(--yh-body);
}
.bar.action,
.dot.action {
  background: var(--yh-primary-deep);
}
.bar.pending {
  background: repeating-linear-gradient(135deg, var(--yh-primary-soft) 0 4px, var(--yh-panel) 4px 8px);
  border: 1.5px dashed var(--yh-primary-deep);
  box-sizing: border-box;
}
.wf-ms {
  text-align: right;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  padding-right: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  display: inline-block;
  flex-shrink: 0;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--yh-body);
}
.legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.span-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--yh-title);
}
.io {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.io-lbl {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
  color: var(--yh-title);
}
.json {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--yh-highlight);
  border: 1px solid var(--yh-border);
  font-size: 12px;
  line-height: 1.6;
  color: var(--yh-title);
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.info {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 8px 12px;
  margin: 14px 0 0;
  font-size: 13px;
}
.info dt {
  color: var(--yh-body);
}
.info dd {
  margin: 0;
  color: var(--yh-title);
}
.low {
  color: var(--yh-error-text);
  font-weight: 700;
}
.conv {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 120px;
}
.msg {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 88%;
}
.msg.user {
  align-self: flex-end;
  align-items: flex-end;
}
.bubble {
  padding: 10px 12px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--yh-title);
  background: var(--yh-panel);
  border: 1px solid var(--yh-border);
}
.msg.user .bubble {
  background: var(--yh-secondary);
  border-color: transparent;
}
.msg[data-current] .bubble {
  box-shadow: 0 0 0 2px var(--yh-primary-soft);
}
.m-tools {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--yh-success-text);
}
.m-tool.fail {
  color: var(--yh-error-text);
}
.m-tool em {
  font-style: normal;
  color: var(--yh-body);
}
.m-text {
  white-space: pre-wrap;
}
.m-card {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--yh-highlight);
  font-size: 12px;
}
.m-card.act {
  border: 1px dashed var(--yh-primary-deep);
}
.m-meta {
  font-size: 11px;
}
@media (max-width: 1200px) {
  .kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .split {
    grid-template-columns: 1fr;
  }
}
</style>
