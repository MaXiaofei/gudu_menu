<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 反馈详情（A.11）：GET /feedback/mine/{id}；「消息与提醒」里的反馈回复点进来到这里。 -->
  <view class="page">
    <ui-back-header title="反馈详情" />

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="error" mode="error" :text="error" :retry="true" @retry="load" />
    <view v-else-if="fb" class="body" data-testid="feedback-detail">
      <view class="card">
        <view class="row">
          <text class="type">{{ feedbackTypeText(fb.type) }}</text>
          <view class="flex1" />
          <ui-status-chip :label="feedbackStatusText(fb.status)" :color="feedbackStatusColor(fb.status)" />
        </view>
        <text class="time">提交于 {{ mdHm(fb.createTime) }}</text>
        <text class="content">{{ fb.content }}</text>
        <view v-if="imgs.length" class="imgs">
          <image
            v-for="(src, i) in imgs"
            :key="src"
            class="img"
            :src="src"
            mode="aspectFill"
            @click="preview(i)"
          />
        </view>
      </view>

      <view v-if="fb.reply" class="card reply" data-testid="feedback-detail-reply">
        <view class="row">
          <text class="reply-label">小食单的回复</text>
          <view class="flex1" />
          <text class="time">{{ mdHm(fb.repliedAt) }}</text>
        </view>
        <text class="reply-txt">{{ fb.reply }}</text>
      </view>
      <view v-else class="pending" data-testid="feedback-detail-pending">
        <text class="pending-txt">我们会在 15 个工作日内答复，有结果会通过「消息与提醒」通知你</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getMyFeedback, feedbackStatusColor, feedbackStatusText, feedbackTypeText, type MyFeedback,
} from '@/api/feedback'
import { mdHm } from '@/utils/datetime'
import { uploadUrl } from '@/utils/image'

const id = ref('')
const fb = ref<MyFeedback | null>(null)
const loading = ref(true)
const error = ref('')

const imgs = computed(() => (fb.value?.images || []).filter(Boolean).map((u) => uploadUrl(u)))

onLoad((o) => {
  id.value = String(o?.id || '')
  load()
})

async function load() {
  if (!/^\d+$/.test(id.value)) {
    error.value = '反馈不存在'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    fb.value = await getMyFeedback(id.value)
  } catch (e: any) {
    error.value = e?.message || '没加载出来'
  } finally {
    loading.value = false
  }
}

function preview(i: number) {
  uni.previewImage({ urls: imgs.value, current: i })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.body {
  padding: var(--sp-4) var(--sp-16) var(--sp-24);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.card {
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
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.time {
  font-size: var(--fs-xs);
  color: var(--caption);
}
.content {
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--title);
  white-space: pre-wrap;
  word-break: break-all;
}
.imgs {
  display: flex;
  gap: var(--sp-8);
}
.img {
  width: 72px;
  height: 72px;
  border-radius: var(--r-sm);
  background: var(--secondary);
}
.reply {
  border-color: var(--primary-soft);
  background: var(--highlight);
}
.reply-label {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.reply-txt {
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--title);
  white-space: pre-wrap;
  word-break: break-all;
}
.pending {
  padding: var(--sp-12);
  border-radius: var(--r-md);
  background: var(--info-soft);
}
.pending-txt {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
</style>
