<template>
  <view class="pantry">
    <view class="tabs">
      <view :class="['tab', tab === 'all' && 'on']" @click="switchTab('all')">全部</view>
      <view :class="['tab', tab === 'expiring' && 'on']" @click="switchTab('expiring')">临期</view>
      <view :class="['tab', tab === 'low' && 'on']" @click="switchTab('low')">不足</view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!list.length" class="empty">暂无库存</view>
    <view v-else class="cards">
      <view v-for="r in list" :key="r.id" :class="['card', mark(r)]">
        <view class="row1">
          <text class="name">{{ r.ingredientName || '#' + r.ingredientId }}</text>
          <text class="amt">{{ r.amount }} {{ r.unitName || '' }}</text>
        </view>
        <view class="row2">
          <text v-if="r.lowThreshold && Number(r.lowThreshold) > 0" class="meta">阈值 {{ r.lowThreshold }}</text>
          <text v-else class="meta">无阈值</text>
          <text :class="['expire', expireClass(r)]">{{ expireText(r) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listPantry, listExpiring, listLow, type PantryVO } from '@/api/pantry'

const tab = ref<'all' | 'expiring' | 'low'>('all')
const list = ref<PantryVO[]>([])
const loading = ref(false)

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}
function daysBetween(expire?: string): number | null {
  if (!expire) return null
  return Math.ceil((new Date(expire).getTime() - new Date(todayStr()).getTime()) / 86400000)
}
function isExpiring(r: PantryVO): boolean {
  const d = daysBetween(r.expireDate)
  return d !== null && d >= 0 && d <= 3
}
function isLow(r: PantryVO): boolean {
  return !!r.lowThreshold && Number(r.lowThreshold) > 0 && Number(r.amount) < Number(r.lowThreshold)
}
function mark(r: PantryVO): string {
  if (isLow(r)) return 'm-low'
  if (isExpiring(r)) return 'm-expiring'
  return ''
}
function expireText(r: PantryVO): string {
  const d = daysBetween(r.expireDate)
  if (d === null) return '无过期日'
  if (d < 0) return `已过期 ${-d} 天`
  if (d === 0) return '今天到期'
  return `剩 ${d} 天`
}
function expireClass(r: PantryVO): string {
  const d = daysBetween(r.expireDate)
  if (d === null) return ''
  if (d < 0) return 'danger'
  if (d <= 3) return 'warning'
  return 'ok'
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'expiring') {
      list.value = await listExpiring(3)
    } else if (tab.value === 'low') {
      list.value = await listLow()
    } else {
      list.value = await listPantry()
    }
  } finally {
    loading.value = false
  }
}

function switchTab(t: 'all' | 'expiring' | 'low') {
  tab.value = t
  load()
}

onShow(() => { load() })
</script>

<style scoped>
.pantry { padding: 12px; }
.tabs { display: flex; margin-bottom: 10px; }
.tab { flex: 1; text-align: center; padding: 8px 0; font-size: 14px; color: #888; border-bottom: 2px solid transparent; }
.tab.on { color: #E89150; border-bottom-color: #E89150; font-weight: 600; }
.empty { text-align: center; color: #aaa; padding: 40px 0; font-size: 13px; }
.cards { display: flex; flex-direction: column; gap: 10px; }
.card { background: #fff; border-radius: 8px; padding: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.card.m-low { border-left: 4px solid #f56c6c; }
.card.m-expiring { border-left: 4px solid #e6a23c; }
.row1 { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.name { font-size: 15px; font-weight: 600; color: #333; }
.amt { font-size: 14px; color: #E89150; font-weight: 600; }
.row2 { display: flex; justify-content: space-between; align-items: center; }
.meta { font-size: 12px; color: #999; }
.expire { font-size: 12px; }
.expire.warning { color: #e6a23c; font-weight: 600; }
.expire.danger { color: #f56c6c; font-weight: 600; }
.expire.ok { color: #999; }
</style>
