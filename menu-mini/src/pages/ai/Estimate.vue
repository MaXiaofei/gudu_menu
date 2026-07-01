<template>
  <view class="est">
    <view class="field">
      <text class="lbl">描述这餐吃了什么</text>
      <u--textarea
        v-model="form.description"
        placeholder="如：一盘番茄炒蛋,大概2个鸡蛋2个番茄；或：一碗牛肉面,加了点青菜"
        count
        :maxlength="200"
        auto-height
      />
    </view>

    <view class="row">
      <view class="field flex1">
        <text class="lbl">份数</text>
        <u-input v-model="form.servingFactor" type="digit" placeholder="默认 1, 半份填 0.5" border="surround" />
      </view>
    </view>

    <u-button type="primary" :loading="loading" @click="onEstimate" class="est-btn">
      AI 估算这餐营养
    </u-button>
    <text class="tip">AI 估算为预估值,仅供参考（按整餐总量,非 per100g）</text>

    <view class="empty" v-if="!loading && !result">描述一餐,让 AI 估算总热量/蛋白质等</view>

    <view class="card" v-if="result">
      <view class="card-head">
        <text class="c-title">估算结果</text>
        <text class="c-src" :class="result.source">{{ srcText(result.source) }}</text>
      </view>
      <view class="desc">「{{ result.description }}」</view>

      <view class="nut-list">
        <view class="nut-item" v-for="it in nutritionRows" :key="it.metricId">
          <text class="nut-name">{{ it.name }}</text>
          <text class="nut-val">{{ it.value }}<text class="unit">{{ it.unit }}</text></text>
        </view>
      </view>

      <view class="note" v-if="result.aiNote">
        <text class="note-lbl">AI 说明</text>
        <text class="note-txt">{{ result.aiNote }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { aiEstimateDish, type AiDishEstimateResult } from '@/api/ai'

const form = reactive({ description: '', servingFactor: '' })
const loading = ref(false)
const result = ref<AiDishEstimateResult | null>(null)

// metricId -> 中文名 + 单位（整体餐不含 gi）
const METRIC: Record<string, { name: string; unit: string }> = {
  '1': { name: '热量', unit: 'kcal' },
  '2': { name: '蛋白质', unit: 'g' },
  '3': { name: '脂肪', unit: 'g' },
  '4': { name: '碳水', unit: 'g' },
  '5': { name: '糖', unit: 'g' },
}

const nutritionRows = computed(() => {
  if (!result.value?.nutrition) return []
  return Object.entries(result.value.nutrition)
    .filter(([id]) => METRIC[id])
    .map(([id, value]) => ({ metricId: id, name: METRIC[id].name, unit: METRIC[id].unit, value }))
})

function srcText(s: string): string {
  return s === 'deepseek' ? 'AI 估算' : 'mock 估算'
}

async function onEstimate() {
  if (!form.description.trim()) {
    uni.showToast({ title: '请描述这餐吃了什么', icon: 'none' })
    return
  }
  const sf = form.servingFactor ? Number(form.servingFactor) : undefined
  if (form.servingFactor && (sf === undefined || Number.isNaN(sf) || sf <= 0)) {
    uni.showToast({ title: '份数格式错误', icon: 'none' })
    return
  }
  loading.value = true
  result.value = null
  try {
    const r = await aiEstimateDish(form.description.trim(), sf)
    result.value = r
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.est { padding: 30rpx; }
.field { margin-bottom: 24rpx; }
.flex1 { flex: 1; }
.row { display: flex; gap: 24rpx; }
.lbl { display: block; font-size: 26rpx; color: #666; margin-bottom: 12rpx; }
.est-btn { margin-bottom: 12rpx; }
.tip { display: block; font-size: 22rpx; color: #E89150; margin-bottom: 24rpx; }
.empty { text-align: center; color: #999; padding: 60rpx 0; }
.card { border: 1rpx solid #eee; border-radius: 12rpx; padding: 24rpx; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.c-title { font-size: 30rpx; font-weight: bold; }
.c-src { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f0f0f0; color: #666; }
.c-src.deepseek { background: #FDFAF4; color: #E89150; }
.desc { font-size: 26rpx; color: #333; margin-bottom: 20rpx; }
.nut-list { display: flex; flex-wrap: wrap; gap: 16rpx; }
.nut-item { width: calc(33.33% - 12rpx); background: #FAFAFA; border-radius: 10rpx; padding: 16rpx; text-align: center; }
.nut-name { display: block; font-size: 24rpx; color: #999; margin-bottom: 8rpx; }
.nut-val { font-size: 32rpx; font-weight: bold; color: #E89150; }
.unit { font-size: 20rpx; color: #999; font-weight: normal; margin-left: 4rpx; }
.note { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid #f0f0f0; }
.note-lbl { display: block; font-size: 24rpx; color: #999; margin-bottom: 6rpx; }
.note-txt { font-size: 24rpx; color: #666; }
</style>
