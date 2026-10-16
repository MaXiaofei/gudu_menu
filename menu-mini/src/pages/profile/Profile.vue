<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 我的 Tab：用户头卡（→ 个人资料）+ 功能列表 -->
  <view class="page">
    <ui-action-bar />

    <!-- 用户头部卡（点 → 个人资料编辑） -->
    <view class="user-card" data-testid="user-card" @click="go('/pages/profile/Edit')">
      <ui-avatar :name="displayName" :src="avatarSrc" :size="56" :fallback="'掌'" />
      <view class="user-info">
        <text class="user-name">{{ displayName }}</text>
        <text class="user-sub">当前就餐：{{ currentMemberName || '选择就餐成员 ›' }}</text>
      </view>
      <text class="user-arrow">›</text>
    </view>

    <!-- 功能卡 -->
    <view class="card">
      <view v-for="item in items" :key="item.label" class="row" @click="go(item.url)">
        <text class="row-label">{{ item.label }}</text>
        <text v-if="item.value" class="row-value">{{ item.value }}</text>
        <text class="row-arrow">›</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="card">
      <view class="row" @click="onLogout">
        <text class="row-label logout">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/store/auth'
import { useMemberStore } from '@/store/member'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getMe, type MemberMe } from '@/api/member'
import { thumbOf } from '@/utils/image'
import { THEME_OPTIONS, themeState, applyTabBar } from '@/utils/theme'
import { APP_VERSION } from '@/config/app'

const auth = useAuthStore()
const memberStore = useMemberStore()
const me = ref<MemberMe | null>(null)

onShow(() => {
  applyTabBar()
  memberStore.load().catch(() => {})
  getMe()
    .then((m) => (me.value = m))
    .catch(() => {})
})
const currentMemberName = computed(() =>
  memberStore.members.find((m) => m.id === memberStore.currentId)?.name || '',
)
const displayName = computed(() => me.value?.name || auth.nickname || uni.getStorageSync('nickname') || '掌勺人')
const avatarSrc = computed(() => (me.value?.avatarUrl ? thumbOf(me.value.avatarUrl) : ''))
const themeLabel = computed(() => THEME_OPTIONS.find((t) => t.name === themeState.name)?.label || '')

const items = computed(() => [
  { label: '家庭成员', url: '/pages/member/Index' },
  { label: '食材库', url: '/pages/ingredient/List' },
  { label: '采购清单', url: '/pages/shopping/List' },
  { label: '食记', url: '/pages/foodlog/Index' },
  { label: '写菜谱', url: '/pages/dish/Create' },
  { label: '草稿箱', url: '/pages/dish/Drafts' },
  { label: '我的评价', url: '/pages/review/Mine' },
  { label: '主题外观', url: '/pages/profile/Theme', value: themeLabel.value },
  { label: '意见反馈', url: '/pages/profile/Feedback' },
  { label: '关于小食单', url: '/pages/profile/About', value: `v${APP_VERSION}` },
])

function go(url: string) {
  uni.navigateTo({ url })
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号？',
    success: ({ confirm }) => {
      if (confirm) auth.logout()
    },
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 0 16px 24px;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 14px;
  margin: 8px 0 12px;
}
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.user-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.user-sub {
  font-size: var(--fs-xs);
  color: var(--caption);
}
.user-arrow {
  color: var(--caption);
  font-size: var(--fs-subtitle);
  font-weight: 700;
}
.card {
  background: var(--card);
  border-radius: var(--r-lg);
  margin-bottom: 12px;
  overflow: hidden;
}
.row {
  display: flex;
  align-items: center;
  padding: 13px 14px;
  gap: 8px;
}
.row + .row {
  border-top: 1px solid var(--bg);
}
.row-label {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.row-value {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.row-arrow {
  color: var(--caption);
  font-size: var(--fs-lg);
}
.logout {
  color: var(--error);
  text-align: center;
  width: 100%;
}
</style>
