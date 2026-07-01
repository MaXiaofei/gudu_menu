<template>
  <view class="create">
    <view class="block">
      <text class="label">食材名 *</text>
      <view class="name-row">
        <u-input v-model="form.name" placeholder="如：番茄" border="surround" />
        <u-button size="mini" type="warning" :loading="aiLoading" @click="onAiFill">AI 补全营养</u-button>
      </view>
    </view>

    <view class="block">
      <text class="label">计量单位 *</text>
      <u-input v-model="form.unitName" placeholder="如：克（先选下方）" border="surround" disabled v-if="!units.length" />
      <view class="picker-row" v-else>
        <u-tag
          v-for="u in units"
          :key="u.id"
          :text="u.name"
          :type="form.unitId === u.id ? 'warning' : 'info'"
          @click="form.unitId = u.id"
        />
      </view>
    </view>

    <view class="block">
      <text class="label">采购分类 *</text>
      <view class="picker-row" v-if="purchases.length">
        <u-tag
          v-for="p in purchases"
          :key="p.id"
          :text="p.name"
          :type="form.purchaseCategoryId === p.id ? 'warning' : 'info'"
          @click="form.purchaseCategoryId = p.id"
        />
      </view>
      <text class="hint" v-else>暂无采购分类</text>
    </view>

    <text class="section">营养（每 100g / AI 预估）</text>
    <text class="mock-tip" v-if="aiSource">AI 预估（mock），待接 GLM，请核对后保存</text>
    <view class="nut-grid" v-if="metrics.length">
      <view class="nut-item" v-for="m in metrics" :key="m.id">
        <text class="nut-label">{{ metricCN(m.name) }}{{ m.unit ? `(${m.unit})` : '' }}</text>
        <u-input v-model="nutritionMap[m.id]" type="digit" placeholder="0" border="surround" />
      </view>
    </view>
    <text class="hint" v-else>营养指标字典加载中…</text>

    <u-button type="primary" :loading="loading" @click="onSave" class="save-btn">保存</u-button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { listDictByGroup, nutritionMetrics, createIngredient, type DictItem } from '@/api/ingredient'
import { aiFillNutrition } from '@/api/ai'

// 营养指标字典 name 是英文，家庭看不懂 → 中文映射，兜底英文
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数',
}
function metricCN(name: string) {
  return METRIC_CN[name] || name
}

const form = reactive({ name: '', unitId: 0, purchaseCategoryId: 0 })
const units = ref<DictItem[]>([])
const purchases = ref<DictItem[]>([])
const metrics = ref<any[]>([])
const nutritionMap = reactive<Record<number, string>>({})
const aiLoading = ref(false)
const aiSource = ref('')
const loading = ref(false)

onMounted(async () => {
  try {
    const [u, p] = await Promise.all([listDictByGroup('unit'), listDictByGroup('purchase_category')])
    units.value = u
    purchases.value = p
  } catch {
    // request.ts 已弹 toast
  }
  try {
    metrics.value = await nutritionMetrics()
    for (const m of metrics.value) nutritionMap[m.id] = ''
  } catch {
    // 字典失败不阻断（但保存时无营养指标会提示）
  }
})

async function onAiFill() {
  const name = form.name.trim()
  if (!name) {
    uni.showToast({ title: '请先输入食材名', icon: 'none' })
    return
  }
  aiLoading.value = true
  try {
    const r = await aiFillNutrition(name)
    aiSource.value = r.source || ''
    // 把 AI 返回的 {metricId,value} 填入表单
    const byId: Record<number, number> = {}
    for (const it of r.nutrition || []) byId[it.metricId] = it.value
    for (const m of metrics.value) {
      if (byId[m.id] !== undefined) nutritionMap[m.id] = String(byId[m.id])
    }
    uni.showToast({ title: 'AI 已填，请核对', icon: 'none' })
  } catch {
    // request.ts 已弹 toast
  } finally {
    aiLoading.value = false
  }
}

async function onSave() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入食材名', icon: 'none' })
    return
  }
  if (!form.unitId) {
    uni.showToast({ title: '请选计量单位', icon: 'none' })
    return
  }
  if (!form.purchaseCategoryId) {
    uni.showToast({ title: '请选采购分类', icon: 'none' })
    return
  }
  const nutritions = []
  for (const m of metrics.value) {
    const raw = nutritionMap[m.id]
    if (raw !== undefined && raw !== '' && !Number.isNaN(Number(raw))) {
      nutritions.push({ metricId: m.id, value: Number(raw) })
    }
  }
  if (!nutritions.length) {
    uni.showToast({ title: '请填或 AI 补全营养', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await createIngredient({
      ingredient: {
        name: form.name.trim(),
        unitId: form.unitId,
        purchaseCategoryId: form.purchaseCategoryId,
      },
      nutritions,
    })
    uni.showToast({ title: '已保存' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.create { padding: 30rpx; }
.block { margin-bottom: 24rpx; }
.label { display: block; font-size: 26rpx; color: #666; margin-bottom: 12rpx; }
.name-row { display: flex; align-items: center; gap: 16rpx; }
.picker-row { display: flex; flex-wrap: wrap; gap: 16rpx; }
.section { display: block; font-size: 30rpx; font-weight: bold; margin: 30rpx 0 12rpx; }
.mock-tip { display: block; font-size: 22rpx; color: #E89150; margin-bottom: 16rpx; }
.hint { font-size: 24rpx; color: #999; }
.nut-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.nut-item { display: flex; flex-direction: column; gap: 8rpx; }
.nut-label { font-size: 24rpx; color: #666; }
.save-btn { margin-top: 40rpx; }
</style>
