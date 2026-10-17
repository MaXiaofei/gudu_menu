<template>
  <!-- 成员头像：有 avatarUrl 用真图；没有则按档案给默认插画头像（宝宝/成人），失败再退首字。 -->
  <view class="mav" :class="{ ring }" :style="{ width: size + 'px', height: size + 'px' }">
    <image v-if="url && !failed" class="img" :src="url" mode="aspectFill" @error="failed = true" />
    <text v-else class="txt" :style="{ fontSize: Math.round(size * 0.42) + 'px' }">{{ initial }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { thumbOf } from '@/utils/image'
import { defaultAvatarSrc } from '@/utils/ingIcon'

const props = withDefaults(
  defineProps<{
    name?: string | null
    avatarUrl?: string | null
    tags?: string[] | null
    size?: number
    /** 白描边（叠放、选中态） */
    ring?: boolean
  }>(),
  { size: 32, ring: false },
)

const failed = ref(false)
watch(() => props.avatarUrl, () => (failed.value = false))
const url = computed(() => (props.avatarUrl ? thumbOf(props.avatarUrl) : defaultAvatarSrc(props.name, props.tags)))
const initial = computed(() => [...(props.name || '').trim()][0] || '我')
</script>

<style scoped>
.mav {
  border-radius: var(--radius-pill);
  background: var(--secondary);
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.mav.ring {
  border: 2px solid var(--card);
}
.img {
  width: 100%;
  height: 100%;
}
.txt {
  font-weight: 700;
  color: var(--primary-deep);
}
</style>
