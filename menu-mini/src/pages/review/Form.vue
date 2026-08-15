<template>
  <!-- 评价表单页（单菜/食集通用）：query: dishId|menuId, name, title -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <text class="title">写评价</text>
      <view style="width: 30px" />
    </view>

    <scroll-view scroll-y class="body">
      <!-- 评价对象头 -->
      <view class="head">
        <ui-avatar :name="targetName" :size="40" :fallback="'菜'" />
        <text class="head-name">{{ targetName }}</text>
      </view>

      <!-- 总评星级卡（渐变橙） -->
      <view class="star-card">
        <text v-if="cardTitle" class="card-title">{{ cardTitle }}</text>
        <view class="stars">
          <text
            v-for="i in 5"
            :key="i"
            class="star"
            :class="{ on: i <= star }"
            @click="star = i"
          >★</text>
        </view>
        <text class="star-hint">{{ starHints[star - 1] }}</text>
      </view>

      <!-- 评价内容 -->
      <text class="sec-label">评价内容</text>
      <textarea v-model="text" class="area" :maxlength="500" placeholder="味道如何？难不难？想再做一次吗？" placeholder-class="ph" />

      <!-- 添加图片（上限 6） -->
      <text class="sec-label">添加图片</text>
      <view class="imgs">
        <view v-for="(img, i) in images" :key="i" class="img-box">
          <image class="img" :src="img" mode="aspectFill" @click="previewImg(i)" />
          <view class="img-del" @click="images.splice(i, 1)"><text class="img-del-txt">✕</text></view>
        </view>
        <view v-if="images.length < 6" class="img-add" @click="pickImages">
          <text class="img-add-txt">{{ images.length }}/6</text>
        </view>
      </view>

      <!-- 分项评分 -->
      <template v-if="dimensions.length">
        <text class="sec-label">评分</text>
        <view class="dim-card">
          <view v-for="d in dimensions" :key="d.id" class="dim-row">
            <text class="dim-name">{{ d.name }}</text>
            <view class="dim-stars">
              <text
                v-for="i in 5"
                :key="i"
                class="dim-star"
                :class="{ on: i <= (dimScores[d.id] ?? star) }"
                @click="dimScores[d.id] = i"
              >★</text>
            </view>
          </view>
        </view>
      </template>

      <view style="height: 96px" />
    </scroll-view>

    <view class="bottom">
      <button class="btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交点评' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { reviewDimensions, submitReview, type ReviewDimension } from '@/api/review'
import { chooseImages, uploadImage } from '@/api/upload'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()

const dishId = ref<number | null>(null)
const menuId = ref<number | null>(null)
const targetName = ref('')

const star = ref(5)
const starHints = ['不太行', '一般般', '还可以', '挺不错', '想天天吃！']
const text = ref('')
const images = ref<string[]>([])
const dimensions = ref<ReviewDimension[]>([])
const dimScores = reactive<Record<number, number>>({})
const submitting = ref(false)

const cardTitle = computed(() =>
  dishId.value ? '给这道菜打个分' : '', // 食集评价不显示标题（对齐 APP）
)

onLoad(async (options) => {
  dishId.value = options?.dishId ? Number(options.dishId) : null
  menuId.value = options?.menuId ? Number(options.menuId) : null
  targetName.value = decodeURIComponent(options?.name || '') || (dishId.value ? '这道菜' : '这顿饭')
  reviewDimensions().then((r) => (dimensions.value = r)).catch(() => {})
})

function goBack() {
  uni.navigateBack()
}

async function pickImages() {
  const list = await chooseImages(6 - images.value.length)
  images.value.push(...list)
}

function previewImg(i: number) {
  uni.previewImage({ urls: images.value, current: images.value[i] })
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  try {
    // 逐张上传
    const urls: string[] = []
    for (const img of images.value) {
      const r = await uploadImage(img)
      urls.push(r.url)
    }
    const scores: Record<string, number> = {}
    dimensions.value.forEach((d) => {
      scores[String(d.id)] = dimScores[d.id] ?? star.value
    })
    await submitReview({
      dishId: dishId.value ?? undefined,
      menuId: menuId.value ?? undefined,
      starRating: star.value,
      text: text.value,
      images: urls,
      dimensionScores: scores,
    })
    uni.showToast({ title: '已点评', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (e) {
    uni.showToast({ title: `提交失败：${e}`, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  display: flex;
  align-items: center;
  padding: 0 14px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 8px;
}
.title {
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
}
.head-name { font-size: 15px; font-weight: 800; color: var(--title); }
.star-card {
  background: linear-gradient(135deg, var(--primary), #E6762A);
  border-radius: var(--r-lg);
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.card-title { color: #FFFFFF; font-size: 13px; font-weight: 700; }
.stars { display: flex; gap: 10px; }
.star { font-size: 36px; color: rgba(255, 255, 255, 0.5); }
.star.on { color: #FFFFFF; }
.star-hint { color: rgba(255, 255, 255, 0.9); font-size: 11px; }
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 16px 0 8px;
}
.area {
  width: 100%;
  box-sizing: border-box;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: 13px;
  color: var(--title);
  height: 96px;
}
.imgs { display: flex; flex-wrap: wrap; gap: 8px; }
.img-box { position: relative; }
.img { width: 80px; height: 80px; border-radius: var(--r-sm); background: var(--secondary); }
.img-del {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.54);
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-del-txt { color: #FFFFFF; font-size: 11px; }
.img-add {
  width: 80px;
  height: 80px;
  border: 1px dashed var(--border);
  border-radius: var(--r-sm);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-add-txt { font-size: 12px; color: var(--caption); }
.dim-card {
  background: var(--card);
  border-radius: var(--r-md);
  padding: 6px 14px;
}
.dim-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
}
.dim-row + .dim-row { border-top: 1px solid var(--bg); }
.dim-name { font-size: 13px; color: var(--body); }
.dim-stars { display: flex; gap: 6px; }
.dim-star { font-size: 24px; color: var(--border); }
.dim-star.on { color: var(--primary); }
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.ph { color: var(--caption); }
</style>
