<template>
  <view class="page">
    <!-- 顶栏（自定义） -->
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="top-title">食集</text>
      <view class="back"></view>
    </view>

    <view class="page-body" v-if="detail">
      <!-- Tab 栏：菜 / 备菜 / 采购 / 一起吃 -->
      <view class="tabbar">
        <view
          v-for="(t, i) in tabs"
          :key="i"
          :class="['tab', { active: tabIndex === i }]"
          @click="tabIndex = i"
        >
          <text>{{ t }}</text>
        </view>
      </view>

      <scroll-view scroll-y class="scroll">
        <!-- Tab 0：包含菜品 -->
        <template v-if="tabIndex === 0">
          <view class="title-card">
            <view class="title-row">
              <text class="title">{{ detail.menu.name }}</text>
              <text :class="['status-chip', detail.menu.status === 'DONE' ? 'done' : 'active']">
                {{ detail.menu.status === 'DONE' ? '✓ 已完成' : '进行中' }}
              </text>
            </view>
            <view class="meta-row">
              <text class="meta-item"><u-icon name="account" :size="22" color="#9C8C7A" /> {{ detail.menu.servingCount || 1 }} 人份</text>
              <text class="meta-dot">·</text>
              <text class="meta-item">包含 {{ detail.dishes.length }} 道菜</text>
            </view>
          </view>

          <view class="block">
            <view class="block-title">
              <view class="tbar"></view>
              <text>包含菜品</text>
            </view>
            <view class="yh-card dish-card">
              <view class="dish-row" v-for="(d, i) in detail.dishes" :key="i">
                <text class="dish-name">{{ d.dishName || `菜 #${d.dishId}` }}</text>
                <view class="dish-right">
                  <text class="dish-amount">× {{ fmtFactor(d.servingFactor) }}</text>
                  <text class="dish-unit">份</text>
                </view>
              </view>
              <view v-if="detail.dishes.length === 0" class="dish-row">
                <text class="dish-name" style="color: #9C8C7A;">本食集暂无菜品</text>
              </view>
            </view>
          </view>
        </template>

        <!-- Tab 1：备菜（Plan C） -->
        <template v-else-if="tabIndex === 1">
          <view v-if="prepLoading" class="prep-loading">加载备菜中…</view>
          <template v-else-if="prep">
            <!-- 进度条 -->
            <view class="prep-progress">
              <view class="progress-head">
                <text class="progress-label">备料进度</text>
                <text class="progress-count">已备 {{ prep.readyCount }} / 共 {{ prep.totalCount }} 样</text>
              </view>
              <view class="progress-track">
                <view class="progress-fill" :style="{ width: progressPct + '%' }"></view>
              </view>
            </view>

            <!-- 备料清单 -->
            <view class="block">
              <view class="block-title">
                <view class="tbar"></view>
                <text>备料清单</text>
              </view>
              <view class="yh-card prep-card">
                <view
                  v-for="it in prep.items"
                  :key="it.ingredientId"
                  :class="['prep-row', { shared: it.shared }]"
                  @click="!isDone && togglePrep(it)"
                  @longpress="!isDone && longPressPrep(it)"
                >
                  <text :class="['prep-chip', chipClass(it.status)]">{{ statusLabel(it.status) }}</text>
                  <view class="prep-main">
                    <view class="prep-name-row">
                      <text class="prep-name">{{ it.ingredientName }}</text>
                      <text v-if="it.shared" class="prep-shared">共用</text>
                    </view>
                    <text v-if="it.dishNames.length" class="prep-sub">{{ prepSub(it) }}</text>
                  </view>
                  <text class="prep-grams">{{ Math.round(it.totalGrams) }}g</text>
                </view>
                <view v-if="prep.items.length === 0" class="prep-row">
                  <text class="prep-name" style="color: #9C8C7A;">本食集暂无备料</text>
                </view>
              </view>
            </view>

            <!-- 调料折叠（与菜分组；计入总数/进度，文案不写「无需备料」） -->
            <view v-if="prep.condiments.length" class="condiment-header" @click="condimentExpanded = !condimentExpanded">
              <u-icon name="tags" :size="22" color="#6E5C49" />
              <text class="condiment-title">调料 {{ prep.condiments.length }} 样</text>
              <text class="condiment-arrow">{{ condimentExpanded ? '▾' : '▸' }}</text>
            </view>
            <view v-if="condimentExpanded" class="yh-card prep-card condiment-card">
            <view
              v-for="it in prep.condiments"
              :key="it.ingredientId"
              :class="['prep-row', { shared: it.shared }]"
              @click="!isDone && togglePrep(it)"
              @longpress="!isDone && longPressPrep(it)"
            >
                <text :class="['prep-chip', chipClass(it.status)]">{{ statusLabel(it.status) }}</text>
                <view class="prep-main">
                  <view class="prep-name-row">
                    <text class="prep-name">{{ it.ingredientName }}</text>
                    <text v-if="it.shared" class="prep-shared">共用</text>
                  </view>
                  <text v-if="it.dishNames.length" class="prep-sub">{{ prepSub(it) }}</text>
                </view>
                <text class="prep-grams">{{ Math.round(it.totalGrams) }}g</text>
              </view>
            </view>
          </template>
          <view v-else class="prep-loading">加载备菜失败</view>
        </template>

        <!-- Tab 2：采购（Plan E） -->
        <template v-else-if="tabIndex === 2">
          <view v-if="shopLoading" class="prep-loading">加载采购中…</view>
          <view v-else-if="!shopVO" class="shop-empty">
            <text class="shop-empty-title">尚未生成采购清单</text>
            <button class="shop-gen-btn" :disabled="isDone" @click="generateShop">按食集生成</button>
          </view>
          <view v-else class="yh-card shop-card">
            <view
              v-for="it in shopVO.items"
              :key="it.id"
              :class="['shop-row', { bought: effShop(it) === 1 }]"
              @click="!isDone && toggleShop(it)"
            >
              <text :class="['shop-check', effShop(it) === 1 ? 'on' : 'off']">
                {{ effShop(it) === 1 ? '✓' : '○' }}
              </text>
              <view class="shop-main">
                <text class="shop-name">{{ shopItemName(it) }}</text>
                <text v-if="shopItemAmount(it)" class="shop-amt">{{ shopItemAmount(it) }}</text>
              </view>
              <text
                v-if="it.ingredientId != null && it.stockStatus"
                :class="['shop-stock', stockClass(it.stockStatus)]"
              >{{ stockLabel(it.stockStatus, it.shortageGrams ?? 0) }}</text>
            </view>
            <view v-if="shopVO.items.length === 0" class="shop-row">
              <text class="shop-name" style="color: #9C8C7A;">本食集暂无采购项</text>
            </view>
          </view>
        </template>

        <!-- Tab 3：一起吃（占位） -->
        <view v-else class="placeholder">
          <text class="placeholder-title">一起吃</text>
          <text class="placeholder-desc">协同点菜 · 即将上线</text>
        </view>

        <view style="height: 160rpx;"></view>
      </scroll-view>
    </view>

    <view v-else class="loading">加载中…</view>

    <!-- 底部整集做菜（Plan A）；完成态加「去评价」 -->
    <view class="bottom-actions" v-if="detail">
      <button
        class="cook-menu-btn"
        :disabled="cooking || detail.menu.status === 'DONE'"
        @click="onCookMenu"
      >
        {{ cooking ? '做菜中…' : (detail.menu.status === 'DONE' ? '已完成' : '整集做菜') }}
      </button>
      <button v-if="detail.menu.status === 'DONE'" class="review-btn" @click="goReview">
        去评价
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMenuDetail, type MenuDetailVO } from '@/api/menu'
import { cookMenu } from '@/api/dish'
import { getMenuPrep, updatePrepStatus, type MenuPrepVO, type PrepItem, type PrepStatus } from '@/api/prep'
import { getMenuShopping, generate as generateShopping, togglePurchased, type ShoppingListVO, type ShoppingItemVO } from '@/api/shopping'

const tabs = ['菜', '备菜', '采购', '一起吃']
const tabIndex = ref(0)

const detail = ref<MenuDetailVO | null>(null)
const menuId = ref(0)
const cooking = ref(false)

/** 已完成食集：整集做后各 tab 只读（备菜/采购交互禁用）。 */
const isDone = computed(() => detail.value?.menu.status === 'DONE')

// 备菜（Plan C）：惰性加载，切到备菜 Tab 才拉
const prep = ref<MenuPrepVO | null>(null)
const prepLoading = ref(false)
const condimentExpanded = ref(false)

// 采购（Plan E）：惰性加载，切到采购 Tab 才拉
const shopVO = ref<ShoppingListVO | null>(null)
const shopLoading = ref(false)
const purchOverride = ref<Record<number, number>>({}) // 乐观：itemId → purchased(0/1)

const progressPct = computed(() => {
  if (!prep.value || prep.value.totalCount === 0) return 0
  return Math.round((prep.value.readyCount / prep.value.totalCount) * 100)
})

watch(tabIndex, async (i) => {
  // 切到备菜 Tab：首次惰性加载（加载过就不重复，切回保留状态）
  if (i === 1 && !prep.value && !prepLoading.value) await loadPrep()
  // 切到采购 Tab：首次惰性加载（Plan E）
  if (i === 2 && !shopVO.value && !shopLoading.value) await loadShop()
})

onLoad(async (q: any) => {
  menuId.value = Number(q.id)
  try {
    detail.value = await getMenuDetail(menuId.value)
  } catch {
    uni.showToast({ title: '加载详情失败', icon: 'none' })
  }
})

function fmtFactor(v?: number): string {
  if (v == null) return '1.0'
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

async function loadPrep() {
  prepLoading.value = true
  try {
    prep.value = await getMenuPrep(menuId.value)
  } catch {
    uni.showToast({ title: '加载备菜失败', icon: 'none' })
  } finally {
    prepLoading.value = false
  }
}

/** 状态中文标签。 */
function statusLabel(s: PrepStatus): string {
  return { PENDING: '待备', READY: '已备', THAWING: '化冻中', MARINATING: '腌制中' }[s]
}

/** 状态 chip 配色类（READY 绿 / THAWING 蓝 / MARINATING 琥珀 / PENDING 白底灰边）。 */
function chipClass(s: PrepStatus): string {
  return { PENDING: 'chip-pending', READY: 'chip-ready', THAWING: 'chip-thawing', MARINATING: 'chip-marinating' }[s]
}

/** 备菜行副标题（共用项列菜名，单菜项也显示来源）。 */
function prepSub(it: PrepItem): string {
  return it.dishNames.length >= 2
    ? `${it.dishCount} 道菜共用 · ${it.dishNames.join('、')}`
    : it.dishNames[0]
}

/** 点 chip：PENDING ↔ READY（乐观更新 + 失败回滚）。 */
async function togglePrep(it: PrepItem) {
  const next: PrepStatus = it.status === 'READY' ? 'PENDING' : 'READY'
  await updatePrep(it, next)
}

/** 长按：弹「化冻 / 腌制 / 重置」三选一。 */
function longPressPrep(it: PrepItem) {
  uni.showActionSheet({
    itemList: ['化冻中', '腌制中', '重置为待备'],
    success: async (res) => {
      const map: PrepStatus[] = ['THAWING', 'MARINATING', 'PENDING']
      const next = map[res.tapIndex]
      if (next === it.status) return
      await updatePrep(it, next)
    },
  })
}

async function updatePrep(it: PrepItem, next: PrepStatus) {
  const prev = it.status
  it.status = next
  recomputeReady()
  try {
    await updatePrepStatus(menuId.value, it.ingredientId, next)
  } catch {
    it.status = prev
    recomputeReady()
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

/** 重算进度（主料 + 调料全量统计，调料计入）。 */
function recomputeReady() {
  if (!prep.value) return
  prep.value.readyCount = prep.value.items
    .concat(prep.value.condiments)
    .filter((x) => x.status === 'READY').length
}

// ===== 采购（Plan E） =====

/** 实际 purchased（乐观 override 优先）。 */
function effShop(it: ShoppingItemVO): number {
  return purchOverride.value[it.id] ?? it.purchased ?? 0
}

function shopItemName(it: ShoppingItemVO): string {
  return it.ingredientName || it.customName || `#${it.ingredientId}`
}

function shopItemAmount(it: ShoppingItemVO): string {
  if (it.purchaseAmount != null) return `${fmtAmount(it.purchaseAmount)} ${it.purchaseUnitName || ''}`
  if (it.referenceGrams != null) return `约 ${Math.round(it.referenceGrams)}g`
  return ''
}

function fmtAmount(v: number): string {
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

// 三色徽章（对齐 shopping/Detail.vue：RED 没有 / YELLOW 差X / GREEN 够）
function stockClass(s: NonNullable<ShoppingItemVO['stockStatus']>): string {
  return { RED_NONE: 'red', YELLOW_SHORT: 'yellow', GREEN_ENOUGH: 'green' }[s]
}

function fmtGrams(g: number | null | undefined): string {
  if (g == null) return ''
  return Number.isInteger(g) ? `${g}g` : g.toFixed(1) + 'g'
}

function stockLabel(s: NonNullable<ShoppingItemVO['stockStatus']>, shortage: number): string {
  switch (s) {
    case 'RED_NONE': return shortage ? `没有 差${fmtGrams(shortage)}` : '没有'
    case 'YELLOW_SHORT': return `差 ${fmtGrams(shortage)}`
    case 'GREEN_ENOUGH': return '够'
  }
}

async function loadShop() {
  shopLoading.value = true
  try {
    shopVO.value = await getMenuShopping(menuId.value)
  } catch {
    uni.showToast({ title: '加载采购失败', icon: 'none' })
  } finally {
    shopLoading.value = false
  }
}

async function generateShop() {
  try {
    await generateShopping({ sourceType: 'menu', sourceId: menuId.value })
    await loadShop()
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '生成失败', icon: 'none' })
  }
}

/** 勾选/取消：乐观 override，失败回滚。 */
async function toggleShop(it: ShoppingItemVO) {
  const cur = effShop(it)
  const next = cur === 1 ? 0 : 1
  purchOverride.value[it.id] = next
  try {
    await togglePurchased(it.id)
  } catch {
    delete purchOverride.value[it.id]
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

/** 整集做菜：POST /menu/{id}/cook。 */
async function onCookMenu() {
  if (cooking.value) return
  cooking.value = true
  try {
    const res = await cookMenu(menuId.value)
    const shortCnt = res?.shortages ? Object.keys(res.shortages).length : 0
    uni.showToast({
      title: shortCnt > 0 ? `已做菜，库存已扣；缺量 ${shortCnt} 项` : '已做菜，库存已扣',
      icon: 'none',
    })
    detail.value = await getMenuDetail(menuId.value)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '做菜失败', icon: 'none' })
  } finally {
    cooking.value = false
  }
}

/** 完成态「去评价」：选食集里的一道菜 → 菜谱评价页。 */
function goReview() {
  const dishes = detail.value?.dishes ?? []
  if (!dishes.length) return
  uni.showActionSheet({
    itemList: dishes.map((d) => d.dishName || `菜 #${d.dishId}`),
    success: (res) => {
      const picked = dishes[res.tapIndex]
      if (!picked) return
      uni.navigateTo({ url: `/pages/dish/Review?dishId=${picked.dishId}` })
    },
  })
}

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/dish/List' }) })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #FDFAF4;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 16rpx) 24rpx 12rpx;
  background: #FDFAF4;
}
.back {
  width: 60rpx;
  font-size: 48rpx;
  color: #4A382A;
  text-align: center;
}
.top-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #4A382A;
}

.page-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

/* Tab 栏 */
.tabbar {
  display: flex;
  background: #FFFFFF;
  border-bottom: 2rpx solid #F0E6D6;
}
.tab {
  flex: 1;
  padding: 20rpx 0;
  text-align: center;
  border-bottom: 4rpx solid transparent;
}
.tab text {
  font-size: 28rpx;
  color: #6E5C49;
}
.tab.active {
  border-bottom-color: #E89150;
}
.tab.active text {
  color: #E89150;
  font-weight: bold;
}

.scroll { flex: 1; }

/* 标题卡 */
.title-card {
  margin: 16rpx 28rpx 0;
  background: #FFFFFF;
  border-radius: 36rpx;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.08);
  padding: 36rpx;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.title {
  flex: 1;
  font-size: 40rpx;
  font-weight: bold;
  color: #4A382A;
}
.status-chip {
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 600;
}
.status-chip.active {
  background: rgba(79, 174, 110, 0.12);
  color: #4FAE6E;
}
.status-chip.done {
  background: rgba(156, 140, 122, 0.15);
  color: #9C8C7A;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 16rpx;
}
.meta-item { font-size: 24rpx; color: #9C8C7A; }
.meta-dot { color: #9C8C7A; }

/* 块 */
.block { margin: 36rpx 28rpx 0; }
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

/* 菜品卡 */
.dish-card { padding: 8rpx 32rpx; }
.dish-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.dish-row:last-child { border-bottom: none; }
.dish-name { font-size: 28rpx; color: #4A382A; }
.dish-right { display: flex; align-items: baseline; gap: 4rpx; }
.dish-amount { font-size: 32rpx; font-weight: bold; color: #E89150; }
.dish-unit { font-size: 22rpx; color: #9C8C7A; }

/* 备菜进度 */
.prep-progress {
  margin: 24rpx 28rpx 0;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}
.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.progress-label { font-size: 28rpx; font-weight: bold; color: #4A382A; }
.progress-count { font-size: 24rpx; color: #9C8C7A; }
.progress-track {
  height: 14rpx;
  background: #F0E6D6;
  border-radius: 7rpx;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #4FAE6E;
  border-radius: 7rpx;
  transition: width 0.3s;
}

/* 备菜行 */
.prep-card { padding: 8rpx 32rpx; margin-top: 0; }
.prep-card.condiment-card { margin: 0 28rpx; }
.prep-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.prep-row:last-child { border-bottom: none; }
.prep-row.shared {
  /* 共用项淡橙底（聚焦"一次备够"） */
  background: #FFF8EC;
  margin: 0 -32rpx;
  padding-left: 32rpx;
  padding-right: 32rpx;
}
.prep-chip {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 600;
  border: 2rpx solid transparent;
}
.chip-pending { background: #FFFFFF; border-color: #E0D5C0; color: #9C8C7A; }
.chip-ready { background: rgba(79, 174, 110, 0.12); border-color: #4FAE6E; color: #4FAE6E; }
.chip-thawing { background: rgba(79, 160, 208, 0.12); border-color: #4FA0D0; color: #4FA0D0; }
.chip-marinating { background: rgba(229, 169, 56, 0.12); border-color: #E5A938; color: #E5A938; }
.prep-main { flex: 1; overflow: hidden; }
.prep-name-row { display: flex; align-items: center; gap: 8rpx; }
.prep-name { font-size: 28rpx; color: #4A382A; font-weight: 500; }
.prep-shared {
  font-size: 18rpx;
  font-weight: 600;
  color: #E89150;
  background: rgba(232, 145, 80, 0.12);
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}
.prep-sub {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #9C8C7A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prep-grams { font-size: 26rpx; color: #9C8C7A; }

/* 调料折叠头 */
.condiment-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 24rpx 28rpx 12rpx;
  padding: 20rpx 28rpx;
  background: #FBF0DD;
  border-radius: 16rpx;
}
.condiment-title { flex: 1; font-size: 26rpx; color: #6E5C49; }
.condiment-arrow { font-size: 26rpx; color: #9C8C7A; }

/* 占位 */
.placeholder {
  padding: 120rpx 60rpx;
  text-align: center;
}
.placeholder-title {
  display: block;
  font-size: 36rpx;
  font-weight: bold;
  color: #4A382A;
  margin-bottom: 16rpx;
}
.placeholder-desc {
  display: block;
  font-size: 26rpx;
  color: #9C8C7A;
}

/* 采购（Plan E） */
.shop-empty {
  padding: 120rpx 60rpx;
  text-align: center;
}
.shop-empty-title {
  display: block;
  font-size: 28rpx;
  color: #9C8C7A;
  margin-bottom: 32rpx;
}
.shop-gen-btn {
  display: inline-block;
  padding: 0 48rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  background: #E89150;
  color: #FFFFFF;
  border-radius: 16rpx;
}
.shop-gen-btn::after { border: none; }

.shop-card { padding: 8rpx 32rpx; margin: 24rpx 28rpx 0; }
.shop-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F0E6D6;
}
.shop-row:last-child { border-bottom: none; }
.shop-row.bought .shop-name {
  color: #9C8C7A;
  text-decoration: line-through;
}
.shop-check {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 50%;
  font-size: 26rpx;
}
.shop-check.on { background: rgba(79, 174, 110, 0.12); color: #4FAE6E; }
.shop-check.off { border: 2rpx solid #E0D5C0; color: #C4B5A2; }
.shop-main { flex: 1; overflow: hidden; }
.shop-name { font-size: 28rpx; color: #4A382A; font-weight: 500; }
.shop-amt { display: block; margin-top: 4rpx; font-size: 22rpx; color: #9C8C7A; }
/* 三色徽章配色对齐 shopping/Detail.vue（实底白字） */
.shop-stock {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: #FFFFFF;
}
.shop-stock.red { background: #DB5A4E; }
.shop-stock.yellow { background: #E5A938; }
.shop-stock.green { background: #4FAE6E; }

.prep-loading, .loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9C8C7A;
  font-size: 14px;
  padding: 80rpx 0;
}

/* 底部操作 */
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
/* 整集做菜（Plan A，扣库存链）。绿色 #4FAE6E 对齐 Flutter AppColors.success。 */
.cook-menu-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  background: #4FAE6E;
  color: #FFFFFF;
  border-radius: 16rpx;
}
.cook-menu-btn::after { border: none; }
.cook-menu-btn[disabled] { opacity: 0.6; }
/* 完成态「去评价」：描边橙字（对齐 Flutter OutlinedButton + AppTokens.warning）。 */
.review-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  margin-top: 16rpx;
  background: #FFFFFF;
  color: #E5A938;
  border: 3rpx solid #E5A938;
  border-radius: 16rpx;
}
.review-btn::after { border: none; }
</style>
