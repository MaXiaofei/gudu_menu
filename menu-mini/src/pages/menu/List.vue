<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <ui-tab-header title="食集">
      <view class="new-btn" @click="openCreate"><ui-icon name="plus" tone="white" :size="14" /><text class="new-txt">新食集</text></view>
    </ui-tab-header>

    <ui-offline-bar />
    <view class="filter-row">
      <view class="fchip" :class="{ on: status === '' }" @click="switchStatus('')">全部</view>
      <view class="fchip" :class="{ on: status === 'ACTIVE' }" @click="switchStatus('ACTIVE')">进行中 {{ activeCount }}</view>
      <view class="fchip" :class="{ on: status === 'DONE' }" @click="switchStatus('DONE')">已完成 {{ doneCount }}</view>
    </view>

    <view class="list">
      <!-- 进行中主卡 -->
      <active-menu-card v-if="current" :menu="current" :dishes="dishesOf(current.id)" @tap="onTapCard(current)" />

      <text v-if="rest.length" class="sec-label">{{ current ? '接下来 · 其他食集' : '全部食集' }}</text>
      <view v-for="m in rest" :key="m.id" class="swipe-wrap">
        <view class="swipe-del" :class="{ on: (offsets[m.id] || 0) < 0 }" @click.stop="confirmDelete(m)">
          <text class="swipe-del-txt">删除</text>
        </view>
        <view
          class="card"
          :class="{ done: m.status === 'DONE' }"
          :style="{ transform: `translateX(${offsets[m.id] || 0}px)` }"
          @touchstart="onTs($event, m.id)"
          @touchmove="onTm($event, m.id)"
          @touchend="onTe($event, m.id)"
          @click="onTapCard(m)"
        >
          <view class="date">
            <text class="date-w">{{ weekOf(m.createTime) }}</text>
            <text class="date-d">{{ dayOf(m.createTime) }}</text>
          </view>
          <view class="info">
            <text class="name">{{ m.name }}</text>
            <text class="meta">{{ m.dishCount ?? 0 }} 道菜 · {{ m.servingCount ?? 1 }} 人份 · {{ m.status === 'DONE' ? '已完成' : '进行中' }}</text>
          </view>
          <ui-thumb-stack :items="dishesOf(m.id)" />
        </view>
      </view>

      <view v-if="!firstLoading && menus.length" class="footer">
        {{ hasMore ? '上拉加载更多' : '没有更多了' }}
      </view>
      <ui-state
        v-if="!firstLoading && !menus.length"
        mode="empty"
        text="还没有食集"
        hint="一顿饭建一个食集：选菜、备菜、采购都在里面"
        action-text="新建食集"
        :illus="['rice', 'egg', 'broccoli']"
        @action="openCreate"
      />
      <ui-state v-if="firstLoading" mode="loading" />
    </view>

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
import { useSwipe } from '@/composables/useSwipe'
import ActiveMenuCard from './parts/ActiveMenuCard.vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { listMenus, createMenu, deleteMenu, menuDetail, type Menu } from '@/api/menu'
import { parseDate } from '@/utils/datetime'

const menus = ref<Menu[]>([])
const status = ref('')
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const firstLoading = ref(true)

const dialogVisible = ref(false)
const newName = ref('')

const { offsets, onTs, onTm, onTe, reset: resetSwipe } = useSwipe()

// 食集 → 菜（封面拼图用；详情按需拉取并缓存）
const covers = reactive<Record<number, { cover?: string | null; name?: string | null }[]>>({})
function dishesOf(id: number) {
  return covers[id] || []
}
async function loadCovers(list: Menu[]) {
  await Promise.all(
    list
      .filter((m) => !covers[m.id] && (m.dishCount ?? 1) > 0)
      .slice(0, 12)
      .map((m) =>
        menuDetail(m.id)
          .then((d) => (covers[m.id] = d.dishes.map((x) => ({ cover: x.coverUrl, name: x.dishName || x.customName }))))
          .catch(() => {}),
      ),
  )
}

const current = computed(() => (status.value === 'DONE' ? null : menus.value.find((m) => m.status !== 'DONE') || null))
const rest = computed(() => menus.value.filter((m) => m.id !== current.value?.id))
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
function weekOf(s?: string | null) {
  const d = parseDate(s)
  return d ? WEEK[d.getDay()] : ''
}
function dayOf(s?: string | null) {
  const d = parseDate(s)
  return d ? String(d.getDate()) : '-'
}

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
  resetSwipe()
  loadCovers(menus.value)
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
  loadCovers(list)
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
    confirmColor: '#DB5A4E', // showModal 只收色值字面量
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

</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.new-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: var(--r-pill);
  background: var(--primary-deep);
  box-shadow: 0 4px 12px rgba(var(--sh), 0.3);
}
.new-txt {
  color: var(--on-primary);
  font-size: var(--fs-sm);
  font-weight: 700;
}
.filter-row {
  display: flex;
  gap: var(--space-sm);
  padding: 0 var(--space-lg) var(--space-md);
}
.fchip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-sm);
}
.fchip.on {
  background: var(--title);
  border-color: var(--title);
  color: var(--on-primary);
  font-weight: 700;
}
.list {
  padding: 0 var(--space-lg) var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sec-label {
  margin-top: var(--space-sm);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.swipe-wrap {
  position: relative;
  border-radius: var(--r-lg);
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
.card {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  transition: transform 0.15s ease-out;
}
.card.done {
  background: var(--bg);
}
.date {
  width: 44px;
  height: 48px;
  border-radius: var(--r-md);
  background: var(--secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.date-w {
  font-size: var(--fs-tiny);
  color: var(--body);
}
.date-d {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
  line-height: 1.1;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  font-size: var(--fs-sm);
  color: var(--body);
}
.footer {
  padding: var(--space-lg) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--body);
}
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
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
  color: var(--body);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary-deep);
  font-weight: 700;
}
.ph {
  color: var(--caption);
}
</style>
