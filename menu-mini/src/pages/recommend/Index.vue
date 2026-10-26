<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 智荐（prototype Smart）：今晚首推大图 → 快到期先吃 → 你家常做 → 应季 → 最近做过 → 想吃点别的。
       数据：/recommend/home + /cookbook/recent；空分区隐藏。 -->
  <view class="page">
    <!-- 原「智荐」Tab → 咕嘟首屏的二级页「全部推荐」 -->
    <ui-back-header title="全部推荐">
      <template #subtitle>
        <view class="sub-row">
          <ui-avatar-stack v-if="faces.length" :items="faces" :size="18" />
          <text class="eyebrow">{{ eyebrow }}</text>
        </view>
      </template>
      <template #action>
        <view class="bell" aria-label="消息" data-testid="bell" @click="goNotice">
          <ui-icon name="bell" :size="20" />
          <text v-if="unread" class="bell-dot" />
        </view>
      </template>
    </ui-back-header>

    <ui-offline-bar />
    <view class="body">
      <ui-state v-if="homeLoading" mode="loading" skeleton="hero" />
      <ui-state v-else-if="homeError" mode="error" text="推荐没加载出来" :retry="true" @retry="loadHome" />
      <template v-else>
        <!-- AI 标识（A.4）：家里有特殊饮食人群时附免责提示 -->
        <view class="ai-row" data-testid="smart-ai"><ui-ai-badge source="recommend" :disclaimer="hasSpecialDiet" /></view>
        <!-- 今晚首推 -->
        <view v-if="heroCard" class="hero" data-testid="smart-hero" @click="goDish(heroCard.dishId)">
          <ui-dish-image :src="cardCover(heroCard)" :name="heroCard.name" ratio="4:3" :radius="22" original>
            <view class="hero-shade" />
            <view class="hero-tags">
              <text v-if="heroExpiry" class="tag-red">{{ heroExpiry }}</text>
              <view class="flex1" />
              <text class="tag-white">今晚首推</text>
            </view>
            <view class="hero-info">
              <text class="hero-name">{{ heroCard.name }}</text>
              <text class="hero-why">{{ heroWhy }}</text>
              <view class="hero-acts">
                <view class="hero-btn" @click.stop="goDish(heroCard.dishId)"><text class="hero-btn-txt">现在就做</text></view>
                <view v-if="heroPool.length > 1" class="hero-btn ghost" data-testid="hero-next" @click.stop="nextHero">
                  <text class="hero-btn-txt">换一道</text>
                </view>
              </view>
            </view>
          </ui-dish-image>
        </view>

        <!-- 快到期的，先吃掉 -->
        <view v-if="home.expiring.length" class="sec" data-testid="sec-expiring">
          <view class="sec-head">
            <text class="sec-title">快到期的，先吃掉</text>
            <text class="sec-more" @click="goPantry">余量 ›</text>
          </view>
          <scroll-view scroll-x class="hscroll" :show-scrollbar="false">
            <view class="hrow">
              <view v-for="e in home.expiring" :key="e.ingredientId" class="pill">
                <ui-ing-icon :icon-key="e.iconKey" :name="e.ingredientName" :size="24" bare />
                <text class="pill-name">{{ e.ingredientName }}</text>
                <ui-expiry-badge :days-left="e.daysLeft" />
              </view>
            </view>
          </scroll-view>
          <scroll-view v-if="restPicks.length" scroll-x class="hscroll" :show-scrollbar="false">
            <view class="hrow">
              <ui-dish-card
                v-for="d in restPicks"
                :key="d.dishId"
                :name="d.name"
                :cover="cardCover(d)"
                :coverage-label="coverageText(d.coverage)"
                :coverage-tone="coverageLevel(d.coverage)"
                :meta="cardMeta(d)"
                @tap="goDish(d.dishId)"
              />
            </view>
          </scroll-view>
        </view>

        <!-- 你家常做 -->
        <view v-if="home.frequent.length" class="sec" data-testid="sec-frequent">
          <text class="sec-title">你家常做</text>
          <scroll-view scroll-x class="hscroll" :show-scrollbar="false">
            <view class="hrow">
              <ui-dish-card
                v-for="d in home.frequent"
                :key="d.dishId"
                :name="d.name"
                :cover="cardCover(d)"
                :coverage-label="coverageText(d.coverage)"
                :coverage-tone="coverageLevel(d.coverage)"
                :meta="cardMeta(d)"
                @tap="goDish(d.dishId)"
              />
            </view>
          </scroll-view>
        </view>

        <!-- 应季 -->
        <view v-if="home.seasonal.length" class="sec" data-testid="sec-seasonal">
          <text class="sec-title">{{ monthLabel }}应季</text>
          <scroll-view scroll-x class="hscroll" :show-scrollbar="false">
            <view class="hrow">
              <ui-dish-card
                v-for="d in home.seasonal"
                :key="d.dishId"
                :name="d.name"
                :cover="cardCover(d)"
                :width="200"
                :ratio="0.62"
                :meta="cardMeta(d)"
                @tap="goDish(d.dishId)"
              />
            </view>
          </scroll-view>
        </view>

        <!-- 最近做过 -->
        <view v-if="recent.length" class="sec" data-testid="sec-recent">
          <view class="sec-head">
            <text class="sec-title">最近做过</text>
            <text class="sec-more" @click="goFoodlog">食记 ›</text>
          </view>
          <view v-for="r in recent.slice(0, 3)" :key="r.dishId" class="recent">
            <ui-dish-image :src="r.coverImage" :name="r.name" :width="56" :height="56" :radius="12" />
            <view class="recent-main" @click="goDish(r.dishId)">
              <text class="recent-name">{{ r.name }}</text>
              <text class="recent-sub">{{ relativeDate(r.lastCookedAt) }} · 做过 {{ r.cookedCount }} 次</text>
            </view>
            <view class="again" @click="goDish(r.dishId)"><text class="again-txt">再做一次</text></view>
          </view>
        </view>
      </template>

      <view class="ask-wrap">
        <ask-block ref="askRef" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { coverageText, coverageLevel, cardCover, type DishCard } from '@/api/dish'
import { recommendHome, recentCooked, type RecommendHome, type RecentCooked } from '@/api/recommend'
import { listMembers } from '@/api/member'
import { unreadCount } from '@/api/notification'
import { relativeDate } from '@/utils/datetime'
import { expiryText } from '@/utils/freshness'
import AskBlock from './parts/AskBlock.vue'
import { goTab } from '@/utils/nav'

const askRef = ref<InstanceType<typeof AskBlock> | null>(null)
const faces = ref<{ name: string; tags?: string[] | null }[]>([])
/** 家里有特殊饮食人群（高血压 / 宝宝辅食 …）：推荐与其相关，需显示免责提示。 */
const hasSpecialDiet = computed(() => faces.value.some((f) => (f.tags?.length ?? 0) > 0))
const unread = ref(0)

onShow(() => {
  askRef.value?.loadDefault()
  loadHome()
  listMembers()
    .then((ms) => (faces.value = ms.map((m) => ({ name: m.name, tags: m.audienceTags }))))
    .catch(() => {})
  unreadCount().then((n) => (unread.value = n))
})

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const eyebrow = computed(() => {
  const d = new Date()
  const h = d.getHours()
  const slot = h < 10 ? '早上' : h < 14 ? '中午' : h < 17 ? '下午' : '晚上'
  const n = faces.value.length
  return `${WEEK[d.getDay()]}${slot}${n ? ` · ${n} 人吃饭` : ''}`
})
const monthLabel = computed(() => `${['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'][new Date().getMonth()]}月`)

// ---- 首页聚合 ----
const homeLoading = ref(true)
const homeError = ref(false)
const homeLoaded = ref(false)
const home = reactive<RecommendHome>({ expiring: [], expiringPicks: [], frequent: [], seasonal: [] })
const recent = ref<RecentCooked[]>([])

/** 首次骨架；再次 onShow 静默刷新。两个接口都失败才进错误态。 */
async function loadHome() {
  if (!homeLoaded.value) homeLoading.value = true
  homeError.value = false
  const [h, r] = await Promise.allSettled([recommendHome(), recentCooked(10)])
  if (h.status === 'fulfilled') Object.assign(home, h.value)
  if (r.status === 'fulfilled') recent.value = r.value
  if (h.status === 'rejected' && r.status === 'rejected') homeError.value = !homeLoaded.value
  else homeLoaded.value = true
  homeLoading.value = false
}

// 首推候选：临期主推优先，其次你家常做
const heroPool = computed<DishCard[]>(() => (home.expiringPicks.length ? home.expiringPicks : home.frequent))
const heroIdx = ref(0)
const heroCard = computed(() => heroPool.value[heroIdx.value % Math.max(1, heroPool.value.length)] || null)
const restPicks = computed(() => home.expiringPicks.filter((d) => d.dishId !== heroCard.value?.dishId))
function nextHero() {
  heroIdx.value = (heroIdx.value + 1) % heroPool.value.length
}
const heroExpiry = computed(() => {
  if (!home.expiringPicks.length || !home.expiring.length) return ''
  const e = home.expiring[0]
  const d = e.daysLeft
  const t = d < 0 ? '已过期' : d === 0 ? '今天到期' : d === 1 ? '明天到期' : expiryText(d)
  return `${e.ingredientName}${t}`
})
const heroWhy = computed(() => {
  const d = heroCard.value
  if (!d) return ''
  const parts: string[] = []
  if (home.expiringPicks.length && home.expiring.length) parts.push(`先用掉快到期的${home.expiring[0].ingredientName}`)
  if (coverageText(d.coverage)) parts.push(coverageText(d.coverage))
  if (d.cookMinutes) parts.push(`${d.cookMinutes} 分钟`)
  return parts.join(' · ')
})

function cardMeta(d: DishCard): string {
  const parts: string[] = []
  if (d.cookMinutes) parts.push(`${d.cookMinutes} 分钟`)
  parts.push(d.cookedCount > 0 ? `做过 ${d.cookedCount} 次` : '没做过')
  return parts.join(' · ')
}

function goPantry() {
  goTab('pantry')
}
function goFoodlog() {
  uni.navigateTo({ url: '/pages/foodlog/Index' })
}
function goNotice() {
  uni.navigateTo({ url: '/pages/notice/Index' })
}
function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.sub-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.eyebrow {
  font-size: var(--fs-sm);
  color: var(--body);
}
.bell {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.bell-dot {
  position: absolute;
  right: 9px;
  top: 9px;
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--error);
  border: 2px solid var(--card);
}
.body {
  padding: var(--space-xs) var(--space-lg) var(--space-xxl);
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}
.hero {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: 0 10px 28px rgba(var(--sh), 0.2);
}
.hero-shade {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.05) 30%, rgba(0, 0, 0, 0.7));
}
.hero-tags {
  position: absolute;
  left: var(--space-md);
  right: var(--space-md);
  top: var(--space-md);
  display: flex;
  align-items: center;
}
.flex1 {
  flex: 1;
}
.tag-red,
.tag-white {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.tag-red {
  background: var(--error);
  color: var(--on-primary);
}
.tag-white {
  background: var(--card);
  color: var(--title);
}
.hero-info {
  position: absolute;
  left: var(--space-lg);
  right: var(--space-lg);
  bottom: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hero-name {
  font-size: var(--fs-h2);
  font-weight: 800;
  color: var(--on-primary);
}
.hero-why {
  font-size: var(--fs-sm);
  color: var(--on-primary);
  opacity: 0.92;
}
.hero-acts {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.hero-btn {
  flex: 1.4;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-btn.ghost {
  flex: 1;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.6);
}
.hero-btn-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--on-primary);
}
.sec {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sec-title {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.sec-more {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.hscroll {
  white-space: nowrap;
  margin: 0 calc(-1 * var(--space-lg));
  width: auto;
}
.hrow {
  display: inline-flex;
  gap: var(--space-md);
  padding: 0 var(--space-lg);
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 6px 8px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
  flex-shrink: 0;
}
.pill-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.recent {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.recent-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recent-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.recent-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.again {
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--primary-soft);
  background: var(--highlight);
}
.again-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.ask-wrap {
  padding-top: var(--space-sm);
  border-top: 1px solid var(--border);
}
.ai-row {
  margin-bottom: var(--sp-12);
}
</style>
