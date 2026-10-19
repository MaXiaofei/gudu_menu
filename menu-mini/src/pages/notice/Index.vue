<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 消息（prototype Notices · P1 先接现有 expiry/shopping 两类；邀请/反馈/投稿消息待 P3 后端）。 -->
  <view class="page">
    <ui-back-header title="消息">
      <template #action>
        <text v-if="unread" class="read-all" data-testid="read-all" @click="readAll">全部已读</text>
      </template>
    </ui-back-header>
    <view class="tabs">
      <ui-segment v-model="tab" :items="tabs" />
    </view>

    <scroll-view scroll-y class="body">
      <ui-state v-if="loading" mode="loading" />
      <ui-state v-else-if="error" mode="error" :retry="true" @retry="load" />
      <ui-state
        v-else-if="!shown.length"
        mode="empty"
        text="没有新消息"
        hint="食材快到期、家人点了菜，都会在这里提醒你"
        :illus="['milk', 'egg', 'fish']"
      />
      <view v-else class="list">
        <view v-for="n in shown" :key="n.id" class="item" :class="{ unread: !n.isRead }" data-testid="notice" @click="open(n)">
          <view class="lead" :class="'g-' + noticeGroup(n.type)">
            <ui-icon :name="iconOf(n.type)" :size="20" />
          </view>
          <view class="main">
            <view class="title-row">
              <text class="title">{{ n.title || '提醒' }}</text>
              <text class="time">{{ relativeDate(n.createTime) }}</text>
            </view>
            <text v-if="n.content" class="content">{{ n.content }}</text>
            <view v-if="n.type === 'expiry'" class="act"><text class="act-txt">去看看能做什么 ›</text></view>
          </view>
          <view v-if="!n.isRead" class="dot" />
        </view>
      </view>
      <view style="height: 40px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listNotices, markRead, noticeGroup, type Notice } from '@/api/notification'
import { relativeDate } from '@/utils/datetime'

const list = ref<Notice[]>([])
const loading = ref(true)
const error = ref(false)
const tab = ref('all')

const count = (g: string) => list.value.filter((n) => g === 'all' || noticeGroup(n.type) === g).length
const unread = computed(() => list.value.filter((n) => !n.isRead).length)
const tabs = computed(() => [
  { key: 'all', label: '全部', badge: unread.value || '' },
  { key: 'remind', label: '提醒', badge: count('remind') || '' },
  { key: 'family', label: '家庭', badge: count('family') || '' },
  { key: 'system', label: '系统', badge: count('system') || '' },
])
const shown = computed(() => list.value.filter((n) => tab.value === 'all' || noticeGroup(n.type) === tab.value))

onShow(load)

async function load() {
  loading.value = !list.value.length
  error.value = false
  try {
    list.value = await listNotices()
  } catch {
    error.value = true
  }
  loading.value = false
}

function iconOf(type?: string | null) {
  if (type === 'expiry') return 'clock'
  if (type === 'shopping') return 'cart'
  if (type === 'invite') return 'users'
  return 'message'
}

async function open(n: Notice) {
  if (!n.isRead) {
    n.isRead = 1
    markRead(n.id).catch(() => (n.isRead = 0))
  }
  if (n.type === 'expiry') uni.switchTab({ url: '/pages/agent/Index' })
  else if (n.type === 'shopping') uni.navigateTo({ url: '/pages/shopping/List' })
}

async function readAll() {
  const ids = list.value.filter((n) => !n.isRead).map((n) => n.id)
  list.value.forEach((n) => (n.isRead = 1))
  await Promise.allSettled(ids.map((id) => markRead(id)))
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.read-all {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.tabs {
  padding: var(--sp-4) var(--sp-16) var(--sp-12);
}
.body {
  flex: 1;
  min-height: 0;
}
.list {
  padding: 0 var(--sp-16);
  display: flex;
  flex-direction: column;
  gap: var(--sp-10);
}
.item {
  position: relative;
  display: flex;
  gap: var(--sp-12);
  padding: var(--sp-12);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.item.unread {
  border-color: var(--primary-soft);
  box-shadow: 0 2px 10px rgba(var(--sh), 0.08);
}
.lead {
  width: 40px;
  height: 40px;
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.g-remind { background: var(--error-soft); }
.g-family { background: var(--primary-soft); }
.g-system { background: var(--info-soft); }
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title-row {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-8);
}
.title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.time {
  flex-shrink: 0;
  font-size: var(--fs-xs);
  color: var(--body);
}
.content {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
.act-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.dot {
  position: absolute;
  right: 10px;
  top: 10px;
  width: 8px;
  height: 8px;
  border-radius: var(--r-pill);
  background: var(--error);
}
</style>
