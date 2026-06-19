<template>
  <view class="rec">
    <view class="bar">
      <view class="field">
        <text class="lbl">预算(元)</text>
        <u-input v-model="form.budget" type="digit" placeholder="如 50" border="surround" />
      </view>
      <view class="field">
        <text class="lbl">范围</text>
        <view class="seg">
          <u-tag text="今天" :type="form.scope === 'DAY' ? 'warning' : 'info'" @click="form.scope = 'DAY'" />
          <u-tag text="本周" :type="form.scope === 'WEEK' ? 'warning' : 'info'" @click="form.scope = 'WEEK'" />
        </view>
      </view>
    </view>

    <u-button type="primary" :loading="loading" @click="onRec" class="rec-btn">
      AI 帮我定{{ form.scope === 'WEEK' ? '这周' : '今晚' }}吃啥
    </u-button>
    <text class="mock-tip">AI 推荐为预估值（mock），待接 GLM</text>

    <view class="empty" v-if="!loading && !groups.length">点上方按钮，让 AI 帮你定菜单</view>

    <view class="group" v-for="(g, gi) in groups" :key="gi">
      <view class="group-head">
        <text class="g-title">方案 {{ gi + 1 }}</text>
        <text class="g-score" v-if="g.score !== undefined">评分 {{ g.score }}</text>
      </view>
      <view class="dishes">
        <u-tag
          v-for="(d, di) in g.dishes"
          :key="di"
          :text="`${d.name}${d.servingFactor && d.servingFactor !== 1 ? ` ×${d.servingFactor}` : ''}`"
          type="success"
          size="mini"
        />
      </view>
      <view class="line">总价：￥{{ g.totalPrice }}</view>
      <view class="line" v-if="nutritionText(g.totalNutrition)">营养：{{ nutritionText(g.totalNutrition) }}</view>
      <view class="reasons" v-if="g.reasons && g.reasons.length">
        <text class="reason" v-for="(r, ri) in g.reasons" :key="ri">· {{ r }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { aiRecommendMenu, type AiRecommendGroup } from '@/api/ai'
import { useMemberStore } from '@/store/member'
import { nutritionMetrics } from '@/api/dish'

const m = useMemberStore()
const form = reactive({ budget: '', scope: 'DAY' as 'DAY' | 'WEEK' })
const loading = ref(false)
const groups = ref<AiRecommendGroup[]>([])
const metrics = ref<any[]>([])

// totalNutrition 是 {metricId(字符串) -> value}，需配 metric 字典映射中文
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数',
}
nutritionMetrics()
  .then((r) => (metrics.value = r))
  .catch(() => {})

function nutritionText(n: Record<string, number> | undefined): string {
  if (!n) return ''
  const parts: string[] = []
  for (const met of metrics.value) {
    const v = n[String(met.id)]
    if (v !== undefined && v !== null) parts.push(`${METRIC_CN[met.name] || met.name} ${v}`)
  }
  return parts.join(' / ')
}

async function onRec() {
  if (!m.currentId) {
    uni.showToast({ title: '请先选当前就餐成员', icon: 'none' })
    return
  }
  const budget = form.budget ? Number(form.budget) : undefined
  if (form.budget && (budget === undefined || Number.isNaN(budget))) {
    uni.showToast({ title: '预算格式错误', icon: 'none' })
    return
  }
  loading.value = true
  groups.value = []
  try {
    const r = await aiRecommendMenu({ budget, scope: form.scope })
    groups.value = Array.isArray(r) ? r : []
    if (!groups.value.length) uni.showToast({ title: '暂无推荐方案', icon: 'none' })
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.rec { padding: 30rpx; }
.bar { display: flex; gap: 24rpx; margin-bottom: 24rpx; }
.field { flex: 1; }
.lbl { display: block; font-size: 26rpx; color: #666; margin-bottom: 12rpx; }
.seg { display: flex; gap: 16rpx; }
.rec-btn { margin-bottom: 12rpx; }
.mock-tip { display: block; font-size: 22rpx; color: #FF8C42; margin-bottom: 24rpx; }
.empty { text-align: center; color: #999; padding: 60rpx 0; }
.group { border: 1rpx solid #eee; border-radius: 12rpx; padding: 24rpx; margin-bottom: 24rpx; }
.group-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.g-title { font-size: 30rpx; font-weight: bold; }
.g-score { font-size: 24rpx; color: #FF8C42; }
.dishes { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 16rpx; }
.line { font-size: 26rpx; color: #666; margin-top: 8rpx; }
.reasons { margin-top: 12rpx; }
.reason { display: block; font-size: 24rpx; color: #999; margin-top: 6rpx; }
</style>
