<template>
  <view class="dailylog">
    <!-- 日期选择 -->
    <view class="date-bar">
      <view class="date-label">日期</view>
      <picker mode="date" :value="date" @change="onDateChange">
        <view class="date-val">{{ date }}</view>
      </picker>
    </view>

    <!-- 当日记录 -->
    <view v-if="loading" class="empty">加载中…</view>
    <template v-else>
      <view v-if="!log" class="empty">当天暂无记录</view>
      <view v-else class="log-card">
        <view class="log-note" v-if="log.note">备注：{{ log.note }}</view>
        <view class="cards" v-if="log.items && log.items.length">
          <view v-for="(it, i) in log.items" :key="i" class="card">
            <view class="row1">
              <text class="type">{{ it.dishId ? '菜' : '食' }}</text>
              <text class="name">{{ itemName(it) }}</text>
              <text class="amt">{{ it.amount }}{{ it.dishId ? ' 份' : ' g' }}</text>
            </view>
          </view>
        </view>

        <!-- 营养汇总（中文） -->
        <view class="nutrition" v-if="log.items && log.items.length">
          <view class="nut-title">营养汇总</view>
          <view v-if="nutritionLoading" class="nut-empty">汇总中…</view>
          <view v-else-if="nutritionDisplay.length" class="nut-list">
            <view v-for="n in nutritionDisplay" :key="n.label" class="nut-item">
              <text class="nut-label">{{ n.label }}</text>
              <text class="nut-value">{{ n.value }}</text>
            </view>
          </view>
          <view v-else class="nut-empty">暂无营养数据</view>
        </view>
      </view>
    </template>

    <!-- 添加摄入项 -->
    <view class="add">
      <view class="add-title">添加摄入</view>
      <view class="seg">
        <view :class="['seg-i', addType === 'ingredient' && 'on']" @click="addType = 'ingredient'">食材</view>
        <view :class="['seg-i', addType === 'dish' && 'on']" @click="addType = 'dish'">菜品</view>
      </view>

      <!-- 食材选择 -->
      <view v-if="addType === 'ingredient'" class="picker-row">
        <picker :range="ingredientNames" :value="ingredientIdx" @change="onIngredientChange">
          <view class="picker-val">{{ ingredientNames[ingredientIdx] || '选择食材' }}</view>
        </picker>
      </view>
      <!-- 菜品选择 -->
      <view v-else class="picker-row">
        <picker :range="dishNames" :value="dishIdx" @change="onDishChange">
          <view class="picker-val">{{ dishNames[dishIdx] || '选择菜品' }}</view>
        </picker>
      </view>

      <view class="qty-row">
        <text class="qty-label">{{ addType === 'ingredient' ? '克数' : '份数' }}</text>
        <input class="qty-input" type="digit" v-model="amount" :placeholder="addType === 'ingredient' ? '如 200' : '如 2'" />
      </view>

      <button class="btn-add" @click="addItem">添加并保存</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { submitDailyLog, getDailyLog, dailyLogNutrition, type DailyLogVO, type DailyLogItemVO } from '@/api/dailylog'
import { searchDishes } from '@/api/dish'
import { request } from '@/utils/request'

// 后端 nutrition_metric 字典 name 是英文（calorie/protein/fat/carb/sugar/gi），家庭看不懂 → 中文映射
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数'
}

const date = ref(new Date().toISOString().slice(0, 10))
const log = ref<DailyLogVO | null>(null)
const loading = ref(false)
const nutritionLoading = ref(false)
const nutritionRaw = ref<Record<string, number>>({})
const metrics = ref<Array<{ id: number; name: string; unit?: string }>>([])

// 添加项表单
const addType = ref<'ingredient' | 'dish'>('ingredient')
const ingredients = ref<Array<{ id: number; name: string }>>([])
const dishes = ref<Array<{ id: number; name: string }>>([])
const ingredientIdx = ref(0)
const dishIdx = ref(0)
const amount = ref('')

const ingredientNames = computed(() => ingredients.value.map(i => i.name))
const dishNames = computed(() => dishes.value.map(d => d.name))

// 把 nutrition(指标id→值) + metrics(字典) 合成「中文标签: 值(单位)」
const nutritionDisplay = computed(() => {
  const arr: { label: string; value: string }[] = []
  for (const m of metrics.value) {
    const v = nutritionRaw.value[String(m.id)]
    if (v !== undefined && v !== null) {
      arr.push({ label: METRIC_CN[m.name] || m.name, value: `${v}${m.unit ? m.unit : ''}` })
    }
  }
  return arr
})

function itemName(it: DailyLogItemVO): string {
  if (it.dishId) {
    const d = dishes.value.find(x => x.id === it.dishId)
    return d ? d.name : `菜品#${it.dishId}`
  }
  const ing = ingredients.value.find(x => x.id === it.ingredientId)
  return ing ? ing.name : `食材#${it.ingredientId}`
}

function onDateChange(e: any) {
  date.value = e.detail.value
  load()
}
function onIngredientChange(e: any) {
  ingredientIdx.value = e.detail.value
}
function onDishChange(e: any) {
  dishIdx.value = e.detail.value
}

async function loadOptions() {
  // 食材列表（pageWithNutrition，records[].name 继承自 Ingredient）
  try {
    const r: any = await request({ url: '/ingredient', method: 'GET', data: { pageNum: 1, pageSize: 1000 } })
    ingredients.value = (r.records || []).map((x: any) => ({ id: x.id, name: x.name }))
  } catch { /* ignore */ }
  // 菜品搜索（空关键字拉全量）
  try {
    const r: any = await searchDishes({ keyword: '', pageNum: 1, pageSize: 1000 })
    dishes.value = (r.records || []).map((x: any) => ({ id: x.id, name: x.name }))
  } catch { /* ignore */ }
  // 营养指标字典
  try {
    metrics.value = await request({ url: '/nutrition/metric', method: 'GET' })
  } catch { /* ignore */ }
}

async function load() {
  loading.value = true
  nutritionRaw.value = {}
  try {
    log.value = await getDailyLog(date.value)
    if (log.value && log.value.items && log.value.items.length) {
      loadNutrition(log.value.id)
    }
  } catch {
    log.value = null
  } finally {
    loading.value = false
  }
}

async function loadNutrition(logId: number) {
  nutritionLoading.value = true
  try {
    nutritionRaw.value = await dailyLogNutrition(logId)
  } catch {
    nutritionRaw.value = {}
  } finally {
    nutritionLoading.value = false
  }
}

async function addItem() {
  const amt = Number(amount.value)
  if (!amt || amt <= 0) {
    uni.showToast({ title: addType.value === 'ingredient' ? '请输入克数' : '请输入份数', icon: 'none' })
    return
  }
  // 构造新 items = 已有 items + 新增项，整体重提交（保持当日日志单一语义）
  const existItems = (log.value?.items || []).map(it => ({
    dishId: it.dishId,
    ingredientId: it.ingredientId,
    amount: it.amount,
    servingFactor: it.servingFactor
  }))
  if (addType.value === 'ingredient') {
    const ing = ingredients.value[ingredientIdx.value]
    if (!ing) { uni.showToast({ title: '请选择食材', icon: 'none' }); return }
    existItems.push({ ingredientId: ing.id, amount: amt })
  } else {
    const d = dishes.value[dishIdx.value]
    if (!d) { uni.showToast({ title: '请选择菜品', icon: 'none' }); return }
    existItems.push({ dishId: d.id, amount: amt, servingFactor: 1 })
  }
  try {
    await submitDailyLog({ date: date.value, note: log.value?.note, items: existItems })
    amount.value = ''
    uni.showToast({ title: '已记录', icon: 'success' })
    await load()
  } catch {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

// 切换添加类型时清空输入
watch(addType, () => { amount.value = '' })

onShow(() => {
  loadOptions()
  load()
})
</script>

<style scoped>
.dailylog { padding: 12px; }
.date-bar { display: flex; align-items: center; background: #fff; border-radius: 8px; padding: 12px; margin-bottom: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.date-label { font-size: 14px; color: #888; margin-right: 12px; }
.date-val { font-size: 15px; color: #E89150; font-weight: 600; }
.empty { text-align: center; color: #aaa; padding: 40px 0; font-size: 13px; }
.log-card { background: #fff; border-radius: 8px; padding: 12px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.log-note { font-size: 13px; color: #666; margin-bottom: 8px; }
.cards { display: flex; flex-direction: column; gap: 8px; }
.card { background: #fafafa; border-radius: 6px; padding: 10px; }
.row1 { display: flex; align-items: center; gap: 8px; }
.type { font-size: 11px; color: #fff; background: #E89150; border-radius: 3px; padding: 1px 5px; }
.name { flex: 1; font-size: 14px; color: #333; }
.amt { font-size: 13px; color: #E89150; font-weight: 600; }
.nutrition { margin-top: 12px; border-top: 1px dashed #eee; padding-top: 10px; }
.nut-title { font-size: 13px; color: #888; margin-bottom: 8px; }
.nut-list { display: flex; flex-wrap: wrap; gap: 8px; }
.nut-item { background: #FDFAF4; border-radius: 4px; padding: 4px 8px; display: flex; gap: 4px; font-size: 12px; }
.nut-label { color: #888; }
.nut-value { color: #E89150; font-weight: 600; }
.nut-empty { font-size: 12px; color: #aaa; }
.add { background: #fff; border-radius: 8px; padding: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.add-title { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 10px; }
.seg { display: flex; margin-bottom: 10px; }
.seg-i { flex: 1; text-align: center; padding: 6px 0; font-size: 13px; color: #888; border: 1px solid #eee; }
.seg-i.on { color: #E89150; border-color: #E89150; font-weight: 600; }
.seg-i:first-child { border-radius: 6px 0 0 6px; }
.seg-i:last-child { border-radius: 0 6px 6px 0; border-left: none; }
.picker-row { margin-bottom: 10px; }
.picker-val { background: #fafafa; border-radius: 6px; padding: 10px; font-size: 14px; color: #333; }
.qty-row { display: flex; align-items: center; margin-bottom: 10px; }
.qty-label { font-size: 13px; color: #888; width: 60px; }
.qty-input { flex: 1; background: #fafafa; border-radius: 6px; padding: 8px; font-size: 14px; }
.btn-add { background: #E89150; color: #fff; font-size: 14px; border-radius: 6px; }
</style>
