<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <ui-state v-if="loading" mode="loading" skeleton="hero" />
    <view v-else-if="!detail" class="err-wrap">
      <ui-back-header />
      <ui-state mode="error" text="菜谱没加载出来" :retry="true" @retry="load(dishId)" />
    </view>
    <template v-else>
      <scroll-view scroll-y class="body">
        <!-- 主图（满宽 280，叠返回 / 分享） -->
        <ui-hero-gallery :images="heroImages" :name="detail.dish.name" :height="280" @preview="previewHero">
          <view class="float-row" :style="{ top: floatTop + 'px' }">
            <view class="fbtn" aria-label="返回" @click="goBack"><ui-icon name="back" :size="20" /></view>
            <!-- #ifdef MP-WEIXIN -->
            <button class="fbtn share" open-type="share" aria-label="分享"><ui-icon name="share" :size="18" /></button>
            <!-- #endif -->
          </view>
        </ui-hero-gallery>

        <view class="sheet">
          <view class="title-row">
            <text class="title">{{ detail.dish.name }}</text>
            <text v-if="isPublic" class="src-tag" data-testid="dish-public-tag">公共库</text>
            <text v-else class="src-tag mine">我家的</text>
          </view>
          <view class="meta">
            <text v-if="totalMinutes" class="meta-it">{{ totalMinutes }} 分钟</text>
            <text v-if="diffLabel" class="meta-it">{{ diffLabel }}</text>
            <text v-if="detail.dish.cookedCount" class="meta-it">做过 {{ detail.dish.cookedCount }} 次</text>
          </view>
          <view v-if="relNames.length" class="chips">
            <text v-for="n in relNames" :key="n" class="chip">{{ n }}</text>
          </view>
          <text v-if="detail.dish.sourceName" class="from">来源：{{ detail.dish.sourceName }}</text>
          <!-- 公共菜谱：复制到我家后可按自家口味改（multi-tenant-family fork） -->
          <view v-if="isPublic" class="fork-row">
            <text class="fork-tip">想按自家口味改？复制一份到我家，改动只在家里生效</text>
            <button class="btn-fork" :disabled="forking" data-testid="dish-fork" @click="onFork">
              {{ forking ? '复制中…' : '复制到我家' }}
            </button>
          </view>

          <ingredient-card
            :ingredients="detail.ingredients"
            :stock="stock"
            :bought="bought"
            @buy="addToShopping"
          />

          <view v-if="detail.dish.note" class="card note-card">
            <text class="card-title">小贴士</text>
            <text class="note">{{ detail.dish.note }}</text>
          </view>
        </view>

        <!-- 步骤：横滑卡 -->
        <view v-if="detail.steps.length" class="steps">
          <view class="steps-head">
            <text class="card-title">步骤 · {{ detail.steps.length }} 步</text>
          </view>
          <ui-step-carousel :steps="detail.steps" :card-width="232" @preview="previewStep" />
          <view class="step-list">
            <view v-for="(s, i) in detail.steps" :key="i" class="step">
              <text class="step-no">{{ i + 1 }}</text>
              <text class="step-text">{{ s.text }}</text>
            </view>
          </view>
        </view>

        <view style="height: 96px" />
      </scroll-view>

      <view v-if="showActions" class="bottom">
        <button class="btn-add" :disabled="sheetRef?.busy" @click="sheetRef?.open()">+ 加入食集</button>
        <button class="btn-follow" data-testid="cook-mode" @click="goCookMode">跟做</button>
        <button class="btn-cook" :disabled="cookLoading" data-testid="cook-now" @click="onCookNow">
          {{ cookLoading ? '加载中…' : '现在就做' }}
        </button>
      </view>

      <ui-cook-sheet
        v-if="cookVisible"
        :items="cookItems"
        :submitting="cookSubmitting"
        :done="cookDone"
        :result-sub="detail.dish.name"
        :cover="detail.dish.coverUrl"
        show-servings
        allow-note
        @submit="onCookSubmit"
        @close="closeCook"
        @foodlog="goFoodlog"
      />

      <add-to-menu-sheet ref="sheetRef" :dish-id="detail.dish.id" :dish-name="detail.dish.name" />
    </template>
    <!-- 首次登录完善资料（A.7） -->
    <ui-profile-setup />
  </view>
</template>

<script setup lang="ts">
import { PAGES, withQuery, COOK_AFTER_MODE_KEY } from '@/utils/nav'
import { buildTarget, ensureLogin } from '@/utils/loginRedirect'
import { ref, computed } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { dishDetail, forkDish, isPublicDish, type DishDetail, type DishIngredient } from '@/api/dish'
import { toAbsolute, originalList } from '@/utils/image'
import { navInset } from '@/utils/token'
import { dishCookMaterials, cookDish } from '@/api/cook'
import { listGroupedAll, type PantryGroupedItem } from '@/api/pantry'
import { listShopping, createEmptyList, addCustomItem } from '@/api/shopping'
import type { CookMaterial } from '@/api/menu'
import AddToMenuSheet from './parts/AddToMenuSheet.vue'
import IngredientCard from './parts/IngredientCard.vue'

const nav = navInset()
const floatTop = nav - 40
const loading = ref(true)
const detail = ref<DishDetail | null>(null)
const showActions = ref(true)
const dishId = ref(0)
const stock = ref<Record<number, PantryGroupedItem>>({})
const bought = ref<Record<number, boolean>>({})
const sheetRef = ref<InstanceType<typeof AddToMenuSheet> | null>(null)

const cookVisible = ref(false)
const cookLoading = ref(false)
const cookSubmitting = ref(false)
const cookDone = ref(false)
const cookItems = ref<CookMaterial[]>([])

onLoad(async (options) => {
  dishId.value = Number(options?.id || 0)
  if (options?.showActions === '0') showActions.value = false
  // 分享落地：未登录先去登录，登录后带着 id 回到本页（A.10）
  if (!(await ensureLogin(buildTarget('/pages/dish/Detail', options)))) return
  load(dishId.value)
})

onShareAppMessage(() => ({
  title: detail.value?.dish.name || '咕嘟小食单',
  path: `/pages/dish/Detail?id=${dishId.value}`,
  imageUrl: toAbsolute(detail.value?.dish.coverUrl) || undefined,
}))

async function load(id: number) {
  loading.value = true
  try {
    detail.value = await dishDetail(id)
  } catch {
    detail.value = null
  }
  loading.value = false
  // 余量状态是增强信息：失败不影响详情
  listGroupedAll()
    .then((g) => {
      const m: Record<number, PantryGroupedItem> = {}
      for (const it of g.items || []) m[it.ingredientId] = it
      stock.value = m
    })
    .catch(() => {})
}

const heroImages = computed(() => {
  const d = detail.value
  if (!d) return []
  const stepImgs = d.steps.flatMap((s) => (s.images || '').split(',').filter(Boolean))
  return [d.dish.coverUrl, ...stepImgs].filter(Boolean).slice(0, 6) as string[]
})
const totalMinutes = computed(() => (detail.value?.dish.prepTime ?? 0) + (detail.value?.dish.cookTime ?? 0))
const diffLabel = computed(() => {
  const d = detail.value?.dish.difficulty
  if (!d) return ''
  return d <= 2 ? '简单' : d <= 3 ? '中等' : '有挑战'
})
const isPublic = computed(() => isPublicDish(detail.value?.dish))
const forking = ref(false)

async function onFork() {
  if (forking.value || !dishId.value) return
  forking.value = true
  try {
    const newId = await forkDish(dishId.value)
    uni.showToast({ title: '已复制到我家', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: `/pages/dish/Detail?id=${newId}` }), 400)
  } catch {
    // request.ts 已提示
  } finally {
    forking.value = false
  }
}

const relNames = computed(() => {
  const d = detail.value?.dish
  if (!d) return []
  return [...(d.cuisineNames ?? []), ...(d.categoryNames ?? []), ...(d.tagNames ?? [])].slice(0, 6)
})

async function addToShopping(ing: DishIngredient) {
  if (bought.value[ing.ingredientId] || !ing.ingredientName) return
  try {
    const r = await listShopping(1, 1)
    const listId = r.records[0]?.id || (await createEmptyList())
    await addCustomItem({ listId, name: ing.ingredientName })
    bought.value = { ...bought.value, [ing.ingredientId]: true }
    uni.showToast({ title: `已加入采购清单`, icon: 'none' })
  } catch {
    // request.ts 已提示
  }
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/dish/List' })
}
function previewHero(i: number) {
  const urls = heroImages.value.map((u) => toAbsolute(u))
  if (urls.length) uni.previewImage({ urls, current: urls[i] })
}
function previewStep(p: { step: number }) {
  const urls = originalList(detail.value?.steps[p.step]?.images)
  if (urls.length) uni.previewImage({ urls, current: urls[0] })
}

async function onCookNow() {
  if (cookLoading.value || !dishId.value) return
  cookLoading.value = true
  try {
    cookItems.value = await dishCookMaterials(dishId.value, 1)
    cookDone.value = false
    cookVisible.value = true
  } catch {
    uni.showToast({ title: '用料加载失败，请稍后重试', icon: 'none' })
  } finally {
    cookLoading.value = false
  }
}
async function onCookSubmit(p: { usedUp: number[]; partiallyUsed: number[]; servings?: number; note?: string }) {
  cookSubmitting.value = true
  try {
    await cookDish(dishId.value, { servings: p.servings ?? 1, usedUp: p.usedUp, partiallyUsed: p.partiallyUsed, note: p.note })
    cookDone.value = true
    uni.showToast({ title: '已记到食记', icon: 'none' })
  } catch {
    // request.ts 已提示
  } finally {
    cookSubmitting.value = false
  }
}
function closeCook() {
  cookVisible.value = false
  cookDone.value = false
}
function goFoodlog() {
  closeCook()
  uni.navigateTo({ url: '/pages/foodlog/Index' })
}

/** 跟做模式最后一步「做好了」→ 回到详情直接打开做完确认（storage 标记，跨分包不传对象）。 */
onShow(() => {
  const flag = uni.getStorageSync(COOK_AFTER_MODE_KEY)
  if (flag && Number(flag) === dishId.value) {
    uni.removeStorageSync(COOK_AFTER_MODE_KEY)
    onCookNow()
  }
})

/** 跟做模式（pkg-ai/CookMode）：大字步骤 + 计时器 + 问咕嘟。 */
function goCookMode() {
  uni.navigateTo({ url: withQuery(PAGES.cookMode, { id: dishId.value }) })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
}
.float-row {
  position: absolute;
  left: var(--space-lg);
  right: var(--space-lg);
  display: flex;
  justify-content: space-between;
}
.fbtn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.fbtn.share {
  margin: 0;
  padding: 0;
  line-height: 36px;
}
.fbtn-ic {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  line-height: 1;
}
.fbtn-ic.sm {
  font-size: var(--fs-lg);
}
.sheet {
  position: relative;
  margin-top: -24px;
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  background: var(--bg);
  padding: var(--space-xl) var(--space-lg) 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.title {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
}
.src-tag {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--info-soft);
  color: var(--info-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.src-tag.mine {
  background: var(--primary-soft);
  color: var(--title);
}
.meta {
  display: flex;
  gap: var(--space-md);
}
.meta-it {
  font-size: var(--fs-sm);
  color: var(--body);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--success-soft);
  color: var(--success-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.from {
  font-size: var(--fs-sm);
  color: var(--body);
}
.fork-row {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  margin-top: var(--sp-10);
  padding: var(--sp-10) var(--sp-12);
  border-radius: var(--r-md);
  background: var(--secondary);
}
.fork-tip {
  flex: 1;
  font-size: var(--fs-sm);
  line-height: 1.5;
  color: var(--body);
}
.btn-fork {
  margin: 0;
  padding: 0 var(--sp-12);
  height: 32px;
  line-height: 32px;
  border-radius: var(--r-pill);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-sm);
  font-weight: 700;
}
.btn-fork::after {
  border: none;
}
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-md) var(--space-lg);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.08);
}
.card-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.note-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.note {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--body);
}
.steps {
  margin-top: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.steps-head {
  padding: 0 var(--space-lg);
}
.step-list {
  padding: 0 var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.step {
  display: flex;
  gap: var(--space-md);
}
.step-no {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: var(--radius-pill);
  background: var(--primary-soft);
  color: var(--title);
  font-size: var(--fs-sm);
  font-weight: 700;
  flex-shrink: 0;
}
.step-text {
  flex: 1;
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--title);
}
.bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg) calc(var(--space-md) + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.btn-follow {
  height: 48px;
  line-height: 48px;
  padding: 0 16px;
  margin: 0;
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.btn-follow::after {
  border: none;
}
.btn-add,
.btn-cook {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius-md);
  font-size: var(--fs-lg);
  font-weight: 700;
  margin: 0;
}
.btn-add {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
}
.btn-cook {
  flex: 1.6;
  background: var(--primary-deep);
  color: var(--on-primary);
  box-shadow: 0 4px 12px rgba(var(--sh), 0.3);
}
.btn-add::after,
.btn-cook::after {
  border: none;
}
.btn-cook[disabled] {
  background: var(--primary-soft);
  color: var(--on-primary);
}
.err-wrap {
  min-height: 100vh;
}
</style>
