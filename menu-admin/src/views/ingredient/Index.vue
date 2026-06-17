<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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

const loading = ref(false)
const list = ref<Ingredient[]>([])
const unitOptions = ref<DictItem[]>([])
const purchaseOptions = ref<DictItem[]>([])
const metrics = ref<NutritionMetric[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await listIngredients()
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

function unitName(id?: number) {
  return unitOptions.value.find((u) => u.id === id)?.name ?? '-'
}
function purchaseName(id?: number) {
  return purchaseOptions.value.find((p) => p.id === id)?.name ?? '-'
}

// ===== 对话框 =====
const dialogVisible = ref(false)
const editing = ref<Ingredient | null>(null)

const baseForm = reactive<{
  id?: number
  name: string
  unitId: number | undefined
  price: number | undefined
  purchaseCategoryId: number | undefined
}>({
  name: '',
  unitId: undefined,
  price: undefined,
  purchaseCategoryId: undefined,
})

// 动态营养值，key=metricId
const nutritionMap = reactive<Record<number, number | undefined>>({})

function resetForm() {
  baseForm.id = undefined
  baseForm.name = ''
  baseForm.unitId = undefined
  baseForm.price = undefined
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
  baseForm.price = row.price
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
    price: baseForm.price ?? 0,
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
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="单位" width="100">
        <template #default="{ row }">{{ unitName(row.unitId) }}</template>
      </el-table-column>
      <el-table-column label="单价(元)" prop="price" width="120" />
      <el-table-column label="采购分类" width="140">
        <template #default="{ row }">{{ purchaseName(row.purchaseCategoryId) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑食材' : '新增食材'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="baseForm.name" placeholder="食材名称" />
        </el-form-item>
        <el-form-item label="计量单位">
          <el-select v-model="baseForm.unitId" placeholder="选择单位" style="width: 100%">
            <el-option v-for="u in unitOptions" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价(元)">
          <el-input-number v-model="baseForm.price" :min="0" :precision="2" />
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
