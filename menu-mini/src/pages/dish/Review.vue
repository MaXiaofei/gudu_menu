<template>
  <view class="page">
    <!-- 总评星级：醒目渐变卡 -->
    <view class="rating-card">
      <text class="rating-label">给这道菜打个分</text>
      <u-rate v-model="form.starRating" :count="5" size="30" />
      <text class="rating-hint">{{ ratingHint }}</text>
    </view>

    <!-- 点评文字 -->
    <view class="block">
      <view class="block-title">
        <view class="tbar"></view>
        <text>说点啥</text>
      </view>
      <view class="yh-card">
        <u-textarea v-model="form.text" placeholder="味道如何？难不难？想再做一次吗？" border="none" :auto-height="true" />
      </view>
    </view>

    <!-- 图片 -->
    <view class="block">
      <view class="block-title">
        <view class="tbar"></view>
        <text>传点图</text>
      </view>
      <view class="yh-card">
        <u-upload :fileList="imgs" @afterRead="onAdd" @delete="onDelete" :maxCount="6" />
      </view>
    </view>

    <!-- 维度打分 -->
    <view class="block" v-if="dims.length">
      <view class="block-title">
        <view class="tbar"></view>
        <text>分项打分</text>
      </view>
      <view class="yh-card dim-card">
        <view class="dim-row" v-for="(d, i) in dims" :key="d.id">
          <text class="dim-name">{{ d.name }}</text>
          <u-rate v-model="scores[d.id]" :count="5" />
        </view>
      </view>
    </view>

    <view style="height: 160rpx;"></view>

    <!-- 底部提交 -->
    <view class="bottom-actions">
      <button class="yh-btn-gradient" :disabled="loading" @click="onSubmit">
        {{ loading ? '提交中…' : '提交点评' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitReview, dimensions, uploadImages } from '@/api/review'

const dishId = ref(0)
const dims = ref<any[]>([])
const imgs = ref<any[]>([])
const form = reactive({ starRating: 5, text: '' })
const scores = reactive<Record<number, number>>({})
const loading = ref(false)

// 按星级给一句反馈文案，增强打分时的反馈感
const ratingHint = computed(() => {
  const map: Record<number, string> = {
    1: '不太行',
    2: '一般般',
    3: '还可以',
    4: '挺不错',
    5: '想天天吃！',
  }
  return map[form.starRating] || ''
})

onLoad(async (q: any) => {
  dishId.value = q.dishId
  try {
    dims.value = await dimensions()
    // 维度分默认跟总评一致，避免用户不逐项打分就提交全 0
    dims.value.forEach((d: any) => { scores[d.id] = form.starRating })
  } catch {}
})

function onAdd(e: any) {
  const files = e.file || (e.files ? e.files : [e.file])
  if (Array.isArray(files)) imgs.value.push(...files)
}
function onDelete(e: any) {
  imgs.value.splice(e.index, 1)
}

async function onSubmit() {
  if (!form.starRating) {
    uni.showToast({ title: '请选择星级', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const urls = await uploadImages(imgs.value.map((f: any) => f.url || f.path))
    await submitReview({
      dishId: dishId.value,
      starRating: form.starRating,
      text: form.text,
      images: urls,
      dimensionScores: { ...scores }
    })
    uni.showToast({ title: '已点评' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #FDFAF4;
  padding: 0 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
}

/* 总评星级：醒目渐变卡 */
.rating-card {
  margin-top: calc(env(safe-area-inset-top) + 24rpx);
  background: linear-gradient(180deg, #E89150, #D17A3C);
  border-radius: 36rpx;
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(232, 145, 80, 0.25);
}
.rating-label {
  font-size: 32rpx;
  font-weight: bold;
  color: #FFFFFF;
}
.rating-hint {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.92);
  min-height: 34rpx;
}

/* 块标题（复用详情页 block-title 视觉） */
.block { margin-top: 36rpx; }
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

/* 维度分项卡 */
.dim-card { padding: 8rpx 32rpx; }
.dim-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.dim-row:last-child { border-bottom: none; }
.dim-name {
  font-size: 28rpx;
  color: #4A382A;
}

/* 底部固定提交栏 */
.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 28rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #FFFFFF;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  z-index: 10;
}
.bottom-actions .yh-btn-gradient {
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
}
</style>
