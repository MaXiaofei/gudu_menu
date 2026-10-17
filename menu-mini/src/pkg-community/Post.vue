<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 动态详情（prototype Post）：大图轮播 → 作者 + 关注 → 正文话题 → 关联菜谱「照着做 · 复制到我家」→
       他们照着做了 → 两级评论 → 底部输入 + 赞 / 收藏 / 分享。 -->
  <view class="page">
    <ui-state v-if="loading" mode="loading" skeleton="hero" />
    <ui-state v-else-if="!post" mode="error" text="这条动态打不开了" :retry="true" @retry="load" />
    <template v-else>
      <scroll-view scroll-y class="body" :scroll-into-view="anchor" @scrolltolower="loadMoreComments">
        <view class="hero">
          <ui-hero-gallery :images="post.images" :name="post.title" :height="400" @preview="preview" />
          <view class="hero-bar" :style="{ top: sb + 'px' }">
            <view class="circle" @click="goBack"><ui-icon name="back" :size="20" /></view>
            <view class="flex1" />
            <view class="circle" data-testid="post-more" @click="onMore"><ui-icon name="more" :size="20" /></view>
          </view>
        </view>

        <view class="pad">
          <view v-if="post.status !== 'PUBLISHED'" class="review">
            <text class="review-txt">{{ post.status === 'REVIEWING' ? '审核中 · 仅自己可见，通过后大家就能看到' : '已被隐藏' }}</text>
          </view>

          <view class="author">
            <ui-avatar :name="post.author.nickname" :src="post.author.avatarUrl ? thumbOf(post.author.avatarUrl) : null" :size="40" />
            <view class="author-main">
              <text class="nick">{{ post.author.nickname }}</text>
              <text class="when">发布于 {{ relativeDate(post.createdAt) }}{{ post.city ? ' · ' + post.city : '' }}</text>
            </view>
            <view class="follow" :class="{ on: post.following }" data-testid="follow" @click="onFollow">
              <text class="follow-txt">{{ post.following ? '已关注' : '+ 关注' }}</text>
            </view>
          </view>

          <text class="title">{{ post.title }}</text>
          <text class="text">{{ post.text }}</text>
          <view v-if="post.topics.length" class="topics">
            <text v-for="t in post.topics" :key="t.topicId" class="topic" @click="goTopic(t.topicId)">#{{ t.name }}</text>
          </view>

          <!-- 关联菜谱 -->
          <view v-if="post.recipe" class="recipe">
            <view class="recipe-top" @click="goDish(post.recipe.id)">
              <ui-dish-image :src="post.recipe.coverUrl" :name="post.recipe.name" :width="72" :height="72" :radius="12" />
              <view class="recipe-main">
                <text class="recipe-kicker">关联菜谱</text>
                <text class="recipe-name">{{ post.recipe.name }}</text>
                <text class="recipe-meta">{{ [post.recipe.minutes ? post.recipe.minutes + ' 分钟' : '', post.recipe.difficulty || ''].filter(Boolean).join(' · ') }}</text>
              </view>
              <ui-icon name="chevron" :size="16" />
            </view>
            <view v-if="coverageLabel(post.recipe.coverage)" class="cov" :class="coverageTone(post.recipe.coverage)">
              <ui-icon name="fridge" :size="14" />
              <text class="cov-txt">你家{{ coverageLabel(post.recipe.coverage) }}</text>
            </view>
            <view class="copy" data-testid="copy-recipe" @click="onCopy">
              <text class="copy-txt">{{ copying ? '复制中…' : '照着做 · 复制到我家' }}</text>
            </view>
            <text class="copy-n">已有 {{ countText(post.recipe.copyCount) }} 人照着做</text>
          </view>

          <!-- 他们照着做了 -->
          <view v-if="copiesList.length" class="sec">
            <text class="sec-title">他们照着做了</text>
            <scroll-view scroll-x class="hs" :show-scrollbar="false">
              <view class="hrow">
                <view v-for="(c, i) in copiesList" :key="i" class="cp">
                  <image class="cp-img" :src="thumbOf(c.imageUrl)" mode="aspectFill" />
                  <view class="cp-user">
                    <ui-avatar :name="c.user.nickname" :src="c.user.avatarUrl ? thumbOf(c.user.avatarUrl) : null" :size="16" />
                    <text class="cp-name">{{ c.user.nickname }}</text>
                  </view>
                </view>
              </view>
            </scroll-view>
          </view>

          <!-- 评论 -->
          <view id="comments" class="sec">
            <text class="sec-title">评论 {{ post.commentCount }}</text>
            <view v-for="c in commentList.items" :key="c.commentId" class="cm">
              <ui-avatar :name="c.author.nickname" :src="c.author.avatarUrl ? thumbOf(c.author.avatarUrl) : null" :size="32" />
              <view class="cm-main">
                <view class="cm-head">
                  <text class="cm-nick">{{ c.author.nickname }}</text>
                  <text v-if="c.isAuthor" class="author-tag">作者</text>
                </view>
                <text class="cm-text" @click="replyTo(c)">{{ c.text }}</text>
                <text class="cm-when">{{ relativeDate(c.createdAt) }} · 回复</text>
                <view v-for="r in c.replies" :key="r.commentId" class="rp">
                  <text class="rp-nick">{{ r.author.nickname }}</text>
                  <text v-if="r.isAuthor" class="author-tag">作者</text>
                  <text class="rp-text">：{{ r.text }}</text>
                </view>
              </view>
            </view>
            <text v-if="!commentList.items.length && !commentList.loading" class="no-cm">还没有评论，说两句？</text>
          </view>
          <view style="height: 24px" />
        </view>
      </scroll-view>

      <!-- 底部栏 -->
      <view class="bar">
        <view class="ipt-box">
          <ui-icon name="comment" :size="16" />
          <input
            v-model="draft"
            class="ipt"
            :placeholder="replyTarget ? `回复 ${replyTarget.author.nickname}` : '说点什么…'"
            placeholder-class="ph"
            confirm-type="send"
            data-testid="comment-input"
            @confirm="sendComment"
          />
        </view>
        <view class="act" data-testid="post-like" @click="onReact('LIKE')">
          <ui-icon name="heart" :tone="post.liked ? 'primary' : 'dark'" :size="20" />
          <text class="act-n" :class="{ on: post.liked }">{{ countText(post.likeCount) }}</text>
        </view>
        <view class="act" data-testid="post-collect" @click="onReact('COLLECT')">
          <ui-icon name="star" :tone="post.collected ? 'primary' : 'dark'" :size="20" />
          <text class="act-n" :class="{ on: post.collected }">{{ countText(post.collectCount) }}</text>
        </view>
        <button class="act share" open-type="share">
          <ui-icon name="share" :size="20" />
          <text class="act-n">分享</text>
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import {
  addComment,
  comments,
  copies,
  copyRecipe,
  countText,
  follow,
  postDetail,
  react,
  report,
  deletePost,
  type Comment,
  type PostDetail,
} from '@/api/community'
import { coverageLabel, coverageTone, type UserLite } from '@/api/contract'
import { createCursorList } from '@/utils/cursor'
import { relativeDate } from '@/utils/datetime'
import { thumbOf, toAbsolute } from '@/utils/image'
import { statusBarHeight } from '@/utils/token'
import { PAGES, withQuery } from '@/utils/nav'

const sb = statusBarHeight() + 6
const id = ref(0)
const post = ref<PostDetail | null>(null)
const loading = ref(true)
const copying = ref(false)
const copiesList = ref<{ user: UserLite; imageUrl: string }[]>([])
const draft = ref('')
const replyTarget = ref<Comment | null>(null)
const anchor = ref('')
const commentList = createCursorList<Comment>((c) => comments(id.value, c), (x) => x.commentId)

onLoad((q: any) => {
  id.value = Number(q?.id) || 0
  load()
})

async function load() {
  loading.value = true
  try {
    post.value = await postDetail(id.value)
    commentList.refresh()
    copies(id.value).then((c) => (copiesList.value = c))
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

onShareAppMessage(() => ({
  title: post.value?.title || '咕嘟厨房圈',
  path: withQuery(PAGES.post, { id: id.value }),
  imageUrl: post.value?.coverUrl ? toAbsolute(post.value.coverUrl) : undefined,
}))

function loadMoreComments() {
  commentList.loadMore()
}

async function onReact(type: 'LIKE' | 'COLLECT') {
  const p = post.value!
  const on = type === 'LIKE' ? !p.liked : !p.collected
  // 乐观更新
  if (type === 'LIKE') {
    p.liked = on
    p.likeCount += on ? 1 : -1
  } else {
    p.collected = on
    p.collectCount += on ? 1 : -1
  }
  try {
    Object.assign(p, await react(p.postId, type, on))
  } catch {
    if (type === 'LIKE') {
      p.liked = !on
      p.likeCount += on ? -1 : 1
    } else {
      p.collected = !on
      p.collectCount += on ? -1 : 1
    }
  }
}

async function onFollow() {
  const p = post.value!
  try {
    p.following = (await follow(p.author.userId, !p.following)).following
  } catch {
    // toast 已由 request 弹出
  }
}

async function onCopy() {
  if (copying.value || !post.value?.recipe) return
  copying.value = true
  try {
    const { dishId } = await copyRecipe(post.value.postId)
    post.value.recipe.copyCount += 1
    uni.showToast({ title: '已复制到我家菜谱', icon: 'success' })
    setTimeout(() => uni.navigateTo({ url: `/pages/dish/Detail?id=${dishId}` }), 500)
  } finally {
    copying.value = false
  }
}

function replyTo(c: Comment) {
  replyTarget.value = c
}

async function sendComment() {
  const t = draft.value.trim()
  if (!t) return
  const parent = replyTarget.value
  const c = await addComment(id.value, t, parent?.commentId)
  draft.value = ''
  replyTarget.value = null
  if (parent) parent.replies.push(c)
  else commentList.items.unshift(c)
  if (post.value) post.value.commentCount += 1
  anchor.value = ''
  setTimeout(() => (anchor.value = 'comments'), 50)
}

function onMore() {
  const mine = String(post.value?.author.userId) === String(uni.getStorageSync('userId'))
  const items = mine ? ['删除这条动态'] : ['举报']
  uni.showActionSheet({
    itemList: items,
    success: ({ tapIndex }) => {
      if (items[tapIndex] === '举报') {
        const reasons = ['广告营销', '不相关内容', '图片侵权', '其他']
        uni.showActionSheet({
          itemList: reasons,
          success: async ({ tapIndex: r }) => {
            await report(id.value, reasons[r])
            uni.showToast({ title: '已收到，我们会尽快处理', icon: 'none' })
          },
        })
      } else {
        uni.showModal({
          title: '删除动态',
          content: '删除后不可恢复',
          success: async ({ confirm }) => {
            if (!confirm) return
            await deletePost(id.value)
            uni.navigateBack()
          },
        })
      }
    },
  })
}

function preview(i: number) {
  uni.previewImage({ urls: (post.value?.images || []).map(toAbsolute), current: i })
}
function goDish(dishId: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${dishId}` })
}
function goTopic(topicId: number) {
  uni.navigateTo({ url: withQuery(PAGES.topic, { id: topicId }) })
}
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/community/Index' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
}
.hero {
  position: relative;
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
.pad {
  padding: var(--space-md) var(--space-lg);
  margin-top: -16px;
  position: relative;
  border-radius: 20px 20px 0 0;
  background: var(--bg);
}
.review {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--warning-soft);
  margin-bottom: var(--space-md);
}
.review-txt {
  font-size: var(--fs-sm);
  color: var(--warning-deep);
}
.author {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.author-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.nick {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.when {
  font-size: var(--fs-xs);
  color: var(--body);
}
.follow {
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
}
.follow.on {
  background: var(--secondary);
}
.follow-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
}
.follow.on .follow-txt {
  color: var(--body);
}
.title {
  display: block;
  margin-top: var(--space-md);
  font-size: var(--fs-h3);
  font-weight: 800;
  line-height: 1.4;
  color: var(--title);
}
.text {
  display: block;
  margin-top: var(--space-sm);
  font-size: var(--fs-md);
  line-height: 1.75;
  color: var(--title);
  white-space: pre-wrap;
}
.topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.topic {
  font-size: var(--fs-md);
  color: var(--info-deep);
  font-weight: 600;
}
.recipe {
  margin-top: var(--space-lg);
  padding: 12px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.recipe-top {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.recipe-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.recipe-kicker {
  font-size: var(--fs-xs);
  color: var(--body);
}
.recipe-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.recipe-meta {
  font-size: var(--fs-sm);
  color: var(--body);
}
.cov {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--secondary);
}
.cov.full {
  background: var(--success-soft);
}
.cov.partial {
  background: var(--warning-soft);
}
.cov-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.copy {
  height: 46px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.copy-txt {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
.copy-n {
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--body);
}
.sec {
  margin-top: var(--space-xl);
}
.sec-title {
  display: block;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
  margin-bottom: var(--space-md);
}
.hs {
  white-space: nowrap;
}
.hrow {
  display: flex;
  gap: var(--space-sm);
}
.cp {
  width: 104px;
  flex-shrink: 0;
}
.cp-img {
  width: 104px;
  height: 104px;
  border-radius: var(--radius-md);
  background: var(--secondary);
}
.cp-user {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}
.cp-name {
  font-size: var(--fs-xs);
  color: var(--body);
}
.cm {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}
.cm-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cm-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cm-nick {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--body);
}
.author-tag {
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--primary-soft);
  color: var(--accent);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.cm-text {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--title);
}
.cm-when {
  font-size: var(--fs-xs);
  color: var(--body);
}
.rp {
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--secondary);
}
.rp-nick {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--body);
}
.rp-text {
  font-size: var(--fs-sm);
  color: var(--title);
}
.no-cm {
  font-size: var(--fs-sm);
  color: var(--body);
}
.bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 10px var(--space-lg) calc(10px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid var(--border);
}
.ipt-box {
  flex: 1;
  height: 40px;
  border-radius: var(--radius-pill);
  background: var(--bg);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
}
.ipt {
  flex: 1;
  font-size: var(--fs-md);
}
.ph {
  color: var(--body);
}
.act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}
.act.share {
  margin: 0;
  padding: 0;
  background: transparent;
  line-height: 1;
}
.act.share::after {
  border: none;
}
.act-n {
  font-size: var(--fs-tiny);
  color: var(--body);
}
.act-n.on {
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
