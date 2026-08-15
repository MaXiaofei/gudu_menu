<template>
  <view class="page">
    <!-- 选菜模式顶栏（食集「+ 加菜」进入） -->
    <template v-if="selectForMenuId">
      <view class="picker-top" :style="{ paddingTop: sb + 'px' }">
        <text class="picker-back" @click="goBack">‹</text>
      </view>
      <view class="picker-hint">选择要加入的菜</view>
    </template>
    <ui-action-bar v-else />

    <!-- 搜索框（回车触发） -->
    <view class="search-row">
      <view class="search-box">
        <text class="search-ico">⌕</text>
        <input
          v-model="kwInput"
          class="search-ipt"
          placeholder="搜菜名"
          placeholder-class="ph"
          confirm-type="search"
          :cursor="kwInput.length"
          cursor-color="#E89150"
          @confirm="onSearch"
        />
        <text v-if="kwInput" class="search-clear" @click="onClearKw">✕</text>
      </view>
    </view>

    <!-- 分类标签条（tag 字典，单选） -->
    <scroll-view v-if="tags.length" scroll-x class="chip-bar" :show-scrollbar="false">
      <view class="chip-row">
        <view
          v-for="c in [{ id: 0, name: '全部' }, ...tags]"
          :key="c.id"
          class="chip"
          :class="{ on: selectedTagId === c.id }"
          @click="onTag(c.id)"
        >{{ c.name }}</view>
      </view>
    </scroll-view>

    <!-- 菜系筛选条（cuisine 字典，可与分类叠加） -->
    <scroll-view v-if="cuisines.length" scroll-x class="chip-bar" :show-scrollbar="false">
      <view class="chip-row">
        <view
          v-for="c in [{ id: 0, name: '全部菜系' }, ...cuisines]"
          :key="c.id"
          class="chip"
          :class="{ on: selectedCuisineId === c.id }"
          @click="onCuisine(c.id)"
        >{{ c.name }}</view>
      </view>
    </scroll-view>

    <!-- 排序行 -->
    <view class="sort-row">
      <text class="total">{{ total }} 道</text>
      <view class="sort-chips">
        <view class="schip" :class="{ on: sort === 'latest' }" @click="onSort('latest')">最新</view>
        <view class="schip" :class="{ on: sort === 'cooked' }" @click="onSort('cooked')">做过最多</view>
      </view>
    </view>

    <!-- 列表 -->
    <view class="list">
      <view v-for="dish in dishes" :key="dish.id" class="swipe-wrap">
        <!-- 左滑删除底（仅浏览模式） -->
        <view v-if="!selectForMenuId" class="swipe-del" @click.stop="confirmDelete(dish)">
          <text class="swipe-del-txt">删除</text>
        </view>
        <!-- 卡片（滑动位移） -->
        <view
          class="card"
          :style="{ transform: `translateX(${offsets[dish.id] || 0}px)` }"
          @touchstart="onTs($event, dish.id)"
          @touchmove="onTm($event, dish.id)"
          @touchend="onTe($event, dish.id)"
          @click="onTapCard(dish)"
        >
          <image
            v-if="dish.coverUrl"
            class="cover"
            :src="thumbOf(dish.coverUrl)"
            mode="aspectFill"
            lazy-load
          />
          <ui-avatar v-else :name="dish.name" :size="44" />
          <view class="info">
            <text class="name">{{ dish.name }}</text>
            <text class="sub">{{ subText(dish) }}</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>

      <!-- 尾部 -->
      <view v-if="!firstLoading && dishes.length" class="footer">
        {{ hasMore ? '上拉加载更多' : '没有更多了' }}
      </view>
      <ui-state v-if="!firstLoading && !dishes.length" mode="empty" text="暂无菜品" />
      <ui-state v-if="firstLoading" mode="loading" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad, onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { searchDishes, deleteDish, type Dish } from '@/api/dish'
import { listDict, type DictItem } from '@/api/common'
import { addDishToMenu } from '@/api/menu'
import { thumbOf } from '@/utils/image'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()

// ---- 选菜模式（食集「+ 加菜」进入） ----
const selectForMenuId = ref<number | null>(null)

// ---- 搜索 / 筛选 / 排序 ----
const kwInput = ref('')
const keyword = ref('')
const tags = ref<DictItem[]>([])
const cuisines = ref<DictItem[]>([])
const selectedTagId = ref(0)
const selectedCuisineId = ref(0)
const sort = ref<'latest' | 'cooked'>('latest')

// ---- 列表分页（DESIGN.md §12.2 每页 10 条） ----
const dishes = ref<Dish[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const firstLoading = ref(true)

// 左滑位移（dishId → px）
const offsets = reactive<Record<number, number>>({})
let touchStartX = 0
let touchStartY = 0
let touching = false

onLoad((options) => {
  selectForMenuId.value = options?.selectForMenuId ? Number(options.selectForMenuId) : null
  loadDicts()
  reload()
})

// 写菜谱发布后 switchTab 无法带参 → 全局标志（storage）触发「最新」排序
onShow(() => {
  if (uni.getStorageSync('dish_sort_latest')) {
    uni.removeStorageSync('dish_sort_latest')
    if (sort.value !== 'latest') {
      sort.value = 'latest'
      reload()
    }
  }
})

async function loadDicts() {
  try {
    tags.value = await listDict('tag')
  } catch {}
  try {
    cuisines.value = await listDict('cuisine')
  } catch {}
}

async function fetch(p: number): Promise<Dish[]> {
  try {
    const r = await searchDishes({
      keyword: keyword.value || undefined,
      tagIds: selectedTagId.value ? String(selectedTagId.value) : undefined,
      cuisineIds: selectedCuisineId.value ? String(selectedCuisineId.value) : undefined,
      sort: sort.value,
      pageNum: p,
    })
    total.value = r.total
    hasMore.value = r.records.length >= 10
    return r.records
  } catch {
    hasMore.value = false
    return []
  }
}

async function reload() {
  page.value = 1
  hasMore.value = true
  firstLoading.value = true
  dishes.value = await fetch(1)
  firstLoading.value = false
  Object.keys(offsets).forEach((k) => delete offsets[Number(k)])
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  const list = await fetch(page.value + 1)
  page.value += 1
  dishes.value.push(...list)
  loading.value = false
}

onPullDownRefresh(async () => {
  await reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => loadMore())

// ---- 交互 ----
function onSearch() {
  keyword.value = kwInput.value.trim()
  reload()
}
function onClearKw() {
  kwInput.value = ''
  keyword.value = ''
  reload()
}
function onTag(id: number) {
  if (selectedTagId.value === id) return
  selectedTagId.value = id
  reload()
}
function onCuisine(id: number) {
  if (selectedCuisineId.value === id) return
  selectedCuisineId.value = id
  reload()
}
function onSort(s: 'latest' | 'cooked') {
  if (sort.value === s) return
  sort.value = s
  reload()
}

function goBack() {
  uni.navigateBack()
}

function subText(d: Dish): string {
  const cooked = d.cookedCount ?? 0
  if (cooked > 0) return `做过 ${cooked} 次`
  const mins = (d.prepTime ?? 0) + (d.cookTime ?? 0)
  return mins > 0 ? `没做过 · ${mins} 分` : '没做过'
}

// 点卡片：选菜模式 → 加菜返回；浏览 → 详情（左滑态先复位）
function onTapCard(d: Dish) {
  if ((offsets[d.id] || 0) !== 0) {
    offsets[d.id] = 0
    return
  }
  if (selectForMenuId.value) {
    addDish(selectForMenuId.value, d)
    return
  }
  uni.navigateTo({ url: `/pages/dish/Detail?id=${d.id}` })
}

async function addDish(menuId: number, d: Dish) {
  try {
    await addDishToMenu(menuId, d.id, d.name)
    uni.showToast({ title: '已加入食集', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    uni.showToast({ title: '加入失败', icon: 'none' })
  }
}

function confirmDelete(d: Dish) {
  uni.showModal({
    title: '删除菜谱',
    content: `确定删除「${d.name}」吗？删除后不可恢复`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) {
        offsets[d.id] = 0
        return
      }
      try {
        await deleteDish(d.id)
        dishes.value = dishes.value.filter((x) => x.id !== d.id)
        total.value = Math.max(0, total.value - 1)
        uni.showToast({ title: `已删除「${d.name}」`, icon: 'none' })
      } catch {
        offsets[d.id] = 0
        uni.showToast({ title: '删除失败，请稍后重试', icon: 'none' })
      }
    },
  })
}

// ---- 左滑手势 ----
function onTs(e: TouchEvent, id: number) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touching = true
}
function onTm(e: TouchEvent, id: number) {
  if (!touching) return
  const dx = e.touches[0].clientX - touchStartX
  const dy = Math.abs(e.touches[0].clientY - touchStartY)
  if (dy > 12) return // 纵向滚动让路
  const base = offsets[id] || 0
  offsets[id] = Math.min(0, Math.max(-72, base + dx))
}
function onTe(_e: TouchEvent, id: number) {
  touching = false
  const cur = offsets[id] || 0
  offsets[id] = cur < -36 ? -72 : 0
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
/* 选菜模式顶栏 */
.picker-top {
  padding: 0 6px;
}
.picker-back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  padding: 4px 10px;
  line-height: 1.2;
}
.picker-hint {
  margin: 0 14px 4px;
  padding: 8px 12px;
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
  border-radius: var(--r-sm);
  color: var(--title);
  font-size: 13px;
}
/* 搜索 */
.search-row {
  padding: 4px 14px 8px;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1.5px solid var(--primary);
  border-radius: var(--r-md);
  padding: 0 12px;
}
.search-ico {
  color: var(--caption);
  font-size: 15px;
}
.search-ipt {
  flex: 1;
  font-size: 12px;
  color: var(--title);
  padding: 9px 0;
}
.ph {
  color: var(--caption);
}
.search-clear {
  color: var(--caption);
  font-size: 13px;
  padding: 4px;
}
/* 标签条 */
.chip-bar {
  white-space: nowrap;
}
.chip-row {
  display: inline-flex;
  gap: 6px;
  padding: 0 14px 6px;
}
.chip {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 11px;
}
.chip.on {
  background: var(--title);
  border-color: var(--title);
  color: #FFFFFF;
}
/* 排序行 */
.sort-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 16px 8px;
}
.total {
  font-size: 12px;
  color: var(--caption);
}
.sort-chips {
  display: flex;
  gap: 6px;
}
.schip {
  padding: 4px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 11px;
}
.schip.on {
  background: var(--title);
  border-color: var(--title);
  color: #FFFFFF;
  font-weight: 700;
}
/* 列表 */
.list {
  padding: 0 12px 16px;
}
.swipe-wrap {
  position: relative;
  margin-bottom: 7px;
  border-radius: var(--r-md);
  overflow: hidden;
}
.swipe-del {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 72px;
  background: #E53935;
  display: flex;
  align-items: center;
  justify-content: center;
}
.swipe-del-txt {
  color: #FFFFFF;
  font-size: 13px;
  font-weight: 700;
}
.card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px;
  transition: transform 0.15s ease-out;
}
.cover {
  width: 44px;
  height: 44px;
  border-radius: var(--r-md);
  flex-shrink: 0;
  background: var(--secondary);
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.name {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub {
  font-size: 10px;
  color: var(--caption);
}
.arrow {
  color: var(--caption);
  font-size: 16px;
  font-weight: 700;
}
.footer {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
</style>
