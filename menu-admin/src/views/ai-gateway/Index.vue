<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createProvider, deleteProvider, getGateway, getGatewayUsage, saveRoute, testProvider, updateProvider,
  type Capability, type GatewayRoute, type Provider, type RouteTarget, type Thinking, type UsageRow,
} from '@/api/aiGateway'
import {
  CAPABILITIES, CAPABILITY_META, DEFAULT_PEAK_HOURS, THINKING_HINT, THINKING_OPTIONS, candidatesFor, ensureMasked,
  keyDisplay, moveItem, peakHoursText, priceText, reorder, targetKey, targetSupports, toModelBody, toModelDraft,
  validateProvider, validateRoute, type ModelDraft, type ProviderDraft,
} from '@/utils/aiGateway'
import { fenToYuan, formatInt, formatMs, formatPct, traceTime } from '@/utils/agentTrace'

/**
 * 模型网关（contracts.md §6）：所有模型调用按能力（CHAT / TOOLS / VISION）路由到「主模型 + 降级链」。
 * 供应商 key 只回显掩码；路由切换即时生效、影响全部用户，保存前二次确认。
 */
const providers = ref<Provider[]>([])
const routes = ref<GatewayRoute[]>([])
const loading = ref(false)
const loadError = ref('')

// ===== 路由编辑 =====
interface RouteDraft {
  primaryKey: string
  fallbacks: RouteTarget[]
  addKey: string
  timeoutSec: number
  maxRetries: number
  thinking: Thinking
  saving: boolean
  error: string
}
const drafts = reactive<Record<Capability, RouteDraft>>({
  CHAT: emptyDraft(),
  TOOLS: emptyDraft(),
  VISION: emptyDraft(),
})
const dragFrom = ref<{ cap: Capability; index: number } | null>(null)

function emptyDraft(): RouteDraft {
  return { primaryKey: '', fallbacks: [], addKey: '', timeoutSec: 20, maxRetries: 1, thinking: 'disabled', saving: false, error: '' }
}

const routeOf = (cap: Capability) => routes.value.find((r) => r.capability === cap) ?? null

function resetDraft(cap: Capability) {
  const r = routeOf(cap)
  Object.assign(drafts[cap], emptyDraft(), r
    ? {
        primaryKey: targetKey(r.primary),
        fallbacks: r.fallbacks.map((f) => ({ ...f })),
        timeoutSec: Math.round(r.timeoutMs / 1000),
        maxRetries: r.maxRetries,
        thinking: r.thinking ?? 'disabled',
      }
    : {})
}

function parseKey(key: string): RouteTarget | null {
  const i = key.indexOf(':')
  if (i < 0) return null
  return { providerId: Number(key.slice(0, i)), model: key.slice(i + 1) }
}

function labelOf(t: RouteTarget) {
  const p = providers.value.find((x) => x.providerId === t.providerId)
  return `${p?.name ?? `#${t.providerId}`} · ${t.model}`
}

const candidates = computed(() => ({
  CHAT: candidatesFor(providers.value, 'CHAT'),
  TOOLS: candidatesFor(providers.value, 'TOOLS'),
  VISION: candidatesFor(providers.value, 'VISION'),
}))

function isDirty(cap: Capability) {
  const r = routeOf(cap)
  const d = drafts[cap]
  if (!r) return !!d.primaryKey
  return (
    d.primaryKey !== targetKey(r.primary) ||
    d.fallbacks.map(targetKey).join('|') !== r.fallbacks.map(targetKey).join('|') ||
    d.timeoutSec * 1000 !== r.timeoutMs ||
    d.maxRetries !== r.maxRetries ||
    d.thinking !== (r.thinking ?? 'disabled')
  )
}

function addFallback(cap: Capability) {
  const t = parseKey(drafts[cap].addKey)
  if (!t) return
  drafts[cap].fallbacks = [...drafts[cap].fallbacks, t]
  drafts[cap].addKey = ''
}

function move(cap: Capability, i: number, dir: -1 | 1) {
  drafts[cap].fallbacks = moveItem(drafts[cap].fallbacks, i, dir)
}

function removeFallback(cap: Capability, i: number) {
  drafts[cap].fallbacks = drafts[cap].fallbacks.filter((_, k) => k !== i)
}

function onDrop(cap: Capability, to: number) {
  const from = dragFrom.value
  dragFrom.value = null
  if (!from || from.cap !== cap) return
  drafts[cap].fallbacks = reorder(drafts[cap].fallbacks, from.index, to)
}

/** 降级链里还能加的：本能力候选，排除主模型与已在链中的。 */
function addable(cap: Capability) {
  const used = new Set([drafts[cap].primaryKey, ...drafts[cap].fallbacks.map(targetKey)])
  return candidates.value[cap].filter((c) => !used.has(`${c.providerId}:${c.model}`))
}

async function onSaveRoute(cap: Capability) {
  const d = drafts[cap]
  const primary = parseKey(d.primaryKey)
  const body = { primary, fallbacks: d.fallbacks, timeoutMs: Math.round(d.timeoutSec * 1000), maxRetries: d.maxRetries }
  const err = validateRoute(body)
  const unsupported = [primary, ...d.fallbacks].filter((t) => t && !targetSupports(providers.value, t, cap))
  d.error = err ?? (unsupported.length ? `${unsupported.map((t) => labelOf(t!)).join('、')} 不支持「${CAPABILITY_META[cap].label}」能力或已停用` : '')
  if (d.error) return
  try {
    await ElMessageBox.confirm(
      `「${CAPABILITY_META[cap].label}」路由将切到 ${labelOf(primary!)}${d.fallbacks.length ? `，降级链 ${d.fallbacks.map(labelOf).join(' → ')}` : ''}。即时生效，影响全部用户。`,
      '确认切换路由',
      { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  d.saving = true
  try {
    const saved = await saveRoute(cap, { ...body, primary: primary!, thinking: d.thinking })
    routes.value = [...routes.value.filter((r) => r.capability !== cap), saved ?? { ...(routeOf(cap) as GatewayRoute), ...body, primary: primary!, thinking: d.thinking }]
    resetDraft(cap)
    ElMessage.success('路由已切换，已写入审计日志')
  } finally {
    d.saving = false
  }
}

// ===== 供应商 =====
const drawer = ref(false)
const editingId = ref<number | null>(null)
const pform = reactive<ProviderDraft>({ name: '', baseUrl: '', apiKey: '', enabled: true, models: [] })
const perrors = ref<Record<string, string>>({})
const psaving = ref(false)

function newModel(): ModelDraft {
  return { model: '', capabilities: ['CHAT'], cacheHitPrice: '0', inputPrice: '0', outputPrice: '0', peakHours: '' }
}

function openCreate() {
  editingId.value = null
  Object.assign(pform, { name: '', baseUrl: 'https://', apiKey: '', enabled: true, models: [newModel()] })
  perrors.value = {}
  drawer.value = true
}

function openEdit(p: Provider) {
  editingId.value = p.providerId
  Object.assign(pform, { name: p.name, baseUrl: p.baseUrl, apiKey: '', enabled: p.enabled, models: p.models.map(toModelDraft) })
  perrors.value = {}
  drawer.value = true
}

async function onSaveProvider() {
  perrors.value = validateProvider(pform, { editing: editingId.value != null })
  if (Object.keys(perrors.value).length) return
  psaving.value = true
  const body = { name: pform.name.trim(), baseUrl: pform.baseUrl.trim(), apiKey: pform.apiKey.trim(), enabled: pform.enabled, models: pform.models.map(toModelBody) }
  try {
    if (editingId.value != null) await updateProvider(editingId.value, body)
    else await createProvider(body)
    ElMessage.success(editingId.value != null ? '已保存' : '已新增供应商')
    drawer.value = false
    await load()
  } finally {
    psaving.value = false
  }
}

async function toggleEnabled(p: Provider, on: boolean) {
  await updateProvider(p.providerId, { name: p.name, baseUrl: p.baseUrl, apiKey: '', enabled: on, models: p.models })
  p.enabled = on
  ElMessage.success(on ? '已启用' : '已停用')
}

async function onDelete(p: Provider) {
  try {
    await ElMessageBox.confirm(`删除供应商「${p.name}」？其 key 会一并清除。`, '删除供应商', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await deleteProvider(p.providerId)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 409) {
      const used = CAPABILITIES.filter((c) => {
        const r = routeOf(c)
        return !!r && [r.primary, ...r.fallbacks].some((t) => t.providerId === p.providerId)
      }).map((c) => CAPABILITY_META[c].label)
      ElMessageBox.alert(
        `「${p.name}」正被${used.length ? `「${used.join('、')}」` : ''}路由引用，请先在路由里替换后再删除。`,
        '无法删除',
        { type: 'warning' },
      )
    }
  }
}

// ===== 测试连接 =====
const testDlg = ref(false)
const testTarget = ref<Provider | null>(null)
const testForm = reactive<{ model: string; capability: Capability }>({ model: '', capability: 'CHAT' })
const testing = ref(false)
const testResult = ref<{ ok: boolean; latencyMs: number | null; error: string | null; sample: string | null } | null>(null)

function openTest(p: Provider) {
  testTarget.value = p
  testForm.model = p.models[0]?.model ?? ''
  testForm.capability = p.models[0]?.capabilities[0] ?? 'CHAT'
  testResult.value = null
  testDlg.value = true
}

const testCaps = computed(() => testTarget.value?.models.find((m) => m.model === testForm.model)?.capabilities ?? [])

async function runTest() {
  if (!testTarget.value) return
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testProvider(testTarget.value.providerId, { ...testForm })
  } catch (e) {
    testResult.value = { ok: false, latencyMs: null, error: (e as Error)?.message || '请求失败', sample: null }
  } finally {
    testing.value = false
  }
}

// ===== 用量 =====
const range = ref<'today' | '7d'>('today')
const usage = ref<UsageRow[]>([])
const usageLoading = ref(false)

async function loadUsage() {
  usageLoading.value = true
  try {
    const res = await getGatewayUsage(range.value)
    usage.value = Array.isArray(res?.byRoute) ? res.byRoute : []
  } catch {
    usage.value = []
  } finally {
    usageLoading.value = false
  }
}

function onRange(r: 'today' | '7d') {
  if (range.value === r) return
  range.value = r
  loadUsage()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getGateway()
    providers.value = Array.isArray(res?.providers) ? res.providers : []
    routes.value = Array.isArray(res?.routes) ? res.routes : []
    CAPABILITIES.forEach(resetDraft)
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  loadUsage()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>模型网关</h1>
        <div class="yh-sub">所有模型调用按能力路由到「主模型 + 降级链」；切换即时生效并写入审计日志，key 只显示掩码</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div v-if="loadError" class="yh-card yh-muted">{{ loadError }}</div>

    <div v-loading="loading" class="routes" data-testid="routes">
      <section v-for="cap in CAPABILITIES" :key="cap" class="yh-card route" :data-testid="`route-${cap}`">
        <div class="r-head">
          <div>
            <span class="yh-card-title">{{ CAPABILITY_META[cap].label }}</span>
            <code class="cap">{{ cap }}</code>
          </div>
          <span v-if="isDirty(cap)" class="yh-tag warn">未保存</span>
        </div>
        <div class="yh-muted">{{ CAPABILITY_META[cap].desc }}</div>

        <label class="lbl">主模型</label>
        <el-select v-model="drafts[cap].primaryKey" placeholder="选择主模型" style="width: 100%" :data-testid="`primary-${cap}`">
          <el-option v-for="c in candidates[cap]" :key="`${c.providerId}:${c.model}`" :label="c.label" :value="`${c.providerId}:${c.model}`" />
        </el-select>
        <div v-if="cap === 'VISION'" class="yh-muted hint">只列出声明了「视觉」能力的模型</div>

        <label class="lbl">降级链（拖动或上下移调整顺序）</label>
        <ol class="chain" :data-testid="`chain-${cap}`">
          <li
            v-for="(f, i) in drafts[cap].fallbacks"
            :key="targetKey(f)"
            draggable="true"
            :class="{ warn: !targetSupports(providers, f, cap) }"
            @dragstart="dragFrom = { cap, index: i }"
            @dragover.prevent
            @drop="onDrop(cap, i)"
          >
            <span class="grip" aria-hidden="true">⋮⋮</span>
            <span class="n">{{ i + 1 }}</span>
            <span class="t">{{ labelOf(f) }}</span>
            <button type="button" class="ic" :disabled="i === 0" aria-label="上移" @click="move(cap, i, -1)">↑</button>
            <button type="button" class="ic" :disabled="i === drafts[cap].fallbacks.length - 1" aria-label="下移" @click="move(cap, i, 1)">↓</button>
            <button type="button" class="ic" aria-label="移除" @click="removeFallback(cap, i)">×</button>
          </li>
          <li v-if="!drafts[cap].fallbacks.length" class="none yh-muted">无降级：主模型失败直接报错</li>
        </ol>
        <div class="add">
          <el-select v-model="drafts[cap].addKey" placeholder="添加降级模型" size="small" style="flex: 1" @change="addFallback(cap)">
            <el-option v-for="c in addable(cap)" :key="`${c.providerId}:${c.model}`" :label="c.label" :value="`${c.providerId}:${c.model}`" />
          </el-select>
        </div>

        <div class="nums">
          <label>超时（秒）<el-input-number v-model="drafts[cap].timeoutSec" :min="1" :max="120" size="small" controls-position="right" /></label>
          <label>重试<el-input-number v-model="drafts[cap].maxRetries" :min="0" :max="3" size="small" controls-position="right" /></label>
        </div>
        <label class="lbl">思考模式</label>
        <el-select v-model="drafts[cap].thinking" size="small" style="width: 100%" :data-testid="`thinking-${cap}`">
          <el-option v-for="o in THINKING_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <div class="yh-muted hint">{{ THINKING_HINT }}</div>

        <div v-if="drafts[cap].error" class="err-txt" role="alert">{{ drafts[cap].error }}</div>
        <div class="r-foot">
          <span class="yh-muted">
            <template v-if="routeOf(cap)">{{ routeOf(cap)!.updatedBy }} · {{ traceTime(routeOf(cap)!.updatedAt) }}</template>
          </span>
          <span>
            <el-button size="small" :disabled="!isDirty(cap)" @click="resetDraft(cap)">还原</el-button>
            <el-button size="small" type="primary" :disabled="!isDirty(cap)" :loading="drafts[cap].saving" :data-testid="`save-${cap}`" @click="onSaveRoute(cap)">保存</el-button>
          </span>
        </div>
      </section>
    </div>

    <section class="yh-card" aria-label="供应商">
      <div class="card-head">
        <span class="yh-card-title">供应商</span>
        <el-button type="primary" data-testid="add-provider" @click="openCreate">新增供应商</el-button>
      </div>
      <el-table :data="providers" data-testid="providers" empty-text="暂无供应商">
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }">
            <b>{{ row.name }}</b>
            <div class="yh-muted">{{ row.type === 'MOCK' ? '规则兜底' : 'OpenAI 兼容' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Base URL" min-width="200">
          <template #default="{ row }"><code class="url">{{ row.baseUrl || '—' }}</code></template>
        </el-table-column>
        <el-table-column label="Key" min-width="150">
          <template #default="{ row }">
            <code data-testid="key-masked">{{ ensureMasked(keyDisplay(row).text) || keyDisplay(row).text }}</code>
            <div><span class="yh-tag" :class="row.keySource === 'NONE' && row.type !== 'MOCK' ? 'err' : 'mute'">{{ keyDisplay(row).source }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="模型与单价（元 / 百万 tokens，高峰）" min-width="320">
          <template #default="{ row }">
            <div v-for="m in row.models" :key="m.model" class="model">
              <code>{{ m.model }}</code>
              <span v-for="c in m.capabilities" :key="c" class="yh-tag info">{{ CAPABILITY_META[c as Capability]?.label || c }}</span>
              <span class="price">缓存命中 {{ priceText(m.price?.inputCacheHitFen) }} · 输入 {{ priceText(m.price?.inputFen) }} · 输出 {{ priceText(m.price?.outputFen) }}</span>
              <div class="yh-muted">{{ peakHoursText(m.peakHours) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="健康" width="120">
          <template #default="{ row }">
            <span class="yh-tag" :class="row.health?.ok ? 'ok' : 'err'">{{ row.health?.ok ? '正常' : '异常' }}</span>
            <div class="yh-muted">{{ row.health?.latencyMs != null ? formatMs(row.health.latencyMs) : '未检测' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" :aria-label="`启用 ${row.name}`" @change="(v: string | number | boolean) => toggleEnabled(row, !!v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :data-testid="`test-${row.providerId}`" @click="openTest(row)">测试连接</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :data-testid="`del-${row.providerId}`" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="yh-card" aria-label="用量">
      <div class="card-head">
        <span class="yh-card-title">按路由用量</span>
        <div class="yh-seg" role="group" aria-label="时间范围">
          <button type="button" :class="{ on: range === 'today' }" @click="onRange('today')">今日</button>
          <button type="button" :class="{ on: range === '7d' }" @click="onRange('7d')">近 7 天</button>
        </div>
      </div>
      <el-table v-loading="usageLoading" :data="usage" data-testid="usage" empty-text="暂无调用">
        <el-table-column label="能力" width="110">
          <template #default="{ row }">{{ CAPABILITY_META[row.capability as Capability]?.label || row.capability }}</template>
        </el-table-column>
        <el-table-column label="供应商 · 模型" min-width="200">
          <template #default="{ row }">{{ row.provider }} · <code>{{ row.model }}</code></template>
        </el-table-column>
        <el-table-column label="调用" width="110" align="right">
          <template #default="{ row }">{{ formatInt(row.calls) }}</template>
        </el-table-column>
        <el-table-column label="失败率" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ bad: (row.failRate <= 1 ? row.failRate * 100 : row.failRate) > 5 }">{{ formatPct(row.failRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="P95" width="110" align="right">
          <template #default="{ row }">{{ formatMs(row.p95Ms) }}</template>
        </el-table-column>
        <el-table-column label="成本" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.costFen, 2) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="drawer" :title="editingId != null ? '编辑供应商' : '新增供应商'" size="560px" data-testid="provider-drawer">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="名称" :error="perrors.name">
          <el-input v-model="pform.name" placeholder="如 DeepSeek" data-testid="p-name" />
        </el-form-item>
        <el-form-item label="Base URL" :error="perrors.baseUrl">
          <el-input v-model="pform.baseUrl" placeholder="https://api.deepseek.com" data-testid="p-url" />
        </el-form-item>
        <el-form-item label="API Key" :error="perrors.apiKey">
          <el-input
            v-model="pform.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingId != null ? '留空表示不修改' : '输入后加密保存，只回显掩码'"
            data-testid="p-key"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="pform.enabled" />
        </el-form-item>

        <div class="m-head">
          <b>模型</b>
          <el-button size="small" @click="pform.models.push(newModel())">添加模型</el-button>
        </div>
        <div v-if="perrors.models" class="err-txt">{{ perrors.models }}</div>
        <div v-for="(m, i) in pform.models" :key="i" class="m-card">
          <div class="m-row">
            <el-form-item label="模型名" :error="perrors[`models.${i}.model`]" class="grow">
              <el-input v-model="m.model" placeholder="deepseek-flash" />
            </el-form-item>
            <el-button link type="danger" :disabled="pform.models.length === 1" @click="pform.models.splice(i, 1)">移除</el-button>
          </div>
          <el-form-item label="能力" :error="perrors[`models.${i}.capabilities`]">
            <el-checkbox-group v-model="m.capabilities">
              <el-checkbox v-for="c in CAPABILITIES" :key="c" :value="c">{{ CAPABILITY_META[c].label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <div class="prices">
            <el-form-item label="缓存命中输入（元）" :error="perrors[`models.${i}.cacheHitPrice`]">
              <el-input v-model="m.cacheHitPrice" />
            </el-form-item>
            <el-form-item label="输入（元）" :error="perrors[`models.${i}.inputPrice`]">
              <el-input v-model="m.inputPrice" />
            </el-form-item>
            <el-form-item label="输出（元）" :error="perrors[`models.${i}.outputPrice`]">
              <el-input v-model="m.outputPrice" />
            </el-form-item>
          </div>
          <el-form-item label="高峰时段（留空 = 不分峰谷）" :error="perrors[`models.${i}.peakHours`]">
            <el-input v-model="m.peakHours" :placeholder="DEFAULT_PEAK_HOURS">
              <template #append><el-button @click="m.peakHours = DEFAULT_PEAK_HOURS">DeepSeek 默认</el-button></template>
            </el-input>
            <div class="yh-muted">{{ peakHoursText(m.peakHours) }}；单价填每百万 token 高峰价</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" :loading="psaving" data-testid="p-save" @click="onSaveProvider">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="testDlg" :title="`测试连接 · ${testTarget?.name ?? ''}`" width="520px">
      <el-form label-position="top" @submit.prevent>
        <div class="prices">
          <el-form-item label="模型">
            <el-select v-model="testForm.model" style="width: 100%">
              <el-option v-for="m in testTarget?.models ?? []" :key="m.model" :label="m.model" :value="m.model" />
            </el-select>
          </el-form-item>
          <el-form-item label="能力">
            <el-select v-model="testForm.capability" style="width: 100%">
              <el-option v-for="c in testCaps" :key="c" :label="CAPABILITY_META[c].label" :value="c" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <div v-if="testResult" class="test-res" :class="testResult.ok ? 'ok' : 'err'" data-testid="test-result">
        <b>{{ testResult.ok ? '连接正常' : '连接失败' }}</b>
        <span v-if="testResult.latencyMs != null"> · {{ formatMs(testResult.latencyMs) }}</span>
        <div v-if="testResult.error" class="msg">{{ testResult.error }}</div>
        <pre v-if="testResult.sample" class="sample">{{ testResult.sample }}</pre>
      </div>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testing" data-testid="test-run" @click="runTest">开始测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.routes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.route {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.r-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cap {
  margin-left: 8px;
  font-size: 12px;
  color: var(--yh-body);
}
.lbl {
  margin-top: 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--yh-body);
}
.hint {
  margin-top: 2px;
}
.chain {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.chain li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--yh-border);
  border-radius: 10px;
  background: var(--yh-panel);
  font-size: 13px;
  color: var(--yh-title);
  cursor: grab;
}
.chain li.warn {
  border-color: var(--yh-error);
}
.chain li.none {
  cursor: default;
  border-style: dashed;
}
.grip {
  color: var(--yh-body);
  letter-spacing: -2px;
}
.chain .n {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: var(--yh-secondary);
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.chain .t {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ic {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--yh-body);
  font-size: 14px;
  padding: 0 4px;
}
.ic:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.add {
  display: flex;
  margin-top: 4px;
}
.nums {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}
.nums label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--yh-body);
}
.r-foot {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.err-txt {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 6px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.url {
  font-size: 12px;
  word-break: break-all;
}
.model {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
}
.model .price {
  font-size: 12px;
  color: var(--yh-body);
}
.model .yh-muted {
  width: 100%;
}
.bad {
  color: var(--yh-error-text);
  font-weight: 700;
}
.m-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
}
.m-card {
  border: 1px solid var(--yh-border);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
}
.m-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.grow {
  flex: 1;
}
.prices {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.test-res {
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 13px;
}
.test-res.ok {
  background: var(--yh-success-soft);
  color: var(--yh-success-text);
}
.test-res.err {
  background: var(--yh-error-soft);
  color: var(--yh-error-text);
}
.msg {
  margin-top: 6px;
}
.sample {
  margin: 8px 0 0;
  padding: 10px;
  border-radius: 8px;
  background: var(--yh-panel);
  color: var(--yh-title);
  white-space: pre-wrap;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
@media (max-width: 1200px) {
  .routes {
    grid-template-columns: 1fr;
  }
}
</style>
