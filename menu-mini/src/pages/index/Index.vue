<template>
  <view class="page">
    <!-- 顶栏：左「小食单」右齿轮（跳设置） -->
    <view class="topbar">
      <text class="brand">小食单</text>
      <text class="gear" @click="goSettings">⚙</text>
    </view>

    <!-- 第一区：家里有啥（横滑食材） -->
    <view class="section-title" @click="goPantry">
      <text>家里有啥</text>
      <text class="arrow">→</text>
    </view>
    <scroll-view scroll-x class="pantry-scroll" :show-scrollbar="false">
      <view v-if="pantryLoading" class="ph">加载中…</view>
      <view v-else-if="!pantry.length" class="ph" @click="goPantry">冰箱空了，去买点啥吧</view>
      <view v-else class="pantry-row">
        <view v-for="p in pantry" :key="p.id" :class="['p-card', pantryTagClass(p)]">
          <text class="p-name">{{ p.ingredientName || ('#' + p.ingredientId) }}</text>
          <text class="p-tag">{{ pantryTagText(p) }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 第二区：操作（算热量 / 换个推荐） -->
    <view class="actions">
      <button class="btn-ghost half" @click="goEstimate">🔥 算热量</button>
      <button class="btn-primary half" @click="onRecommend" :disabled="recommending">
        {{ recommending ? '推荐中…' : '🤖 换个推荐' }}
      </button>
    </view>

    <!-- 第三区：根据家里的菜，这些能做 -->
    <view class="section-title">
      <text>根据家里的菜，这些能做</text>
    </view>
    <view v-if="dishLoading" class="empty">找菜中…</view>
    <view v-else-if="!matches.length" class="empty">家里这些菜还没匹配到菜谱，试试手动搜搜</view>
    <view v-else>
      <view v-for="m in matches" :key="m.dish.id" class="card dish-card" @click="goDetail(m.dish.id)">
        <view class="d-name">
          {{ m.dish.name }}
          <text v-if="m.canMake" class="badge-ok">能做</text>
          <text v-else class="badge-mid">差{{ m.missingIngredients.length }}样</text>
        </view>
        <view class="d-meta">
          <text class="meta-item">用料 {{ m.totalCount }} 种</text>
          <text class="meta-item">难度 {{ difficultyText(m.dish.difficulty) }}</text>
          <text v-if="minutes(m.dish)" class="meta-item">{{ minutes(m.dish) }} 分钟</text>
        </view>
        <view v-if="m.missingIngredients && m.missingIngredients.length" class="d-missing">
          还差：{{ m.missingIngredients.join('、') }}
        </view>
      </view>
      <view class="more" @click="goFind">查看更多 / 手动选食材 →</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listPantry, type PantryVO } from '@/api/pantry'
import { findDishesByIngredients, type DishMatch } from '@/api/dish'
import { aiRecommendMenu } from '@/api/ai'

const pantry = ref<PantryVO[]>([])
const pantryLoading = ref(false)
const matches = ref<DishMatch[]>([])
const dishLoading = ref(false)
const recommending = ref(false)

function daysBetween(expire?: string): number | null {
  if (!expire) return null
  const today = new Date().toISOString().slice(0, 10)
  return Math.ceil((new Date(expire).getTime() - new Date(today).getTime()) / 86400000)
}

/** 食材标签文本：临期「剩N天」、不足「偏少」、其他「充足」。 */
function pantryTagText(p: PantryVO): string {
  const d = daysBetween(p.expireDate)
  if (d !== null && d < 0) return `过期${-d}天`
  if (d !== null && d <= 3) return `剩${d}天`
  if (!!p.lowThreshold && Number(p.lowThreshold) > 0 && Number(p.amount) < Number(p.lowThreshold)) return '偏少'
  return '充足'
}
function pantryTagClass(p: PantryVO): string {
  const t = pantryTagText(p)
  if (t.startsWith('过期')) return 'p-danger'
  if (t.startsWith('剩')) return 'p-warn'
  if (t === '偏少') return 'p-warn'
  return 'p-ok'
}

function difficultyText(d?: number): string {
  if (d == null) return '-'
  if (d <= 1) return '易'
  if (d === 2) return '中'
  return '难'
}
function minutes(dish: any): number {
  return (Number(dish.prepTime) || 0) + (Number(dish.cookTime) || 0)
}

async function loadPantry() {
  pantryLoading.value = true
  try {
    pantry.value = await listPantry()
    await loadMatches()
  } catch {
    pantry.value = []
    matches.value = []
  } finally {
    pantryLoading.value = false
  }
}

async function loadMatches() {
  const ids = pantry.value.map((p) => p.ingredientId).filter((x): x is number => x != null)
  if (!ids.length) {
    matches.value = []
    return
  }
  dishLoading.value = true
  try {
    const all = await findDishesByIngredients(ids)
    matches.value = (all || []).slice(0, 10)
  } catch {
    matches.value = []
  } finally {
    dishLoading.value = false
  }
}

async function onRecommend() {
  if (recommending.value) return
  recommending.value = true
  try {
    const groups = await aiRecommendMenu({ scope: 'DAY' })
    if (groups && groups.length) {
      const first = groups[0]
      const names = (first.dishes || []).map((d) => d.name).join('、')
      uni.showModal({
        title: '今晚吃这些？',
        content: names || '暂无推荐',
        confirmText: '看看菜谱',
        cancelText: '换一个',
        success: (r) => {
          if (r.confirm && first.dishes && first.dishes[0]) {
            uni.navigateTo({ url: `/pages/dish/Detail?id=${first.dishes[0].dishId}` })
          }
        },
      })
    } else {
      uni.showToast({ title: '暂无推荐', icon: 'none' })
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '推荐失败', icon: 'none' })
  } finally {
    recommending.value = false
  }
}

function goSettings() {
  uni.navigateTo({ url: '/pages/profile/Settings' })
}
function goPantry() {
  uni.navigateTo({ url: '/pages/pantry/List' })
}
function goEstimate() {
  uni.navigateTo({ url: '/pages/ai/Estimate' })
}
function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}
function goFind() {
  uni.navigateTo({ url: '/pages/cookbook/FindByIngredients' })
}

onShow(() => {
  loadPantry()
})
</script>

<style scoped>
.page { padding: 0 14px 24px; }

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 2px 6px;
}
.brand { font-size: 18px; font-weight: 700; color: #222; }
.gear { font-size: 22px; color: #666; padding: 4px 8px; }

/* 小标题 */
.section-title {
  font-size: 14px; color: #999;
  margin: 16px 0 8px;
  display: flex; align-items: center; gap: 6px;
}
.arrow { font-size: 13px; color: #ccc; }

/* 家里有啥 横滑 */
.pantry-scroll { white-space: nowrap; }
.ph {
  display: inline-block; font-size: 13px; color: #999;
  background: #fff; border-radius: 8px; padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.pantry-row { display: inline-flex; gap: 8px; padding-right: 6px; }
.p-card {
  display: inline-flex; flex-direction: column; gap: 4px;
  background: #fff; border-radius: 8px; padding: 10px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  min-width: 78px;
}
.p-name { font-size: 14px; color: #222; font-weight: 600; }
.p-tag { font-size: 12px; }
.p-ok .p-tag { color: #2a9d8f; }
.p-warn .p-tag { color: #FF6B35; }
.p-danger .p-tag { color: #e63946; }

/* 操作行 */
.actions { display: flex; gap: 10px; margin: 6px 0 4px; }
.half { flex: 1; }

/* 菜谱卡片 */
.dish-card { display: flex; flex-direction: column; gap: 6px; }
.d-name { font-size: 16px; font-weight: 600; color: #222; display: flex; align-items: center; gap: 8px; }
.badge-ok {
  font-size: 11px; color: #fff; background: #2a9d8f;
  padding: 1px 7px; border-radius: 10px;
}
.badge-mid {
  font-size: 11px; color: #FF6B35; background: #fff1ea;
  padding: 1px 7px; border-radius: 10px;
}
.d-meta { display: flex; gap: 14px; }
.meta-item { font-size: 12px; color: #888; }
.d-missing { font-size: 12px; color: #999; }

.empty { color: #aaa; font-size: 13px; padding: 24px 0; text-align: center; }
.more {
  text-align: center; color: #FF6B35; font-size: 13px;
  padding: 14px 0;
}

/* 原生按钮复位 */
button::after { border: none; }
</style>
