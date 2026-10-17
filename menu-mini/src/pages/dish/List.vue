<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <!-- 选菜模式顶栏（食集「+ 加菜」进入） -->
    <template v-if="selectForMenuId">
      <view class="picker-top" :style="{ paddingTop: sb + 'px' }">
        <text class="picker-back" @click="goBack">‹</text>
      </view>
      <view class="picker-hint">选择要加入的菜</view>
    </template>
    <ui-tab-header v-else title="菜谱">
      <view class="fam" @click="goMembers">
        <ui-avatar-stack :items="memberFaces" :size="22" :max="3" />
        <text class="fam-txt">我家的厨房</text>
      </view>
    </ui-tab-header>

    <ui-offline-bar />
    <view class="search-row">
      <view class="search-box">
        <text class="search-ico">⌕</text>
        <input
          v-model="kwInput"
          class="search-ipt"
          placeholder="搜菜名、食材"
          placeholder-class="ph"
          confirm-type="search"
          :cursor="kwInput.length"
          @confirm="onSearch"
        />
        <text v-if="kwInput" class="search-clear" @click="onClearKw">✕</text>
      </view>
    </view>

    <!-- 排序（家里够的优先 / 做得最多 / 最新） -->
    <scroll-view scroll-x class="chip-bar" :show-scrollbar="false">
      <view class="chip-row">
        <view class="chip" :class="{ on: sort === 'coverage' }" data-testid="sort-coverage" @click="onSort('coverage')">家里够的优先</view>
        <view class="chip" :class="{ on: sort === 'cooked' }" @click="onSort('cooked')">做得最多</view>
        <view class="chip" :class="{ on: sort === 'latest' }" @click="onSort('latest')">最新</view>
        <view class="chip-sep" />
        <view
          v-for="c in [{ id: 0, name: '全部' }, ...tags]"
          :key="'t' + c.id"
          class="chip lite"
          :class="{ on: selectedTagId === c.id }"
          @click="onTag(c.id)"
        >{{ c.name }}</view>
        <view
          v-for="c in cuisines"
          :key="'c' + c.id"
          class="chip lite"
          :class="{ on: selectedCuisineId === c.id }"
          @click="onCuisine(c.id)"
        >{{ c.name }}</view>
      </view>
    </scroll-view>

    <view class="list">
      <!-- 首推大图卡（第一道有封面的菜） -->
      <dish-hero v-if="hero && !selectForMenuId" :dish="hero" :sub="heroSub(hero)" @tap="onTapCard(hero)" />

      <view v-for="dish in rest" :key="dish.id" class="swipe-wrap">
        <view
          v-if="!selectForMenuId"
          class="swipe-del"
          :class="{ on: (offsets[dish.id] || 0) < 0 }"
          @click.stop="confirmDelete(dish)"
        >
          <text class="swipe-del-txt">删除</text>
        </view>
        <view
          class="slide"
          :style="{ transform: `translateX(${offsets[dish.id] || 0}px)` }"
          @touchstart="onTs($event, dish.id)"
          @touchmove="onTm($event, dish.id)"
          @touchend="onTe($event, dish.id)"
        >
          <ui-dish-row
            :name="dish.name"
            :cover="dish.coverUrl"
            :right="dish.cookedCount ? `做过 ${dish.cookedCount} 次` : ''"
            :coverage-label="coverageLabel(dish)"
            :coverage-tone="coverageLevel(dish.coverage)"
            :chips="chipsOf(dish)"
            :sub="rowSub(dish)"
            @tap="onTapCard(dish)"
          />
        </view>
      </view>

      <view v-if="!firstLoading && dishes.length" class="footer">
        {{ hasMore ? '上拉加载更多' : '没有更多了' }}
      </view>
      <ui-state
        v-if="!firstLoading && !dishes.length"
        mode="empty"
        :text="keyword ? '没找到这道菜' : '还没有菜谱'"
        :hint="keyword ? '换个说法试试，比如食材名' : '写下家里常做的菜，智荐才知道你家口味'"
        :action-text="keyword ? '' : '写菜谱'"
        @action="goCreate"
      />
      <ui-state v-if="firstLoading" mode="loading" />
    </view>

    <view v-if="!selectForMenuId" class="fab" data-testid="fab-create" @click="goCreate">
      <text class="fab-plus">＋</text><text class="fab-txt">写菜谱</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { ref, computed } from 'vue'
import { useSwipe } from '@/composables/useSwipe'
import DishHero from './parts/DishHero.vue'
import { searchDishes, deleteDish, coverageText, coverageLevel, type Dish, type DishSort } from '@/api/dish'
import { listMembers } from '@/api/member'
import { relativeDate } from '@/utils/datetime'
import { listDict, type DictItem } from '@/api/common'
import { addDishToMenu } from '@/api/menu'
import { navInset } from '@/utils/token'

const sb = navInset()

// ---- 选菜模式（食集「+ 加菜」进入） ----
const selectForMenuId = ref<number | null>(null)

// ---- 搜索 / 筛选 / 排序 ----
const kwInput = ref('')
const keyword = ref('')
const tags = ref<DictItem[]>([])
const cuisines = ref<DictItem[]>([])
const selectedTagId = ref(0)
const selectedCuisineId = ref(0)
const sort = ref<DishSort>('coverage')

// ---- 列表分页（DESIGN.md §12.2 每页 10 条） ----
const dishes = ref<Dish[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const firstLoading = ref(true)

// 左滑删除手势
const { offsets, onTs, onTm, onTe, reset: resetSwipe } = useSwipe()

const memberFaces = ref<{ name: string; tags?: string[] | null }[]>([])

onLoad((options) => {
  selectForMenuId.value = options?.selectForMenuId ? Number(options.selectForMenuId) : null
  loadDicts()
  reload()
  listMembers()
    .then((ms) => (memberFaces.value = ms.map((m) => ({ name: m.name, tags: m.audienceTags }))))
    .catch(() => {})
})

// 首推：首页（无关键词）第一道有封面的菜；其余进列表
const hero = computed(() => (keyword.value || selectedTagId.value || selectedCuisineId.value ? null : dishes.value.find((d) => d.coverUrl) || null))
const rest = computed(() => (hero.value ? dishes.value.filter((d) => d.id !== hero.value!.id) : dishes.value))

function coverageLabel(d: Dish): string {
  const t = coverageText(d.coverage)
  const miss = d.coverage?.missing || []
  return t && miss.length && miss.length <= 2 ? `${t} · 缺${miss.map((m) => m.name).join('、')}` : t
}
function minutesOf(d: Dish): number {
  return (d.prepTime ?? 0) + (d.cookTime ?? 0)
}
function chipsOf(d: Dish): string[] {
  const out: string[] = []
  if (minutesOf(d)) out.push(`${minutesOf(d)} 分钟`)
  const tag = (d.tagNames ?? [])[0] || (d.categoryNames ?? [])[0]
  if (tag) out.push(tag)
  return out
}
function rowSub(d: Dish): string {
  if (d.lastCookedAt) {
    const by = d.lastCookedBy?.name ? ` · ${d.lastCookedBy.name}做的` : ''
    return `上次 ${relativeDate(d.lastCookedAt)}${by}`
  }
  return subText(d)
}
function heroSub(d: Dish): string {
  return [minutesOf(d) ? `${minutesOf(d)} 分钟` : '', d.cookedCount ? `做过 ${d.cookedCount} 次` : '', d.lastCookedAt ? `上次 ${relativeDate(d.lastCookedAt)}` : '']
    .filter(Boolean)
    .join(' · ')
}
function goCreate() {
  uni.navigateTo({ url: '/pages/dish/Create' })
}
function goMembers() {
  uni.navigateTo({ url: '/pages/member/Index' })
}

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
  resetSwipe()
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
function onSort(s: DishSort) {
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
    confirmColor: '#DB5A4E', // showModal 只收色值字面量
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

</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 96px;
}
.picker-top {
  padding: 0 6px;
}
.picker-back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  padding: 4px 10px;
  line-height: 1.2;
}
.picker-hint {
  margin: 0 var(--space-lg) var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
  border-radius: var(--r-sm);
  color: var(--title);
  font-size: var(--fs-md);
}
.fam {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 6px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
}
.fam-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.search-row {
  padding: var(--space-xs) var(--space-lg) var(--space-sm);
}
.search-box {
  height: 44px;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 var(--space-md);
}
.search-ico {
  color: var(--body);
  font-size: var(--fs-lg);
}
.search-ipt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--caption);
}
.search-clear {
  color: var(--body);
  font-size: var(--fs-md);
  padding: 4px;
}
.chip-bar {
  white-space: nowrap;
}
.chip-row {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 var(--space-lg) var(--space-md);
}
.chip {
  flex-shrink: 0;
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-sm);
}
.chip.on {
  background: var(--title);
  border-color: var(--title);
  color: var(--on-primary);
  font-weight: 700;
}
.chip.lite.on {
  background: var(--primary-soft);
  border-color: var(--primary-soft);
  color: var(--title);
}
.chip-sep {
  width: 1px;
  height: 16px;
  background: var(--border);
  flex-shrink: 0;
}
.list {
  padding: 0 var(--space-lg) var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.swipe-wrap {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.swipe-del {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 72px;
  background: var(--error);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s ease-out;
}
.swipe-del.on {
  opacity: 1;
}
.swipe-del-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
.slide {
  position: relative;
  transition: transform 0.15s ease-out;
}
.footer {
  padding: var(--space-lg) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--body);
}
.fab {
  position: fixed;
  right: var(--space-lg);
  bottom: calc(var(--window-bottom, 0px) + var(--space-lg) + env(safe-area-inset-bottom));
  height: 48px;
  padding: 0 18px;
  border-radius: var(--radius-pill);
  background: var(--title);
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 6px 18px rgba(var(--sh), 0.3);
  z-index: 10;
}
.fab-plus,
.fab-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
</style>
