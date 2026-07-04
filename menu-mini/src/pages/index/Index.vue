<template>
  <view class="page">
    <!-- 顶：日期 + 今天吃点什么？ + 头像 -->
    <view class="hero">
      <view class="hero-info">
        <text class="hero-date">{{ dateStr }}</text>
        <text class="hero-title">今天吃点什么？</text>
      </view>
      <view class="avatar" @click="onSwitchMember">
        <text class="avatar-emoji">🧑</text>
      </view>
    </view>

    <!-- 主推荐：临期驱动渐变卡 -->
    <view class="rec-main" v-if="mainRec">
      <text class="rec-flag">⏰ 临期提醒 · 优先用掉</text>
      <text class="rec-expire">{{ mainRec.expireHint }}</text>
      <view class="rec-dish" @click="goDetail(mainRec)">
        <view class="rec-emoji-wrap"><text class="rec-emoji">{{ mainRec.emoji }}</text></view>
        <view class="rec-dish-info">
          <text class="rec-dish-name">{{ mainRec.name }}</text>
          <text class="rec-dish-meta">{{ mainRec.meta }}</text>
        </view>
      </view>
      <view class="rec-btns">
        <view class="rec-btn rec-btn-pri" @click="cookNow(mainRec)">✨ 今天做</view>
        <view class="rec-btn rec-btn-ghost" @click="addMenu(mainRec)">🍱 加食集</view>
      </view>
    </view>

    <!-- 副推荐横滑 -->
    <scroll-view scroll-x class="sub-scroll" :show-scrollbar="false" v-if="subRecs.length">
      <view class="sub-row">
        <view class="sub-card" v-for="(r, i) in subRecs" :key="i" @click="goDetail(r)">
          <text class="sub-tag" :class="r.tagClass">{{ r.tag }}</text>
          <view class="sub-dish">
            <text class="sub-emoji">{{ r.emoji }}</text>
            <text class="sub-name">{{ r.name }}</text>
          </view>
          <text class="sub-meta">{{ r.meta }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 找菜四宫格 -->
    <view class="sec-label">找菜</view>
    <view class="find-grid">
      <view class="find-cell" @click="goSearch">
        <view class="find-ico">🔍</view>
        <text class="find-name">搜菜名</text>
      </view>
      <view class="find-cell" @click="go('/pages/cookbook/FindByIngredients')">
        <view class="find-ico">🥕</view>
        <text class="find-name">按食材找</text>
      </view>
      <view class="find-cell" @click="go('/pages/dish/List')">
        <view class="find-ico">📖</view>
        <text class="find-name">逛菜谱库</text>
      </view>
      <view class="find-cell" @click="go('/pages/ai/Recommend')">
        <view class="find-ico">✨</view>
        <text class="find-name">AI 帮我</text>
      </view>
    </view>

    <!-- 最近做过 -->
    <block v-if="recent.length">
      <view class="sec-label">最近做过</view>
      <view class="recent-row">
        <view class="recent-card" v-for="(r, i) in recent" :key="i" @click="goDetail(r)">
          <text class="recent-emoji">{{ r.emoji }}</text>
          <view class="recent-info">
            <text class="recent-name">{{ r.name }}</text>
            <text class="recent-time">{{ r.time }}</text>
          </view>
        </view>
      </view>
    </block>

    <view style="height: 200rpx;"></view>
    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import { listMembers, setCurrentMember } from '@/api/member'
import { aiRecommendMenu } from '@/api/ai'
import CustomTabBar from '@/components/CustomTabBar.vue'

const m = useMemberStore()
const mainRec = ref<any>(null)
const subRecs = ref<any[]>([])
const recent = ref<any[]>([])

const dateStr = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const meal = d.getHours() < 15 ? '午餐' : '晚餐'
  return `${d.getMonth() + 1}/${d.getDate()} 周${week} · ${meal}`
})

// 食物 emoji 兜底（菜名无 emoji 字段时按关键字推一个，对齐原型 emoji 风格）
function pickEmoji(name: string, idx = 0): string {
  if (!name) return ['🍅', '🐟', '🥬', '🍖', '🍲'][idx % 5]
  if (/蛋/.test(name)) return '🍳'
  if (/鸡|鸭|鹅/.test(name)) return '🍗'
  if (/鱼|虾|蟹|贝|鲈|带鱼|三文鱼/.test(name)) return '🐟'
  if (/牛|羊|猪|肉|排骨|里脊/.test(name)) return '🍖'
  if (/汤|煲|炖/.test(name)) return '🍲'
  if (/面|粉|米线/.test(name)) return '🍜'
  if (/沙拉|凉拌|菜|蔬|菠|芹|生|瓜|青菜/.test(name)) return '🥬'
  if (/豆腐|豆干|豆/.test(name)) return '🥘'
  return ['🍅', '🐟', '🥬', '🍖', '🍲'][(name.length + idx) % 5]
}

const SUB_TAGS = [
  { tag: '🍂 季节鲜', tagClass: 'tag-green' },
  { tag: '🔁 你常做', tagClass: 'tag-yellow' },
  { tag: '💡 好评高', tagClass: 'tag-orange' },
]

function toRec(d: any, idx = 0) {
  return {
    dishId: d.dishId || d.id,
    name: d.name,
    emoji: pickEmoji(d.name, idx),
    meta: idx === 0
      ? '家里够 · 简单快手'
      : (d.servingFactor && d.servingFactor !== 1 ? `×${d.servingFactor} 份` : '推荐试试'),
    expireHint: idx === 0 ? '你家「鸡蛋」还剩 3 天' : '',
    tag: SUB_TAGS[(idx - 1 + SUB_TAGS.length) % SUB_TAGS.length].tag,
    tagClass: SUB_TAGS[(idx - 1 + SUB_TAGS.length) % SUB_TAGS.length].tagClass,
    time: '昨天',
  }
}

async function loadHome() {
  // 主推 + 副推：AI 推荐方案（当前 mock，待接 GLM）；空则不显主推卡（降级）
  try {
    const groups = await aiRecommendMenu({ scope: 'DAY' })
    const dishes = groups && groups[0] ? (groups[0].dishes || []) : []
    if (dishes.length) {
      mainRec.value = toRec(dishes[0], 0)
      subRecs.value = dishes.slice(1, 4).map((d: any, i: number) => toRec(d, i + 1))
    }
  } catch {}
  // 最近做过：待接 cooking_record 列表（暂留空，有数据再展示）
}

function go(url: string) {
  uni.navigateTo({ url, fail: () => uni.switchTab({ url }) })
}
function goSearch() {
  uni.switchTab({ url: '/pages/dish/List' })
}
function goDetail(r: any) {
  if (r.dishId) uni.navigateTo({ url: `/pages/dish/Detail?id=${r.dishId}` })
}
function cookNow(r: any) {
  // 单菜直做：进菜谱详情（含"直接做"按钮，走 Plan A cook-now 扣库存）
  if (r.dishId) uni.navigateTo({ url: `/pages/dish/Detail?id=${r.dishId}` })
}
function addMenu(_r: any) {
  // 加食集：跳食集页选目标食集
  uni.switchTab({ url: '/pages/menu/Home', fail: () => uni.navigateTo({ url: '/pages/menu/Home' }) })
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

onShow(() => {
  m.load()
  loadHome()
})
</script>

<style scoped>
.page {
  background: #FDFAF4;
  min-height: 100vh;
  padding: 0 28rpx 40rpx;
}

/* hero */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: calc(env(safe-area-inset-top) + 36rpx) 8rpx 0;
}
.hero-info { display: flex; flex-direction: column; gap: 4rpx; }
.hero-date { font-size: 22rpx; color: #9C8C7A; }
.hero-title { font-size: 42rpx; font-weight: 800; color: #4A382A; }
.avatar {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: #F6D9BE;
  display: flex; align-items: center; justify-content: center;
}
.avatar-emoji { font-size: 36rpx; }

/* 主推渐变卡 */
.rec-main {
  margin-top: 28rpx;
  background: linear-gradient(135deg, #E89150, #D17A3C);
  border-radius: 32rpx;
  padding: 32rpx;
  box-shadow: 0 14rpx 36rpx rgba(169, 101, 30, 0.18);
  color: #fff;
}
.rec-flag { font-size: 20rpx; opacity: 0.9; letter-spacing: 1px; display: block; }
.rec-expire {
  display: inline-block;
  margin-top: 16rpx;
  font-size: 22rpx;
  background: rgba(255, 255, 255, 0.18);
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
}
.rec-dish {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 20rpx;
}
.rec-emoji-wrap {
  width: 92rpx; height: 92rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.25);
  display: flex; align-items: center; justify-content: center;
}
.rec-emoji { font-size: 48rpx; }
.rec-dish-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.rec-dish-name { font-size: 32rpx; font-weight: 800; }
.rec-dish-meta { font-size: 20rpx; opacity: 0.9; }
.rec-btns { display: flex; gap: 12rpx; margin-top: 22rpx; }
.rec-btn {
  flex: 1; text-align: center;
  font-size: 24rpx; font-weight: 800;
  padding: 16rpx 0; border-radius: 18rpx;
}
.rec-btn-pri { background: #fff; color: #D17A3C; }
.rec-btn-ghost { background: rgba(255, 255, 255, 0.22); color: #fff; }

/* 副推横滑 */
.sub-scroll { margin-top: 24rpx; white-space: nowrap; }
.sub-row { display: inline-flex; gap: 16rpx; padding: 4rpx; }
.sub-card {
  display: inline-block;
  width: 260rpx;
  background: #fff;
  border: 1px solid #F0E6D6;
  border-radius: 24rpx;
  padding: 20rpx;
  box-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.04);
  vertical-align: top;
}
.sub-tag { display: block; font-size: 18rpx; font-weight: 800; }
.tag-green { color: #4FAE6E; }
.tag-yellow { color: #E5A938; }
.tag-orange { color: #E89150; }
.sub-dish { display: flex; align-items: center; gap: 14rpx; margin-top: 12rpx; }
.sub-emoji { font-size: 36rpx; }
.sub-name { font-size: 26rpx; font-weight: 800; color: #4A382A; }
.sub-meta { display: block; font-size: 18rpx; color: #9C8C7A; margin-top: 8rpx; }

/* section 标签 */
.sec-label {
  font-size: 20rpx;
  font-weight: 800;
  color: #9C8C7A;
  letter-spacing: 1px;
  margin: 32rpx 8rpx 14rpx;
}

/* 找菜四宫格 */
.find-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.find-cell {
  background: #fff;
  border: 1px solid #F0E6D6;
  border-radius: 24rpx;
  padding: 22rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.find-ico {
  width: 60rpx; height: 60rpx;
  border-radius: 16rpx;
  background: #FBF0DD;
  display: flex; align-items: center; justify-content: center;
  font-size: 30rpx;
}
.find-name { font-size: 26rpx; font-weight: 700; color: #4A382A; }

/* 最近做过 */
.recent-row { display: flex; gap: 14rpx; }
.recent-card {
  flex: 1;
  background: #FBF0DD;
  border-radius: 20rpx;
  padding: 16rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
}
.recent-emoji { font-size: 32rpx; }
.recent-info { display: flex; flex-direction: column; gap: 2rpx; }
.recent-name { font-size: 22rpx; color: #6E5C49; font-weight: 700; }
.recent-time { font-size: 18rpx; color: #9C8C7A; }
</style>
