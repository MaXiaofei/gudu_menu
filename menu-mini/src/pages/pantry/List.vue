<template>
  <view class="pantry">
    <!-- 顶栏 -->
    <view class="topbar">
      <view class="top-left">
        <text class="crumb">🏠 我家</text>
        <text class="title">我家余量</text>
      </view>
      <text class="search-ico">🔍</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!list.length" class="empty-box">
      <text class="empty-ico">🏠</text>
      <text>还没加过食材，做完菜或采购后会自动入库</text>
    </view>
    <template v-else>
      <!-- 三色汇总条 -->
      <view class="summary">
        <view class="seg seg-ok">🟢 够 {{ cnt.ok }}</view>
        <view class="seg seg-low">🟡 低 {{ cnt.low }}</view>
        <view class="seg seg-miss">🔴 缺 {{ cnt.miss }}</view>
      </view>

      <!-- 筛选chips -->
      <view class="chips">
        <view :class="['chip', filter === 'all' && 'on']" @click="setFilter('all')">全部 {{ list.length }}</view>
        <view :class="['chip', 'c-miss', filter === 'miss' && 'on']" @click="setFilter('miss')">缺 {{ cnt.miss }}</view>
        <view :class="['chip', 'c-low', filter === 'low' && 'on']" @click="setFilter('low')">低 {{ cnt.low }}</view>
        <view :class="['chip', 'c-ok', filter === 'ok' && 'on']" @click="setFilter('ok')">够 {{ cnt.ok }}</view>
      </view>

      <!-- 分组列表 -->
      <view class="groups">
        <template v-for="g in groups" :key="g.key">
          <view v-if="g.items.length" class="grp-label" :class="g.key">
            {{ g.label }} · {{ g.items.length }}
          </view>
          <view v-for="r in g.items" :key="r.id" class="row">
            <text class="emoji">{{ emoji(r.ingredientName) }}</text>
            <view class="info">
              <text class="name">{{ r.ingredientName || '#' + r.ingredientId }}</text>
              <text class="src">{{ srcText(r) }}</text>
            </view>
            <text class="amt" :class="g.key">{{ r.amount }} {{ r.unitName || '' }}</text>
            <text class="adj" @click="onAdjust(r)">±</text>
          </view>
        </template>
      </view>
    </template>

    <view style="height: 180rpx;"></view>
    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listPantry, type PantryVO } from '@/api/pantry'
import CustomTabBar from '@/components/CustomTabBar.vue'

type Tier = 'miss' | 'low' | 'ok'

const list = ref<PantryVO[]>([])
const loading = ref(false)
const filter = ref<'all' | Tier>('all')

/** 三色分档：缺(≤0) / 低(<阈值) / 够(其余) */
function tier(r: PantryVO): Tier {
  if (Number(r.amount) <= 0) return 'miss'
  const th = Number(r.lowThreshold)
  if (th > 0 && Number(r.amount) < th) return 'low'
  return 'ok'
}

const cnt = computed(() => {
  let miss = 0, low = 0, ok = 0
  for (const r of list.value) {
    const t = tier(r)
    if (t === 'miss') miss++
    else if (t === 'low') low++
    else ok++
  }
  return { miss, low, ok }
})

const groups = computed(() => {
  const miss: PantryVO[] = [], low: PantryVO[] = [], ok: PantryVO[] = []
  for (const r of list.value) {
    const t = tier(r)
    if (filter.value !== 'all' && filter.value !== t) continue
    if (t === 'miss') miss.push(r)
    else if (t === 'low') low.push(r)
    else ok.push(r)
  }
  return [
    { key: 'miss' as Tier, label: '缺 / 空', items: miss },
    { key: 'low' as Tier, label: '偏低', items: low },
    { key: 'ok' as Tier, label: '充足', items: ok },
  ]
})

function setFilter(f: 'all' | Tier) {
  filter.value = f
}

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}
function daysBetween(expire?: string): number | null {
  if (!expire) return null
  return Math.ceil((new Date(expire).getTime() - new Date(todayStr()).getTime()) / 86400000)
}

/** 行来源/状态说明：缺→空了，临期→剩X天，低→低于警戒，其余→充足 */
function srcText(r: PantryVO): string {
  if (Number(r.amount) <= 0) return '空了 · 该补'
  const d = daysBetween(r.expireDate)
  if (d !== null && d < 0) return `已过期 ${-d} 天`
  if (d !== null && d <= 3) return `⚠️ 临期 剩 ${d} 天`
  const th = Number(r.lowThreshold)
  if (th > 0 && Number(r.amount) < th) return `低于警戒 ${th}`
  if (d !== null) return `剩 ${d} 天`
  return '充足'
}

function emoji(name?: string): string {
  if (!name) return '🥘'
  const map: Record<string, string> = {
    '蛋': '🥚', '鱼': '🐟', '虾': '🦐', '蟹': '🦀',
    '鸡肉': '🐔', '鸡': '🐔', '鸭': '🦆',
    '牛肉': '🥩', '牛': '🐂', '猪': '🥓', '羊': '🍖',
    '葱': '🧅', '姜': '🫚', '蒜': '🧄',
    '番茄': '🍅', '西红柿': '🍅', '茄子': '🍆',
    '土豆': '🥔', '马铃薯': '🥔', '萝卜': '🥕', '胡萝卜': '🥕',
    '黄瓜': '🥒', '瓜': '🥒', '椒': '🌶', '蘑菇': '🍄', '菌': '🍄',
    '菜': '🥬', '奶': '🥛', '黄油': '🧈', '油': '🫒',
    '米': '🍚', '面': '🍜', '粉': '🍜', '面包': '🍞', '麦': '🌾',
    '苹果': '🍎', '橙': '🍊', '柠檬': '🍋', '梨': '🍐', '香蕉': '🍌',
    '豆': '🫘', '果': '🥑',
  }
  for (const k in map) if (name.includes(k)) return map[k]
  return '🥘'
}

/** 盘点：弹原生 editable modal（后端调整接口待补，先提示） */
function onAdjust(r: PantryVO) {
  uni.showModal({
    title: `盘点 · ${r.ingredientName || ''}`,
    content: `系统记 ${r.amount} ${r.unitName || ''}`,
    editable: true,
    placeholderText: '实际数了多少？',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '盘点接口开发中', icon: 'none' })
      }
    },
  })
}

async function load() {
  loading.value = true
  try {
    list.value = await listPantry()
  } catch {
    /* 静默，request.ts 已 toast */
  } finally {
    loading.value = false
  }
}

onShow(() => { load() })
</script>

<style scoped>
.pantry {
  min-height: 100vh;
  background: #FDFAF4;
  padding: 0 28rpx calc(env(safe-area-inset-bottom) + 40rpx);
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 32rpx) 8rpx 20rpx;
}
.top-left { display: flex; flex-direction: column; gap: 4rpx; }
.crumb { font-size: 22rpx; color: #9C8C7A; }
.title { font-size: 44rpx; font-weight: 800; color: #4A382A; }
.search-ico { font-size: 36rpx; }

/* 三色汇总 */
.summary {
  display: flex;
  margin: 0 8rpx 20rpx;
  border-radius: 16rpx;
  overflow: hidden;
}
.seg {
  flex: 1;
  padding: 16rpx 12rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: #fff;
  text-align: center;
}
.seg-ok { background: #4FAE6E; }
.seg-low { background: #E5A938; }
.seg-miss { background: #DB5A4E; }

/* 筛选chips */
.chips {
  display: flex;
  gap: 12rpx;
  padding: 0 8rpx 16rpx;
}
.chip {
  font-size: 22rpx;
  font-weight: 600;
  padding: 10rpx 22rpx;
  border-radius: 28rpx;
  background: #fff;
  border: 2rpx solid #F0E6D6;
  color: #9C8C7A;
}
.chip.on { background: #4A382A; color: #fff; border-color: #4A382A; }
.chip.c-miss.on { background: #DB5A4E; border-color: #DB5A4E; }
.chip.c-low.on { background: #E5A938; border-color: #E5A938; }
.chip.c-ok.on { background: #4FAE6E; border-color: #4FAE6E; }

/* 分组列表 */
.groups { padding: 0 8rpx; }
.grp-label {
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  margin: 24rpx 0 8rpx;
}
.grp-label.miss { color: #DB5A4E; }
.grp-label.low { color: #E5A938; }
.grp-label.ok { color: #4FAE6E; }

.row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 22rpx 8rpx;
  border-bottom: 2rpx dashed #F0E6D6;
}
.emoji { font-size: 40rpx; }
.info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.name { font-size: 28rpx; font-weight: 700; color: #4A382A; }
.src { font-size: 22rpx; color: #9C8C7A; }
.amt { font-size: 28rpx; font-weight: 800; min-width: 120rpx; text-align: right; }
.amt.miss { color: #DB5A4E; }
.amt.low { color: #E5A938; }
.amt.ok { color: #4A382A; }
.adj {
  width: 48rpx; height: 48rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 32rpx; font-weight: 800;
  color: #D17A3C;
}

/* 空态 */
.empty { text-align: center; color: #9C8C7A; padding: 200rpx 0; font-size: 26rpx; }
.empty-box {
  display: flex; flex-direction: column; align-items: center;
  gap: 20rpx; padding: 200rpx 40rpx;
  color: #9C8C7A; font-size: 26rpx; text-align: center;
}
.empty-ico { font-size: 96rpx; }
</style>
