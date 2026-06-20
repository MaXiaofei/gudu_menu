<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPlans,
  createPlan,
  getPlan,
  addItem,
  delItem,
  listTemplates,
  applyTemplate,
  type MealPlan,
  type MealPlanItem,
  type MenuTemplate,
} from '@/api/mealplan'
import { listDishes } from '@/api/dish'
import Pagination from '@/components/Pagination.vue'

const loading = ref(false)
const list = ref<MealPlan[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const dishes = ref<{ id: number; name: string }[]>([])

async function load() {
  loading.value = true
  try {
    const page = await listPlans({ pageNum: pageNum.value, pageSize })
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
  const d = await listDishes()
  dishes.value = d.map((x) => ({ id: x.id, name: x.name }))
}

onMounted(() => {
  load()
  loadOptions()
})

// 新建周计划
const today = new Date()
function mondayOf(d: Date) {
  const x = new Date(d)
  const day = (x.getDay() + 6) % 7
  x.setDate(x.getDate() - day)
  return x
}
function fmt(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}
const newVisible = ref(false)
const newForm = ref<{ weekStart: string; name: string }>({ weekStart: fmt(mondayOf(today)), name: '' })

function openNew() {
  newForm.value = { weekStart: fmt(mondayOf(new Date())), name: '' }
  newVisible.value = true
}

async function onCreate() {
  if (!newForm.value.weekStart) {
    ElMessage.warning('请选择周起始')
    return
  }
  await createPlan(newForm.value.weekStart, newForm.value.name || undefined)
  ElMessage.success('已创建')
  newVisible.value = false
  await load()
}

// 排菜管理
const manageVisible = ref(false)
const managing = ref<MealPlan | null>(null)
const items = ref<MealPlanItem[]>([])
const templates = ref<MenuTemplate[]>([])
const MEALS = ['早餐', '午餐', '晚餐', '加餐']

function dishName(id: number) {
  return dishes.value.find((d) => d.id === id)?.name || `#${id}`
}

async function openManage(row: MealPlan) {
  managing.value = row
  manageVisible.value = true
  await loadDetail(row.id)
  if (templates.value.length === 0) {
    try {
      templates.value = await listTemplates()
    } catch {
      /* ignore */
    }
  }
}

async function loadDetail(planId: number) {
  const d = await getPlan(planId)
  items.value = d.items || []
}

// 行内新增一项
const addRow = ref<{ date: string; meal: string; dishId: number | undefined; servingFactor: number }>({
  date: '',
  meal: '午餐',
  dishId: undefined,
  servingFactor: 1,
})

function groupItems() {
  // 按 date 分组
  const map = new Map<string, MealPlanItem[]>()
  for (const it of items.value) {
    if (!map.has(it.date)) map.set(it.date, [])
    map.get(it.date)!.push(it)
  }
  return Array.from(map.entries()).sort((a, b) => (a[0] < b[0] ? -1 : 1))
}

async function onAdd() {
  if (!managing.value) return
  if (!addRow.value.date || !addRow.value.dishId) {
    ElMessage.warning('请填写日期与菜品')
    return
  }
  const r = await addItem(managing.value.id, {
    date: addRow.value.date,
    meal: addRow.value.meal,
    dishId: addRow.value.dishId,
    servingFactor: addRow.value.servingFactor,
  })
  await loadDetail(managing.value.id)
  if (r.duplicates && r.duplicates.length) {
    ElMessage.warning('同日同餐已有此菜')
  } else {
    ElMessage.success('已添加')
  }
  addRow.value = { date: addRow.value.date, meal: addRow.value.meal, dishId: undefined, servingFactor: 1 }
}

async function onRemove(it: MealPlanItem) {
  if (!it.id) return
  await ElMessageBox.confirm(`移除「${dishName(it.dishId)}」？`, '提示', { type: 'warning' })
  await delItem(it.id)
  await loadDetail(managing.value!.id)
  ElMessage.success('已移除')
}

async function onApplyTemplate(tplId: number) {
  if (!managing.value) return
  const n = await applyTemplate(managing.value.id, tplId)
  await loadDetail(managing.value.id)
  ElMessage.success(`已套用，新增 ${n} 条`)
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openNew">新建周计划</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="周起始" prop="weekStart" width="140" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openManage(row)">排菜</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="onPageChange"
    />

    <!-- 新建 -->
    <el-dialog v-model="newVisible" title="新建周计划" width="480px">
      <el-form label-width="90px">
        <el-form-item label="周起始">
          <el-date-picker v-model="newForm.weekStart" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="newForm.name" placeholder="如：本周计划" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 排菜管理 -->
    <el-dialog v-model="manageVisible" :title="`排菜管理：${managing?.name || ''}`" width="820px">
      <div class="tpl-bar">
        <span>套用模板：</span>
        <el-select v-if="templates.length" placeholder="选择模板" style="width: 220px" @change="onApplyTemplate">
          <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <span v-else class="muted">暂无模板</span>
      </div>

      <!-- 行内新增 -->
      <div class="add-row">
        <el-date-picker v-model="addRow.date" type="date" value-format="YYYY-MM-DD" placeholder="日期" style="width: 150px" />
        <el-select v-model="addRow.meal" style="width: 100px; margin-left: 8px">
          <el-option v-for="m in MEALS" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="addRow.dishId" filterable placeholder="选择菜品" style="width: 240px; margin-left: 8px">
          <el-option v-for="d in dishes" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-input-number v-model="addRow.servingFactor" :min="0" :step="0.5" :precision="2" style="margin-left: 8px" />
        <el-button type="primary" style="margin-left: 8px" @click="onAdd">添加</el-button>
      </div>

      <!-- 按日期分组展示 -->
      <el-divider content-position="left">已排菜</el-divider>
      <div v-for="[date, arr] in groupItems()" :key="date" class="day-block">
        <div class="day-title">{{ date }}</div>
        <el-tag
          v-for="it in arr"
          :key="it.id"
          closable
          class="chip"
          @close="onRemove(it)"
        >
          {{ it.meal }} · {{ dishName(it.dishId) }} ×{{ it.servingFactor }}
        </el-tag>
      </div>
      <div v-if="items.length === 0" class="muted">暂无排菜</div>
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
.tpl-bar {
  margin-bottom: 12px;
  font-size: 13px;
}
.add-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.day-block {
  margin-bottom: 12px;
}
.day-title {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 6px;
}
.chip {
  margin: 0 6px 6px 0;
}
.muted {
  color: #999;
  font-size: 13px;
}
</style>
