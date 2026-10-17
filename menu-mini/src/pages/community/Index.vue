<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 厨房圈（prototype Community）：推荐 / 关注 / 话题；话题挑战横幅；关注的厨友更新；双列瀑布流（游标分页）。 -->
  <view class="page">
    <ui-tab-header title="厨房圈">
      <view class="post-btn" data-testid="go-create" @click="goCreate()">
        <ui-icon name="plus" tone="white" :size="14" /><text class="post-txt">发布</text>
      </view>
      <ui-me-avatar />
    </ui-tab-header>

    <view class="seg-wrap">
      <ui-segment v-model="tab" :items="tabs" />
    </view>
    <ui-offline-bar />

    <view class="pad">
      <!-- 话题挑战横幅 -->
      <view v-if="topic && tab !== 'follow'" class="banner" data-testid="topic-banner" @click="goTopic(topic.topicId)">
        <view class="banner-pics">
          <ui-dish-image v-for="(u, i) in topic.coverUrls.slice(0, 3)" :key="i" :src="u" :height="96" :radius="0" />
        </view>
        <view class="banner-shade" />
        <view class="banner-info">
          <text class="banner-kicker">本周话题挑战</text>
          <text class="banner-name">#{{ topic.name }}</text>
          <text class="banner-sub">{{ countText(topic.participantCount) }} 人参与 · {{ countText(topic.postCount) }} 作品</text>
        </view>
        <view class="banner-btn"><text class="banner-btn-txt">去打卡</text></view>
      </view>

      <!-- 关注的厨友刚更新 -->
      <view v-if="updates.length && tab !== 'topic'" class="ups">
        <text class="ups-title">关注的厨友刚更新</text>
        <scroll-view scroll-x class="ups-scroll" :show-scrollbar="false">
          <view class="ups-row">
            <view v-for="u in updates" :key="u.userId" class="up">
              <view class="up-ring"><ui-avatar :name="u.nickname" :src="u.avatarUrl ? thumbOf(u.avatarUrl) : null" :size="44" /></view>
              <text class="up-name">{{ u.nickname }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <ui-state v-if="list.loading && !list.items.length" mode="loading" skeleton="grid" />
      <ui-state v-else-if="list.error && !list.items.length" mode="error" text="厨房圈没加载出来" :retry="true" @retry="list.refresh()" />
      <ui-state
        v-else-if="!list.items.length"
        mode="empty"
        :text="tab === 'follow' ? '还没有关注的厨友' : '还没有人发布'"
        :hint="tab === 'follow' ? '在推荐里看到喜欢的，点头像关注' : '做了好吃的，晒一下'"
        action-text="发布第一条"
        :illus="['egg', 'tomato', 'fish']"
        @action="goCreate()"
      />
      <view v-else class="flow">
        <view class="col">
          <post-card v-for="p in cols[0]" :key="p.postId" :post="p" @open="openPost" @like="onLike" />
        </view>
        <view class="col">
          <post-card v-for="p in cols[1]" :key="p.postId" :post="p" @open="openPost" @like="onLike" />
        </view>
      </view>
      <text v-if="list.items.length" class="foot">{{ list.loading ? '加载中…' : list.done ? '到底了，去发一条吧' : '' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom, onShareAppMessage } from '@dcloudio/uni-app'
import { banner, countText, feed, react, toggleLikeLocal, type FeedTab, type PostCard as Post, type Topic } from '@/api/community'
import type { UserLite } from '@/api/contract'
import { createCursorList, splitColumns } from '@/utils/cursor'
import { thumbOf } from '@/utils/image'
import { PAGES, withQuery } from '@/utils/nav'
import PostCard from './parts/PostCard.vue'

const tabs = [
  { key: 'recommend', label: '推荐' },
  { key: 'follow', label: '关注' },
  { key: 'topic', label: '话题' },
]
const tab = ref<string>('recommend')
const topic = ref<Topic | null>(null)
const updates = ref<UserLite[]>([])

const list = createCursorList<Post>((cursor) => feed(tab.value as FeedTab, cursor, { topicId: tab.value === 'topic' ? topic.value?.topicId : undefined }), (p) => p.postId)
/** 列高 ≈ 图高（1/coverRatio，夹取同卡片）+ 文字区 0.45 */
const cols = computed(() => splitColumns(list.items, (p) => Math.min(1.6, Math.max(0.75, 1 / (p.coverRatio || 1))) + 0.45))

let loaded = false
onShow(() => {
  banner()
    .then((b) => {
      topic.value = b.topic
      updates.value = b.followingUpdates
    })
    .catch(() => {})
  if (!loaded) {
    loaded = true
    list.refresh()
  }
})
watch(tab, () => list.refresh())
onPullDownRefresh(async () => {
  await list.refresh()
  uni.stopPullDownRefresh()
})
onReachBottom(() => list.loadMore())
onShareAppMessage(() => ({ title: '咕嘟厨房圈：看看大家今天做了什么', path: '/pages/community/Index' }))

async function onLike(p: Post) {
  toggleLikeLocal(p)
  try {
    const r = await react(p.postId, 'LIKE', p.liked)
    p.likeCount = r.likeCount
    p.liked = r.liked
  } catch {
    toggleLikeLocal(p) // 回滚
  }
}

function openPost(p: Post) {
  uni.navigateTo({ url: withQuery(PAGES.post, { id: p.postId }) })
}
function goTopic(id: number) {
  uni.navigateTo({ url: withQuery(PAGES.topic, { id }) })
}
function goCreate(q: Record<string, string | number> = {}) {
  uni.navigateTo({ url: withQuery(PAGES.postCreate, q) })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.post-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  gap: 4px;
}
.post-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
.seg-wrap {
  padding: 0 var(--space-lg) var(--space-sm);
}
.pad {
  padding: var(--space-sm) var(--space-md) var(--space-xl);
}
.banner {
  position: relative;
  height: 96px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: var(--space-md);
}
.banner-pics {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 2px;
}
.banner-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(40, 24, 12, 0.85), rgba(40, 24, 12, 0.35) 70%, rgba(40, 24, 12, 0.1));
}
.banner-info {
  position: absolute;
  left: 14px;
  top: 12px;
  bottom: 12px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}
.banner-kicker {
  font-size: var(--fs-xs);
  color: var(--primary-soft);
  font-weight: 700;
}
.banner-name {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--on-primary);
}
.banner-sub {
  font-size: var(--fs-xs);
  color: var(--on-primary);
  opacity: 0.85;
}
.banner-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  background: var(--card);
}
.banner-btn-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--accent);
}
.ups {
  margin-bottom: var(--space-md);
}
.ups-title {
  display: block;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  margin: 0 4px var(--space-sm);
}
.ups-scroll {
  white-space: nowrap;
}
.ups-row {
  display: flex;
  gap: var(--space-md);
  padding: 0 4px;
}
.up {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.up-ring {
  padding: 2px;
  border-radius: var(--radius-md);
  border: 2px solid var(--primary);
}
.up-name {
  font-size: var(--fs-xs);
  color: var(--body);
}
.flow {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-start;
}
.col {
  flex: 1;
  min-width: 0;
}
.foot {
  display: block;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--body);
  padding: var(--space-md) 0;
}
</style>
