<template>
  <view class="review">
    <view class="block">
      <text class="label">总评星级</text>
      <u-rate v-model="form.starRating" :count="5" />
    </view>
    <view class="block">
      <text class="label">点评文字</text>
      <u-textarea v-model="form.text" placeholder="说说味道、难度……" />
    </view>
    <view class="block">
      <text class="label">图片</text>
      <u-upload :fileList="imgs" @afterRead="onAdd" @delete="onDelete" :maxCount="6" />
    </view>
    <view class="block">
      <text class="label">维度打分</text>
      <view class="dim" v-for="d in dims" :key="d.id">
        <text class="dim-name">{{ d.name }}</text>
        <u-rate v-model="scores[d.id]" :count="5" />
      </view>
    </view>
    <u-button type="primary" :loading="loading" @click="onSubmit">提交</u-button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitReview, dimensions, uploadImages } from '@/api/review'

const dishId = ref(0)
const dims = ref<any[]>([])
const imgs = ref<any[]>([])
const form = reactive({ starRating: 5, text: '' })
const scores = reactive<Record<number, number>>({})
const loading = ref(false)

onLoad(async (q: any) => {
  dishId.value = q.dishId
  try { dims.value = await dimensions() } catch {}
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
.review { padding: 30rpx; }
.block { margin-bottom: 30rpx; }
.label { display: block; font-size: 28rpx; color: #666; margin-bottom: 16rpx; }
.dim { display: flex; justify-content: space-between; align-items: center; padding: 12rpx 0; }
.dim-name { font-size: 28rpx; }
</style>
