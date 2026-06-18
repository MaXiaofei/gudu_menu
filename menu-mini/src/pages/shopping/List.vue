<template>
  <view class="shopping">
    <!-- 生成入口：数据源切换 -->
    <view class="gen-bar">
      <view class="src-tabs">
        <view :class="['tab', sourceType === 'plan' && 'on']" @click="sourceType = 'plan'">周计划</view>
        <view :class="['tab', sourceType === 'dish' && 'on']" @click="sourceType = 'dish'">菜品</view>
        <view :class="['tab', sourceType === 'menu' && 'on']" @click="sourceType = 'menu'">菜单</view>
      </view>

      <!-- 周计划选择 -->
      <view v-if="sourceType === 'plan'" class="gen-row">
        <view class="gen-input" @click="showPlanPicker = true">
          {{ currentPlan ? (currentPlan.name || weekText(currentPlan.weekStart)) : '选择周计划' }}
        </view>
      </view>

      <!-- 菜品多选（弹层勾选） -->
      <view v-if="sourceType === 'dish'" class="gen-row">
        <view class="gen-input" @click="showDishPicker = true">
          {{ pickedDishNames || '选择菜品（可多选）' }}
        </view>
      </view>

      <!-- 菜单选择 -->
      <view v-if="sourceType === 'menu'" class="gen-row">
        <view class="gen-input" @click="showMenuPicker = true">
          {{ currentMenu ? (currentMenu.name || `菜单 #${currentMenu.id}`) : '选择菜单' }}
        </view>
      </view>

      <view class="gen-btn" :class="{ disabled: generating || !canGenerate }" @click="onGenerate">
        {{ generating ? '生成中…' : '生成清单' }}
      </view>
    </view>

    <!-- 生成出的清单详情 -->
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!detail" class="empty">暂无采购清单（请先生成）</view>
    <view v-else class="detail">
      <view class="detail-head">
        <text class="title">采购清单</text>
        <text class="range">{{ rangeText(detail) }}</text>
      </view>

      <view v-if="!detail.items || !detail.items.length" class="empty">该清单暂无采购项</view>

      <!-- 按品类分区展示 -->
      <view v-for="(items, catKey) in detail.grouped" :key="catKey" class="category">
        <view class="cat-title">{{ categoryName(catKey) }}</view>
        <view v-for="it in items" :key="it.id" :class="['item', it.purchased === 1 && 'done']">
          <view class="check" @click="onToggle(it)">
            <view :class="['box', it.purchased === 1 && 'checked']">✓</view>
          </view>
          <view class="main">
            <view class="row1">
              <text class="iname">{{ it.ingredientName || '#' + it.ingredientId }}</text>
              <text v-if="it.referenceGrams" class="ref-g">约 {{ it.referenceGrams }}g</text>
            </view>
            <view class="row2">
              <input
                class="amt"
                type="digit"
                v-model="ensureDraft(it).amount"
                placeholder="买多少"
                @click.stop
              />
              <picker
                class="unit-pk"
                mode="selector"
                :range="unitNames"
                :value="ensureDraft(it).unitIdx"
                @change="(e: any) => onPickUnit(it.id, e.detail.value)"
                @click.stop
              >
                <view class="unit-txt">
                  {{ ensureDraft(it).unitIdx >= 0 ? unitNames[ensureDraft(it).unitIdx] : '单位' }}
                </view>
              </picker>
              <view class="save" @click.stop="onSavePurchase(it)">保存</view>
            </view>
            <view v-if="it.purchaseAmount != null && it.purchaseUnitName" class="cur">
              已填：{{ it.purchaseAmount }} {{ it.purchaseUnitName }}
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 周计划选择 -->
    <u-picker :show="showPlanPicker" :columns="[planNames]" @confirm="onPickPlan" @cancel="showPlanPicker = false" />
    <!-- 菜单选择 -->
    <u-picker :show="showMenuPicker" :columns="[menuNames]" @confirm="onPickMenu" @cancel="showMenuPicker = false" />
    <!-- 菜品选择（点选切换，可多次） -->
    <u-picker :show="showDishPicker" :columns="[dishNames]" @confirm="onPickDish" @cancel="showDishPicker = false" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  generate,
  getDetail,
  togglePurchased,
  updatePurchase,
  listShopping,
  type ShoppingListVO,
  type ShoppingItemVO,
  type ShoppingSourceType,
} from '@/api/shopping'
import { request } from '@/utils/request'

interface PlanLite { id: number; weekStart: string; name?: string }
interface MenuLite { id: number; name?: string }
interface DishLite { id: number; name: string }

const detail = ref<ShoppingListVO | null>(null)
const loading = ref(false)
const generating = ref(false)

// 数据源
const sourceType = ref<ShoppingSourceType>('plan')
const plans = ref<PlanLite[]>([])
const menus = ref<MenuLite[]>([])
const dishes = ref<DishLite[]>([])
const currentPlan = ref<PlanLite | null>(null)
const currentMenu = ref<MenuLite | null>(null)
const pickedDishIds = ref<number[]>([])

const showPlanPicker = ref(false)
const showMenuPicker = ref(false)
const showDishPicker = ref(false)

const planNames = computed(() => plans.value.map((p) => p.name || weekText(p.weekStart)))
const menuNames = computed(() => menus.value.map((m) => m.name || `菜单 #${m.id}`))
const dishNames = computed(() => dishes.value.map((d) => d.name))
const pickedDishNames = computed(() =>
  pickedDishIds.value
    .map((id) => dishes.value.find((d) => d.id === id)?.name)
    .filter(Boolean)
    .join('、')
)

// 采购单位字典（中文）
const units = ref<{ id: number; name: string }[]>([])
const unitNames = computed(() => units.value.map((u) => u.name))

// 草稿：每行采购量+单位序号
const draft = reactive<Record<number, { amount: string; unitIdx: number }>>({})

function weekText(weekStart?: string): string {
  if (!weekStart) return '#'
  return weekStart + ' 起'
}
function rangeText(d: ShoppingListVO): string {
  if (d.startDate && d.endDate) return `${d.startDate} ~ ${d.endDate}`
  return d.timeRange || ''
}
function categoryName(catKey: string | number): string {
  const names = detail.value?.categoryNames
  if (names && names[String(catKey)]) return names[String(catKey)]
  return catKey === 'null' || catKey == null ? '其他' : `品类#${catKey}`
}

function ensureDraft(it: ShoppingItemVO) {
  if (!draft[it.id]) {
    const idx = it.purchaseUnitId != null ? units.value.findIndex((u) => u.id === it.purchaseUnitId) : -1
    draft[it.id] = {
      amount: it.purchaseAmount != null ? String(it.purchaseAmount) : '',
      unitIdx: idx >= 0 ? idx : -1,
    }
  }
  return draft[it.id]
}

function onPickUnit(id: number, idx: number) {
  if (draft[id]) draft[id].unitIdx = idx
}

const canGenerate = computed(() => {
  if (sourceType.value === 'plan') return !!currentPlan.value
  if (sourceType.value === 'menu') return !!currentMenu.value
  if (sourceType.value === 'dish') return pickedDishIds.value.length > 0
  return false
})

async function loadRefData() {
  try {
    const [mealPlans, dishRows, menuRows, unitRows] = await Promise.all([
      request<any>({ url: '/mealplan', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/dish/search', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/menu', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'purchase_unit', pageNum: 1, pageSize: 50 } })
        .then((p: any) => p.records || []),
    ])
    plans.value = mealPlans
    dishes.value = dishRows
    menus.value = menuRows
    units.value = unitRows
  } catch {
    /* 静默 */
  }
}

async function loadPlans() {
  await loadRefData()
  try {
    const records: any[] = await listShopping()
    if (records.length && !detail.value) {
      await loadDetail(records[0].id)
    }
  } catch {
    /* 静默 */
  }
}

async function loadDetail(listId: number) {
  loading.value = true
  try {
    detail.value = await getDetail(listId)
  } finally {
    loading.value = false
  }
}

function onPickPlan(e: any) {
  const idx = e.indexs ? e.indexs[0] : e.index[0]
  currentPlan.value = plans.value[idx] || null
  showPlanPicker.value = false
}
function onPickMenu(e: any) {
  const idx = e.indexs ? e.indexs[0] : e.index[0]
  currentMenu.value = menus.value[idx] || null
  showMenuPicker.value = false
}
function onPickDish(e: any) {
  const idx = e.indexs ? e.indexs[0] : e.index[0]
  const picked = dishes.value[idx]
  if (picked) {
    const i = pickedDishIds.value.indexOf(picked.id)
    if (i >= 0) pickedDishIds.value.splice(i, 1)
    else pickedDishIds.value.push(picked.id)
  }
  // 不自动关闭，让用户继续选；点 cancel 关闭
}

async function onGenerate() {
  if (!canGenerate.value || generating.value) return
  generating.value = true
  try {
    const req =
      sourceType.value === 'plan'
        ? { sourceType: 'plan', sourceId: currentPlan.value!.id }
        : sourceType.value === 'menu'
          ? { sourceType: 'menu', sourceId: currentMenu.value!.id }
          : { sourceType: 'dish', sourceIds: [...pickedDishIds.value] }
    const listId = await generate(req as any)
    uni.showToast({ title: '生成成功', icon: 'success' })
    await loadDetail(listId)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '生成失败', icon: 'none' })
  } finally {
    generating.value = false
  }
}

async function onToggle(it: ShoppingItemVO) {
  try {
    await togglePurchased(it.id)
    it.purchased = it.purchased === 1 ? 0 : 1
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '操作失败', icon: 'none' })
  }
}

async function onSavePurchase(it: ShoppingItemVO) {
  const d = ensureDraft(it)
  const amt = parseFloat(d.amount)
  if (d.amount === '' || isNaN(amt)) {
    uni.showToast({ title: '请输入采购量', icon: 'none' })
    return
  }
  if (d.unitIdx < 0 || !units.value[d.unitIdx]) {
    uni.showToast({ title: '请选择单位', icon: 'none' })
    return
  }
  try {
    await updatePurchase(it.id, amt, units.value[d.unitIdx].id)
    it.purchaseAmount = amt
    it.purchaseUnitId = units.value[d.unitIdx].id
    it.purchaseUnitName = units.value[d.unitIdx].name
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '保存失败', icon: 'none' })
  }
}

onShow(() => {
  loadPlans()
})
</script>

<style scoped>
.shopping { padding: 12px; }
.gen-bar { background: #fff; padding: 10px; border-radius: 8px; margin-bottom: 12px; }
.src-tabs { display: flex; gap: 8px; margin-bottom: 10px; }
.tab { flex: 1; text-align: center; font-size: 13px; padding: 7px 0; border-radius: 6px; background: #f5f0ea; color: #666; }
.tab.on { background: #FF8C42; color: #fff; font-weight: 600; }
.gen-row { margin-bottom: 10px; }
.gen-input { font-size: 14px; color: #333; border: 1px solid #eee; border-radius: 6px; padding: 8px 10px; }
.gen-btn { font-size: 14px; color: #fff; background: #FF8C42; padding: 9px 0; border-radius: 6px; text-align: center; }
.gen-btn.disabled { background: #ccc; }
.empty { text-align: center; color: #aaa; padding: 40px 0; font-size: 13px; }
.detail { display: flex; flex-direction: column; gap: 12px; }
.detail-head { display: flex; justify-content: space-between; align-items: baseline; }
.title { font-size: 16px; font-weight: 700; color: #333; }
.range { font-size: 12px; color: #999; }
.category { background: #fff; border-radius: 8px; padding: 10px 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.cat-title { font-size: 13px; font-weight: 600; color: #FF8C42; padding-bottom: 6px; border-bottom: 1px dashed #f0e0d0; margin-bottom: 6px; }
.item { display: flex; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.item:last-child { border-bottom: none; }
.item.done .iname { color: #bbb; text-decoration: line-through; }
.check { padding: 0 10px 0 0; }
.box { width: 20px; height: 20px; border: 2px solid #ddd; border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 13px; color: transparent; }
.box.checked { background: #FF8C42; border-color: #FF8C42; color: #fff; }
.main { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.row1 { display: flex; align-items: center; gap: 8px; }
.iname { font-size: 15px; color: #333; }
.ref-g { font-size: 11px; color: #999; }
.row2 { display: flex; align-items: center; gap: 8px; }
.amt { flex: 1; border: 1px solid #eee; border-radius: 6px; padding: 6px 8px; font-size: 14px; }
.unit-pk { min-width: 64px; }
.unit-txt { border: 1px solid #eee; border-radius: 6px; padding: 6px 10px; font-size: 14px; color: #333; }
.save { font-size: 13px; color: #fff; background: #2a9d8f; padding: 7px 12px; border-radius: 6px; }
.cur { font-size: 11px; color: #2a9d8f; }
</style>
