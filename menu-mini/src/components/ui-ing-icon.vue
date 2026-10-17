<template>
  <!-- 食材插画（prototype-ui-rollout P0）：iconKey → 名称关键词 → 品类兜底 → 首字；secondary 浅底圆角块，「用完」去色。 -->
  <view class="uing" :class="{ out }" :style="boxStyle">
    <image v-if="src && !failed" class="img" :style="imgStyle" :src="src" mode="aspectFit" @error="failed = true" />
    <text v-else class="txt" :style="{ fontSize: Math.round(size * 0.42) + 'px' }">{{ initial }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ingIconSrc, resolveIngKey } from '@/utils/ingIcon'

const props = withDefaults(
  defineProps<{
    iconKey?: string | null
    name?: string | null
    category?: string | null
    size?: number
    /** 用完：灰度 + 半透明 */
    out?: boolean
    /** 去掉底块（叠放在彩色底上时） */
    bare?: boolean
  }>(),
  { size: 40, out: false, bare: false },
)

const failed = ref(false)
const key = computed(() => resolveIngKey({ iconKey: props.iconKey, name: props.name, category: props.category }))
watch(key, () => (failed.value = false))
const src = computed(() => ingIconSrc(key.value))
const initial = computed(() => [...(props.name || '').trim()][0] || '食')

const boxStyle = computed(() => ({
  width: props.size + 'px',
  height: props.size + 'px',
  borderRadius: Math.round(props.size * 0.28) + 'px',
  background: props.bare ? 'transparent' : 'var(--secondary)',
}))
const imgStyle = computed(() => {
  const w = Math.round(props.size * (props.bare ? 1 : 0.74)) + 'px'
  return { width: w, height: w }
})
</script>

<style scoped>
.uing {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}
.uing.out {
  filter: grayscale(1);
  opacity: 0.55;
}
.txt {
  font-weight: 700;
  color: var(--primary-deep);
}
</style>
