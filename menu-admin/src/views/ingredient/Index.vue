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
import { aiFillNutrition } from '@/api/ai'
import Pagination from '@/components/Pagination.vue'

const loading = ref(false)
// 全量食材（含营养），由后端一次拉取；数据量小（~187），前端负责筛选/排序/分页
const allList = ref<Ingredient[]>([])
// 采购分类筛选：null=全部；非空则按 purchaseCategoryId 过滤
const purchaseFilter = ref<number | null>(null)
// 营养排序：sortBy=metric name(如 calorie)，sortOrder=asc/desc；都为空则按后端默认 id 倒序
const sortBy = ref<string>('')
const sortOrder = ref<'asc' | 'desc'>('desc')
// 前端分页
const pageNum = ref(1)
const pageSize = 10

const unitOptions = ref<DictItem[]>([])
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

function numOr(v: number | undefined | null, fallback: number): number {
  return v === undefined || v === null || Number.isNaN(v) ? fallback : v
}

// 全量 → 按采购分类过滤 → 按营养排序
const filteredList = computed<Ingredient[]>(() => {
  let rows = allList.value
  if (purchaseFilter.value !== null) {
    rows = rows.filter((r) => r.purchaseCategoryId === purchaseFilter.value)
  }
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

// 当前页数据（前端切片）
const list = computed<Ingredient[]>(() => {
  const start = (pageNum.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

async function load() {
  loading.value = true
  try {
    allList.value = await listIngredients()
    pageNum.value = 1
  } finally {
    loading.value = false
  }
}

async function loadDicts() {
  const [u, p] = await Promise.all([listByGroup('unit'), listByGroup('purchase_category')])
  unitOptions.value = u
  purchaseOptions.value = p
  metrics.value = await listNutritionMetrics()
}

onMounted(() => {
  load()
  loadDicts()
})

// 筛选/排序变化时回到第一页
watch([purchaseFilter, sortBy, sortOrder], () => {
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

// 清空采购分类筛选（“全部”）
function clearPurchaseFilter() {
  purchaseFilter.value = null
}

function unitName(id?: number) {
  return unitOptions.value.find((u) => u.id === id)?.name ?? '-'
}
function purchaseName(id?: number) {
  return purchaseOptions.value.find((p) => p.id === id)?.name ?? '-'
}

// 单元格营养值显示：缺值显示 “-”
function nutritionVal(row: Ingredient, metric: string): string {
  const v = row.nutrition?.[metric]
  return v === undefined || v === null ? '-' : String(v)
}

// ===== 对话框 =====
const dialogVisible = ref(false)
const editing = ref<Ingredient | null>(null)

const baseForm = reactive<{
  id?: number
  name: string
  unitId: number | undefined
  purchaseCategoryId: number | undefined
}>({
  name: '',
  unitId: undefined,
  purchaseCategoryId: undefined,
})

// 动态营养值，key=metricId
const nutritionMap = reactive<Record<number, number | undefined>>({})

// AI 补全营养（mock，待接 GLM）
const aiLoading = ref(false)
async function onAiFillNutrition() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请先填写食材名称')
    return
  }
  aiLoading.value = true
  try {
    const r = await aiFillNutrition(baseForm.name.trim())
    // 后端返回 {nutrition:[{metricId,value}], source}
    for (const it of r.nutrition || []) {
      nutritionMap[it.metricId] = Number(it.value)
    }
    ElMessage.success(
      `AI 已填${r.nutrition?.length || 0}项${r.source === 'mock' ? '（mock，待接 GLM，请核对）' : ''}`,
    )
  } finally {
    aiLoading.value = false
  }
}

// 把 nutrition(metric name->value) 合成紧凑文本「热量 19 / 蛋白质 0.9 / ...」（弹窗/其他展示备用）
function nutritionText(nutrition?: Record<string, number>): string {
  if (!nutrition) return '-'
  const parts = METRIC_ORDER.filter((k) => nutrition[k] !== undefined && nutrition[k] !== null).map(
    (k) => `${METRIC_CN[k] || k} ${nutrition[k]}`,
  )
  return parts.length ? parts.join(' / ') : '-'
}
void nutritionText

function resetForm() {
  baseForm.id = undefined
  baseForm.name = ''
  baseForm.unitId = undefined
  baseForm.purchaseCategoryId = undefined
  for (const m of metrics.value) {
    nutritionMap[m.id] = undefined
  }
}

function openCreate() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: Ingredient) {
  editing.value = row
  resetForm()
  baseForm.id = row.id
  baseForm.name = row.name
  baseForm.unitId = row.unitId
  baseForm.purchaseCategoryId = row.purchaseCategoryId
  // 拉取已有营养值
  try {
    const existing = await getIngredientNutrition(row.id)
    for (const m of metrics.value) {
      const v = existing[String(m.id)]
      nutritionMap[m.id] = v === undefined || v === null ? undefined : Number(v)
    }
  } catch {
    // 忽略，留空
  }
  dialogVisible.value = true
}

async function onSubmit() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请填写食材名称')
    return
  }
  if (baseForm.unitId === undefined) {
    ElMessage.warning('请选择计量单位')
    return
  }
  if (baseForm.purchaseCategoryId === undefined) {
    ElMessage.warning('请选择采购分类')
    return
  }
  const nutritions = []
  for (const m of metrics.value) {
    const v = nutritionMap[m.id]
    if (v !== undefined && v !== null && !Number.isNaN(v)) {
      nutritions.push({ metricId: m.id, value: Number(v) })
    }
  }
  const ingredient = {
    name: baseForm.name.trim(),
    unitId: baseForm.unitId,
    purchaseCategoryId: baseForm.purchaseCategoryId,
  }
  if (editing.value) {
    await updateIngredient({
      ingredient: { id: editing.value.id, ...ingredient },
      nutritions,
    })
    ElMessage.success('已更新')
  } else {
    await createIngredient({ ingredient, nutritions })
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function onDelete(row: Ingredient) {
  await ElMessageBox.confirm(`确定删除食材「${row.name}」？`, '提示', { type: 'warning' })
  await deleteIngredient(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增食材</el-button>
      <el-select
        v-model="purchaseFilter"
        placeholder="采购分类（全部）"
        clearable
        style="width: 200px; margin-left: 12px"
        @clear="clearPurchaseFilter"
      >
        <el-option label="全部" :value="null" />
        <el-option v-for="p in purchaseOptions" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </div>
    <el-table v-loading="loading" :data="list" border @sort-change="onSortChange">
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="单位" width="100">
        <template #default="{ row }">{{ unitName(row.unitId) }}</template>
      </el-table-column>
      <el-table-column label="采购分类" width="140">
        <template #default="{ row }">{{ purchaseName(row.purchaseCategoryId) }}</template>
      </el-table-column>
      <el-table-column
        v-for="metric in METRIC_ORDER"
        :key="metric"
        :label="METRIC_CN[metric]"
        :prop="metric"
        :sortable="'custom'"
        width="110"
      >
        <template #default="{ row }">{{ nutritionVal(row, metric) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑食材' : '新增食材'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="baseForm.name" placeholder="食材名称" />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="warning" :loading="aiLoading" @click="onAiFillNutrition">
            AI 补全营养（按名称预估 6 项，mock 待接 GLM，请核对）
          </el-button>
        </el-form-item>
        <el-form-item label="计量单位">
          <el-select v-model="baseForm.unitId" placeholder="选择单位" style="width: 100%">
            <el-option v-for="u in unitOptions" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购分类">
          <el-select v-model="baseForm.purchaseCategoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="p in purchaseOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">营养指标（每单位含量）</el-divider>
        <div class="nut-grid">
          <el-form-item
            v-for="m in metrics"
            :key="m.id"
            :label="`${m.name}(${m.unit})`"
            class="nut-item"
          >
            <el-input-number v-model="nutritionMap[m.id]" :min="0" :precision="2" controls-position="right" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: var(--yh-panel, #fff);
  padding: 16px;
  border-radius: 8px;
}
.toolbar {
  margin-bottom: 12px;
}
.nut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.nut-item :deep(.el-input-number) {
  width: 100%;
}
</style>
