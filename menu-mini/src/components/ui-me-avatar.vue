<template>
  <!-- 主 Tab 右上角「我」的头像（showcase-ai-community 导航改版：「我的」不再是 Tab）。点击进入我的。 -->
  <view class="me" aria-label="我的" data-testid="me-avatar" @click="goProfile">
    <ui-member-avatar :name="me.name" :avatar-url="me.avatarUrl" :size="size" ring />
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { getMe } from '@/api/member'
import { goProfile } from '@/utils/nav'

withDefaults(defineProps<{ size?: number }>(), { size: 36 })

// 模块级缓存：五个 Tab 共用一次请求
const me = reactive<{ name: string; avatarUrl: string | null }>({
  name: uni.getStorageSync('nickname') || '我',
  avatarUrl: null,
})
if (!cache.loaded) {
  cache.loaded = true
  getMe()
    .then((m) => {
      cache.name = m.name
      cache.avatarUrl = m.avatarUrl ?? null
      Object.assign(me, { name: m.name, avatarUrl: m.avatarUrl ?? null })
    })
    .catch(() => (cache.loaded = false))
} else {
  Object.assign(me, { name: cache.name || me.name, avatarUrl: cache.avatarUrl })
}
</script>

<script lang="ts">
const cache: { loaded: boolean; name: string; avatarUrl: string | null } = { loaded: false, name: '', avatarUrl: null }
</script>

<style scoped>
.me {
  border-radius: var(--radius-pill);
  box-shadow: 0 2px 8px rgba(var(--sh), 0.12);
  flex-shrink: 0;
}
</style>
