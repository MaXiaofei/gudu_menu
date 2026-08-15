<template>
  <view class="page">
    <ui-action-bar>
      <!-- 筛选条 + 最右侧「新建食集」（APP 定稿布局） -->
      <view class="filter-row">
        <view class="fchip" :class="{ on: status === '' }" @click="switchStatus('')">全部 {{ loadedCount }}</view>
        <view class="fchip" :class="{ on: status === 'ACTIVE' }" @click="switchStatus('ACTIVE')">进行中 {{ activeCount }}</view>
        <view class="fchip" :class="{ on: status === 'DONE' }" @click="switchStatus('DONE')">已完成 {{ doneCount }}</view>
        <view class="new-btn" @click="openCreate">新建食集</view>
      </view>
    </ui-action-bar>

    <!-- 列表 -->
    <view class="list">
      <view v-for="m in menus" :key="m.id" class="swipe-wrap">
        <view class="swipe-del" @click.stop="confirmDelete(m)">
          <text class="swipe-del-txt">删除</text>
        </view>
        <view
          class="card"
          :class="{ highlight: m.status !== 'DONE' }"
          :style="{ transform: `translateX(${offsets[m.id] || 0}px)` }"
          @touchstart="onTs($event, m.id)"
          @touchmove="onTm($event, m.id)"
          @touchend="onTe($event, m.id)"
          @click="onTapCard(m)"
        >
          <view class="row1">
            <text class="name">{{ m.name }}</text>
            <view class="pill" :class="m.status === 'DONE' ? 'done' : 'active'">
              {{ m.status === 'DONE' ? '已完成' : '进行中' }}
            </view>
          </view>
          <view class="row2">
            <text class="meta">{{ m.dishCount ?? 0 }} 道菜</text>
            <text class="meta spacer">{{ m.servingCount ?? 1 }} 人份 · {{ relativeDate(m.createTime) }}</text>
          </view>
        </view>
      </view>

      <view v-if="!firstLoading && menus.length" class="footer">
        {{ hasMore ? '上拉加载更多' : '没有更多了' }}
      </view>
      <ui-state v-if="!firstLoading && !menus.length" mode="empty" text="还没有食集" />
      <ui-state v-if="firstLoading" mode="loading" />
    </view>

    <!-- 新建食集弹窗 -->
    <view v-if="dialogVisible" class="mask" @click="dialogVisible = false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">新建食集</text>
        <input
          v-model="newName"
          class="dialog-ipt"
          placeholder="食集名（如：今晚的饭）"
          placeholder-class="ph"
          :focus="dialogVisible"
          @confirm="confirmCreate"
        />
        <view class="dialog-actions">
          <text class="dialog-btn" @click="dialogVisible = false">取消</text>
          <text class="dialog-btn primary" @click="confirmCreate">确定</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { listMenus, createMenu, deleteMenu, type Menu } from '@/api/menu'
import { relativeDate } from '@/utils/datetime'

const menus = ref<Menu[]>([])
const status = ref('')
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const firstLoading = ref(true)

const dialogVisible = ref(false)
const newName = ref('')

const offsets = reactive<Record<number, number>>({})
let touchStartX = 0
let touchStartY = 0
let touching = false

const loadedCount = computed(() => menus.value.length)
const activeCount = computed(() => menus.value.filter((m) => m.status !== 'DONE').length)
const doneCount = computed(() => menus.value.filter((m) => m.status === 'DONE').length)

reload()

async function fetch(p: number): Promise<Menu[]> {
  try {
    const r = await listMenus(p, 10, status.value || undefined)
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
  menus.value = await fetch(1)
  firstLoading.value = false
  Object.keys(offsets).forEach((k) => delete offsets[Number(k)])
}

onPullDownRefresh(async () => {
  await reload()
  uni.stopPullDownRefresh()
})
onReachBottom(async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  const list = await fetch(page.value + 1)
  page.value += 1
  menus.value.push(...list)
  loading.value = false
})

function switchStatus(s: string) {
  if (status.value === s) return
  status.value = s
  reload()
}

function onTapCard(m: Menu) {
  if ((offsets[m.id] || 0) !== 0) {
    offsets[m.id] = 0
    return
  }
  uni.navigateTo({ url: `/pages/menu/Detail?id=${m.id}` })
}

// ---- 新建 ----
function openCreate() {
  newName.value = ''
  dialogVisible.value = true
}
async function confirmCreate() {
  const name = newName.value.trim()
  if (!name) return
  try {
    await createMenu(name)
    dialogVisible.value = false
    uni.showToast({ title: '已创建食集', icon: 'none' })
    reload()
  } catch {
    uni.showToast({ title: '创建失败', icon: 'none' })
  }
}

// ---- 删除 ----
function confirmDelete(m: Menu) {
  uni.showModal({
    title: '删除食集',
    content: `确认删除食集「${m.name}」？该操作不可撤销。`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) {
        offsets[m.id] = 0
        return
      }
      try {
        await deleteMenu(m.id)
        menus.value = menus.value.filter((x) => x.id !== m.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch {
        offsets[m.id] = 0
        uni.showToast({ title: '删除失败', icon: 'none' })
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
  if (dy > 12) return
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
.filter-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
}
.fchip {
  padding: 4px 9px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 11px;
}
.fchip.on {
  background: var(--title);
  border-color: var(--title);
  color: #FFFFFF;
}
.new-btn {
  margin-left: auto;
  padding: 5px 12px;
  border-radius: var(--r-pill);
  background: var(--primary);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 700;
}
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
  padding: 11px;
  border-radius: var(--r-md);
  background: var(--card);
  border: 1px solid var(--border);
  transition: transform 0.15s ease-out;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.card.highlight {
  background: var(--highlight);
  border: 1.5px solid var(--primary-soft);
}
.row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name {
  flex: 1;
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pill {
  padding: 2px 8px;
  border-radius: var(--r-pill);
  font-size: 10px;
  font-weight: 700;
  color: #FFFFFF;
}
.pill.active {
  background: var(--warning);
}
.pill.done {
  background: var(--success);
}
.row2 {
  display: flex;
  align-items: center;
}
.meta {
  font-size: 10px;
  color: var(--caption);
}
.spacer {
  margin-left: auto;
}
.footer {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
/* 弹窗 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
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
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
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
.ph {
  color: var(--caption);
}
</style>
