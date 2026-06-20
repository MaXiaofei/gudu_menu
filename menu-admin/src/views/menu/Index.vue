<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMenus,
  createMenu,
  updateMenu,
  deleteMenu,
  getMenuDetail,
  getMenuSummary,
  type Menu,
  type MenuDish,
  type MenuSummary,
} from '@/api/menu'
import { listDishes } from '@/api/dish'
import Pagination from '@/components/Pagination.vue'
import { listByGroup, listNutritionMetrics, type DictItem, type NutritionMetric } from '@/api/dict'

const loading = ref(false)
const list = ref<Menu[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const dishes = ref<{ id: number; name: string }[]>([])
const metrics = ref<NutritionMetric[]>([])
const menuTypeOptions = ref<DictItem[]>([])

function menuTypeName(id?: number): string {
  if (id === undefined || id === null) return '-'
  return menuTypeOptions.value.find((t) => t.id === id)?.name ?? `#${id}`
}

async function load() {
  loading.value = true
  try {
    const page = await listMenus({ pageNum: pageNum.value, pageSize })
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
  const [d, m, mt] = await Promise.all([listDishes(), listNutritionMetrics(), listByGroup('menu_type')])
  dishes.value = d.map((x) => ({ id: x.id, name: x.name }))
  metrics.value = m
  menuTypeOptions.value = mt
}

onMounted(() => {
  load()
  loadOptions()
})

// ===== 新增/编辑 =====
const dialogVisible = ref(false)
const editing = ref<Menu | null>(null)

const baseForm = reactive<{
  id?: number
  name: string
  typeId: number | undefined
  servingCount: number
}>({ name: '', typeId: undefined, servingCount: 1 })

const menuDishes = ref<MenuDish[]>([{ dishId: undefined as unknown as number, servingFactor: 1 }])

function resetForm() {
  editing.value = null
  baseForm.id = undefined
  baseForm.name = ''
  baseForm.typeId = undefined
  baseForm.servingCount = 1
  menuDishes.value = [{ dishId: undefined as unknown as number, servingFactor: 1 }]
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: Menu) {
  resetForm()
  editing.value = row
  baseForm.id = row.id
  baseForm.name = row.name
  baseForm.typeId = row.typeId
  baseForm.servingCount = row.servingCount
  dialogVisible.value = true
  // 拉详情填排菜
  try {
    const d = await getMenuDetail(row.id)
    menuDishes.value =
      d.dishes && d.dishes.length
        ? d.dishes.map((x) => ({ dishId: x.dishId, servingFactor: x.servingFactor }))
        : [{ dishId: undefined as unknown as number, servingFactor: 1 }]
  } catch {
    // ignore
  }
}

function addDish() {
  menuDishes.value.push({ dishId: undefined as unknown as number, servingFactor: 1 })
}
function removeDish(i: number) {
  if (menuDishes.value.length <= 1) return
  menuDishes.value.splice(i, 1)
}

async function onSubmit() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请填写菜单名称')
    return
  }
  const payloadDishes = menuDishes.value
    .filter((d) => d.dishId !== undefined && d.dishId !== null)
    .map((d) => ({ dishId: Number(d.dishId), servingFactor: Number(d.servingFactor) }))

  const menu = {
    name: baseForm.name.trim(),
    typeId: baseForm.typeId,
    servingCount: baseForm.servingCount,
  }
  if (editing.value && baseForm.id) {
    await updateMenu({ menu: { id: baseForm.id, ...menu }, dishes: payloadDishes })
    ElMessage.success('已更新')
  } else {
    await createMenu({ menu, dishes: payloadDishes })
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function onDelete(row: Menu) {
  await ElMessageBox.confirm(`确定删除菜单「${row.name}」？`, '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('已删除')
  await load()
}

// ===== 汇总（ECharts）=====
const summaryVisible = ref(false)
const summaryTitle = ref('')
const summary = ref<MenuSummary | null>(null)
const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

async function showSummary(row: Menu) {
  summaryTitle.value = `「${row.name}」汇总`
  summaryVisible.value = true
  try {
    summary.value = await getMenuSummary(row.id)
    await nextTick()
    renderChart()
  } catch {
    summary.value = null
  }
}

function renderChart() {
  if (!chartRef.value || !summary.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  const raw = summary.value.totalNutrition || {}
  const data = metrics.value
    .filter((m) => raw[String(m.id)] !== undefined && raw[String(m.id)] !== null)
    .map((m) => ({ name: m.name, value: Number(raw[String(m.id)]) }))
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.name),
      axisLabel: { interval: 0, rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value),
        itemStyle: { color: '#2EA66B', borderRadius: [4, 4, 0, 0] },
      },
    ],
  })
}

watch(summaryVisible, (v) => {
  if (!v) {
    chartInstance?.dispose()
    chartInstance = null
  }
})

onUnmounted(() => {
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增菜单</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="菜单名称" prop="name" min-width="200" />
      <el-table-column label="菜单类型" width="120">
        <template #default="{ row }">{{ menuTypeName(row.typeId) }}</template>
      </el-table-column>
      <el-table-column label="份数" prop="servingCount" width="100" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="showSummary(row)">汇总</el-button>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新增菜单'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="菜单名称">
          <el-input v-model="baseForm.name" placeholder="菜单名称" />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-select v-model="baseForm.typeId" clearable placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in menuTypeOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="份数">
          <el-input-number v-model="baseForm.servingCount" :min="1" />
        </el-form-item>

        <el-divider content-position="left">排菜</el-divider>
        <div v-for="(d, i) in menuDishes" :key="i" class="inline-row">
          <el-select v-model="d.dishId" placeholder="选择菜品" filterable style="width: 280px">
            <el-option v-for="x in dishes" :key="x.id" :label="x.name" :value="x.id" />
          </el-select>
          <el-input-number
            v-model="d.servingFactor"
            :min="0"
            :precision="2"
            :step="0.5"
            style="margin-left: 8px"
          />
          <el-button link type="danger" style="margin-left: 8px" @click="removeDish(i)">删除</el-button>
        </div>
        <el-button text type="primary" @click="addDish">+ 添加菜品</el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 汇总 -->
    <el-dialog v-model="summaryVisible" :title="summaryTitle" width="640px">
      <div class="price-line">
        总价：<b>{{ summary?.totalPrice ?? '-' }}</b> 元
      </div>
      <div ref="chartRef" style="width: 100%; height: 320px"></div>
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
.inline-row {
  margin-bottom: 8px;
}
.price-line {
  font-size: 14px;
  margin-bottom: 12px;
}
</style>
