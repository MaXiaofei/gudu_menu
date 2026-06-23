<template>
  <view class="page">
    <!-- 问候区（对齐 Flutter GreetingHeader） -->
    <view class="greeting">
      <view class="g-row">
        <text class="g-hi">{{ greeting }}，{{ memberName }}</text>
      </view>
      <text class="g-quote">{{ todayQuote }}</text>
    </view>

    <!-- 今日推荐渐变卡（对齐 Flutter AppCard + primaryVertical） -->
    <view class="rec-card" @click="onRecommend">
      <view class="rec-top" :style="{ background: 'linear-gradient(180deg, #FF8C42, #E6762A)' }">
        <view class="rec-head">
          <text class="rec-flame">🔥</text>
          <text class="rec-label">今日推荐</text>
        </view>
        <text class="rec-title">今天给全家做点啥？</text>
        <text class="rec-sub">{{ recommending ? 'AI 想菜中…' : '打开 AI 帮你换换灵感 →' }}</text>
      </view>
    </view>

    <!-- 功能宫格（对齐 Flutter 2×3） -->
    <view class="block-title">
      <view class="tbar"></view>
      <text>常用功能</text>
    </view>
    <view class="grid">
      <view class="grid-cell" @click="go('/pages/dish/List')">
        <view class="cell-ico" style="background: rgba(255,159,90,0.15); color:#FF9F5A;">📖</view>
        <text class="cell-name">菜库</text>
      </view>
      <view class="grid-cell" @click="go('/pages/pantry/List')">
        <view class="cell-ico" style="background: rgba(111,191,142,0.15); color:#6FBF8E;">🧊</view>
        <text class="cell-name">食材库存</text>
      </view>
      <view class="grid-cell" @click="go('/pages/mealplan/Calendar')">
        <view class="cell-ico" style="background: rgba(107,168,232,0.15); color:#6BA8E8;">📅</view>
        <text class="cell-name">周计划</text>
      </view>
      <view class="grid-cell" @click="go('/pages/shopping/List')">
        <view class="cell-ico" style="background: rgba(232,163,61,0.15); color:#E8A33D;">🛒</view>
        <text class="cell-name">采购清单</text>
      </view>
      <view class="grid-cell" @click="go('/pages/dailylog/Index')">
        <view class="cell-ico" style="background: rgba(176,123,216,0.15); color:#B07BD8;">📝</view>
        <text class="cell-name">饮食记录</text>
      </view>
      <view class="grid-cell" @click="go('/pages/ai/Recommend')">
        <view class="cell-ico" style="background: rgba(224,123,123,0.15); color:#E07B7B;">✨</view>
        <text class="cell-name">AI 帮我</text>
      </view>
    </view>

    <view style="height: 80rpx;"></view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import { listMembers, getCurrentMember, setCurrentMember } from '@/api/member'
import { aiRecommendMenu } from '@/api/ai'

const m = useMemberStore()
const memberName = computed(() => {
  const cur = m.members.find((x: any) => x.id === m.currentId)
  return cur?.name || '掌勺人'
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早安'
  if (h < 14) return '午安'
  if (h < 18) return '下午好'
  return '晚上好'
})

// Flutter 版金句（完全对齐 home_page.dart _quotes）
const quotes = [
  '三餐四季，不过一碗人间烟火',
  '是谁来自山川湖海，却囿于昼夜、厨房与爱',
  '食一碗人间烟火，饮几杯人生起落',
  '四方食事，不过一碗烟火',
  '人生忽如寄，莫辜负茶、汤和好天气',
  '把日子炖成一锅汤，小火慢熬才有味道',
  '总有一顿饭，让你想起家的方向',
  '味道是时间的信使，一口回到从前',
  '酸甜苦辣过后，碗里剩的都是温柔',
  '风吹炉火，人间值得',
  '每一缕油烟升起的地方，都藏着深情',
  '此心安处，便是家宴',
  '让食材在锅里讲一个温暖的故事',
  '日落归山海，饭菜归家常',
]
// Flutter：按天取一条（不轮播全部）
const todayQuote = computed(() => {
  const day = Math.floor((Date.now() - new Date(2026, 0, 1).getTime()) / 86400000)
  return quotes[((day % quotes.length) + quotes.length) % quotes.length]
})

const recommending = ref(false)

function go(url: string) {
  uni.navigateTo({ url, fail: () => uni.switchTab({ url }) })
}

async function onSwitchMember() {
  try {
    const members = await listMembers()
    if (!members || !members.length) return
    const names = members.map((x: any) => x.name)
    uni.showActionSheet({
      itemList: names,
      success: async (r) => {
        const picked = members[r.tapIndex]
        if (picked) {
          await setCurrentMember(picked.id)
          m.currentId = picked.id
        }
      },
    })
  } catch {}
}

async function onRecommend() {
  if (recommending.value) return
  recommending.value = true
  try {
    const groups = await aiRecommendMenu({ scope: 'DAY' })
    if (groups && groups.length) {
      const first = groups[0]
      const names = (first.dishes || []).map((d: any) => d.name).join('、')
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

onShow(() => { m.load() })
</script>

<style scoped>
.page {
  background: #FFFBF5;
  min-height: 100vh;
  padding: 0 36rpx calc(env(safe-area-inset-bottom) + 40rpx);
}

/* 问候区（对齐 Flutter GreetingHeader） */
.greeting {
  padding: calc(env(safe-area-inset-top) + 48rpx) 0 0;
}
.g-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.g-hi {
  font-size: 48rpx;
  font-weight: bold;
  color: #2D2A26;
}
.g-switch {
  background: rgba(255, 140, 66, 0.12);
  border-radius: 32rpx;
  padding: 8rpx 24rpx;
}
.g-chip {
  font-size: 26rpx;
  color: #FF8C42;
  font-weight: 500;
}
.g-quote {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #9B958C;
  line-height: 1.6;
}

/* 今日推荐卡（对齐 Flutter primaryVertical 渐变） */
.rec-card {
  margin-top: 40rpx;
  border-radius: 36rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(255, 140, 66, 0.25);
}
.rec-top {
  padding: 44rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.rec-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.rec-flame { font-size: 28rpx; }
.rec-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
}
.rec-title {
  font-size: 40rpx;
  font-weight: bold;
  color: #FFFFFF;
}
.rec-sub {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.92);
}

/* 宫格标题 */
.block-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 48rpx 0 24rpx;
}
.tbar {
  width: 8rpx;
  height: 36rpx;
  background: #FF8C42;
  border-radius: 4rpx;
}
.block-title text {
  font-size: 34rpx;
  font-weight: bold;
  color: #2D2A26;
}

/* 宫格 */
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 24rpx;
}
.grid-cell {
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.05);
  padding: 32rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}
.cell-ico {
  width: 88rpx;
  height: 88rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
}
.cell-name {
  font-size: 26rpx;
  color: #2D2A26;
  font-weight: 500;
}
</style>
