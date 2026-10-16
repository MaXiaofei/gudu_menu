<template>
  <!-- 首字色块头像（DESIGN.md §10.4/§10.5 占位铁律）：无图位一律渲染，绝不留白。 -->
  <view class="avatar" :style="{ width: size + 'px', height: size + 'px', borderRadius: r + 'px' }">
    <image v-if="src && !failed" class="img" :src="src" mode="aspectFill" @error="failed = true" />
    <text v-else class="txt" :style="{ fontSize: fontSize + 'px' }">{{ initial }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    name?: string | null
    size?: number
    fallback?: string
    /** 头像图（绝对地址）；加载失败回退首字 */
    src?: string | null
  }>(),
  { name: '', size: 40, fallback: '菜' },
)

const initial = computed(() => {
  const n = (props.name || '').trim()
  return n ? [...n][0] : props.fallback
})
const failed = ref(false)
watch(() => props.src, () => (failed.value = false))
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
  overflow: hidden;
}
.img {
  width: 100%;
  height: 100%;
}
.txt {
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
