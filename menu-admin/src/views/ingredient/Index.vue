<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listIngredients,
  createIngredient,
  updateIngredient,
  deleteIngredient,
  getIngredientNutrition,
  type Ingredient,
} from '@/api/ingredient'
import { listByGroup, listNutritionMetrics, type DictItem, type NutritionMetric } from '@/api/dict'
import { searchDishes, type DishSearchRow } from '@/api/dish'
import { aiFillNutrition } from '@/api/ai'
import { countNutrition, hasNutrition } from '@/utils/nutrition'
import { formatCount } from '@/utils/dashboard'
import Pagination from '@/components/Pagination.vue'
import StatCard from '@/components/StatCard.vue'
import IngIcon from '@/components/IngIcon.vue'
import DishThumb from '@/components/DishThumb.vue'

const loading = ref(false)
// 全量食材（含营养），由后端一次拉取（已按 usage_count 权重倒序）；数据量小（数千条以内），
// 前端负责名称/分类/数据状态筛选 + 营养排序 + 分页
const allList = ref<Ingredient[]>([])
const keyword = ref('')
const purchaseFilter = ref<number | null>(null)
type DataFilter = 'all' | 'missing' | 'withData'
const dataFilter = ref<DataFilter>('all')
// 营养排序：sortBy=metric name(如 calorie)，sortOrder=asc/desc；都为空则保留后端 usage_count 权重顺序
const sortBy = ref<string>('')
const sortOrder = ref<'asc' | 'desc'>('desc')
const pageNum = ref(1)
const pageSize = 15

const purchaseOptions = ref<DictItem[]>([])
const metrics = ref<NutritionMetric[]>([])

// 后端 nutrition key 是 metric name（英文），前端映射成中文展示
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数',
}
const METRIC_ORDER = ['calorie', 'protein', 'fat', 'carb', 'sugar', 'gi']
// 打开右侧面板时表格变窄，只保留四大营养素列
const METRIC_COMPACT = ['calorie', 'protein', 'fat', 'carb']

const counts = computed(() => countNutrition(allList.value))

function numOr(v: number | undefined | null, fallback: number): number {
  return v === undefined || v === null || Number.isNaN(v) ? fallback : v
}

function isInedible(row: Ingredient) {
  return row.edible === false
}

const filteredList = computed<Ingredient[]>(() => {
  let rows = allList.value
  const kw = keyword.value.trim().toLowerCase()
  if (kw) rows = rows.filter((r) => (r.name || '').toLowerCase().includes(kw))
  if (purchaseFilter.value !== null) rows = rows.filter((r) => r.purchaseCategoryId === purchaseFilter.value)
  if (dataFilter.value === 'missing') rows = rows.filter((r) => !isInedible(r) && !hasNutrition(r.nutrition))
  if (dataFilter.value === 'withData') rows = rows.filter((r) => hasNutrition(r.nutrition))
  if (!sortBy.value) return rows
  const metric = sortBy.value
  const asc = sortOrder.value === 'asc'
  // 缺值项始终排在末尾：升序用 +∞，降序用 -∞
  const miss = asc ? Number.POSITIVE_INFINITY : Number.NEGATIVE_INFINITY
  return [...rows].sort((a, b) => {
    const va = numOr(a.nutrition?.[metric] as number | undefined, miss)
    const vb = numOr(b.nutrition?.[metric] as number | undefined, miss)
    return asc ? va - vb : vb - va
  })
})

const total = computed(() => filteredList.value.length)
const list = computed<Ingredient[]>(() => {
  const start = (pageNum.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

async function load() {
  loading.value = true
  try {
    allList.value = await listIngredients()
  } finally {
    loading.value = false
  }
}

async function loadDicts() {
  const [p] = await Promise.all([listByGroup('purchase_category')])
  purchaseOptions.value = p
  metrics.value = await listNutritionMetrics()
}

onMounted(() => {
  load()
  loadDicts()
})

watch([keyword, purchaseFilter, sortBy, sortOrder, dataFilter], () => {
  pageNum.value = 1
})

function onPageChange(p: number) {
  pageNum.value = p
}

// 表头点击排序：el-table sort-change 回调（sortable="custom"）
function onSortChange({ prop, order }: { prop: string | null; order: 'ascending' | 'descending' | null }) {
  if (!order) {
    sortBy.value = ''
    sortOrder.value = 'desc'
    return
  }
  sortBy.value = prop || ''
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
}

function purchaseName(id?: number) {
  return purchaseOptions.value.find((p) => p.id === id)?.name ?? '-'
}

function nutritionVal(row: Ingredient, metric: string): string {
  const v = row.nutrition?.[metric]
  return v === undefined || v === null ? '—' : String(v)
}

// ===== 右侧编辑面板（原型「核对」面板；核对状态字段待后端上线，这里是编辑 + AI 补全）=====
const panelMode = ref<'none' | 'edit' | 'create'>('none')
const editing = ref<Ingredient | null>(null)
const saving = ref(false)

const baseForm = reactive<{ id?: number; name: string; purchaseCategoryId: number | undefined }>({
  name: '',
  purchaseCategoryId: undefined,
})
// 动态营养值，key=metricId
const nutritionMap = reactive<Record<number, number | undefined>>({})

const aiLoading = ref(false)
/** 本次面板内营养值是否由 AI 预填（用于展示「AI 估算，请核对后保存」提示） */
const aiFilled = ref(false)

// 被引用菜谱（/dish/search?ingredientIds=）
const refDishes = ref<DishSearchRow[]>([])
const refTotal = ref<number | null>(null)

async function loadRefs(id: number) {
  refDishes.value = []
  refTotal.value = null
  try {
    const page = await searchDishes({ ingredientIds: [id], pageNum: 1, pageSize: 3 })
    refDishes.value = Array.isArray(page?.records) ? page.records : []
    refTotal.value = page?.total ?? refDishes.value.length
  } catch {
    refTotal.value = null
  }
}

async function onAiFillNutrition() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请先填写食材名称')
    return
  }
  aiLoading.value = true
  try {
    const r = await aiFillNutrition(baseForm.name.trim())
    for (const it of r.nutrition || []) nutritionMap[it.metricId] = Number(it.value)
    aiFilled.value = (r.nutrition?.length || 0) > 0
    if (aiFilled.value) ElMessage.success(`AI 已估算 ${r.nutrition.length} 项，请核对后保存`)
    else ElMessage.warning('AI 暂未给出估算结果，请手动填写')
  } finally {
    aiLoading.value = false
  }
}

function resetForm() {
  baseForm.id = undefined
  baseForm.name = ''
  baseForm.purchaseCategoryId = undefined
  for (const m of metrics.value) nutritionMap[m.id] = undefined
}

function openCreate() {
  aiFilled.value = false
  editing.value = null
  resetForm()
  refDishes.value = []
  refTotal.value = null
  panelMode.value = 'create'
}

async function openEdit(row: Ingredient) {
  aiFilled.value = false
  editing.value = row
  resetForm()
  baseForm.id = row.id
  baseForm.name = row.name
  baseForm.purchaseCategoryId = row.purchaseCategoryId
  panelMode.value = 'edit'
  loadRefs(row.id)
  try {
    const existing = await getIngredientNutrition(row.id)
    for (const m of metrics.value) {
      const v = existing[String(m.id)]
      nutritionMap[m.id] = v === undefined || v === null ? undefined : Number(v)
    }
  } catch {
    // 忽略，留空
  }
}

function closePanel() {
  panelMode.value = 'none'
  editing.value = null
}

async function onSubmit() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请填写食材名称')
    return
  }
  if (baseForm.purchaseCategoryId === undefined) {
    ElMessage.warning('请选择采购分类')
    return
  }
  const nutritions = []
  for (const m of metrics.value) {
    const v = nutritionMap[m.id]
    if (v !== undefined && v !== null && !Number.isNaN(v)) nutritions.push({ metricId: m.id, value: Number(v) })
  }
  const ingredient = { name: baseForm.name.trim(), purchaseCategoryId: baseForm.purchaseCategoryId }
  saving.value = true
  try {
    if (editing.value) {
      await updateIngredient({ ingredient: { id: editing.value.id, ...ingredient }, nutritions })
      ElMessage.success('已保存')
    } else {
      await createIngredient({ ingredient, nutritions })
      ElMessage.success('已新增')
    }
    closePanel()
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  const row = editing.value
  if (!row) return
  await ElMessageBox.confirm(`确定删除食材「${row.name}」？`, '提示', { type: 'warning' })
  await deleteIngredient(row.id)
  ElMessage.success('已删除')
  closePanel()
  await load()
}

function rowClass({ row }: { row: Ingredient }) {
  return row.id === editing.value?.id ? 'is-selected' : ''
}
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>食材与营养</h1>
        <div class="yh-sub">每 100 克可食部分；AI 估算的数据需人工核对后保存，才会用于菜谱营养和特殊人群标记</div>
      </div>
      <el-button type="primary" @click="openCreate">新建食材</el-button>
    </div>

    <div class="kpis">
      <StatCard label="食材总数" :value="formatCount(counts.total)" data-testid="nut-total" />
      <StatCard label="有营养数据" :value="formatCount(counts.withData)" tone="ok" data-testid="nut-with-data" />
      <StatCard label="缺营养数据" :value="formatCount(counts.missing)" tone="err" hint="可在右侧面板用 AI 补全" data-testid="nut-missing" />
      <StatCard label="AI 估算待核对" value="—" hint="核对状态接口待上线" data-testid="nut-pending" />
    </div>

    <div class="toolbar">
      <div class="yh-seg" role="group" aria-label="数据状态" data-testid="nut-filter">
        <button type="button" :class="{ on: dataFilter === 'all' }" @click="dataFilter = 'all'">全部</button>
        <button type="button" :class="{ on: dataFilter === 'missing' }" @click="dataFilter = 'missing'">缺数据 {{ counts.missing }}</button>
        <button type="button" :class="{ on: dataFilter === 'withData' }" @click="dataFilter = 'withData'">有数据</button>
      </div>
      <el-input v-model="keyword" placeholder="搜食材" clearable class="filter-input" />
      <el-select v-model="purchaseFilter" placeholder="全部品类" clearable class="filter-cat">
        <el-option label="全部品类" :value="null" />
        <el-option v-for="p in purchaseOptions" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <span class="yh-muted">默认按使用次数排序</span>
    </div>

    <div class="split" :class="{ 'with-panel': panelMode !== 'none' }">
      <section class="yh-card table-card">
        <el-table
          v-loading="loading"
          :data="list"
          :row-class-name="rowClass"
          data-testid="nut-table"
          @sort-change="onSortChange"
          @row-click="openEdit"
        >
          <el-table-column label="食材" min-width="180">
            <template #default="{ row }">
              <div class="ing-cell">
                <IngIcon :name="row.name" :icon-key="(row.iconKey as string) || null" :category-name="purchaseName(row.purchaseCategoryId)" :muted="isInedible(row)" />
                <span class="ing-txt">
                  <b>{{ row.name }}</b>
                  <span class="yh-muted">{{ purchaseName(row.purchaseCategoryId) }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-for="metric in panelMode === 'none' ? METRIC_ORDER : METRIC_COMPACT"
            :key="metric"
            :label="METRIC_CN[metric]"
            :prop="metric"
            :sortable="'custom'"
            min-width="84"
          >
            <template #default="{ row }">{{ nutritionVal(row, metric) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span v-if="isInedible(row)" class="yh-tag mute">不适用</span>
              <span v-else-if="hasNutrition(row.nutrition)" class="yh-tag ok">有数据</span>
              <span v-else class="yh-tag err">缺数据</span>
            </template>
          </el-table-column>
          <el-table-column v-if="panelMode === 'none'" label="使用次数" prop="usageCount" width="90" align="center" />
        </el-table>
        <Pagination :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
      </section>

      <section v-if="panelMode !== 'none'" class="yh-card panel" data-testid="nut-panel">
        <div class="p-head">
          <IngIcon :name="baseForm.name" :category-name="purchaseName(baseForm.purchaseCategoryId)" :size="56" />
          <div class="p-title">
            <b>{{ panelMode === 'create' ? '新建食材' : `编辑：${editing?.name}` }}</b>
            <span class="yh-muted">{{ purchaseName(baseForm.purchaseCategoryId) }}</span>
          </div>
          <el-button text aria-label="关闭" @click="closePanel">✕</el-button>
        </div>

        <el-form label-position="top" class="p-form">
          <div class="two">
            <el-form-item label="名称">
              <el-input v-model="baseForm.name" placeholder="食材名称" />
            </el-form-item>
            <el-form-item label="采购分类">
              <el-select v-model="baseForm.purchaseCategoryId" placeholder="选择分类" style="width: 100%">
                <el-option v-for="p in purchaseOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </div>

          <div class="ai-row">
            <el-button plain :loading="aiLoading" @click="onAiFillNutrition">AI 补全营养</el-button>
            <span class="yh-muted">按名称估算每 100g 营养，结果需核对</span>
          </div>
          <div v-if="aiFilled" class="ai-hint" data-testid="ai-nutrition-hint">AI 估算，请对照权威来源核对后保存</div>

          <div class="nut-grid">
            <el-form-item v-for="m in metrics" :key="m.id" :label="`${m.name}（${m.unit}）`">
              <el-input-number v-model="nutritionMap[m.id]" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
          </div>
        </el-form>

        <div v-if="panelMode === 'edit'" class="refs" data-testid="nut-refs">
          <span class="yh-muted">
            {{ refTotal === null ? '正在查询引用的菜谱…' : refTotal === 0 ? '暂无菜谱用到它' : `被 ${refTotal} 道菜谱引用；修改会影响这些菜的营养计算` }}
          </span>
          <div v-if="refDishes.length" class="ref-list">
            <DishThumb v-for="d in refDishes" :key="d.id" :src="d.coverUrl" :name="d.name" :size="36" :radius="8" />
            <span class="yh-muted">{{ refDishes.map((d) => d.name).join('、') }}<template v-if="(refTotal ?? 0) > refDishes.length"> 等</template></span>
          </div>
        </div>

        <div class="p-actions">
          <el-button v-if="panelMode === 'edit'" type="danger" text @click="onDelete">删除</el-button>
          <span class="spacer" />
          <el-button @click="closePanel">取消</el-button>
          <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.filter-input {
  width: 220px;
}
.filter-cat {
  width: 160px;
}
.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.split.with-panel {
  grid-template-columns: minmax(0, 1.6fr) minmax(360px, 1fr);
}
@media (max-width: 1200px) {
  .split.with-panel {
    grid-template-columns: 1fr;
  }
}
.table-card {
  padding: 6px 8px 12px;
}
.table-card :deep(.el-table__row) {
  cursor: pointer;
}
.table-card :deep(.el-table__row.is-selected > td) {
  background: var(--yh-highlight);
}
.ing-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ing-txt {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ing-txt b {
  color: var(--yh-title);
}
.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 0;
}
.p-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.p-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.p-title b {
  font-size: 18px;
  color: var(--yh-title);
}
.two,
.nut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.nut-grid :deep(.el-input-number) {
  width: 100%;
}
.p-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.ai-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.ai-hint {
  margin-bottom: 12px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 10px;
  color: var(--yh-info-text);
  background: var(--yh-info-soft);
}
.refs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ref-list {
  display: flex;
  align-items: center;
  gap: 6px;
}
.p-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.spacer {
  flex: 1;
}
</style>
