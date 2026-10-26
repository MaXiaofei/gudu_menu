<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 我的（prototype Me）：渐变头 + 大头像 + 三格统计 + 最近食记照片条 + 分组设置。 -->
  <view class="page">
    <view class="head" :style="{ paddingTop: nav + 'px' }">
      <!-- 「我的」不再是 Tab（右上角头像进入），顶部返回 -->
      <view class="back-row" data-testid="profile-back" @click="goBack"><text class="back">‹</text><text class="back-title">我的</text></view>
      <view class="user-card" data-testid="user-card" @click="go('/pages/profile/Edit')">
        <ui-member-avatar :name="displayName" :avatar-url="me?.avatarUrl" :size="64" ring />
        <view class="user-info">
          <text class="user-name">{{ displayName }}</text>
          <text class="user-sub">{{ family.current?.name || '我的家' }} · {{ roleLabel(family.currentRole) }}</text>
        </view>
        <view class="edit"><text class="edit-txt">编辑资料</text></view>
      </view>
      <!-- 家庭切换器（multi-tenant-family）：当前家庭 + 快速切换；点名称进家庭页 -->
      <view class="fam-switch" data-testid="family-switcher">
        <view class="fs-main" @click="go('/pkg-family/Index')">
          <text class="fs-label">当前家庭</text>
          <text class="fs-name" data-testid="family-current-name">{{ family.current?.name || '我的家' }}</text>
        </view>
        <view class="fs-btn" data-testid="family-switch-btn" @click="onSwitchFamily">
          <ui-icon name="swap" :size="14" />
          <text class="fs-btn-txt">切换</text>
        </view>
      </view>
      <ui-stat-grid v-if="stats" class="stats" :items="statItems" />
    </view>

    <view class="body">
      <view v-if="recent.length" class="card strip-card">
        <view class="card-head" @click="go('/pages/foodlog/Index')">
          <text class="card-title">食记 · 最近做的</text>
          <ui-icon name="chevron" :size="16" />
        </view>
        <scroll-view scroll-x class="strip" :show-scrollbar="false">
          <view class="strip-row">
            <view v-for="r in recent" :key="r.dishId" class="strip-it" @click="go(`/pages/dish/Detail?id=${r.dishId}`)">
              <ui-dish-image :src="r.coverImage" :name="r.name" :width="72" :height="72" :radius="12" />
              <text class="strip-name">{{ r.name }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-for="g in groups" :key="g.title" class="card">
        <text class="group-title">{{ g.title }}</text>
        <view v-for="item in g.items" :key="item.label" class="row" @click="go(item.url)">
          <view class="ic" :class="item.tone"><ui-icon :name="item.icon" :size="18" /></view>
          <text class="row-label">{{ item.label }}</text>
          <text v-if="item.value" class="row-value">{{ item.value }}</text>
          <text v-if="item.badge" class="badge">{{ item.badge }}</text>
          <ui-icon name="chevron" :size="16" />
        </view>
      </view>

      <view class="logout" @click="onLogout"><text class="logout-txt">退出登录</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/store/auth'
import { useMemberStore } from '@/store/member'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getMe, meStats, type MemberMe, type MeStats } from '@/api/member'
import { recentCooked, type RecentCooked } from '@/api/recommend'
import { unreadCount } from '@/api/notification'
import { navInset } from '@/utils/token'
import { THEME_OPTIONS, themeState } from '@/utils/theme'
import { APP_VERSION } from '@/config/app'
import { PAGES, goTab } from '@/utils/nav'
import { useFamilyStore } from '@/store/family'
import { FAMILY_SWITCH_PAGE, roleLabel } from '@/utils/family'

const auth = useAuthStore()
const memberStore = useMemberStore()
const family = useFamilyStore()
const me = ref<MemberMe | null>(null)
const stats = ref<MeStats | null>(null)
const recent = ref<RecentCooked[]>([])
const unread = ref(0)
const nav = navInset()

onShow(() => {
  family.refresh().catch(() => {})
  reload()
})

function reload() {
  memberStore.load().catch(() => {})
  getMe()
    .then((m) => (me.value = m))
    .catch(() => {})
  meStats().then((r) => (stats.value = r))
  recentCooked(6).then((r) => (recent.value = r)).catch(() => {})
  unreadCount().then((n) => (unread.value = n))
}

/** 快速切换：其他家庭（≤5）+「管理 / 新建家庭」走 ActionSheet（微信上限 6 项）；只有一个家庭直接进选择页。 */
function onSwitchFamily() {
  const others = family.families.filter((f) => f.id !== family.currentId)
  if (!others.length) {
    uni.navigateTo({ url: FAMILY_SWITCH_PAGE })
    return
  }
  const shown = others.slice(0, 5)
  uni.showActionSheet({
    itemList: [...shown.map((f) => `切换到「${f.name}」`), '管理 / 新建家庭'],
    success: async ({ tapIndex }) => {
      const target = shown[tapIndex]
      if (!target) {
        uni.navigateTo({ url: FAMILY_SWITCH_PAGE })
        return
      }
      try {
        await family.switchTo(target.id)
        uni.showToast({ title: `已切换到「${target.name}」`, icon: 'none' })
        reload()
      } catch {
        // request.ts 已提示
      }
    },
  })
}
const statItems = computed(() =>
  stats.value
    ? [
        { value: stats.value.cookedThisMonth, label: '本月做饭' },
        { value: stats.value.familyDishes, label: '家庭菜谱' },
        { value: stats.value.savedFromWaste, label: '本月少扔', tone: 'success' as const },
      ]
    : [],
)
const currentMemberName = computed(() =>
  memberStore.members.find((m) => m.id === memberStore.currentId)?.name || '',
)
const displayName = computed(() => me.value?.name || auth.nickname || uni.getStorageSync('nickname') || '掌勺人')
const themeLabel = computed(() => THEME_OPTIONS.find((t) => t.name === themeState.name)?.label || '')

const groups = computed(() => [
  {
    title: '家庭',
    items: [
      { label: '我的家庭', icon: 'users', tone: 't1', url: '/pkg-family/Index', value: family.current ? `${family.current.memberCount} 人` : '' },
      { label: '成员档案', icon: 'heart', tone: 't2', url: '/pages/member/Index', value: memberStore.members.length ? `${memberStore.members.length} 份` : '' },
    ],
  },
  {
    title: '我的内容',
    items: [
      { label: '营养报告', icon: 'chart', tone: 't3', url: PAGES.nutrition, value: '本周' },
      { label: '食记', icon: 'calendar', tone: 't2', url: '/pages/foodlog/Index' },
      { label: '采购清单', icon: 'cart', tone: 't3', url: '/pages/shopping/List' },
      { label: '写菜谱', icon: 'book', tone: 't1', url: '/pages/dish/Create' },
      { label: '草稿箱', icon: 'image', tone: 't4', url: '/pages/dish/Drafts' },
      { label: '我的评价', icon: 'star', tone: 't2', url: '/pages/review/Mine' },
      { label: '食材库', icon: 'fridge', tone: 't3', url: '/pages/ingredient/List' },
    ],
  },
  {
    title: '设置',
    items: [
      { label: '主题外观', icon: 'palette', tone: 't1', url: '/pages/profile/Theme', value: themeLabel.value },
      { label: '消息与提醒', icon: 'bell', tone: 't2', url: '/pages/notice/Index', badge: unread.value || '' },
      { label: '意见反馈', icon: 'message', tone: 't3', url: '/pages/profile/Feedback' },
      { label: '账号与安全', icon: 'shield', tone: 't1', url: '/pkg-account/Security' },
      { label: '隐私与协议', icon: 'shield', tone: 't4', url: '/pages/profile/Legal?type=privacy' },
      { label: '关于小食单', icon: 'info', tone: 't4', url: '/pages/profile/About', value: `v${APP_VERSION}` },
    ],
  },
])

function go(url: string) {
  uni.navigateTo({ url })
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else goTab('dish')
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
}
.head {
  padding-left: var(--sp-16);
  padding-right: var(--sp-16);
  padding-bottom: var(--sp-16);
  background: linear-gradient(180deg, var(--primary-soft), var(--bg));
}
.back-row {
  height: 32px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding-right: 6px;
}
.back-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.user-card {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: var(--sp-8) 0 var(--sp-16);
}
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.user-name {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.user-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.edit {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
}
.edit-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.fam-switch {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  margin-bottom: var(--sp-12);
  padding: var(--sp-10) var(--sp-12);
  border-radius: var(--r-md);
  background: var(--card);
  border: 1px solid var(--border);
}
.fs-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fs-label {
  font-size: var(--fs-xs);
  color: var(--caption);
}
.fs-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.fs-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--primary-soft);
}
.fs-btn-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--primary-deep);
}
.body {
  padding: 0 var(--sp-16) var(--sp-24);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-12) var(--sp-16);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.strip {
  margin-top: var(--sp-10);
  white-space: nowrap;
}
.strip-row {
  display: inline-flex;
  gap: var(--sp-10);
}
.strip-it {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 72px;
}
.strip-name {
  width: 72px;
  font-size: var(--fs-xs);
  color: var(--body);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-title {
  display: block;
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
  padding-bottom: var(--sp-4);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: var(--sp-10) 0;
}
.row + .row {
  border-top: 1px solid var(--border);
}
.ic {
  width: 32px;
  height: 32px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.t1 { background: var(--primary-soft); }
.t2 { background: var(--warning-soft); }
.t3 { background: var(--success-soft); }
.t4 { background: var(--info-soft); }
.row-label {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.row-value {
  font-size: var(--fs-sm);
  color: var(--body);
}
.badge {
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  box-sizing: border-box;
  text-align: center;
  border-radius: var(--r-pill);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.logout {
  height: 48px;
  border-radius: var(--r-lg);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.logout-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--error-deep);
}
</style>
