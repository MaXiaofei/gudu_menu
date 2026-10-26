<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 家庭选择 / 切换（也是 403 FAMILY_ACCESS_DENIED 与退出、解散后的落脚页）：切换、新建、凭邀请码加入。 -->
  <view class="page">
    <ui-back-header title="选择家庭" subtitle="每个家庭的菜谱、余量和食集互相独立" />
    <view class="body">
      <view v-if="reasonText" class="callout" data-testid="switch-reason">
        <text class="callout-txt">{{ reasonText }}</text>
      </view>

      <ui-state v-if="loading" mode="loading" />
      <view v-else class="card">
        <text class="group-title">我加入的家庭（{{ store.families.length }}/5）</text>
        <view
          v-for="f in store.families"
          :key="f.id"
          class="row"
          :data-testid="'family-item-' + f.id"
          @click="onPick(f.id)"
        >
          <view class="row-main">
            <text class="row-label strong">{{ f.name }}</text>
            <text class="row-sub">{{ f.memberCount }} 位成员 · 我是{{ roleLabel(f.role) }}</text>
          </view>
          <text v-if="f.id === store.currentId" class="role OWNER" data-testid="family-current">当前</text>
          <text v-else class="row-act">切换</text>
        </view>
        <text v-if="!store.families.length" class="note">你还没有家庭，新建一个，或用家人给的邀请码加入。</text>
      </view>

      <view class="card">
        <text class="group-title">用邀请码加入</text>
        <view class="join-row">
          <view class="input-wrap grow">
            <input v-model="code" class="ipt" data-testid="join-code" placeholder="输入家人发来的邀请码" placeholder-class="ipt-ph" />
          </view>
          <button class="btn join-btn" :disabled="busy" data-testid="join-submit" @click="onJoin">加入</button>
        </view>
      </view>

      <button class="btn ghost" :disabled="busy || store.families.length >= 5" data-testid="family-create" @click="onCreate">
        新建一个家庭
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useFamilyStore } from '@/store/family'
import { roleLabel } from '@/utils/family'
import { inputModal } from './parts/dialog'

const store = useFamilyStore()
const loading = ref(true)
const busy = ref(false)
const code = ref('')
const reason = ref('')

const reasonText = computed(() => {
  if (reason.value === 'denied') return '你已不在之前的家庭中（可能被移出或家庭已解散），请选择一个家庭继续。'
  if (reason.value === 'left') return '已离开该家庭。选择一个家庭继续，或新建一个。'
  return ''
})

onLoad((o) => {
  reason.value = String(o?.reason || '')
})

onShow(async () => {
  loading.value = !store.families.length
  try {
    await store.refresh()
  } catch {}
  loading.value = false
})

function goHome(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
  setTimeout(() => uni.switchTab({ url: '/pages/dish/List' }), 300)
}

async function onPick(id: number) {
  if (busy.value) return
  if (id === store.currentId && !reason.value) {
    uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/dish/List' }) })
    return
  }
  busy.value = true
  try {
    await store.switchTo(id)
    goHome(`已切换到「${store.current?.name || '家庭'}」`)
  } finally {
    busy.value = false
  }
}

async function onJoin() {
  const c = code.value.trim()
  if (!c) {
    uni.showToast({ title: '请输入邀请码', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pkg-family/Invite?code=${encodeURIComponent(c)}` })
}

async function onCreate() {
  const name = await inputModal('新建家庭', '', '给家庭起个名字，如「爸妈家」')
  if (name === null) return
  const v = name.trim()
  if (v.length > 20) {
    uni.showToast({ title: '家庭名最多 20 个字', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await store.create(v || undefined)
    goHome('已新建并切换到新家庭')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
@import './parts/family.css';
.join-row {
  display: flex;
  gap: var(--sp-8);
  margin-top: var(--sp-4);
}
.grow {
  flex: 1;
}
.join-btn {
  width: 88px;
}
</style>
