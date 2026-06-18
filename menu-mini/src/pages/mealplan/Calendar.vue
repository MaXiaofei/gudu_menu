<template>
  <view class="mealplan">
    <view class="bar">
      <text class="title">周计划</text>
      <u-button size="mini" type="primary" @click="onNewWeek">新建本周计划</u-button>
    </view>

    <view v-if="planId" class="week">
      <view class="week-label">{{ plan?.name || '本周' }}（{{ plan?.weekStart || '' }} 起）</view>
      <view class="grid">
        <view class="cell head"></view>
        <view v-for="d in days" :key="'h-' + d.date" class="cell head">{{ d.label }}</view>

        <template v-for="m in meals" :key="m">
          <view class="cell row-head">{{ m }}</view>
          <view
            v-for="d in days"
            :key="m + d.date"
            class="cell slot"
            @click="openPicker(d.date, m)"
          >
            <view
              v-for="it in itemsOf(d.date, m)"
              :key="it.id"
              class="dish-chip"
              @click.stop="onRemove(it)"
            >
              {{ dishName(it.dishId) }}
              <text class="x">×</text>
            </view>
            <text v-if="itemsOf(d.date, m).length === 0" class="plus">+</text>
          </view>
        </template>
      </view>
    </view>

    <view v-else class="empty">还没有周计划，点右上角新建</view>

    <!-- 选菜弹窗 -->
    <u-popup :show="pickerShow" mode="bottom" @close="pickerShow = false">
      <view class="picker">
        <view class="picker-title">{{ curDate }} {{ curMeal }}</view>
        <u-search v-model="kw" placeholder="搜菜名" @search="loadDishes" @clear="loadDishes" />
        <scroll-view scroll-y class="dish-list">
          <u-cell
            v-for="d in dishes"
            :key="d.id"
            :title="d.name"
            :label="`${d.cookTime || 0}分钟 · 难度${d.difficulty || '-'}`"
            isLink
            @click="onPick(d.id)"
          />
          <view v-if="dishes.length === 0" class="empty">暂无菜品</view>
        </scroll-view>
      </view>
    </u-popup>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createPlan, getPlan, addItem, delItem, type MealPlanItem } from '@/api/mealplan'
import { searchDishes } from '@/api/dish'

const MEALS = ['早餐', '午餐', '晚餐', '加餐']
const meals = ref(MEALS)

const planId = ref<number | null>(null)
const plan = ref<any>(null)
const items = ref<MealPlanItem[]>([])
const dishMap = ref<Record<number, string>>({})

// 当前周一为起点的 7 天
const days = ref<{ date: string; label: string }[]>([])
function buildWeek(weekStart: string) {
  const start = new Date(weekStart)
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  days.value = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return { date: `${yyyy}-${mm}-${dd}`, label: `${labels[i]}\n${mm}/${dd}` }
  })
}

function mondayOf(d: Date) {
  const x = new Date(d)
  const day = (x.getDay() + 6) % 7 // 周一=0
  x.setDate(x.getDate() - day)
  return x
}
function fmt(d: Date) {
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

async function loadPlan(id: number) {
  const r = await getPlan(id)
  plan.value = r.plan
  items.value = r.items || []
  if (r.plan?.weekStart) buildWeek(r.plan.weekStart)
  // 预取菜品名（一次性拉取本周涉及的 dishId；MVP 简化：用 search 兜底缓存）
  for (const it of items.value) {
    if (!dishMap.value[it.dishId]) dishMap.value[it.dishId] = `#${it.dishId}`
  }
}

onShow(async () => {
  // MVP：从本地取上次 planId；无则留空让用户新建
  const id = uni.getStorageSync('currentPlanId')
  if (id) {
    planId.value = Number(id)
    try {
      await loadPlan(planId.value)
    } catch {
      planId.value = null
    }
  }
})

async function onNewWeek() {
  const monday = fmt(mondayOf(new Date()))
  const id = await createPlan(monday, '本周计划')
  planId.value = id
  uni.setStorageSync('currentPlanId', id)
  await loadPlan(id)
  uni.showToast({ title: '已创建', icon: 'success' })
}

function itemsOf(date: string, meal: string) {
  return items.value.filter((it) => it.date === date && it.meal === meal)
}
function dishName(dishId: number) {
  return dishMap.value[dishId] || `#${dishId}`
}

// 选菜弹窗
const pickerShow = ref(false)
const curDate = ref('')
const curMeal = ref('')
const kw = ref('')
const dishes = ref<any[]>([])

function openPicker(date: string, meal: string) {
  curDate.value = date
  curMeal.value = meal
  kw.value = ''
  pickerShow.value = true
  loadDishes()
}

async function loadDishes() {
  try {
    const r: any = await searchDishes({ keyword: kw.value, pageNum: 1, pageSize: 50 })
    const records = Array.isArray(r) ? r : (r.records || [])
    dishes.value = records
    // 顺手缓存菜名
    for (const d of records) dishMap.value[d.id] = d.name
  } catch {
    dishes.value = []
  }
}

async function onPick(dishId: number) {
  if (!planId.value) return
  try {
    const r = await addItem(planId.value, {
      date: curDate.value,
      meal: curMeal.value,
      dishId,
      servingFactor: 1,
    })
    // 刷新本地 items
    await loadPlan(planId.value)
    if (r.duplicates && r.duplicates.length) {
      uni.showToast({ title: '同日同餐已有此菜', icon: 'none' })
    } else {
      uni.showToast({ title: '已挂菜', icon: 'success' })
    }
    pickerShow.value = false
  } catch {
    // request 层已弹错
  }
}

async function onRemove(it: MealPlanItem) {
  if (!it.id) return
  uni.showModal({
    title: '移除',
    content: `移除「${dishName(it.dishId)}」？`,
    success: async (res) => {
      if (!res.confirm) return
      await delItem(it.id!)
      await loadPlan(planId.value!)
      uni.showToast({ title: '已移除', icon: 'none' })
    },
  })
}
</script>

<style scoped>
.mealplan { padding: 20rpx; }
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.bar .title { font-size: 32rpx; font-weight: 700; }
.week-label { font-size: 26rpx; color: #888; margin-bottom: 12rpx; }
.grid { display: grid; grid-template-columns: 90rpx repeat(7, 1fr); border: 1rpx solid #eee; }
.cell { border: 1rpx solid #f0f0f0; min-height: 110rpx; padding: 6rpx; font-size: 22rpx; }
.cell.head { background: #fff7f0; font-weight: 700; text-align: center; white-space: pre-line; display: flex; align-items: center; justify-content: center; }
.cell.row-head { background: #fafafa; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.cell.slot { display: flex; flex-direction: column; align-items: stretch; justify-content: flex-start; }
.cell.slot .plus { color: #ccc; text-align: center; line-height: 90rpx; font-size: 32rpx; }
.dish-chip { background: #FF8C42; color: #fff; border-radius: 6rpx; padding: 2rpx 8rpx; margin: 2rpx 0; font-size: 20rpx; display: flex; justify-content: space-between; align-items: center; }
.dish-chip .x { font-size: 24rpx; }
.empty { color: #aaa; text-align: center; padding: 60rpx 0; }
.picker { padding: 24rpx; }
.picker-title { font-size: 30rpx; font-weight: 700; margin-bottom: 16rpx; }
.dish-list { max-height: 60vh; }
</style>
