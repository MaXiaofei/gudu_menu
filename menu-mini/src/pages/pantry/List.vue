<template>
  <view class="page">
    <ui-action-bar>
      <view class="top-btn ghost" @click="goAdd">入库</view>
      <view class="top-btn fill" @click="goShopping">去采购</view>
    </ui-action-bar>

    <!-- 搜索框（输入即搜，300ms 防抖） -->
    <view class="search-row">
      <view class="search-box">
        <text class="search-ico">⌕</text>
        <input v-model="kwInput" class="search-ipt" placeholder="搜库存" placeholder-class="ph" @input="onInput" />
        <text v-if="kwInput" class="search-clear" @click="clearKw">✕</text>
      </view>
    </view>

    <!-- 筛选条（搜索态隐藏） -->
    <view v-if="!searching" class="chips">
      <view class="chip all" :class="{ on: filter === 'all' }" @click="filter = 'all'">全部 {{ totalCount }}</view>
      <view class="chip none" :class="{ on: filter === 'none' }" @click="filter = 'none'">用完 {{ summary.none }}</view>
      <view class="chip low" :class="{ on: filter === 'low' }" @click="filter = 'low'">不足 {{ summary.low }}</view>
      <view class="chip enough" :class="{ on: filter === 'enough' }" @click="filter = 'enough'">充足 {{ summary.enough }}</view>
    </view>

    <ui-state v-if="loading && first" mode="loading" />

    <!-- 搜索态：结果平铺（服务端已按档位排序）+ 分页 -->
    <scroll-view v-else-if="searching" scroll-y class="body" @scrolltolower="loadMoreSearch">
      <view class="found">找到 {{ searchTotal }} 个</view>
      <view v-if="!searchItems.length" class="no-hit">搜不到「{{ searchingKw }}」</view>
      <view
        v-for="it in searchItems"
        :key="it.ingredientId"
        class="row"
        :style="{ borderColor: stockColor(it.level) + '26' }"
        @click="goDetail(it)"
      >
        <ui-avatar :name="it.ingredientName" :size="40" />
        <view class="info">
          <text class="name">{{ it.ingredientName || `#${it.ingredientId}` }}</text>
          <text class="sub">{{ subText(it) }}</text>
        </view>
        <text class="lvl" :style="{ color: stockColor(it.level) }">{{ stockLabel(it.level) }}</text>
        <text class="arrow">›</text>
      </view>
      <ui-load-more
        v-if="searchItems.length && searchRemain > 0"
        :remain="searchRemain"
        :loading="searchLoadingMore"
        @more="loadMoreSearch"
      />
    </scroll-view>

    <!-- 分组态：三组独立分页（组标题计数=汇总总数，不随加载变化） -->
    <scroll-view v-else class="body" scroll-y>
      <template v-for="sec in sections" :key="sec.key">
        <view class="sec-title" :style="{ color: sec.color }">{{ sec.label }} · {{ sec.total }}</view>
        <view
          v-for="it in items[sec.key]"
          :key="it.ingredientId"
          class="row"
          :style="{ borderColor: stockColor(it.level) + '26' }"
          @click="goDetail(it)"
        >
          <ui-avatar :name="it.ingredientName" :size="40" />
          <view class="info">
            <text class="name">{{ it.ingredientName || `#${it.ingredientId}` }}</text>
            <text class="sub">{{ subText(it) }}</text>
          </view>
          <text class="lvl" :style="{ color: stockColor(it.level) }">{{ stockLabel(it.level) }}</text>
          <text class="arrow">›</text>
        </view>
        <ui-load-more
          v-if="remain(sec.key) > 0"
          :remain="remain(sec.key)"
          :color="sec.color"
          :loading="loadingMore[sec.key]"
          @more="loadMore(sec.key)"
        />
      </template>
      <ui-state v-if="totalCount === 0" mode="empty" text="暂无库存" />
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { listGroupedPage, sourceLabel, sourceSub, type PantryGroupedItem } from '@/api/pantry'
import { stockColor, stockLabel } from '@/utils/token'

const kwInput = ref('')
const searchingKw = ref('')
const searching = computed(() => searchingKw.value.length > 0)

const filter = ref('all')
const summary = reactive({ enough: 0, low: 0, none: 0 })
const totalCount = computed(() => summary.enough + summary.low + summary.none)

// 三组定义（筛选态过滤显示）
const sections = computed(() => {
  const defs = [
    { key: 'NONE', tag: 'none', label: '用完', color: '#DB5A4E', total: summary.none },
    { key: 'LOW', tag: 'low', label: '不足', color: '#B8860B', total: summary.low },
    { key: 'ENOUGH', tag: 'enough', label: '充足', color: '#4FAE6E', total: summary.enough },
  ]
  return defs.filter((d) => filter.value === 'all' || filter.value === d.tag)
})

// 三组独立分页
const items = reactive<Record<string, PantryGroupedItem[]>>({ NONE: [], LOW: [], ENOUGH: [] })
const page = reactive<Record<string, number>>({ NONE: 1, LOW: 1, ENOUGH: 1 })
const loadingMore = reactive<Record<string, boolean>>({ NONE: false, LOW: false, ENOUGH: false })

// 搜索分页
const searchItems = ref<PantryGroupedItem[]>([])
const searchPage = ref(1)
const searchTotal = ref(0)
const searchLoadingMore = ref(false)
const searchRemain = computed(() => searchTotal.value - searchItems.value.length)

const loading = ref(false)
const first = ref(true)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let reloadToken = 0

onShow(() => reload())
onPullDownRefresh(async () => {
  await reload()
  uni.stopPullDownRefresh()
})

function onInput() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => reload(), 300)
}

function clearKw() {
  if (debounceTimer) clearTimeout(debounceTimer)
  kwInput.value = ''
  reload()
}

async function reload() {
  const token = ++reloadToken
  loading.value = true
  searchingKw.value = kwInput.value.trim() // 先同步搜索词再分流
  try {
    if (searching.value) {
      const r = await listGroupedPage({ keyword: searchingKw.value, pageNum: 1 })
      if (token !== reloadToken) return
      applySummary(r.summary)
      searchItems.value = r.items
      searchPage.value = 1
      searchTotal.value = r.summary.enough + r.summary.low + r.summary.none
    } else {
      const results = await Promise.all([
        listGroupedPage({ level: 'NONE', pageNum: 1 }),
        listGroupedPage({ level: 'LOW', pageNum: 1 }),
        listGroupedPage({ level: 'ENOUGH', pageNum: 1 }),
      ])
      if (token !== reloadToken) return
      applySummary(results[0].summary)
      items.NONE = results[0].items
      items.LOW = results[1].items
      items.ENOUGH = results[2].items
      page.NONE = page.LOW = page.ENOUGH = 1
    }
  } catch {
    // request 已 toast
  }
  if (token === reloadToken) {
    loading.value = false
    first.value = false
  }
}

function applySummary(s: { enough: number; low: number; none: number }) {
  summary.enough = s.enough
  summary.low = s.low
  summary.none = s.none
}

function remain(level: string): number {
  const total = level === 'NONE' ? summary.none : level === 'LOW' ? summary.low : summary.enough
  return total - (items[level]?.length ?? 0)
}

async function loadMore(level: string) {
  if (loadingMore[level]) return
  loadingMore[level] = true
  try {
    const r = await listGroupedPage({ level, pageNum: page[level] + 1 })
    page[level] += 1
    items[level].push(...r.items)
  } catch {}
  loadingMore[level] = false
}

async function loadMoreSearch() {
  if (searchLoadingMore.value || searchRemain.value <= 0) return
  searchLoadingMore.value = true
  try {
    const r = await listGroupedPage({ keyword: searchingKw.value, pageNum: searchPage.value + 1 })
    searchPage.value += 1
    searchItems.value.push(...r.items)
  } catch {}
  searchLoadingMore.value = false
}

function subText(it: PantryGroupedItem): string {
  const label = sourceLabel(it)
  if (!label) return it.level === 'NONE' ? '本来就没有' : '无变动记录'
  return `${label} ${sourceSub(it)}`.trim()
}

function goDetail(it: PantryGroupedItem) {
  uni.navigateTo({ url: `/pages/pantry/Detail?id=${it.ingredientId}` })
}
function goAdd() {
  uni.navigateTo({ url: '/pages/pantry/Add' })
}
function goShopping() {
  uni.navigateTo({ url: '/pages/shopping/List' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top-btn {
  padding: 5px 12px;
  border-radius: var(--r-sm);
  font-size: 10px;
  font-weight: 700;
}
.ghost {
  color: var(--primary);
  border: 1.5px solid var(--primary);
}
.fill {
  color: #FFFFFF;
  background: var(--primary);
}
.search-row {
  padding: 8px 14px 0;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 10px;
}
.search-ico {
  color: var(--caption);
  font-size: 14px;
}
.search-ipt {
  flex: 1;
  font-size: 12px;
  color: var(--title);
  padding: 9px 0;
}
.search-clear {
  color: var(--caption);
  font-size: 13px;
  padding: 4px;
}
.ph { color: var(--caption); }
.chips {
  display: flex;
  gap: 6px;
  padding: 10px 14px 8px;
}
.chip {
  padding: 6px 12px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  font-size: 10px;
  font-weight: 800;
}
.chip.all { color: var(--body); }
.chip.none { color: var(--error); }
.chip.low { color: var(--warning-text); }
.chip.enough { color: var(--success); }
.chip.all.on { background: var(--title); border-color: var(--title); color: #FFFFFF; }
.chip.none.on { background: var(--error); border-color: var(--error); color: #FFFFFF; }
.chip.low.on { background: var(--warning); border-color: var(--warning); color: #FFFFFF; }
.chip.enough.on { background: var(--success); border-color: var(--success); color: #FFFFFF; }
.body {
  flex: 1;
  min-height: 0;
  padding: 0 12px;
  box-sizing: border-box;
}
.found {
  font-size: 10px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 12px 2px 8px;
}
.no-hit {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 18px;
  text-align: center;
  font-size: 11px;
  color: var(--caption);
}
.sec-title {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1px;
  margin: 10px 2px 4px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 8px 12px;
  margin-bottom: 4px;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.sub {
  font-size: 9px;
  color: var(--caption);
}
.lvl {
  font-size: 11px;
  font-weight: 800;
}
.arrow {
  color: var(--caption);
  font-size: 14px;
  font-weight: 700;
}
</style>
