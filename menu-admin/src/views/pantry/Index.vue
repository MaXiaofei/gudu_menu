<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPantry,
  createPantry,
  updatePantry,
  deletePantry,
  type PantryVO,
  type Pantry,
} from '@/api/pantry'
import { listIngredients } from '@/api/ingredient'
import { listByGroup, type DictItem } from '@/api/dict'
import Pagination from '@/components/Pagination.vue'

const loading = ref(false)
const list = ref<PantryVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10

// 食材下拉项需含 unitId，选食材后自动带入单位（默认带，允许调）
const ingredients = ref<{ id: number; name: string; unitId: number }[]>([])
const unitOptions = ref<DictItem[]>([])

async function load() {
  loading.value = true
  try {
    const page = await listPantry({ pageNum: pageNum.value, pageSize })
    list.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

async function loadOptions() {
  const [ings, units] = await Promise.all([listIngredients(), listByGroup('unit')])
  ingredients.value = ings.map((x) => ({ id: x.id, name: x.name, unitId: x.unitId }))
  unitOptions.value = units
}

// 选食材后，按该食材的 unitId 自动带入单位（默认带，仍允许手改）
function onIngredientChange(ingredientId?: number) {
  if (ingredientId == null) return
  const ing = ingredients.value.find((x) => x.id === ingredientId)
  if (ing) form.unitId = ing.unitId
}

onMounted(() => {
  load()
  loadOptions()
})

// ============ 临期/不足标记（基于 VO 数据客户端再判，避免每次请求） ============
const today = ref(new Date().toISOString().slice(0, 10))
function daysBetween(expire?: string): number | null {
  if (!expire) return null
  const ms = new Date(expire).getTime() - new Date(today.value).getTime()
  return Math.ceil(ms / 86400000)
}
function isExpiring(row: PantryVO): boolean {
  const d = daysBetween(row.expireDate)
  return d !== null && d >= 0 && d <= 3
}
function isLow(row: PantryVO): boolean {
  return row.lowThreshold != null && row.lowThreshold > 0 && Number(row.amount) < Number(row.lowThreshold)
}
function expireText(row: PantryVO): string {
  const d = daysBetween(row.expireDate)
  if (d === null) return '-'
  if (d < 0) return `${row.expireDate}（已过期 ${-d} 天）`
  if (d === 0) return `${row.expireDate}（今天）`
  return `${row.expireDate}（剩 ${d} 天）`
}

// ============ 新增/编辑 ============
const dialogVisible = ref(false)
const editing = ref<PantryVO | null>(null)

function blankForm(): Pantry {
  return {
    ingredientId: undefined as unknown as number,
    amount: 0,
    unitId: undefined,
    expireDate: '',
    lowThreshold: 0,
  }
}

const form = reactive<Pantry>(blankForm())

function resetForm() {
  Object.assign(form, blankForm())
}

function openCreate() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PantryVO) {
  editing.value = row
  resetForm()
  form.id = row.id
  form.ingredientId = row.ingredientId
  form.amount = Number(row.amount)
  form.unitId = row.unitId
  form.expireDate = row.expireDate || ''
  form.lowThreshold = row.lowThreshold != null ? Number(row.lowThreshold) : 0
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.ingredientId) {
    ElMessage.warning('请选择食材')
    return
  }
  if (editing.value) {
    await updatePantry(form)
    ElMessage.success('已更新')
  } else {
    await createPantry(form)
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function onDelete(row: PantryVO) {
  await ElMessageBox.confirm(`确定删除「${row.ingredientName || '该项'}」库存？`, '提示', { type: 'warning' })
  await deletePantry(row.id!)
  ElMessage.success('已删除')
  await load()
}

const tableRowClass = ({ row }: { row: PantryVO }) => {
  if (isLow(row)) return 'row-low'
  if (isExpiring(row)) return 'row-expiring'
  return ''
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增库存</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border :row-class-name="tableRowClass">
      <el-table-column label="食材" min-width="140">
        <template #default="{ row }">
          {{ row.ingredientName || `#${row.ingredientId}` }}
        </template>
      </el-table-column>
      <el-table-column label="余量" width="120">
        <template #default="{ row }">
          <span :class="{ 'text-low': isLow(row) }">{{ row.amount }} {{ row.unitName || '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="阈值" width="100">
        <template #default="{ row }">
          {{ row.lowThreshold || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="过期日" min-width="180">
        <template #default="{ row }">
          <span :class="{ 'text-expiring': isExpiring(row) }">{{ expireText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">
          <span class="mini">{{ row.updateTime || '-' }}</span>
        </template>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑库存' : '新增库存'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="食材">
          <el-select v-model="form.ingredientId" filterable placeholder="选择食材" style="width: 100%" @change="onIngredientChange">
            <el-option
              v-for="i in ingredients"
              :key="i.id"
              :label="i.name"
              :value="i.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="余量">
          <el-input-number v-model="form.amount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.unitId" clearable placeholder="选食材后自动带入" style="width: 100%">
            <el-option
              v-for="u in unitOptions"
              :key="u.id"
              :label="u.name"
              :value="u.id"
            />
          </el-select>
          <span class="mini" style="margin-left: 8px">随食材自动带入，可手改</span>
        </el-form-item>
        <el-form-item label="过期日">
          <el-date-picker
            v-model="form.expireDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择过期日（可空）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="低库存阈值">
          <el-input-number v-model="form.lowThreshold" :min="0" :precision="2" />
          <span class="mini" style="margin-left: 8px">余量低于此值提示采购（0 表示不监控）</span>
        </el-form-item>
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
.mini {
  font-size: 12px;
  color: #7a6f60;
}
:deep(.row-low) {
  background: var(--el-color-danger-light-9, #fef0f0);
}
:deep(.row-expiring) {
  background: var(--el-color-warning-light-9, #fdf6ec);
}
.text-low {
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
}
.text-expiring {
  color: var(--el-color-warning, #e6a23c);
  font-weight: 600;
}
</style>
