<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <ui-back-header :title="detail?.menu.name" :subtitle="headSub">
      <template #action>
        <view v-if="detail" class="pill" :class="isDone ? 'done' : 'active'">
          {{ isDone ? '已完成' : '进行中' }}
        </view>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="error" text="食集没加载出来" :retry="true" @retry="load()" />
    <template v-else>
      <view class="tabs">
        <ui-segment v-model="tabKey" :items="tabs" />
      </view>

      <scroll-view scroll-y class="body">
        <view class="pad">
          <dishes-tab
            v-if="tab === 0"
            :dishes="detail.dishes"
            :is-done="isDone"
            @open="tapDish"
            @note="editNote"
            @remove="confirmRemoveDish"
            @pick="goPicker"
          />
          <prep-tab v-else-if="tab === 1" :prep="prep" :loading="prepLoading" @toggle="togglePrep" @more="longPressPrep" @retry="loadPrep" />
          <shop-tab v-else-if="tab === 2" :prep="prep" :loading="prepLoading" :menu-id="menuId" :dish-count="detail.dishes.length" />
          <together-tab
            v-else
            :together="together"
            :loading="togetherLoading"
            :is-done="isDone"
            :invite="invite"
            :inviting="inviting"
            @create="doCreateInvite"
            @retry="loadTogether"
          />
        </view>
        <view style="height: 96px" />
      </scroll-view>

      <view v-if="tab === 0" class="bottom">
        <template v-if="!isDone">
          <view class="prog">
            <text class="prog-v">{{ detail.dishes.length }} 道菜</text>
            <text class="prog-l">{{ prep ? `已备 ${prep.readyCount}/${prep.totalCount}` : '' }}</text>
          </view>
          <button class="btn-cook" :disabled="cooking || !detail.dishes.length" data-testid="menu-cook" @click="startCook">
            {{ cooking ? '加载中…' : '全部做完' }}
          </button>
        </template>
        <template v-else>
          <button class="btn-cook done" disabled>已完成</button>
          <button class="btn-review" @click="goReview">去评价</button>
        </template>
      </view>
    </template>

    <view v-if="noteDialog" class="mask" @click="noteDialog = false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">菜备注</text>
        <input
          v-model="noteInput"
          class="dialog-ipt"
          maxlength="255"
          placeholder="如：宝宝那份少盐"
          placeholder-class="ph"
          :focus="noteDialog"
          @confirm="confirmNote"
        />
        <view class="dialog-actions">
          <text class="dialog-btn" @click="noteDialog = false">取消</text>
          <text class="dialog-btn primary" @click="confirmNote">确定</text>
        </view>
      </view>
    </view>

    <ui-cook-sheet
      v-if="cookSheet"
      title="这顿饭用了什么"
      :items="cookItems"
      :submitting="cookSubmitting"
      :done="cookDone"
      :result-sub="detail?.menu.name"
      @submit="onCookSubmit"
      @close="closeCook"
      @foodlog="cookToFoodlog"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onHide, onShareAppMessage } from '@dcloudio/uni-app'
import {
  menuDetail, updateDishNote, removeDishFromMenu, togetherCount,
  cookMaterials, cookMenu,
  type MenuDetail, type MenuDish, type CookMaterial,
} from '@/api/menu'
import { getPrep, updatePrepStatus, type MenuPrep, type PrepItem } from '@/api/prep'
import { getTogether, createInvite, togetherSharePath, type TogetherVO } from '@/api/together'
import { mdHm } from '@/utils/datetime'
import DishesTab from './parts/DishesTab.vue'
import PrepTab from './parts/PrepTab.vue'
import ShopTab from './parts/ShopTab.vue'
import TogetherTab from './parts/TogetherTab.vue'

const menuId = ref(0)
const loading = ref(true)
const detail = ref<MenuDetail | null>(null)
const tab = ref(0)
const togetherCnt = ref(0)

// 备菜
const prep = ref<MenuPrep | null>(null)
const prepLoading = ref(false)

// 备注
const noteDialog = ref(false)
const noteInput = ref('')
let noteDish: MenuDish | null = null

// 做菜确认（ui-cook-sheet）
const cooking = ref(false)
const cookSheet = ref(false)
const cookSubmitting = ref(false)
const cookDone = ref(false)
const cookItems = ref<CookMaterial[]>([])

// ---- 聚餐（一起吃） ----
const together = ref<TogetherVO | null>(null)
const togetherLoading = ref(false)
const invite = ref<{ code: string; token: string } | null>(null)
const inviting = ref(false)
let togetherTimer: ReturnType<typeof setInterval> | null = null

/** 分享给微信好友：落地页带免登录 token（小程序码/分享卡片方案）。 */
onShareAppMessage(() => {
  if (invite.value) {
    return {
      title: `一起来点菜：${detail.value?.menu.name || '聚餐清单'}`,
      path: togetherSharePath(menuId.value, invite.value.token),
    }
  }
  return { title: '咕嘟小食单', path: '/pages/dish/List' }
})

onLoad((options) => {
  menuId.value = Number(options?.id || 0)
  load()
})

// 从选菜页返回刷新
onShow(() => {
  if (menuId.value && detail.value) load(false)
})

async function load(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    detail.value = await menuDetail(menuId.value)
    loadPrep()
    togetherCount(menuId.value).then((n) => (togetherCnt.value = n)).catch(() => {})
  } catch {
    detail.value = null
  }
  loading.value = false
}

async function loadPrep() {
  prepLoading.value = true
  try {
    prep.value = await getPrep(menuId.value)
  } catch {
    prep.value = null
  }
  prepLoading.value = false
}

const isDone = computed(() => detail.value?.menu.status === 'DONE')
const headSub = computed(() => {
  const m = detail.value?.menu
  if (!m) return undefined
  const mins = detail.value!.totalMinutes ? ` · 约 ${detail.value!.totalMinutes} 分钟` : ''
  return `${mdHm(m.createTime)} · ${m.servingCount ?? 1} 人${mins}`
})
const TAB_KEYS = ['dish', 'prep', 'shop', 'together']
const tabKey = computed({
  get: () => TAB_KEYS[tab.value],
  set: (k: string) => (tab.value = Math.max(0, TAB_KEYS.indexOf(k))),
})
const tabs = computed(() => [
  { key: 'dish', label: '菜', badge: detail.value?.dishes.length ?? 0 },
  { key: 'prep', label: '备菜', badge: prep.value ? `${prep.value.readyCount}/${prep.value.totalCount}` : '' },
  { key: 'shop', label: '采购', badge: '' },
  { key: 'together', label: '一起吃', badge: togetherCnt.value || '' },
])
// ---- 菜 Tab ----
function tapDish(d: MenuDish) {
  if (!d.dishId) return
  uni.navigateTo({ url: `/pages/dish/Detail?id=${d.dishId}&showActions=0` })
}
function goPicker() {
  uni.navigateTo({ url: `/pages/dish/List?selectForMenuId=${menuId.value}` })
}
function goReview() {
  uni.navigateTo({ url: `/pages/review/MenuReview?id=${menuId.value}` })
}

function editNote(d: MenuDish) {
  if (isDone.value || !d.dishId) return
  noteDish = d
  noteInput.value = d.note || ''
  noteDialog.value = true
}
async function confirmNote() {
  if (!noteDish) return
  try {
    await updateDishNote(menuId.value, noteDish.dishId, noteInput.value)
    noteDialog.value = false
    load(false)
  } catch {
    uni.showToast({ title: '备注更新失败', icon: 'none' })
  }
}

function confirmRemoveDish(d: MenuDish) {
  uni.showModal({
    title: '移出食集',
    content: `确认将「${d.dishName || '这道菜'}」移出食集？`,
    confirmText: '移出',
    confirmColor: '#DB5A4E', // showModal 只收色值字面量
    success: async ({ confirm }) => {
      if (!confirm || !d.dishId) return
      try {
        await removeDishFromMenu(menuId.value, d.dishId)
        load(false)
      } catch {
        uni.showToast({ title: '移出失败', icon: 'none' })
      }
    },
  })
}

// ---- 备菜 Tab ----
async function togglePrep(it: PrepItem) {
  if (isDone.value) return
  const next = it.status === 'READY' ? 'PENDING' : 'READY'
  await updatePrep(it, next)
}

function longPressPrep(it: PrepItem) {
  if (isDone.value) return
  uni.showActionSheet({
    itemList: ['化冻中', '腌制中', '重置为待备'],
    success: async ({ tapIndex }) => {
      const next = ['THAWING', 'MARINATING', 'PENDING'][tapIndex]
      if (next && next !== it.status) await updatePrep(it, next)
    },
  })
}

/** 乐观更新 + 失败回滚（对齐 APP）。 */
async function updatePrep(it: PrepItem, next: string) {
  const prev = prep.value
  const rebuilt = (): MenuPrep | null => {
    if (!prev) return null
    const map = (list: PrepItem[]) =>
      list.map((x) => (x.ingredientId === it.ingredientId ? { ...x, status: next } : x))
    const items = map(prev.items)
    const condiments = map(prev.condiments)
    const readyCount = [...items, ...condiments].filter((x) => x.status === 'READY').length
    return { items, condiments, readyCount, totalCount: prev.totalCount }
  }
  prep.value = rebuilt()
  try {
    await updatePrepStatus(menuId.value, it.ingredientId, next)
  } catch {
    prep.value = prev
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

// ---- 做菜确认 ----
async function startCook() {
  if (cooking.value) return
  cooking.value = true
  try {
    cookItems.value = await cookMaterials(menuId.value)
    cookDone.value = false
    cookSheet.value = true
  } catch {
    uni.showToast({ title: '用料加载失败', icon: 'none' })
  } finally {
    cooking.value = false
  }
}
async function onCookSubmit(p: { usedUp: number[]; partiallyUsed: number[] }) {
  cookSubmitting.value = true
  try {
    await cookMenu(menuId.value, p.usedUp, p.partiallyUsed)
    cookDone.value = true
  } catch {
    // request.ts 已提示
  } finally {
    cookSubmitting.value = false
  }
}
function closeCook() {
  const done = cookDone.value
  cookSheet.value = false
  cookDone.value = false
  if (done) load(false)
}
function cookToFoodlog() {
  closeCook()
  uni.navigateTo({ url: '/pages/foodlog/Index' })
}

// ---- 聚餐逻辑 ----
async function loadTogether() {
  try {
    const r = await getTogether(menuId.value)
    together.value = r
    // 已有邀请：本地持有（优先于轮询返回值）
    if (!invite.value && r.invite?.token) invite.value = { code: r.invite.code || '', token: r.invite.token }
  } catch {
    together.value = null
    stopPolling()
  }
}

watch(tab, (t) => {
  if (t === 3) {
    togetherLoading.value = !together.value
    loadTogether().finally(() => (togetherLoading.value = false))
    startPolling()
  } else {
    stopPolling()
  }
})

function startPolling() {
  if (togetherTimer) return
  togetherTimer = setInterval(() => loadTogether(), 10000)
}
function stopPolling() {
  if (togetherTimer) {
    clearInterval(togetherTimer)
    togetherTimer = null
  }
}
onHide(stopPolling)
onShow(() => {
  if (tab.value === 3) startPolling()
})

async function doCreateInvite() {
  if (inviting.value) return
  inviting.value = true
  try {
    const r = await createInvite(menuId.value)
    invite.value = r
    uni.showToast({ title: `邀请已生成，口令 ${r.code}`, icon: 'none' })
  } catch {
    uni.showToast({ title: '生成邀请失败', icon: 'none' })
  } finally {
    inviting.value = false
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
.pill {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.pill.active {
  background: var(--primary-soft);
  color: var(--title);
}
.pill.done {
  background: var(--success-soft);
  color: var(--success-deep);
}
.tabs {
  padding: var(--sp-4) var(--sp-16) var(--sp-12);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--sp-16);
}
.bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: var(--sp-12) var(--sp-16) calc(var(--sp-12) + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.prog {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 88px;
}
.prog-v {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.prog-l {
  font-size: var(--fs-sm);
  color: var(--body);
}
.btn-cook,
.btn-review {
  flex: 1;
  height: 48px;
  line-height: 48px;
  margin: 0;
  border-radius: var(--r-md);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.btn-cook {
  background: var(--primary-deep);
  color: var(--on-primary);
}
.btn-cook[disabled] {
  background: var(--primary-soft);
  color: var(--on-primary);
}
.btn-cook.done {
  background: var(--success-soft);
  color: var(--success-deep);
}
.btn-review {
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
}
.btn-cook::after,
.btn-review::after {
  border: none;
}
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--mask);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.dialog {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 78vw;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 20px 18px 12px;
  z-index: 110;
}
.dialog-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: var(--fs-md);
  color: var(--title);
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
}
.dialog-btn {
  font-size: var(--fs-md);
  color: var(--caption);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary);
  font-weight: 700;
}
.ph {
  color: var(--caption);
}
</style>
