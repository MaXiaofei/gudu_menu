<template>
  <view class="page">
    <!-- ===== 列表视图 ===== -->
    <template v-if="view === 'list'">
      <view class="top" :style="{ paddingTop: sb + 'px' }">
        <text class="title">{{ batchMode ? `已选 ${batchSel.size} 个` : '采购清单' }}</text>
        <view class="top-actions">
          <template v-if="!batchMode">
            <text class="top-btn danger" @click="batchMode = true">批量删除</text>
            <text class="top-btn primary" @click="createList">＋ 新建清单</text>
          </template>
          <text v-else class="top-btn" @click="cancelBatch">取消</text>
        </view>
      </view>

      <scroll-view scroll-y class="body" @scrolltolower="loadMore">
        <view v-for="l in lists" :key="l.id" class="lcard" @click="openDetail(l.id)" @longpress="confirmDeleteList(l)">
          <view v-if="batchMode" class="checkbox" :class="{ on: batchSel.has(l.id) }" @click.stop="toggleBatch(l.id)">
            <text v-if="batchSel.has(l.id)" class="checkbox-txt">✓</text>
          </view>
          <view v-else class="order-no"><text class="order-no-txt">#{{ (l.id % 100) + 1 }}</text></view>
          <view class="lcard-main">
            <text class="lcard-name">{{ listTitle(l) }}</text>
            <text class="lcard-sub">{{ listSub(l) }}</text>
          </view>
          <text v-if="!batchMode" class="arrow">›</text>
        </view>
        <view v-if="!firstLoading && !lists.length" class="empty">
          <text class="empty-main">还没有采购清单</text>
          <text class="empty-sub">清单来自食集：去食集详情 → 备菜 Tab →「一键加采购」；或者点右上角「＋ 新建清单」自己列</text>
        </view>
        <ui-state v-if="firstLoading" mode="loading" />
        <view v-if="lists.length && !hasMore" class="footer">没有更多了</view>
        <view style="height: 80px" />
      </scroll-view>

      <!-- 批量删除底栏 -->
      <view v-if="batchMode" class="bottom">
        <view class="batch-all" @click="toggleBatchAll">{{ allBatchSelected ? '取消全选' : '全选' }}</view>
        <button class="btn-batch-del" :disabled="batchSel.size === 0" @click="confirmBatchDelete">
          删除 {{ batchSel.size }} 个
        </button>
      </view>
    </template>

    <!-- ===== 详情视图 ===== -->
    <template v-else-if="view === 'detail'">
      <view class="top" :style="{ paddingTop: sb + 'px' }">
        <text class="back" @click="closeDetail">‹</text>
        <view class="detail-title-wrap" @click="openRename">
          <text class="title detail-title">{{ detail?.name ? detail.name : `采购单 #${detail?.id}` }}</text>
          <text v-if="detail?.name" class="rename">✎</text>
        </view>
        <text class="top-btn" @click="view = 'share'">分享</text>
      </view>

      <scroll-view scroll-y class="body">
        <!-- 全选行 -->
        <view v-if="detail && detail.items.length" class="all-row" @click="toggleSelectAll">
          <view class="checkbox" :class="{ on: allSelected }">
            <text v-if="allSelected" class="checkbox-txt">✓</text>
          </view>
          <text class="all-label">全选</text>
          <text v-if="selected.size > 0" class="all-count">已选 {{ selected.size }} 项</text>
        </view>

        <!-- 分区列表 -->
        <template v-if="detail">
          <template v-for="(groupItems, key) in detail.grouped" :key="key">
            <view class="sec-head">
              <view class="sec-bar" />
              <text class="sec-name">{{ detail.categoryNames?.[key] || '其他' }}</text>
            </view>
            <view v-for="it in groupItems" :key="it.id" class="item-row" @click="toggleItem(it)">
              <!-- 勾选框：未入库=本地选择；已入库=固定绿勾 -->
              <view class="checkbox" :class="{ on: it.purchased ? true : selected.has(it.id), done: !!it.purchased }">
                <text class="checkbox-txt">✓</text>
              </view>
              <view class="item-main">
                <text class="item-name" :class="{ done: it.purchased }">{{ itemName(it) }}</text>
                <text v-if="amountText(it)" class="item-amount">{{ amountText(it) }}</text>
              </view>
              <text v-if="stockBadge(it)" class="item-badge" :style="{ color: stockBadge(it)!.color }">{{ stockBadge(it)!.text }}</text>
              <text class="item-del" @click.stop="confirmRemoveItem(it)">✕</text>
            </view>
          </template>
          <view v-if="!detail.items.length" class="empty">
            <text class="empty-main">还没有采购项</text>
            <text class="empty-sub">点下方「添加」列要买的。匹配到食材库的勾选后可入库，匹配不到的照常列、只标已买。</text>
          </view>
        </template>
        <view style="height: 88px" />
      </scroll-view>

      <!-- 底栏：添加 + 保存入库 -->
      <view class="bottom">
        <button class="btn-ghost bottom-add" @click="addSheet = true">添加</button>
        <button
          v-if="detail && detail.items.length"
          class="btn-primary bottom-restock"
          :disabled="selected.size === 0 || restocking"
          @click="confirmRestock"
        >
          {{ restocking ? '入库中…' : `保存入库 · ${selected.size} 项` }}
        </button>
      </view>

      <!-- 添加弹层 -->
      <view v-if="addSheet" class="mask" @click="addSheet = false">
        <view class="sheet" @click.stop>
          <text class="sheet-title">添加采购项</text>
          <view class="add-row">
            <input v-model="addName" class="add-ipt" placeholder="名称" placeholder-class="ph" :focus="addSheet" />
            <input v-model="addQty" class="add-qty" placeholder="数量+单位 · 如 2斤" placeholder-class="ph" />
          </view>
          <view class="add-more" @click="pushAddRow">＋ 再加一行</view>
          <view v-if="addedRows.length" class="added-list">
            <text class="added-label">已添加 {{ addedRows.length }} 种</text>
            <view v-for="(r, i) in addedRows" :key="i" class="added-row">
              <text class="added-txt">{{ r.name }} {{ r.qty }}</text>
              <text class="added-del" @click="addedRows.splice(i, 1)">✕</text>
            </view>
          </view>
          <view class="sheet-bottom-row">
            <button class="btn-primary" :disabled="!canSaveAdd" @click="saveAdd">{{ saveAddLabel }}</button>
          </view>
        </view>
      </view>
    </template>

    <!-- ===== 分享预览视图 ===== -->
    <template v-else>
      <view class="top" :style="{ paddingTop: sb + 'px' }">
        <text class="back" @click="view = 'detail'">‹</text>
        <text class="title">分享采购清单</text>
        <view style="width: 40px" />
      </view>
      <scroll-view scroll-y class="body">
        <text class="share-tip">预览与导出的内容、格式完全一致</text>
        <view class="share-card">
          <text class="share-title">{{ shareTitle }}</text>
          <view v-for="it in unPurchasedItems" :key="it.id" class="share-row">
            <text class="share-name">{{ itemName(it) }}</text>
            <text class="share-qty">{{ amountText(it) }}</text>
          </view>
          <text v-if="!unPurchasedItems.length" class="share-none">清单里的都入库了，没有要分享的项</text>
        </view>
        <view class="share-actions">
          <view class="share-btn" @click="copyText"><text class="share-btn-txt">复制文字</text></view>
          <view class="share-btn" @click="saveImage"><text class="share-btn-txt">转图片分享</text></view>
        </view>
        <view style="height: 40px" />
      </scroll-view>
    </template>

    <!-- 改名弹窗 -->
    <view v-if="renameDialog" class="mask" @click="renameDialog = false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">清单改名</text>
        <text class="dialog-hint">列表页和分享内容会同步显示新名字</text>
        <input v-model="renameInput" class="dialog-ipt" :focus="renameDialog" @confirm="confirmRename" />
        <view class="dialog-actions">
          <text class="dialog-btn" @click="renameDialog = false">取消</text>
          <text class="dialog-btn primary" @click="confirmRename">确定</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  listShopping, shoppingDetail, createEmptyList, restockItems, undoRestock,
  addCustomItem, removeShoppingItem, deleteShoppingList, renameShoppingList,
  sourceTypeLabel, amountText, stockBadge,
  type ShoppingListSummary, type ShoppingDetail, type ShoppingItem,
} from '@/api/shopping'
import { mdHm } from '@/utils/datetime'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()

// 视图切换：list → detail → share
const view = ref<'list' | 'detail' | 'share'>('list')

// ---- 列表视图 ----
const lists = ref<ShoppingListSummary[]>([])
const page = ref(1)
const hasMore = ref(true)
const firstLoading = ref(true)
const batchMode = ref(false)
const batchSel = reactive(new Set<number>())

// ---- 详情视图 ----
const detail = ref<ShoppingDetail | null>(null)
const selected = reactive(new Set<number>())
const restocking = ref(false)
const addSheet = ref(false)
const addName = ref('')
const addQty = ref('')
const addedRows = ref<{ name: string; qty: string }[]>([])
const renameDialog = ref(false)
const renameInput = ref('')

onLoad(async (options) => {
  await reload()
  // 备菜一键加购跳转：加载完自动打开该清单详情
  if (options?.listId) openDetail(Number(options.listId))
})

onPullDownRefresh(async () => {
  if (view.value === 'list') await reload()
  uni.stopPullDownRefresh()
})

async function reload() {
  page.value = 1
  hasMore.value = true
  firstLoading.value = true
  lists.value = await fetch(1)
  firstLoading.value = false
}

async function fetch(p: number): Promise<ShoppingListSummary[]> {
  try {
    const r = await listShopping(p)
    hasMore.value = r.records.length >= 10
    return r.records
  } catch {
    hasMore.value = false
    return []
  }
}

async function loadMore() {
  if (!hasMore.value || firstLoading.value) return
  const list = await fetch(page.value + 1)
  page.value += 1
  lists.value.push(...list)
}

function listTitle(l: ShoppingListSummary): string {
  return l.name || `采购单 · ${sourceTypeLabel(l.sourceType)}`
}
function listSub(l: ShoppingListSummary): string {
  if (l.startDate && l.endDate) return `${l.startDate.slice(5, 10)} ~ ${l.endDate.slice(5, 10)}`
  return l.createTime ? mdHm(l.createTime) : ''
}

// ---- 详情 ----
async function openDetail(id: number) {
  try {
    detail.value = await shoppingDetail(id)
    selected.clear()
    view.value = 'detail'
  } catch {
    uni.showToast({ title: '加载采购单失败', icon: 'none' })
  }
}

function closeDetail() {
  view.value = 'list'
  reload()
}

const unPurchasedItems = computed(() =>
  (detail.value?.items ?? []).filter((it) => !it.purchased),
)
const allSelected = computed(
  () => unPurchasedItems.value.length > 0 && unPurchasedItems.value.every((it) => selected.has(it.id)),
)

function toggleSelectAll() {
  if (allSelected.value) {
    selected.clear()
  } else {
    unPurchasedItems.value.forEach((it) => selected.add(it.id))
  }
}
function toggleItem(it: ShoppingItem) {
  if (it.purchased) return
  selected.has(it.id) ? selected.delete(it.id) : selected.add(it.id)
}
function itemName(it: ShoppingItem): string {
  return it.ingredientName || it.customName || `#${it.id}`
}

function confirmRestock() {
  const ids = [...selected]
  uni.showModal({
    title: '保存入库',
    content: `将 ${ids.length} 项入库（库存记「充足」），已匹配食材库的会建档位。`,
    success: async ({ confirm }) => {
      if (!confirm) return
      restocking.value = true
      try {
        const r = await restockItems(ids)
        const msg = r.markedOnly > 0 ? `已入库 ${r.restocked} 项，${r.markedOnly} 项只标已买` : `已入库 ${r.restocked} 项`
        uni.showToast({ title: msg, icon: 'none' })
        await openDetail(detail.value!.id)
      } catch (e) {
        uni.showToast({ title: `保存入库失败：${e}`, icon: 'none' })
      } finally {
        restocking.value = false
      }
    },
  })
}

function confirmRemoveItem(it: ShoppingItem) {
  if (it.purchased) {
    // 撤回入库
    uni.showModal({
      title: '撤回入库？',
      content: '库存回到入库前状态，这项从清单移除。流水里会记一笔「撤回入库」。',
      confirmText: '撤回',
      confirmColor: '#DB5A4E',
      success: async ({ confirm }) => {
        if (!confirm) return
        try {
          await undoRestock(it.id)
          uni.showToast({ title: '已撤回入库', icon: 'none' })
          openDetail(detail.value!.id)
        } catch {
          uni.showToast({ title: '撤回失败', icon: 'none' })
        }
      },
    })
  } else {
    uni.showModal({
      title: '移除采购项',
      content: '以后要买，从备菜「一键加采购」或重新生成清单就能加回来。',
      confirmText: '移除',
      confirmColor: '#DB5A4E',
      success: async ({ confirm }) => {
        if (!confirm) return
        try {
          await removeShoppingItem(it.id)
          openDetail(detail.value!.id)
        } catch {
          uni.showToast({ title: '移除失败', icon: 'none' })
        }
      },
    })
  }
}

// ---- 添加弹层 ----
const canSaveAdd = computed(
  () => addedRows.value.length > 0 || addName.value.trim().length > 0,
)
const saveAddLabel = computed(() => {
  const n = addedRows.value.length + (addName.value.trim() ? 1 : 0)
  return n > 0 ? `添加 · 保存 ${n} 种` : '保存'
})

function pushAddRow() {
  const name = addName.value.trim()
  if (!name) return
  addedRows.value.push({ name, qty: addQty.value.trim() })
  addName.value = ''
  addQty.value = ''
}

async function saveAdd() {
  const rows = [...addedRows.value]
  if (addName.value.trim()) rows.push({ name: addName.value.trim(), qty: addQty.value.trim() })
  if (!rows.length) return
  try {
    for (const r of rows) {
      const m = r.qty.match(/\d+(\.\d+)?/)
      await addCustomItem({ listId: detail.value!.id, name: r.name, amount: m ? Number(m[0]) : undefined })
    }
    addSheet.value = false
    addedRows.value = []
    addName.value = ''
    addQty.value = ''
    openDetail(detail.value!.id)
  } catch {
    uni.showToast({ title: '添加失败', icon: 'none' })
  }
}

// ---- 新建 / 删除 / 批量 / 改名 ----
async function createList() {
  try {
    const id = await createEmptyList()
    openDetail(id)
  } catch {
    uni.showToast({ title: '创建失败', icon: 'none' })
  }
}

function confirmDeleteList(l: ShoppingListSummary) {
  uni.showModal({
    title: '删除采购单',
    content: `确定删除「${listTitle(l)}」？`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deleteShoppingList(l.id)
        lists.value = lists.value.filter((x) => x.id !== l.id)
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    },
  })
}

function toggleBatch(id: number) {
  batchSel.has(id) ? batchSel.delete(id) : batchSel.add(id)
}
function cancelBatch() {
  batchMode.value = false
  batchSel.clear()
}
const allBatchSelected = computed(() => lists.value.length > 0 && lists.value.every((l) => batchSel.has(l.id)))
function toggleBatchAll() {
  if (allBatchSelected.value) batchSel.clear()
  else lists.value.forEach((l) => batchSel.add(l.id))
}
function confirmBatchDelete() {
  const ids = [...batchSel]
  uni.showModal({
    title: '批量删除',
    content: `确定删除选中的 ${ids.length} 个采购单？`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        for (const id of ids) await deleteShoppingList(id)
        lists.value = lists.value.filter((l) => !batchSel.has(l.id))
        cancelBatch()
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    },
  })
}

function openRename() {
  if (!detail.value?.name) return
  renameInput.value = detail.value.name
  renameDialog.value = true
}
async function confirmRename() {
  const name = renameInput.value.trim()
  if (!name || !detail.value) return
  try {
    await renameShoppingList(detail.value.id, name)
    renameDialog.value = false
    openDetail(detail.value.id)
  } catch {
    uni.showToast({ title: '改名失败', icon: 'none' })
  }
}

// ---- 分享 ----
const shareTitle = computed(() => {
  const d = detail.value
  return `${d?.name || `采购单 #${d?.id}`} · ${new Date().getMonth() + 1}月${new Date().getDate()}日`
})

function copyText() {
  const lines = unPurchasedItems.value.map((it) => `${itemName(it)} ${amountText(it)}`.trim())
  uni.setClipboardData({
    data: [shareTitle.value, ...lines].join('\n'),
    success: () => uni.showToast({ title: '采购清单已复制', icon: 'none' }),
  })
}

function saveImage() {
  // 阶段 4 简化：canvas 转图后续迭代；先用系统截屏分享文案替代
  copyText()
  uni.showToast({ title: '已复制文字，可直接粘贴分享', icon: 'none' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  min-height: 44px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 6px;
}
.title {
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
}
.detail-title-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.detail-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rename {
  color: var(--primary);
  font-size: 13px;
}
.top-actions {
  display: flex;
  gap: 12px;
}
.top-btn {
  font-size: 12px;
  color: var(--primary);
  font-weight: 700;
}
.top-btn.danger {
  color: var(--error);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 12px;
  box-sizing: border-box;
}
/* 清单卡 */
.lcard {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 4px;
}
.order-no {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.order-no-txt {
  font-size: 11px;
  font-weight: 800;
  color: var(--primary-deep);
}
.lcard-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lcard-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lcard-sub {
  font-size: 11px;
  color: var(--caption);
}
.arrow {
  color: var(--caption);
  font-size: 16px;
  font-weight: 700;
}
/* 勾选框 */
.checkbox {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 1.5px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.checkbox.on {
  background: var(--primary);
  border-color: var(--primary);
}
.checkbox.on.done {
  background: var(--success);
  border-color: var(--success);
}
.checkbox-txt {
  color: #FFFFFF;
  font-size: 13px;
  font-weight: 800;
}
/* 空态 / footer */
.empty {
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.empty-main {
  font-size: 13px;
  color: var(--caption);
}
.empty-sub {
  font-size: 11px;
  color: var(--caption);
  opacity: 0.8;
  text-align: center;
  line-height: 1.6;
}
.footer {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
/* 底栏 */
.bottom {
  display: flex;
  gap: 8px;
  padding: 10px 14px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  align-items: center;
}
.bottom-add { flex: 1; }
.bottom-restock { flex: 2; }
.batch-all {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--body);
}
.btn-batch-del {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--error);
  color: var(--error);
  border-radius: var(--r-md);
  font-size: 14px;
  font-weight: 700;
  padding: 11px 0;
}
.btn-batch-del[disabled] {
  border-color: var(--border);
  color: var(--caption);
}
/* 全选行 */
.all-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 2px;
}
.all-label {
  font-size: 13px;
  color: var(--body);
}
.all-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--primary);
}
/* 分区 */
.sec-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 4px;
}
.sec-bar {
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--primary);
}
.sec-name {
  font-size: 10px;
  font-weight: 800;
  color: var(--primary-deep);
  letter-spacing: 1px;
}
/* 采购项行 */
.item-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 4px;
}
.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.item-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.item-name.done {
  color: var(--caption);
  text-decoration: line-through;
}
.item-amount {
  font-size: 9px;
  color: var(--caption);
}
.item-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: var(--r-pill);
  background: var(--bg);
  font-size: 9px;
  font-weight: 800;
}
.item-del {
  flex-shrink: 0;
  color: var(--caption);
  font-size: 13px;
  padding: 0 2px;
}
/* 弹层 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  padding: 16px 18px calc(14px + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
  display: block;
  margin-bottom: 12px;
}
.add-row {
  display: flex;
  gap: 8px;
}
.add-ipt {
  flex: 1;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 9px 10px;
  font-size: 13px;
  color: var(--title);
}
.add-qty {
  width: 150px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 9px 10px;
  font-size: 13px;
  color: var(--title);
}
.ph { color: var(--caption); }
.add-more {
  margin-top: 8px;
  font-size: 12px;
  color: var(--primary);
  font-weight: 700;
}
.added-list {
  margin-top: 10px;
}
.added-label {
  font-size: 11px;
  color: var(--caption);
}
.added-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
}
.added-txt {
  font-size: 13px;
  color: var(--title);
}
.added-del {
  color: var(--caption);
  font-size: 12px;
}
.sheet-bottom-row {
  margin-top: 12px;
}
/* 分享视图 */
.share-tip {
  display: block;
  text-align: center;
  font-size: 10px;
  color: var(--caption);
  margin: 10px 0;
}
.share-card {
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 16px;
}
.share-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--title);
  display: block;
  margin-bottom: 8px;
}
.share-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
}
.share-name {
  font-size: 13px;
  color: var(--body);
}
.share-qty {
  font-size: 12px;
  color: var(--caption);
}
.share-none {
  font-size: 12px;
  color: var(--caption);
  text-align: center;
  padding: 12px 0;
}
.share-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.share-btn {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--primary);
  border-radius: var(--r-md);
  padding: 11px 0;
  text-align: center;
}
.share-btn-txt {
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
}
/* 对话框 */
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
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
}
.dialog-hint {
  display: block;
  font-size: 11px;
  color: var(--caption);
  margin-top: 4px;
}
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: 14px;
  color: var(--title);
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
}
.dialog-btn {
  font-size: 14px;
  color: var(--caption);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary);
  font-weight: 700;
}
</style>
