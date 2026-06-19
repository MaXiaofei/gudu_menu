<template>
  <view class="index">
    <view class="member-bar">
      <text class="label">当前就餐：</text>
      <u-tag :text="currentName || '未选择'" type="warning" />
      <u-button size="mini" type="primary" @click="showPicker = true">切换</u-button>
    </view>
    <view class="entries">
      <u-button @click="goList">浏览菜库</u-button>
      <u-button @click="goCreate">录入新菜</u-button>
      <u-button type="warning" @click="goIngredientCreate">录入食材</u-button>
      <u-button type="warning" @click="goAiRecommend">AI 帮我定菜单</u-button>
      <u-button type="error" @click="onLogout">退出登录</u-button>
    </view>
    <u-picker
      :show="showPicker"
      :columns="[memberNames]"
      @confirm="onPick"
      @cancel="showPicker = false"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import { useAuthStore } from '@/store/auth'

const m = useMemberStore()
const auth = useAuthStore()
const showPicker = ref(false)

const memberNames = computed(() => m.members.map(x => x.name))
const currentName = computed(() => m.members.find(x => x.id === m.currentId)?.name || '')

onShow(() => { m.load() })

function onPick(e: any) {
  const idx = e.indexs ? e.indexs[0] : e.index[0]
  const picked = m.members[idx]
  if (picked) m.switchTo(picked.id)
  showPicker.value = false
}
function goList() {
  // dish/List 由 B4 建页面并注册 pages.json；当前未建，navigateTo 会失败但不影响首页
  try {
    uni.navigateTo({ url: '/pages/dish/List', fail: () => uni.showToast({ title: '菜库页未就绪', icon: 'none' }) })
  } catch {
    uni.showToast({ title: '菜库页未就绪', icon: 'none' })
  }
}
function goCreate() {
  // dish/Create 由 B7 建页面并注册 pages.json；当前未建，navigateTo 会失败但不影响首页
  try {
    uni.navigateTo({ url: '/pages/dish/Create', fail: () => uni.showToast({ title: '录入页未就绪', icon: 'none' }) })
  } catch {
    uni.showToast({ title: '录入页未就绪', icon: 'none' })
  }
}
function goIngredientCreate() {
  uni.navigateTo({ url: '/pages/ingredient/Create' })
}
function goAiRecommend() {
  uni.navigateTo({ url: '/pages/ai/Recommend' })
}
function onLogout() { auth.logout() }
</script>

<style scoped>
.index { padding: 30rpx; }
.member-bar { display: flex; align-items: center; gap: 20rpx; padding: 20rpx 0; border-bottom: 1rpx solid #eee; }
.member-bar .label { font-size: 28rpx; }
.entries { margin-top: 40rpx; }
.entries .u-button, .member-bar .u-button { margin-top: 20rpx; }
</style>
