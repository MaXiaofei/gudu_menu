<template>
  <view class="page">
    <!-- 当前账号 -->
    <view class="section-title">当前账号</view>
    <view class="card row">
      <text class="lbl">手机号</text>
      <text class="val">{{ phoneText }}</text>
    </view>

    <!-- 健康档案摘要 -->
    <view class="section-title">健康档案（当前就餐：{{ currentName || '未选择' }}）</view>
    <view class="card">
      <view v-if="!current" class="empty-line">未选择就餐成员</view>
      <template v-else>
        <view class="kv">
          <text class="k">特殊人群</text>
          <text class="v">{{ audiencesText }}</text>
        </view>
        <view class="kv">
          <text class="k">营养约束</text>
          <text class="v">{{ nutritionText }}</text>
        </view>
        <view class="kv">
          <text class="k">完整档案</text>
          <text class="v muted">{{ rawProfile }}</text>
        </view>
      </template>
      <view class="switch-row">
        <text class="switch-hint">就餐成员影响营养推荐与采购</text>
        <button class="btn-ghost sm" @click="onSwitchMember">切换</button>
      </view>
    </view>

    <button class="btn-primary logout" @click="onLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import { useAuthStore } from '@/store/auth'

const m = useMemberStore()
const auth = useAuthStore()

const current = computed(() => m.members.find((x) => x.id === m.currentId) || null)
const currentName = computed(() => current.value?.name || '')
const phoneText = computed(() => {
  // 当前登录账号优先显示其 phone；否则回退到本地存的登录态
  return current.value?.phone || auth.nickname || '—'
})

/** healthProfile 里常见字段：特殊人群 audiences / 营养约束 nutritionLimits。 */
function profileMap(): Record<string, any> {
  const hp = current.value?.healthProfile
  if (!hp) return {}
  if (hp instanceof Object) return hp as Record<string, any>
  try { return JSON.parse(String(hp)) } catch { return {} }
}

const audiencesText = computed(() => {
  const a = profileMap().audiences || profileMap().audience || profileMap().specialGroups
  if (!a) return '未设置'
  return Array.isArray(a) ? a.join('、') : String(a)
})
const nutritionText = computed(() => {
  const n = profileMap().nutritionLimits || profileMap().nutrition || profileMap().constraints
  if (!n) return '未设置'
  try {
    const obj = typeof n === 'string' ? JSON.parse(n) : n
    if (obj && typeof obj === 'object') {
      return Object.entries(obj).map(([k, v]) => `${k}≤${v}`).join('，')
    }
  } catch { /* ignore */ }
  return String(n)
})
const rawProfile = computed(() => {
  const hp = profileMap()
  const keys = Object.keys(hp)
  return keys.length ? JSON.stringify(hp) : '空'
})

/** 用 ActionSheet 切换就餐成员（原生，无输入框响应问题）。 */
function onSwitchMember() {
  if (!m.members.length) {
    uni.showToast({ title: '暂无成员', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: m.members.map((x) => x.name),
    success: (r) => {
      const picked = m.members[r.tapIndex]
      if (picked) {
        m.switchTo(picked.id).then(() => {
          uni.showToast({ title: '已切换', icon: 'success' })
        })
      }
    },
  })
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出吗？',
    success: (r) => {
      if (r.confirm) auth.logout()
    },
  })
}

onShow(() => { m.load() })
</script>

<style scoped>
.page { padding: 0 14px 24px; }
.section-title { font-size: 14px; color: #999; margin: 18px 0 8px; }

.card { padding: 14px; }
.row { display: flex; align-items: center; justify-content: space-between; }
.lbl { font-size: 14px; color: #666; }
.val { font-size: 15px; color: #222; font-weight: 600; }

.kv { display: flex; padding: 6px 0; border-bottom: 1px solid #f3f3f3; }
.kv:last-of-type { border-bottom: none; }
.k { flex: 0 0 90px; font-size: 13px; color: #999; }
.v { flex: 1; font-size: 13px; color: #222; word-break: break-all; }
.v.muted { color: #aaa; font-size: 12px; }
.empty-line { color: #aaa; font-size: 13px; padding: 8px 0; }

.switch-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 10px; padding-top: 10px; border-top: 1px solid #f3f3f3;
}
.switch-hint { font-size: 12px; color: #999; }
.sm { font-size: 13px; padding: 6px 14px; }

.logout { margin-top: 28px; }

button::after { border: none; }
</style>
