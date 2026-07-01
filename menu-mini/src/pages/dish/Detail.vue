<template>
  <view class="page">
    <!-- 顶栏（自定义） -->
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="top-title">菜谱</text>
      <view class="back"></view>
    </view>

    <scroll-view scroll-y class="scroll" v-if="dish">
      <!-- 封面大图 -->
      <view class="cover-wrap">
        <image
          v-if="dish.coverUrl"
          class="cover"
          :src="imgUrl(dish.coverUrl)"
          mode="aspectFill"
        />
        <view v-else class="cover ph-cover">🍽</view>
      </view>

      <!-- 标题区 -->
      <view class="title-card">
        <view class="title-row">
          <text class="title">{{ dish.name }}</text>
          <text :class="['src-tag', dish.source === 'IMPORT' ? 'imp' : 'own']">
            {{ dish.source === 'IMPORT' ? '🌐' : '🏠' }}
          </text>
        </view>
        <view class="meta-row">
          <text class="meta-item">备料 {{ dish.prepTime || 0 }}分</text>
          <text class="meta-dot">·</text>
          <text class="meta-item">烹饪 {{ dish.cookTime || 0 }}分</text>
          <text class="meta-dot">·</text>
          <text class="diff-tag">{{ difficultyText(dish.difficulty) }}</text>
        </view>
        <view v-if="dish.note" class="note">{{ dish.note }}</view>
      </view>

      <!-- 营养 -->
      <view v-if="nutritionDisplay.length" class="block">
        <view class="block-title" @click="nutritionExpanded = !nutritionExpanded">
          <view class="tbar"></view>
          <text>营养（份数 {{ serving }}）</text>
          <text class="expand-toggle">{{ nutritionExpanded ? '收起 ▲' : '展开 ▼' }}</text>
        </view>
        <view v-if="nutritionExpanded" class="yh-card nutrition-card">
          <view
            class="nutrition-row"
            v-for="(n, i) in nutritionDisplay"
            :key="i"
          >
            <text class="n-label">{{ n.label }}</text>
            <view class="n-right">
              <text class="n-value">{{ n.value }}</text>
              <text class="n-unit">{{ n.unit }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 食材 -->
      <view class="block" v-if="ingredients.length">
        <view class="block-title">
          <view class="tbar"></view>
          <text>食材</text>
        </view>
        <view class="yh-card ingredient-card">
          <view class="ingredient-row" v-for="(ing, i) in ingredients" :key="i">
            <text class="ing-name">{{ ing.ingredientName || '未知食材' }}</text>
            <view class="ing-right">
              <text class="ing-amount">{{ ing.amount }}</text>
              <text class="ing-unit">g</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 做法 -->
      <view class="block">
        <view class="block-title">
          <view class="tbar"></view>
          <text>做法</text>
        </view>
        <view class="yh-card step-card" v-for="(s, i) in steps" :key="i">
          <view class="step-head">
            <text class="step-no">步骤 {{ i + 1 }}</text>
            <button
              :class="['timer-btn', active === i ? 'stop' : '']"
              @click="toggleTimer(i)"
            >
              {{ active === i ? '停止' : '计时' }}
            </button>
          </view>
          <text class="step-text">{{ s.text }}</text>
          <view class="step-imgs" v-if="imgs(s.images).length">
            <image
              v-for="(img, j) in imgs(s.images)"
              :key="j"
              class="step-img"
              :src="imgUrl(img)"
              mode="aspectFill"
            />
          </view>
          <view v-if="active === i" class="timer">⏱ {{ elapsed }}s</view>
        </view>
      </view>

      <view style="height: 160rpx;"></view>
    </scroll-view>

    <view v-else class="loading">加载中…</view>

    <!-- 底部操作 -->
    <view class="bottom-actions" v-if="dish">
      <button class="yh-btn-ghost half" @click="onMarkDone">标记做过</button>
      <button class="yh-btn-gradient half" @click="goReview">去点评</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { dishDetail, dishNutrition, markDone, nutritionMetrics } from '@/api/dish'
import { useMemberStore } from '@/store/member'

const m = useMemberStore()
const data = ref<any>(null)
const dish = computed(() => data.value?.dish || null)
const steps = computed(() => data.value?.steps || [])
const ingredients = computed(() => data.value?.ingredients || [])

const nutrition = ref<Record<string, any>>({})
const metrics = ref<any[]>([])
const serving = ref(1)
const dishId = ref(0)
const active = ref(-1)
const elapsed = ref(0)
const nutritionExpanded = ref(false)

function difficultyText(d?: number): string {
  if (d == null) return '难度未知'
  if (d <= 2) return '简单'
  if (d === 3) return '中等'
  return '有难度'
}
let timer: any = null

const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数'
}
const nutritionDisplay = computed(() => {
  const arr: { label: string; value: any; unit: string }[] = []
  for (const m of metrics.value) {
    const v = nutrition.value[m.id]
    if (v !== undefined && v !== null) {
      arr.push({ label: METRIC_CN[m.name] || m.name, value: v, unit: m.unit ? m.unit : '' })
    }
  }
  return arr
})

onLoad(async (q: any) => {
  dishId.value = q.id
  try {
    data.value = await dishDetail(q.id)
  } catch {
    uni.showToast({ title: '加载详情失败', icon: 'none' })
    return
  }
  try { nutrition.value = await dishNutrition(q.id, serving.value) } catch {}
  try { metrics.value = await nutritionMetrics() } catch {}
})

function toggleTimer(i: number) {
  if (active.value === i && timer) {
    clearInterval(timer)
    timer = null
    active.value = -1
    return
  }
  if (timer) clearInterval(timer)
  active.value = i
  elapsed.value = 0
  timer = setInterval(() => { elapsed.value++ }, 1000)
}
onUnmounted(() => { if (timer) clearInterval(timer) })

function imgs(s: any): string[] {
  return s ? String(s).split(',').filter(Boolean) : []
}
function imgUrl(u: string): string {
  if (!u) return ''
  // 后端返回相对根的路径（如 /gudu/uploads/xxx），H5 经 vite proxy 透传到后端；真机直连已含 host
  return u.startsWith('http') || u.startsWith('/gudu') ? u : '/gudu' + u
}
async function onMarkDone() {
  if (!m.currentId) {
    uni.showToast({ title: '请先选择就餐成员', icon: 'none' })
    return
  }
  try {
    await markDone(dishId.value, m.currentId)
    uni.showToast({ title: '已记录', icon: 'success' })
  } catch {}
}
function goReview() {
  uni.navigateTo({ url: `/pages/dish/Review?dishId=${dishId.value}` })
}
function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/dish/List' }) })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #FDFAF4;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 16rpx) 24rpx 12rpx;
  background: #FDFAF4;
}
.back {
  width: 60rpx;
  font-size: 48rpx;
  color: #4A382A;
  text-align: center;
}
.top-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #4A382A;
}

.scroll { flex: 1; }

/* 封面 */
.cover-wrap {
  width: 100%;
  height: 420rpx;
}
.cover { width: 100%; height: 100%; }
.ph-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F6D9BE, #F6D9BE);
  font-size: 100rpx;
  color: rgba(255, 255, 255, 0.85);
}

/* 标题卡（上浮盖在封面底部） */
.title-card {
  margin: -48rpx 28rpx 0;
  background: #FFFFFF;
  border-radius: 36rpx;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.08);
  padding: 36rpx;
  position: relative;
  z-index: 2;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.title {
  flex: 1;
  font-size: 40rpx;
  font-weight: bold;
  color: #4A382A;
}
.src-tag {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(232, 145, 80, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 16rpx;
}
.meta-item { font-size: 24rpx; color: #9C8C7A; }
.meta-dot { color: #9C8C7A; }
.diff-tag {
  display: inline-block;
  background: rgba(232, 145, 80, 0.1);
  color: #E89150;
  border-radius: 8rpx;
  padding: 4rpx 16rpx;
  font-size: 22rpx;
}
.expand-toggle {
  margin-left: auto;
  font-size: 24rpx;
  color: #9C8C7A;
}
.note {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #9C8C7A;
  line-height: 1.6;
}

/* 块 */
.block { margin: 36rpx 28rpx 0; }
.block-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 18rpx;
}
.tbar {
  width: 8rpx;
  height: 32rpx;
  background: #E89150;
  border-radius: 4rpx;
}
.block-title text {
  font-size: 32rpx;
  font-weight: bold;
  color: #4A382A;
}

/* 营养卡 */
.nutrition-card { padding: 8rpx 32rpx; }
.nutrition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.nutrition-row:last-child { border-bottom: none; }
.n-label { font-size: 28rpx; color: #4A382A; }
.n-right { display: flex; align-items: baseline; gap: 6rpx; }
.n-value { font-size: 32rpx; font-weight: bold; color: #E89150; }
.n-unit { font-size: 22rpx; color: #9C8C7A; }

/* 食材卡（复用营养卡视觉语言） */
.ingredient-card { padding: 8rpx 32rpx; }
.ingredient-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.ingredient-row:last-child { border-bottom: none; }
.ing-name { font-size: 28rpx; color: #4A382A; }
.ing-right { display: flex; align-items: baseline; gap: 4rpx; }
.ing-amount { font-size: 32rpx; font-weight: bold; color: #E89150; }
.ing-unit { font-size: 22rpx; color: #9C8C7A; }

/* 步骤卡 */
.step-card { padding: 28rpx 32rpx; }
.step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.step-no { font-size: 28rpx; font-weight: 600; color: #4A382A; }
.timer-btn {
  background: #E89150;
  color: #FFFFFF;
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 24rpx;
  line-height: 1.4;
}
.timer-btn::after { border: none; }
.timer-btn.stop {
  background: #DB5A4E;
}
.step-text {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #4A382A;
  line-height: 1.6;
}
.step-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}
.step-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
}
.timer {
  color: #E89150;
  font-size: 36rpx;
  font-weight: bold;
  margin-top: 16rpx;
}

.loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9C8C7A;
  font-size: 14px;
}

/* 底部操作 */
.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 28rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #FFFFFF;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  display: flex;
  gap: 20rpx;
  z-index: 10;
}
.bottom-actions .half {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  padding: 0;
}
</style>
