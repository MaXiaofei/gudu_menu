<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 话题挑战（prototype Topic，周期周一至周日）：头图拼贴 → 我的进度（7 天）→ 参与按钮 → 排行前 5 → 最新作品。 -->
  <view class="page">
    <ui-state v-if="loading" mode="loading" skeleton="hero" />
    <ui-state v-else-if="!topic" mode="error" text="话题打不开了" :retry="true" @retry="load" />
    <scroll-view v-else scroll-y class="body" @scrolltolower="works.loadMore()">
      <view class="hero">
        <view class="pics">
          <ui-dish-image v-for="(u, i) in topic.coverUrls.slice(0, 2)" :key="i" :src="u" :height="250" :radius="0" original />
        </view>
        <view class="shade" />
        <view class="hero-bar" :style="{ top: sb + 'px' }">
          <view class="circle" @click="goBack"><ui-icon name="back" :size="20" /></view>
          <view class="flex1" />
          <button class="circle share" open-type="share"><ui-icon name="share" :size="18" /></button>
        </view>
        <view class="hero-info">
          <view class="kicker"><ui-icon name="trophy" tone="white" :size="12" /><text class="kicker-txt">厨房圈话题挑战{{ weekNoText(topic) ? ' · ' + weekNoText(topic) : '' }} · {{ shortDate(topic.startDate) }} – {{ shortDate(topic.endDate) }}</text></view>
          <text class="name">#{{ topic.name }}</text>
          <text class="intro">{{ topic.intro }}</text>
          <view class="stats">
            <text class="stat">{{ countText(topic.participantCount) }} 人参与</text>
            <text class="stat">{{ countText(topic.postCount) }} 作品</text>
            <text class="stat">{{ daysLeftText }}</text>
          </view>
        </view>
      </view>

      <view class="pad">
        <!-- 我的进度 -->
        <view class="card">
          <view class="prog-head">
            <view>
              <text class="lab">我的进度</text>
              <text class="prog">本周已打卡 <text class="prog-n">{{ topic.myProgress.doneCount }}</text> / 7 天</text>
            </view>
            <text class="prog-tip">{{ progressTip }}</text>
          </view>
          <view class="days">
            <view v-for="(d, i) in days" :key="d.date" class="day" :class="{ today: d.date === todayIso }">
              <view class="c" :class="{ done: d.done, today: d.date === todayIso && !d.done }">
                <image v-if="d.done && d.coverUrl" class="c-img" :src="thumbOf(d.coverUrl)" mode="aspectFill" />
                <ui-icon v-else-if="d.date === todayIso" name="plus" tone="primary" :size="14" />
                <view v-if="d.done" class="c-tick"><ui-icon name="check" tone="white" :size="8" /></view>
              </view>
              <text class="day-lb">{{ d.date === todayIso ? '今天' : WEEKDAYS[i] }}</text>
            </view>
          </view>
          <view class="bar"><view class="bar-in" :style="{ width: (topic.myProgress.doneCount / 7) * 100 + '%' }" /></view>
          <view class="acts">
            <view class="btn primary" data-testid="topic-post" @click="goCreate"><ui-icon name="plus" tone="white" :size="16" /><text class="btn-txt on">发布今天的作品</text></view>
            <view class="btn" data-testid="topic-ask" @click="askGudu"><ui-icon name="sparkle" tone="primary" :size="16" /><text class="btn-txt">问咕嘟做什么</text></view>
          </view>
          <text class="rule">{{ topic.rule }}</text>
        </view>

        <!-- 排行 -->
        <view class="sec">
          <view class="sec-head"><text class="sec-title">打卡排行</text><text class="sec-hint">按打卡天数 · 获赞</text></view>
          <view class="card list">
            <view v-for="r in topic.ranking.slice(0, 5)" :key="r.user.userId" class="rank">
              <text class="medal" :class="'m' + r.rank">{{ r.rank }}</text>
              <ui-avatar :name="r.user.nickname" :src="r.user.avatarUrl ? thumbOf(r.user.avatarUrl) : null" :size="36" />
              <view class="rank-main">
                <text class="rank-nick">{{ r.user.nickname }}</text>
                <text class="rank-sub">打卡 {{ r.days }} 天 · 获赞 {{ countText(r.likeCount) }}</text>
              </view>
              <text v-if="r.days >= 7 || r.days >= passedDays" class="full">全勤</text>
            </view>
            <view v-if="topic.myRank" class="rank me">
              <text class="medal">{{ topic.myRank }}</text>
              <text class="rank-nick">我 · 第 {{ topic.myRank }} 名</text>
            </view>
          </view>
        </view>

        <!-- 最新作品 -->
        <view class="sec">
          <text class="sec-title">最新作品</text>
          <view class="works">
            <view v-for="p in works.items" :key="p.postId" class="work" @click="openPost(p.postId)">
              <ui-dish-image :src="p.coverUrl" :name="p.title" ratio="1:1" :radius="12" />
              <text class="work-title">{{ p.title }}</text>
              <view class="work-meta">
                <ui-avatar :name="p.author.nickname" :src="p.author.avatarUrl ? thumbOf(p.author.avatarUrl) : null" :size="16" />
                <text class="work-nick">{{ p.author.nickname }}</text>
                <text class="work-like">♥ {{ countText(p.likeCount) }}</text>
              </view>
            </view>
          </view>
          <text v-if="works.loading" class="more">加载中…</text>
        </view>
      </view>
    </scroll-view>
    <!-- 厨房圈紧急关停（A.12）：后端返回「厨房圈暂停服务」时整页提示 -->
    <ui-community-paused />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { countText, feed, topicDetail, weekNoText, type PostCard, type TopicDetail } from '@/api/community'
import { createCursorList } from '@/utils/cursor'
import { thumbOf } from '@/utils/image'
import { statusBarHeight } from '@/utils/token'
import { WEEKDAYS, isoDate, parseIsoDate, shortDate, weekDays } from '@/utils/week'
import { PAGES, withQuery } from '@/utils/nav'

const sb = statusBarHeight() + 6
const id = ref(0)
const topic = ref<TopicDetail | null>(null)
const loading = ref(true)
const todayIso = isoDate(new Date())
const works = createCursorList<PostCard>((c) => feed('topic', c, { topicId: id.value }), (p) => p.postId)

onLoad((q: any) => {
  id.value = Number(q?.id) || 0
  load()
})
onShareAppMessage(() => ({ title: `#${topic.value?.name || '话题挑战'}，一起来打卡`, path: withQuery(PAGES.topic, { id: id.value }) }))

async function load() {
  loading.value = true
  try {
    topic.value = await topicDetail(id.value)
    works.refresh()
  } catch {
    topic.value = null
  } finally {
    loading.value = false
  }
}

/** 周一至周日 7 个圆点：以挑战开始日所在周为准，后端 days 缺的日补空。 */
const days = computed(() => {
  const t = topic.value
  if (!t) return []
  const byDate = new Map(t.myProgress.days.map((d) => [d.date, d]))
  return weekDays(parseIsoDate(t.startDate)).map((w) => byDate.get(w.date) || { date: w.date, done: false, coverUrl: null })
})
const passedDays = computed(() => days.value.filter((d) => d.date <= todayIso).length)
const daysLeftText = computed(() => {
  const t = topic.value
  if (!t) return ''
  const left = Math.round((parseIsoDate(t.endDate).getTime() - parseIsoDate(todayIso).getTime()) / 86400000)
  return left < 0 ? '已结束' : left === 0 ? '今天截止' : `还剩 ${left} 天`
})
const progressTip = computed(() => {
  const n = topic.value?.myProgress.doneCount || 0
  return n >= 7 ? '已拿到「不重样」徽章' : `再打卡 ${7 - n} 天得徽章`
})

function goCreate() {
  uni.navigateTo({ url: withQuery(PAGES.postCreate, { topicId: id.value }) })
}
function askGudu() {
  uni.navigateTo({ url: withQuery(PAGES.agentChat, { q: `#${topic.value?.name} 今天做什么不重样？` }) })
}
function openPost(pid: number) {
  uni.navigateTo({ url: withQuery(PAGES.post, { id: pid }) })
}
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/community/Index' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  background: var(--bg);
}
.body {
  height: 100vh;
}
.hero {
  position: relative;
  height: 250px;
}
.pics {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15), rgba(40, 24, 12, 0.85));
}
.hero-bar {
  position: absolute;
  left: 12px;
  right: 12px;
  display: flex;
}
.flex1 {
  flex: 1;
}
.circle {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
}
.circle.share {
  margin: 0;
  padding: 0;
  line-height: 1;
}
.circle.share::after {
  border: none;
}
.hero-info {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 30px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.kicker {
  display: flex;
  align-items: center;
  gap: 4px;
}
.kicker-txt {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--primary-soft);
}
.name {
  font-size: var(--fs-h2);
  font-weight: 800;
  color: var(--on-primary);
}
.intro {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--on-primary);
  opacity: 0.9;
}
.stats {
  display: flex;
  gap: 6px;
}
.stat {
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.18);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.pad {
  position: relative;
  margin-top: -18px;
  padding: 0 var(--space-lg) var(--space-xxl);
}
.card {
  padding: 14px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: 0 4px 16px rgba(var(--sh), 0.1);
}
.prog-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.lab {
  display: block;
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.prog {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.prog-n {
  font-size: var(--fs-h3);
  color: var(--primary-deep);
}
.prog-tip {
  font-size: var(--fs-xs);
  color: var(--body);
}
.days {
  display: flex;
  justify-content: space-between;
  margin: var(--space-md) 0 var(--space-sm);
}
.day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.c {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.c.today {
  border: 2px dashed var(--primary);
  background: var(--card);
}
.c-img {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
}
.c-tick {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 14px;
  height: 14px;
  border-radius: var(--radius-pill);
  background: var(--success);
  border: 2px solid var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.day-lb {
  font-size: var(--fs-xs);
  color: var(--body);
}
.day.today .day-lb {
  color: var(--accent);
  font-weight: 700;
}
.bar {
  height: 6px;
  border-radius: 3px;
  background: var(--secondary);
  overflow: hidden;
}
.bar-in {
  height: 100%;
  background: var(--primary);
}
.acts {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}
.btn {
  flex: 1;
  height: 44px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.btn.primary {
  flex: 1.3;
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.btn-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--accent);
}
.btn-txt.on {
  color: var(--on-primary);
}
.rule {
  display: block;
  margin-top: var(--space-sm);
  font-size: var(--fs-xs);
  color: var(--body);
  line-height: 1.6;
}
.sec {
  margin-top: var(--space-xl);
}
.sec-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.sec-title {
  display: block;
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
  margin-bottom: var(--space-md);
}
.sec-hint {
  font-size: var(--fs-xs);
  color: var(--body);
}
.list {
  padding: 4px 14px;
}
.rank {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.rank:last-child {
  border-bottom: none;
}
.medal {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-pill);
  background: var(--secondary);
  text-align: center;
  line-height: 22px;
  font-size: var(--fs-xs);
  font-weight: 800;
  color: var(--body);
}
.medal.m1 {
  background: var(--warning);
  color: var(--on-primary);
}
.medal.m2 {
  background: var(--caption);
  color: var(--on-primary);
}
.medal.m3 {
  background: var(--accent);
  color: var(--on-primary);
}
.rank-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.rank-nick {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.rank-sub {
  font-size: var(--fs-xs);
  color: var(--body);
}
.full {
  padding: 1px 8px;
  border-radius: var(--radius-xs);
  background: var(--success-soft);
  color: var(--success-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.rank.me {
  background: var(--highlight);
  margin: 0 -14px;
  padding: 10px 14px;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
.works {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md) var(--space-sm);
}
.work {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.work-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.work-meta {
  display: flex;
  align-items: center;
  gap: 4px;
}
.work-nick {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--body);
}
.work-like {
  font-size: var(--fs-xs);
  color: var(--body);
}
.more {
  display: block;
  text-align: center;
  padding: var(--space-md);
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
