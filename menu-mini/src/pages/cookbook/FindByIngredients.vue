<template>
  <view class="find">
    <!-- 顶栏：‹ 按食材找 重置（对齐原型 cookbook-search 右屏） -->
    <view class="topbar">
      <text class="back" @click="onBack">‹</text>
      <text class="topbar-title">按食材找</text>
      <text class="topbar-action" @click="onReset">重置</text>
    </view>

    <!-- 标题区 -->
    <view class="head">
      <text class="head-title">勾选你家有的食材</text>
      <text class="head-sub">从我家余量自动带入，可加减</text>
    </view>

    <!-- 来源切换（库存 / 全部食材） -->
    <view class="src-row">
      <view :class="['src-tab', src === 'pantry' && 'on']" @click="switchSrc('pantry')">从库存</view>
      <view :class="['src-tab', src === 'all' && 'on']" @click="switchSrc('all')">全部食材</view>
    </view>

    <!-- 食材勾选 chips -->
    <view v-if="loadingSrc" class="empty">加载食材中…</view>
    <view v-else-if="!filteredList.length" class="empty">没有食材</view>
    <view v-else class="chips">
      <view
        v-for="ing in showList"
        :key="ing.id"
        :class="['chip', selected.has(ing.id) && 'on']"
        @click="toggle(ing.id)"
      >
        {{ ing.name }}<text v-if="selected.has(ing.id)"> ✓</text>
      </view>
      <view v-if="!showAllChips && filteredList.length > 12" class="chip more" @click="showAllChips = true">+ 更多</view>
    </view>

    <!-- 找菜按钮 -->
    <view class="find-btn-row">
      <button class="find-btn" :class="{ off: !selected.size }" @click="search">
        找菜{{ selected.size ? `（已选 ${selected.size}）` : '' }}
      </button>
    </view>

    <!-- 结果 -->
    <block v-if="searched">
      <view class="sort-row">
        <text class="sort-label">按「家里够几样」排序</text>
        <text class="sort-pick">缺的在上 ▾</text>
      </view>

      <view v-if="!matches.length" class="empty">没找到能做的菜，换个组合试试</view>
      <view v-else class="match-list">
        <view
          v-for="m in sortedMatches"
          :key="m.dish.id"
          :class="['match-card', selectedDishes.has(m.dish.id) && 'sel']"
          @click="toggleDish(m.dish.id, $event)"
        >
          <view class="match-head">
            <text class="match-emoji">{{ initial(m.dish.name) }}</text>
            <text class="match-name">{{ m.dish.name }}</text>
            <text :class="['match-pill', pillClass(m)]">{{ pillText(m) }}</text>
          </view>
          <text :class="['match-note', noteClass(m)]">{{ noteText(m) }}</text>
        </view>
      </view>
    </block>

    <!-- 底部固定：多选一起加食集 -->
    <view v-if="searched && matches.length" class="bottom-bar">
      <button class="add-menu-btn" :class="{ off: !selectedDishes.size }" @click="onAddMenu">
        把这 {{ selectedDishes.size }} 道一起加食集
      </button>
      <text class="bottom-hint">多选模式：一次加几道到目标食集</text>
    </view>

    <view style="height: 280rpx;"></view>
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
const loadingSrc = ref(false)
const searched = ref(false)
const showAllChips = ref(false)

const pool = ref<{ id: number; name: string }[]>([])
const selected = ref<Set<number>>(new Set())
const matches = ref<DishMatch[]>([])
const selectedDishes = ref<Set<number>>(new Set())

const filteredList = computed(() => pool.value)
const showList = computed(() => (showAllChips.value ? filteredList.value : filteredList.value.slice(0, 12)))

/** 缺的在上：缺失数降序，全够的垫底 */
const sortedMatches = computed(() => {
  return [...matches.value].sort((a, b) => {
    const ma = a.totalCount - a.matchCount
    const mb = b.totalCount - b.matchCount
    return mb - ma
  })
})

onShow(() => { loadSource() })

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
    // 默认全选库存/全部（让用户快速减法）
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
  showAllChips.value = false
  loadSource()
}

function toggle(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function toggleDish(id: number, e: any) {
  // 长按（app.h5 鼠标右键/ touch 长按）进详情；普通点击切换选中
  if (e && (e.type === 'longpress' || e.type === 'contextmenu')) {
    uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
    return
  }
  const s = new Set(selectedDishes.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedDishes.value = s
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
    selectedDishes.value = new Set()
    if (!matches.value.length) uni.showToast({ title: '没找到能做的菜', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function onReset() {
  selected.value = new Set()
  searched.value = false
  matches.value = []
  selectedDishes.value = new Set()
}

function onBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/Index' }) })
}

function onAddMenu() {
  if (!selectedDishes.value.size) {
    uni.showToast({ title: '先勾选要加的菜', icon: 'none' })
    return
  }
  // 加食集：跳食集页选目标（批量加食集接口待后端落地后改为直传 dishIds）
  uni.switchTab({ url: '/pages/menu/Home' })
  uni.showToast({ title: `选中 ${selectedDishes.value.size} 道，请选目标食集`, icon: 'none' })
}

/** 无封面图时的占位首字（DESIGN.md §10.4：不用食物 emoji 顶替图片）。 */
function initial(name?: string): string {
  if (!name) return '菜'
  return name.trim().charAt(0) || '菜'
}

function pillClass(m: DishMatch): string {
  if (m.canMake) return 'pill-ok'
  const miss = m.totalCount - m.matchCount
  return miss >= 3 ? 'pill-red' : 'pill-yellow'
}
function pillText(m: DishMatch): string {
  if (m.canMake) return `够 ${m.matchCount}/${m.totalCount}`
  return `差 ${m.totalCount - m.matchCount}`
}
function noteClass(m: DishMatch): string {
  if (m.canMake) return 'note-ok'
  const miss = m.totalCount - m.matchCount
  return miss >= 3 ? 'note-red' : 'note-yellow'
}
function noteText(m: DishMatch): string {
  if (m.canMake) return '家里全够，不用买'
  return `缺：${m.missingIngredients.join(' / ')}`
}
</script>

<style scoped>
.find {
  min-height: 100vh;
  background: #FDFAF4;
  padding: 0 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
}

/* 顶栏 */
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 32rpx) 8rpx 12rpx;
}
.back { font-size: 40rpx; color: #6E5C49; width: 48rpx; line-height: 1; }
.topbar-title { font-size: 28rpx; color: #9C8C7A; }
.topbar-action { font-size: 24rpx; color: #D17A3C; font-weight: 800; }

/* 标题区 */
.head { padding: 16rpx 8rpx 8rpx; }
.head-title { display: block; font-size: 30rpx; font-weight: 800; color: #4A382A; }
.head-sub { display: block; font-size: 22rpx; color: #9C8C7A; margin-top: 4rpx; }

/* 来源切换 */
.src-row { display: flex; gap: 12rpx; padding: 8rpx 8rpx 12rpx; }
.src-tab {
  font-size: 22rpx; padding: 8rpx 22rpx; border-radius: 28rpx;
  border: 2rpx solid #F0E6D6; background: #fff; color: #6E5C49;
}
.src-tab.on { background: #E89150; color: #fff; border-color: #E89150; font-weight: 700; }

/* 食材勾选 */
.chips { display: flex; flex-wrap: wrap; gap: 12rpx; padding: 0 8rpx 12rpx; }
.chip {
  font-size: 22rpx; font-weight: 700;
  padding: 10rpx 20rpx; border-radius: 16rpx;
  background: #fff; border: 2rpx solid #F0E6D6; color: #6E5C49;
}
.chip.on { background: #E89150; color: #fff; border-color: #E89150; }
.chip.more { color: #9C8C7A; font-weight: 600; border-style: dashed; }

/* 找菜按钮 */
.find-btn-row { padding: 4rpx 8rpx 12rpx; }
.find-btn {
  width: 100%; height: 80rpx; line-height: 80rpx;
  background: #E89150; color: #fff; font-size: 28rpx; font-weight: 800;
  border-radius: 20rpx; border: none;
}
.find-btn.off { background: #F0E6D6; color: #9C8C7A; }

/* 排序行 */
.sort-row {
  display: flex; justify-content: space-between;
  padding: 16rpx 8rpx 12rpx; font-size: 22rpx;
}
.sort-label { color: #9C8C7A; }
.sort-pick { color: #D17A3C; font-weight: 800; }

/* 匹配结果 */
.match-list { padding: 0 8rpx; }
.match-card {
  background: #fff; border: 1px solid #F0E6D6; border-radius: 24rpx;
  padding: 20rpx; margin-bottom: 14rpx;
}
.match-card.sel { border-color: #E89150; background: #FFF7EC; }
.match-head { display: flex; align-items: center; gap: 16rpx; }
.match-emoji {
  width: 56rpx; height: 56rpx;
  border-radius: 14rpx;
  background: #FBF0DD;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  font-size: 28rpx; font-weight: 600;
  color: rgba(74, 56, 42, 0.45);
}
.match-name { flex: 1; font-size: 28rpx; font-weight: 800; color: #4A382A; }
.match-pill {
  font-size: 20rpx; font-weight: 800;
  padding: 4rpx 16rpx; border-radius: 99rpx; color: #fff;
}
.pill-ok { background: #4FAE6E; }
.pill-yellow { background: #E5A938; }
.pill-red { background: #DB5A4E; }

.match-note { display: block; font-size: 20rpx; margin-top: 8rpx; padding-left: 52rpx; }
.note-ok { color: #4FAE6E; }
.note-yellow { color: #B8762E; }
.note-red { color: #B8382B; }

/* 底部固定 */
.bottom-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: #fff; border-top: 1px solid #F0E6D6;
  padding: 18rpx 28rpx calc(env(safe-area-inset-bottom) + 18rpx);
  z-index: 50;
}
.add-menu-btn {
  width: 100%; height: 88rpx; line-height: 88rpx;
  background: #E89150; color: #fff; font-size: 28rpx; font-weight: 800;
  border-radius: 24rpx; border: none;
}
.add-menu-btn.off { background: #F0E6D6; color: #9C8C7A; }
.bottom-hint { display: block; text-align: center; font-size: 20rpx; color: #9C8C7A; margin-top: 8rpx; }

.empty { text-align: center; color: #9C8C7A; padding: 80rpx 0; font-size: 26rpx; }

button::after { border: none; }
</style>
