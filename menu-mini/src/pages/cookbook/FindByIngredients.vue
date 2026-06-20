<template>
  <view class="find">
    <!-- ① 选「我有的食材」：默认拉库存，可切换查全部 -->
    <view class="toolbar">
      <view :class="['src-tab', src === 'pantry' && 'on']" @click="switchSrc('pantry')">从库存选</view>
      <view :class="['src-tab', src === 'all' && 'on']" @click="switchSrc('all')">从全部食材选</view>
      <u-button size="mini" type="primary" :disabled="!selected.size" @click="search">
        找菜 ({{ selected.size }})
      </u-button>
    </view>

    <u-search v-model="keyword" placeholder="搜食材名" @search="filterList" @clear="filterList" />

    <view v-if="loadingSrc" class="empty">加载食材中…</view>
    <view v-else-if="!filteredList.length" class="empty">没有匹配的食材</view>

    <!-- 食材多选：tap 勾选/取消 -->
    <view v-else class="chips">
      <view
        v-for="ing in filteredList"
        :key="ing.id"
        :class="['chip', selected.has(ing.id) && 'on']"
        @click="toggle(ing.id)"
      >
        {{ ing.name }}
      </view>
    </view>

    <!-- ② 结果：分「能做」「还差一点」两组 -->
    <view v-if="searched" class="result">
      <view class="section-title">能做的菜（食材齐全）</view>
      <view v-if="!canMake.length" class="empty small">暂无完全能做的菜</view>
      <view v-for="m in canMake" :key="m.dish.id" class="card canMake" @click="goDetail(m.dish.id)">
        <view class="card-name">{{ m.dish.name }}</view>
        <view class="card-meta">
          用料 {{ m.totalCount }} 种 · {{ minutes(m.dish) }}分钟
          <text class="tag-ok">可做</text>
        </view>
      </view>

      <view class="section-title">还差一点（缺 1-2 种）</view>
      <view v-if="!partial.length" class="empty small">没有相近的菜</view>
      <view v-for="m in partial" :key="m.dish.id" class="card partial" @click="goDetail(m.dish.id)">
        <view class="card-name">{{ m.dish.name }}</view>
        <view class="card-meta">还缺 {{ m.missingIngredients.length }} 种 · {{ minutes(m.dish) }}分钟</view>
        <view class="missing">缺：{{ m.missingIngredients.join('、') }}</view>
      </view>
    </view>
    <u-loadmore v-if="searched" status="nomore" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listPantry, type PantryVO } from '@/api/pantry'
import { listAllIngredients } from '@/api/ingredient'
import { findDishesByIngredients, type DishMatch } from '@/api/dish'

type Src = 'pantry' | 'all'

const src = ref<Src>('pantry')
const keyword = ref('')
const loadingSrc = ref(false)
const searched = ref(false)

// 全量食材池（按当前 src 加载），name + id
const pool = ref<{ id: number; name: string }[]>([])
// 勾选集合
const selected = ref<Set<number>>(new Set())
// 接口返回
const matches = ref<DishMatch[]>([])

const canMake = computed(() => matches.value.filter((m) => m.canMake))
const partial = computed(() => matches.value.filter((m) => !m.canMake))

const filteredList = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return pool.value
  return pool.value.filter((i) => i.name.includes(kw))
})

onShow(() => {
  loadSource()
})

async function loadSource() {
  loadingSrc.value = true
  try {
    if (src.value === 'pantry') {
      const pantry: PantryVO[] = await listPantry()
      pool.value = pantry.map((p) => ({ id: p.ingredientId, name: p.ingredientName || `#${p.ingredientId}` }))
    } else {
      const all = await listAllIngredients()
      pool.value = all.map((i: any) => ({ id: i.id, name: i.name }))
    }
    // 切源后默认全选库存/全选（让用户快速减法）
    selected.value = new Set(pool.value.map((i) => i.id))
  } catch {
    pool.value = []
    selected.value = new Set()
  } finally {
    loadingSrc.value = false
  }
}

function switchSrc(s: Src) {
  if (s === src.value) return
  src.value = s
  searched.value = false
  matches.value = []
  loadSource()
}

// u-search 仅做本地过滤
function filterList() {
  // computed 自动响应，这里无需做事
}

function toggle(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

async function search() {
  const ids = [...selected.value]
  if (!ids.length) {
    uni.showToast({ title: '请先选食材', icon: 'none' })
    return
  }
  uni.showLoading({ title: '找菜中…' })
  try {
    matches.value = await findDishesByIngredients(ids)
    searched.value = true
    if (!matches.value.length) {
      uni.showToast({ title: '没找到能做的菜', icon: 'none' })
    }
  } finally {
    uni.hideLoading()
  }
}

function minutes(d: any): number {
  return Number(d?.prepTime || 0) + Number(d?.cookTime || 0)
}

function goDetail(id: number) {
  try {
    uni.navigateTo({ url: `/pages/dish/Detail?id=${id}`, fail: () => uni.showToast({ title: '详情页未就绪', icon: 'none' }) })
  } catch {
    uni.showToast({ title: '详情页未就绪', icon: 'none' })
  }
}
</script>

<style scoped>
.find { padding: 20rpx; }
.toolbar { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; flex-wrap: wrap; }
.src-tab { font-size: 26rpx; color: #666; padding: 8rpx 20rpx; border: 1rpx solid #ddd; border-radius: 30rpx; }
.src-tab.on { color: #fff; background: #FF8C42; border-color: #FF8C42; }
.chips { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 20rpx 0; }
.chip { font-size: 26rpx; color: #333; padding: 12rpx 24rpx; border: 1rpx solid #ddd; border-radius: 30rpx; background: #f7f7f7; }
.chip.on { color: #fff; background: #FF8C42; border-color: #FF8C42; }
.empty { text-align: center; color: #999; padding: 60rpx 0; }
.empty.small { padding: 24rpx 0; font-size: 26rpx; }
.result { margin-top: 20rpx; }
.section-title { font-size: 30rpx; font-weight: 600; color: #333; margin: 30rpx 0 16rpx; }
.card { padding: 24rpx; border-radius: 16rpx; margin-bottom: 16rpx; background: #fff; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.05); }
.card.canMake { border-left: 8rpx solid #4CAF50; }
.card.partial { border-left: 8rpx solid #FF8C42; }
.card-name { font-size: 32rpx; font-weight: 600; color: #222; }
.card-meta { font-size: 26rpx; color: #888; margin-top: 8rpx; }
.tag-ok { color: #4CAF50; font-size: 24rpx; margin-left: 12rpx; }
.missing { font-size: 26rpx; color: #FF8C42; margin-top: 8rpx; }
</style>
