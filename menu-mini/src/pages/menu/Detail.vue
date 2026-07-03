<template>
  <view class="page">
    <!-- 顶栏（自定义） -->
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="top-title">食集</text>
      <view class="back"></view>
    </view>

    <scroll-view scroll-y class="scroll" v-if="detail">
      <!-- 标题区 -->
      <view class="title-card">
        <view class="title-row">
          <text class="title">{{ detail.menu.name }}</text>
          <text :class="['status-chip', detail.menu.status === 'DONE' ? 'done' : 'active']">
            {{ detail.menu.status === 'DONE' ? '✓ 已完成' : '🍽 进行中' }}
          </text>
        </view>
        <view class="meta-row">
          <text class="meta-item">👥 {{ detail.menu.servingCount || 1 }} 人份</text>
          <text class="meta-dot">·</text>
          <text class="meta-item">🍳 包含 {{ detail.dishes.length }} 道菜</text>
        </view>
      </view>

      <!-- 包含菜品 -->
      <view class="block">
        <view class="block-title">
          <view class="tbar"></view>
          <text>包含菜品</text>
        </view>
        <view class="yh-card dish-card">
          <view
            class="dish-row"
            v-for="(d, i) in dishRows"
            :key="i"
          >
            <text class="dish-name">{{ d.name || `菜 #${d.dishId}` }}</text>
            <view class="dish-right">
              <text class="dish-amount">× {{ fmtFactor(d.servingFactor) }}</text>
              <text class="dish-unit">份</text>
            </view>
          </view>
          <view v-if="dishRows.length === 0" class="dish-row">
            <text class="dish-name" style="color: #9C8C7A;">本食集暂无菜品</text>
          </view>
        </view>
      </view>

      <view style="height: 160rpx;"></view>
    </scroll-view>

    <view v-else class="loading">加载中…</view>

    <!-- 底部整集做菜（Plan A） -->
    <view class="bottom-actions" v-if="detail">
      <button
        class="cook-menu-btn"
        :disabled="cooking || detail.menu.status === 'DONE'"
        @click="onCookMenu"
      >
        {{ cooking ? '做菜中…' : (detail.menu.status === 'DONE' ? '已完成' : '整集做菜') }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMenuDetail, type MenuDetailVO, type MenuDish } from '@/api/menu'
import { cookMenu, dishDetail } from '@/api/dish'

const detail = ref<MenuDetailVO | null>(null)
const menuId = ref(0)
const cooking = ref(false)

/** dishes 只带 dishId（无菜名），逐个拉菜名（食集规模小，可接受）。 */
const dishRows = ref<(MenuDish & { name?: string })[]>([])

onLoad(async (q: any) => {
  menuId.value = Number(q.id)
  try {
    detail.value = await getMenuDetail(menuId.value)
    // 拉菜名：并发请求（规模小）
    const rows = await Promise.all(
      detail.value.dishes.map(async (d) => {
        const name = await fetchName(d.dishId)
        return { ...d, name }
      }),
    )
    dishRows.value = rows
  } catch {
    uni.showToast({ title: '加载详情失败', icon: 'none' })
  }
})

async function fetchName(dishId: number): Promise<string | undefined> {
  try {
    const r = await dishDetail(dishId)
    return r?.dish?.name
  } catch {
    return undefined
  }
}

function fmtFactor(v?: number): string {
  if (v == null) return '1.0'
  // 整数显示无小数
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

/** 整集做菜：POST /menu/{id}/cook。
 *  扣 pantry + 每菜写 cooking_record + 食集标 DONE。
 *  欠量时提示缺几项。 */
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
    // 做完刷新状态（食集已标 DONE）
    detail.value = await getMenuDetail(menuId.value)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '做菜失败', icon: 'none' })
  } finally {
    cooking.value = false
  }
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

.loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9C8C7A;
  font-size: 14px;
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
</style>
