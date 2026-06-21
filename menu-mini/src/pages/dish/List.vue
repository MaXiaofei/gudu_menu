<template>
  <view class="page">
    <!-- 顶栏：搜索 + 分享入口 -->
    <view class="topbar">
      <view class="search-box">
        <text class="ico">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜菜名"
          confirm-type="search"
          @confirm="reload"
        />
        <text v-if="keyword" class="clear" @click="keyword = ''; reload()">✕</text>
      </view>
      <text class="share-btn" @click="onCreate">分享个菜谱</text>
    </view>

    <!-- 筛选折叠 -->
    <view class="filter-toggle" @click="showFilter = !showFilter">
      <text>{{ showFilter ? '收起筛选' : '筛选' }}</text>
      <text v-if="activeFilterCount" class="badge">{{ activeFilterCount }}</text>
    </view>

    <view v-if="showFilter" class="card filter-panel">
      <view class="f-section">
        <view class="f-title">
          营养上限（每份不超）
          <text class="f-prefill" @click="prefillFromHealth">按我的档案</text>
        </view>
        <view class="f-grid">
          <view class="f-cell">
            <text class="f-label">糖≤(g)</text>
            <input class="f-input" type="digit" v-model="filters.sugar" placeholder="如25" />
          </view>
          <view class="f-cell">
            <text class="f-label">GI≤</text>
            <input class="f-input" type="digit" v-model="filters.gi" placeholder="如55" />
          </view>
          <view class="f-cell">
            <text class="f-label">热量≤(kcal)</text>
            <input class="f-input" type="digit" v-model="filters.cal" placeholder="如500" />
          </view>
        </view>
      </view>

      <view class="f-section">
        <view class="f-grid">
          <view class="f-cell">
            <text class="f-label">难度≤</text>
            <picker mode="selector" :range="diffNames" :value="diffIdx" @change="(e:any)=>diffIdx=Number(e.detail.value)">
              <view class="f-picker">{{ diffNames[diffIdx] }}</view>
            </picker>
          </view>
          <view class="f-cell">
            <text class="f-label">耗时≤(分)</text>
            <input class="f-input" type="number" v-model="filters.minutes" placeholder="如30" />
          </view>
        </view>
      </view>

      <view class="f-actions">
        <button class="btn-ghost half sm" @click="resetFilters">重置</button>
        <button class="btn-primary half sm" @click="reload">应用筛选</button>
      </view>
    </view>

    <!-- 菜谱卡片（单列） -->
    <view v-if="loading && !dishes.length" class="empty">加载中…</view>
    <view v-else-if="!dishes.length" class="empty">暂无菜品，去分享一个吧</view>
    <view v-else>
      <view v-for="d in dishes" :key="d.id" class="card dish-card" @click="goDetail(d.id)">
        <image
          v-if="d.coverUrl"
          class="cover"
          :src="d.coverUrl"
          mode="aspectFill"
        />
        <view v-else class="cover ph-cover">🍽</view>
        <view class="d-body">
          <view class="d-name">{{ d.name }}</view>
          <view class="d-meta">
            <text class="tag">{{ difficultyText(d.difficulty) }}</text>
            <text v-if="totalMinutes(d)" class="meta-item">{{ totalMinutes(d) }} 分钟</text>
            <text v-if="d.price" class="meta-item">¥{{ d.price }}</text>
          </view>
        </view>
      </view>
      <view v-if="status === 'nomore' && dishes.length" class="end">— 到底了 —</view>
      <view v-else-if="status === 'loading'" class="end">加载中…</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { searchDishes, searchDishesByNutrition } from '@/api/dish'
import { getCurrentMember, listMembers } from '@/api/member'

const dishes = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = 20
const status = ref<'loadmore' | 'loading' | 'nomore'>('loadmore')
const loading = ref(false)

const showFilter = ref(false)

const filters = reactive({
  sugar: '',
  gi: '',
  cal: '',
  minutes: '',
})

const diffNames = ['不限', '1', '2', '3', '4', '5']
const diffIdx = ref(0)

const METRIC_SUGAR = 5
const METRIC_GI = 6
const METRIC_CAL = 1

const activeFilterCount = computed(() => {
  let n = 0
  if (filters.sugar) n++
  if (filters.gi) n++
  if (filters.cal) n++
  if (filters.minutes) n++
  if (diffIdx.value > 0) n++
  return n
})

function buildParams(pn: number) {
  const p: Record<string, any> = {
    keyword: keyword.value,
    pageNum: pn,
    pageSize,
  }
  if (diffIdx.value > 0) p.maxDifficulty = diffIdx.value
  if (filters.minutes) p.maxMinutes = Number(filters.minutes)

  const limits: Record<string, number> = {}
  if (filters.sugar) limits[METRIC_SUGAR] = Number(filters.sugar)
  if (filters.gi) limits[METRIC_GI] = Number(filters.gi)
  if (filters.cal) limits[METRIC_CAL] = Number(filters.cal)
  if (Object.keys(limits).length) p.nutritionLimits = limits
  return p
}

function resetFilters() {
  filters.sugar = ''
  filters.gi = ''
  filters.cal = ''
  filters.minutes = ''
  diffIdx.value = 0
  reload()
}

async function prefillFromHealth() {
  try {
    const memberId = await getCurrentMember()
    if (!memberId) {
      uni.showToast({ title: '请先设置当前就餐成员', icon: 'none' })
      return
    }
    const members = await listMembers()
    const m = (members || []).find((x: any) => x.id === memberId)
    const c = m?.healthProfile?.constraints
    if (!c || (!c.sugarMax && !c.calMax)) {
      uni.showToast({ title: '档案未配置营养约束', icon: 'none' })
      return
    }
    if (c.sugarMax != null) filters.sugar = String(c.sugarMax)
    if (c.calMax != null) filters.cal = String(c.calMax)
    uni.showToast({ title: '已按档案预填', icon: 'success' })
  } catch {
    uni.showToast({ title: '读取档案失败', icon: 'none' })
  }
}

async function reload() {
  page.value = 1
  dishes.value = []
  await load()
}

async function load() {
  status.value = 'loading'
  loading.value = true
  try {
    const params = buildParams(page.value)
    const r = params.nutritionLimits
      ? await searchDishesByNutrition(params)
      : await searchDishes(params)
    const records = Array.isArray(r) ? r : (r.records || [])
    dishes.value.push(...records)
    status.value = records.length < pageSize ? 'nomore' : 'loadmore'
  } catch {
    status.value = 'loadmore'
  } finally {
    loading.value = false
  }
}

function difficultyText(d?: number): string {
  if (d == null) return '难度-'
  if (d <= 1) return '难度·易'
  if (d === 2) return '难度·中'
  return '难度·难'
}
function totalMinutes(d: any): number {
  return (Number(d.prepTime) || 0) + (Number(d.cookTime) || 0)
}

/** 分享个菜谱：手动录 vs URL 导入。 */
function onCreate() {
  uni.showActionSheet({
    itemList: ['手动录入菜谱', '从网页链接导入'],
    success: (r) => {
      if (r.tapIndex === 0) {
        uni.navigateTo({ url: '/pages/dish/Create' })
      } else if (r.tapIndex === 1) {
        uni.navigateTo({ url: '/pages/dish/Create?url=1' })
      }
    },
  })
}

onPullDownRefresh(() => {
  reload().finally(() => uni.stopPullDownRefresh())
})
onReachBottom(() => {
  if (status.value === 'loadmore') {
    page.value++
    load()
  }
})

reload()
function goDetail(id: number) {
  uni.navigateTo({
    url: `/pages/dish/Detail?id=${id}`,
    fail: () => uni.showToast({ title: '详情页未就绪', icon: 'none' }),
  })
}
</script>

<style scoped>
.page { padding: 8px 12px 24px; }

/* 顶栏：搜索 + 分享 */
.topbar { display: flex; align-items: center; gap: 8px; padding: 6px 0 10px; }
.search-box {
  flex: 1; display: flex; align-items: center; gap: 6px;
  background: #fff; border-radius: 8px; padding: 7px 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.ico { font-size: 14px; color: #999; }
.search-input { flex: 1; font-size: 14px; color: #222; }
.clear { font-size: 14px; color: #ccc; padding: 0 4px; }
.share-btn {
  font-size: 13px; color: #FF6B35; white-space: nowrap;
  padding: 4px 2px;
}

/* 筛选 */
.filter-toggle {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 0; color: #FF6B35; font-size: 13px;
}
.badge {
  background: #FF6B35; color: #fff; font-size: 11px;
  border-radius: 10px; padding: 0 6px; line-height: 16px;
}
.filter-panel { padding: 12px; }
.f-section { margin-bottom: 10px; }
.f-section:last-of-type { margin-bottom: 0; }
.f-title {
  font-size: 12px; color: #999; margin-bottom: 6px;
  display: flex; justify-content: space-between; align-items: center;
}
.f-prefill { font-size: 12px; color: #2a9d8f; }
.f-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.f-cell { flex: 1; min-width: 90px; display: flex; flex-direction: column; gap: 4px; }
.f-label { font-size: 11px; color: #666; }
.f-input { border: 1px solid #eee; border-radius: 6px; padding: 6px 8px; font-size: 13px; }
.f-picker { border: 1px solid #eee; border-radius: 6px; padding: 6px 10px; font-size: 13px; color: #333; }
.f-actions { display: flex; gap: 8px; margin-top: 10px; }
.half { flex: 1; }
.sm { font-size: 13px; padding: 8px 0; }

/* 菜谱卡片 */
.dish-card { display: flex; gap: 12px; padding: 12px; }
.cover {
  width: 76px; height: 76px; border-radius: 8px; flex-shrink: 0;
  background: #f3f3f3;
}
.ph-cover {
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; color: #ddd;
}
.d-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 8px; }
.d-name { font-size: 16px; font-weight: 600; color: #222; }
.d-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tag {
  font-size: 11px; color: #FF6B35; background: #fff1ea;
  padding: 1px 7px; border-radius: 10px;
}
.meta-item { font-size: 12px; color: #888; }

.empty { text-align: center; color: #aaa; padding: 50px 0; font-size: 13px; }
.end { text-align: center; color: #ccc; font-size: 12px; padding: 16px 0; }

button::after { border: none; }
</style>
