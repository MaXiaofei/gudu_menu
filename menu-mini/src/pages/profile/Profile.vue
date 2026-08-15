<template>
  <!-- 我的 Tab（阶段 1 骨架：用户头卡 + 功能列表；各入口页面随阶段落地） -->
  <view class="page">
    <ui-action-bar />

    <!-- 用户头部卡（点 → 成员管理，阶段 7 落地） -->
    <view class="user-card" @click="todo">
      <view class="user-avatar">
        <text class="user-avatar-txt">人</text>
      </view>
      <view class="user-info">
        <text class="user-name">{{ auth.nickname || '掌勺人' }}</text>
        <text class="user-sub">当前就餐：{{ currentMemberName || '选择就餐成员 ›' }}</text>
      </view>
      <text class="user-arrow">›</text>
    </view>

    <!-- 功能卡 -->
    <view class="card">
      <view v-for="item in items" :key="item.label" class="row" @click="todo(item)">
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
import { computed } from 'vue'

const auth = useAuthStore()
const memberStore = useMemberStore()

onShow(() => memberStore.load().catch(() => {}))
const currentMemberName = computed(() =>
  memberStore.members.find((m) => m.id === memberStore.currentId)?.name || '',
)

// 功能入口：url 为空的展示 toast「建设中」（页面随阶段落地后补 url）
const items = [
  { label: '家庭成员', url: '/pages/member/Index' },
  { label: '食材库', url: '/pages/ingredient/List' },
  { label: '采购清单', url: '/pages/shopping/List' },
  { label: '食记', url: '/pages/foodlog/Index' },
  { label: '写菜谱', url: '/pages/dish/Create' },
  { label: '草稿箱', url: '/pages/dish/Drafts' },
  { label: '我的评价', url: '/pages/review/Mine' },
  { label: '主题外观' },
  { label: '关于小食单', value: 'v1.0.0' },
]

function todo(item?: { url?: string }) {
  if (item?.url) {
    uni.navigateTo({ url: item.url })
    return
  }
  uni.showToast({ title: '建设中', icon: 'none' })
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
.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-avatar-txt {
  color: #FFFFFF;
  font-size: 22px;
  font-weight: 700;
}
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.user-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
}
.user-sub {
  font-size: 11px;
  color: var(--caption);
}
.user-arrow {
  color: var(--caption);
  font-size: 18px;
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
  font-size: 14px;
  color: var(--title);
}
.row-value {
  font-size: 12px;
  color: var(--caption);
}
.row-arrow {
  color: var(--caption);
  font-size: 16px;
}
.logout {
  color: var(--error);
  text-align: center;
  width: 100%;
}
</style>
