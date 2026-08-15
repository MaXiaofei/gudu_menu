<template>
  <!-- 首字色块头像（DESIGN.md §10.4/§10.5 占位铁律）：无图位一律渲染，绝不留白。 -->
  <view class="avatar" :style="{ width: size + 'px', height: size + 'px', borderRadius: r + 'px' }">
    <text class="txt" :style="{ fontSize: fontSize + 'px' }">{{ initial }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    name?: string | null
    size?: number
    fallback?: string
  }>(),
  { name: '', size: 40, fallback: '菜' },
)

const initial = computed(() => {
  const n = (props.name || '').trim()
  return n ? [...n][0] : props.fallback
})
const r = computed(() => Math.max(8, Math.round(props.size * 0.25)))
const fontSize = computed(() => Math.round(props.size * 0.42))
</script>

<style scoped>
.avatar {
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.txt {
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
