<template>
  <view class="list">
    <u-search v-model="keyword" placeholder="搜菜名" @search="reload" @clear="reload" />

    <!-- 筛选切换条 -->
    <view class="filter-toggle" @click="showFilter = !showFilter">
      <text>{{ showFilter ? '收起筛选' : '筛选' }}</text>
      <text v-if="activeFilterCount" class="badge">{{ activeFilterCount }}</text>
    </view>

    <!-- 筛选区 -->
    <view v-if="showFilter" class="filter-panel">
      <!-- 营养上限（健康档案常用三项） -->
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

      <!-- 难度/时间 -->
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
        <view class="f-btn reset" @click="resetFilters">重置</view>
        <view class="f-btn apply" @click="reload">应用筛选</view>
      </view>
    </view>

    <u-cell
      v-for="d in dishes"
      :key="d.id"
      :title="d.name"
      :label="`${d.cookTime || 0}分钟 · 难度${d.difficulty || '-'}`"
      isLink
      @click="goDetail(d.id)"
    />
    <view v-if="dishes.length === 0" class="empty">暂无菜品</view>
    <u-loadmore :status="status" />
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { searchDishes } from '@/api/dish'
import { getCurrentMember, listMembers } from '@/api/member'

const dishes = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = 20
const status = ref<'loadmore' | 'loading' | 'nomore'>('loadmore')

const showFilter = ref(false)

// 营养上限（语义输入，空=不限）+ 维度筛选
const filters = reactive({
  sugar: '',   // metric 5
  gi: '',      // metric 6
  cal: '',     // metric 1
  minutes: '', // maxMinutes
})

const diffNames = ['不限', '1', '2', '3', '4', '5']
const diffIdx = ref(0) // 0 = 不限

// 营养指标 id（对齐 sql/V03: 1=calorie 5=sugar 6=gi）
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

/** 组装 search 参数：含 nutritionLimits（只带非空的）。 */
function buildParams(pn: number) {
  const p: Record<string, any> = {
    keyword: keyword.value,
    pageNum: pn,
    pageSize,
  }
  if (diffIdx.value > 0) p.maxDifficulty = diffIdx.value
  if (filters.minutes) p.maxMinutes = Number(filters.minutes)

  // 营养上限 → nutritionLimits: { metricId: 上限 }
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

/** 按当前就餐成员健康档案预填营养上限（healthProfile.constraints: { sugarMax, calMax }）。 */
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
  try {
    const r = await searchDishes(buildParams(page.value))
    const records = Array.isArray(r) ? r : (r.records || [])
    dishes.value.push(...records)
    status.value = records.length < pageSize ? 'nomore' : 'loadmore'
  } catch {
    status.value = 'loadmore'
  }
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
  try {
    uni.navigateTo({ url: `/pages/dish/Detail?id=${id}`, fail: () => uni.showToast({ title: '详情页未就绪', icon: 'none' }) })
  } catch {
    uni.showToast({ title: '详情页未就绪', icon: 'none' })
  }
}
</script>

<style scoped>
.list { padding: 20rpx; }
.empty { text-align: center; color: #999; padding: 60rpx 0; }

.filter-toggle { display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 0; color: #FF8C42; font-size: 13px; }
.filter-toggle .badge { background: #FF8C42; color: #fff; font-size: 11px;
  border-radius: 10px; padding: 0 6px; line-height: 16px; }

.filter-panel { background: #fff; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.f-section { margin-bottom: 10px; }
.f-section:last-of-type { margin-bottom: 0; }
.f-title { font-size: 12px; color: #999; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center; }
.f-prefill { font-size: 12px; color: #2a9d8f; }
.f-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.f-cell { flex: 1; min-width: 90px; display: flex; flex-direction: column; gap: 4px; }
.f-label { font-size: 11px; color: #666; }
.f-input { border: 1px solid #eee; border-radius: 6px; padding: 6px 8px; font-size: 13px; }
.f-picker { border: 1px solid #eee; border-radius: 6px; padding: 6px 10px; font-size: 13px; color: #333; }

.f-actions { display: flex; gap: 8px; margin-top: 10px; }
.f-btn { flex: 1; text-align: center; padding: 8px 0; border-radius: 6px; font-size: 13px; }
.f-btn.reset { background: #f5f0ea; color: #666; }
.f-btn.apply { background: #FF8C42; color: #fff; }
</style>
