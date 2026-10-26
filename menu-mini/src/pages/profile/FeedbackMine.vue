<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 我的反馈（A.11）：GET /feedback/mine，看处理状态与回复；点进详情。 -->
  <view class="page">
    <ui-back-header title="我的反馈">
      <template #action>
        <text class="new-link" data-testid="feedback-new" @click="goNew">写反馈</text>
      </template>
    </ui-back-header>

    <scroll-view scroll-y class="body">
      <ui-state v-if="loading" mode="loading" />
      <ui-state v-else-if="error" mode="error" :retry="true" @retry="load(true)" />
      <ui-state
        v-else-if="!list.length"
        mode="empty"
        text="还没有提交过反馈"
        hint="遇到问题或有想法，随时告诉我们"
        action-text="写反馈"
        @action="goNew"
      />
      <view v-else class="list">
        <view v-for="f in list" :key="f.id" class="item" data-testid="my-feedback" @click="goDetail(f.id)">
          <view class="row">
            <text class="type">{{ feedbackTypeText(f.type) }}</text>
            <text class="time">{{ relativeDate(f.createTime) }}</text>
            <view class="flex1" />
            <ui-status-chip :label="feedbackStatusText(f.status)" :color="feedbackStatusColor(f.status)" />
          </view>
          <text class="content">{{ f.content }}</text>
          <view v-if="f.reply" class="reply" data-testid="my-feedback-reply">
            <text class="reply-label">回复</text>
            <text class="reply-txt">{{ f.reply }}</text>
          </view>
        </view>
        <view v-if="list.length < total" class="more" data-testid="my-feedback-more" @click="load(false)">
          <text class="more-txt">{{ loadingMore ? '加载中…' : '加载更多' }}</text>
        </view>
      </view>
      <view style="height: 40px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  listMyFeedback, feedbackStatusColor, feedbackStatusText, feedbackTypeText, type MyFeedback,
} from '@/api/feedback'
import { relativeDate } from '@/utils/datetime'

const PAGE_SIZE = 20
const list = ref<MyFeedback[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref(false)

onShow(() => load(true))

/** reset=true 重新从第一页拉（回到页面时刷新状态）；否则追加下一页。 */
async function load(reset: boolean) {
  if (!reset && loadingMore.value) return
  if (reset) {
    loading.value = !list.value.length
    error.value = false
  } else {
    loadingMore.value = true
  }
  const next = reset ? 1 : pageNum.value + 1
  try {
    const r = await listMyFeedback(next, PAGE_SIZE)
    list.value = reset ? r.records : [...list.value, ...r.records]
    total.value = r.total
    pageNum.value = next
  } catch {
    if (reset) error.value = !list.value.length
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/profile/FeedbackDetail?id=${id}` })
}

function goNew() {
  uni.navigateTo({ url: '/pages/profile/Feedback' })
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
.new-link {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.list {
  padding: var(--sp-4) var(--sp-16) 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-10);
}
.item {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
  padding: var(--sp-12);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.flex1 {
  flex: 1;
}
.type {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.time {
  font-size: var(--fs-xs);
  color: var(--caption);
}
.content {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--body);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.reply {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-8) var(--sp-10);
  border-radius: var(--r-md);
  background: var(--highlight);
}
.reply-label {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--primary-deep);
}
.reply-txt {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--title);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.more {
  padding: var(--sp-12) 0;
  text-align: center;
}
.more-txt {
  font-size: var(--fs-sm);
  color: var(--caption);
}
</style>
