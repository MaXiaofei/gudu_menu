<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listShopping,
  getShoppingDetail,
  generateShopping,
  updatePurchase,
  togglePurchased,
  deleteShoppingItem,
  deleteShoppingList,
  type ShoppingList,
  type ShoppingListVO,
  type ShoppingItemVO,
  type ShoppingSourceType,
  type GenerateReq,
} from '@/api/shopping'
import { listPlans, type MealPlan } from '@/api/mealplan'
import { listMenus, type Menu } from '@/api/menu'
import { listDishes, type Dish } from '@/api/dish'
import { listByGroup, type DictItem } from '@/api/dict'
import Pagination from '@/components/Pagination.vue'

// ============ 清单分页列表 ============
const loading = ref(false)
const list = ref<ShoppingList[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20

async function load() {
  loading.value = true
  try {
    const page = await listShopping({ pageNum: pageNum.value, pageSize })
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

// ============ 生成（三数据源） ============
const plans = ref<MealPlan[]>([])
const menus = ref<Menu[]>([])
const dishes = ref<Dish[]>([])
const genDialogVisible = ref(false)
const genForm = reactive({
  sourceType: 'plan' as ShoppingSourceType,
  planId: undefined as unknown as number,
  menuId: undefined as unknown as number,
  dishIds: [] as number[],
})
const generating = ref(false)

function openGenerate() {
  genForm.sourceType = 'plan'
  genForm.planId = undefined as unknown as number
  genForm.menuId = undefined as unknown as number
  genForm.dishIds = []
  genDialogVisible.value = true
}

async function onGenerate() {
  if (genForm.sourceType === 'plan' && !genForm.planId) {
    ElMessage.warning('请选择周计划')
    return
  }
  if (genForm.sourceType === 'menu' && !genForm.menuId) {
    ElMessage.warning('请选择菜单')
    return
  }
  if (genForm.sourceType === 'dish' && (!genForm.dishIds || genForm.dishIds.length === 0)) {
    ElMessage.warning('请至少选择一道菜品')
    return
  }
  generating.value = true
  try {
    const req: GenerateReq =
      genForm.sourceType === 'plan'
        ? { sourceType: 'plan', sourceId: genForm.planId }
        : genForm.sourceType === 'menu'
          ? { sourceType: 'menu', sourceId: genForm.menuId }
          : { sourceType: 'dish', sourceIds: genForm.dishIds }
    const listId = await generateShopping(req)
    ElMessage.success(`已生成采购清单 #${listId}`)
    genDialogVisible.value = false
    pageNum.value = 1
    await load()
    await openDetail(listId)
  } finally {
    generating.value = false
  }
}

// ============ 清单详情：参考克提示 + 采购量/单位用户填 + 勾选 ============
const detailVisible = ref(false)
const detail = ref<ShoppingListVO | null>(null)
const detailLoading = ref(false)
const purchaseUnits = ref<DictItem[]>([])

async function openDetail(listId: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getShoppingDetail(listId)
  } finally {
    detailLoading.value = false
  }
}

// 分区视图：把 grouped（key 是字符串/数字，含 null）转成有序数组
function categoryGroups() {
  const g = detail.value?.grouped
  if (!g) return []
  return Object.entries(g).map(([k, items]) => ({
    catKey: k,
    catName: categoryName(k),
    items: items || [],
  }))
}

function categoryName(catKey: string): string {
  const names = detail.value?.categoryNames
  if (names && names[String(catKey)]) return String(names[String(catKey)])
  if (catKey === 'null' || catKey === 'undefined' || catKey == null) return '其他'
  return `品类#${catKey}`
}

// 用户填采购量 + 单位的草稿态（避免每次输入都请求）
const draft = reactive<Record<number, { amount: string; unitId: number | null }>>({})

function ensureDraft(row: ShoppingItemVO) {
  if (!draft[row.id]) {
    draft[row.id] = {
      amount: row.purchaseAmount != null ? String(row.purchaseAmount) : '',
      unitId: row.purchaseUnitId ?? null,
    }
  }
}

async function onSavePurchase(row: ShoppingItemVO) {
  const d = draft[row.id]
  if (!d) return
  const amt = parseFloat(d.amount)
  if (d.amount === '' || isNaN(amt)) {
    ElMessage.warning('请输入采购量')
    return
  }
  if (!d.unitId) {
    ElMessage.warning('请选择采购单位')
    return
  }
  try {
    await updatePurchase(row.id, amt, d.unitId)
    ElMessage.success('已保存')
    row.purchaseAmount = amt
    row.purchaseUnitId = d.unitId
    const u = purchaseUnits.value.find((x) => x.id === d.unitId)
    row.purchaseUnitName = u?.name
  } catch {
    ElMessage.error('保存失败')
  }
}

async function onToggle(row: ShoppingItemVO) {
  try {
    await togglePurchased(row.id)
    row.purchased = row.purchased === 1 ? 0 : 1
  } catch {
    ElMessage.error('操作失败')
  }
}

async function onDeleteItem(row: ShoppingItemVO) {
  await ElMessageBox.confirm(`确定删除「${row.ingredientName || '该项'}」？`, '提示', { type: 'warning' })
  await deleteShoppingItem(row.id)
  ElMessage.success('已删除')
  if (detail.value) await openDetail(detail.value.id)
}

async function onDeleteList(row: ShoppingList) {
  await ElMessageBox.confirm(`确定删除采购清单 #${row.id}？`, '提示', { type: 'warning' })
  await deleteShoppingList(row.id)
  ElMessage.success('已删除')
  await load()
}

function rangeText(row: ShoppingList): string {
  if (row.startDate && row.endDate) return `${row.startDate} ~ ${row.endDate}`
  return row.timeRange || '-'
}

function sourceText(row: ShoppingList): string {
  if (row.timeRange === 'menu') return '来自菜单'
  if (row.timeRange === 'dish') return '来自菜品'
  if (row.sourcePlanId) return `来自周计划 #${row.sourcePlanId}`
  return '手工'
}

onMounted(async () => {
  await load()
  try {
    purchaseUnits.value = await listByGroup('purchase_unit')
  } catch {
    /* 静默 */
  }
  try {
    const p = await listPlans({ pageNum: 1, pageSize: 100 })
    plans.value = p.records || []
  } catch {
    /* 静默 */
  }
  try {
    const m = await listMenus({ pageNum: 1, pageSize: 100 })
    menus.value = m.records || []
  } catch {
    /* 静默 */
  }
  try {
    dishes.value = (await listDishes()) || []
  } catch {
    /* 静默 */
  }
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openGenerate">生成采购清单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="清单号" prop="id" width="100" />
      <el-table-column label="来源" min-width="160">
        <template #default="{ row }">
          <span>{{ sourceText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间范围" min-width="180">
        <template #default="{ row }">
          <span class="mini">{{ rangeText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生成时间" width="180">
        <template #default="{ row }">
          <span class="mini">{{ row.createdAt || row.timeRange || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">查看明细</el-button>
          <el-button link type="danger" @click="onDeleteList(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="onPageChange"
    />

    <!-- 生成弹窗：三数据源 -->
    <el-dialog v-model="genDialogVisible" title="生成采购清单" width="480px">
      <el-form label-width="90px">
        <el-form-item label="数据源">
          <el-radio-group v-model="genForm.sourceType">
            <el-radio value="plan">周计划</el-radio>
            <el-radio value="menu">菜单</el-radio>
            <el-radio value="dish">菜品</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="genForm.sourceType === 'plan'" label="周计划">
          <el-select v-model="genForm.planId" filterable placeholder="选择周计划" style="width: 100%">
            <el-option
              v-for="p in plans"
              :key="p.id"
              :label="p.name || `${p.weekStart} 起`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="genForm.sourceType === 'menu'" label="菜单">
          <el-select v-model="genForm.menuId" filterable placeholder="选择菜单" style="width: 100%">
            <el-option v-for="m in menus" :key="m.id" :label="(m as any).name || `菜单 #${m.id}`" :value="m.id" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="genForm.sourceType === 'dish'" label="菜品">
          <el-select
            v-model="genForm.dishIds"
            multiple
            filterable
            placeholder="可多选菜品"
            style="width: 100%"
          >
            <el-option v-for="d in dishes" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 清单详情：参考克提示 + 采购量/单位用户填 + 勾选 -->
    <el-dialog v-model="detailVisible" title="采购清单明细" width="720px">
      <div v-loading="detailLoading">
        <div v-if="!detail || !detail.items || !detail.items.length" class="empty">
          该清单暂无采购项
        </div>
        <div v-else>
          <div class="detail-head">
            <span>共 {{ detail.items.length }} 项</span>
            <span class="mini">{{ rangeText(detail) }}</span>
          </div>
          <div v-for="g in categoryGroups()" :key="g.catKey" class="cat-block">
            <div class="cat-title">{{ g.catName }}（{{ g.items.length }}）</div>
            <div v-for="it in g.items" :key="it.id" :class="['item-row', it.purchased === 1 && 'done']">
              <el-checkbox :model-value="it.purchased === 1" @change="onToggle(it)">
                <span class="iname">{{ it.ingredientName || `#${it.ingredientId}` }}</span>
              </el-checkbox>
              <span v-if="it.referenceGrams" class="ref-g">约 {{ it.referenceGrams }}g</span>

              <template v-if="(ensureDraft(it), true)">
                <el-input
                  v-model="draft[it.id].amount"
                  class="amt-input"
                  size="small"
                  placeholder="买多少"
                  type="number"
                />
                <el-select v-model="draft[it.id].unitId" class="unit-sel" size="small" placeholder="单位">
                  <el-option v-for="u in purchaseUnits" :key="u.id" :label="u.name" :value="u.id" />
                </el-select>
                <el-button link type="primary" size="small" @click="onSavePurchase(it)">保存</el-button>
              </template>

              <span v-if="it.purchaseAmount != null && it.purchaseUnitName" class="cur">
                已填：{{ it.purchaseAmount }} {{ it.purchaseUnitName }}
              </span>
              <el-button link type="danger" size="small" @click="onDeleteItem(it)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
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
.empty {
  text-align: center;
  color: #aaa;
  padding: 30px 0;
  font-size: 13px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
  font-size: 14px;
  color: #555;
}
.cat-block {
  margin-bottom: 14px;
  border: 1px solid #f0e0d0;
  border-radius: 6px;
  padding: 8px 12px;
}
.cat-title {
  font-size: 14px;
  font-weight: 600;
  color: #E89150;
  border-bottom: 1px dashed #f0e0d0;
  padding-bottom: 6px;
  margin-bottom: 4px;
}
.item-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}
.item-row:last-child {
  border-bottom: none;
}
.item-row.done .iname {
  color: #bbb;
  text-decoration: line-through;
}
.iname {
  font-size: 15px;
}
.ref-g {
  font-size: 12px;
  color: #999;
  margin-right: auto;
}
.amt-input {
  width: 110px;
}
.unit-sel {
  width: 100px;
}
.cur {
  font-size: 12px;
  color: #2a9d8f;
}
</style>
