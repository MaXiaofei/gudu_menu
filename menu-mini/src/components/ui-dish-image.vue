<template>
  <!-- 菜品图（prototype-ui-rollout P0）：比例 + 圆角 + 缩略图规则；无图/加载失败回退首字占位（secondary 底 45% 字），不留白。 -->
  <view class="dimg" :style="boxStyle">
    <image
      v-if="url && !failed"
      class="img"
      :src="url"
      mode="aspectFill"
      lazy-load
      @error="failed = true"
    />
    <view v-else class="ph"><text class="ph-txt" :style="{ fontSize: phSize }">{{ initial }}</text></view>
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { thumbOf, toAbsolute } from '@/utils/image'

const props = withDefaults(
  defineProps<{
    /** 后端 coverUrl（相对或绝对） */
    src?: string | null
    name?: string | null
    /** 宽（px 数字或 CSS 长度，默认 100%） */
    width?: number | string
    /** 比例：'1:1' | '4:3' | '16:9' | '3:4'；给了 height 则忽略 */
    ratio?: string
    height?: number | string
    radius?: number | string
    /** true = 用原图（大图位），默认缩略图 */
    original?: boolean
  }>(),
  { ratio: '1:1', radius: 12, original: false },
)

const failed = ref(false)
watch(() => props.src, () => (failed.value = false))

const url = computed(() => (props.src ? (props.original ? toAbsolute(props.src) : thumbOf(props.src)) : ''))
const initial = computed(() => [...(props.name || '').trim()][0] || '菜')
const len = (v: number | string | undefined) => (typeof v === 'number' ? v + 'px' : v)

const boxStyle = computed(() => {
  const s: Record<string, string> = {
    width: len(props.width) || '100%',
    borderRadius: len(props.radius) || '0',
  }
  if (props.height !== undefined) {
    s.height = len(props.height)!
  } else {
    const [w, h] = (props.ratio || '1:1').split(':').map(Number)
    s.paddingTop = `${((h || 1) / (w || 1)) * 100}%`
    s.height = '0'
  }
  return s
})

const phSize = computed(() => {
  const w = typeof props.width === 'number' ? props.width : 96
  return Math.max(14, Math.min(40, Math.round(w * 0.36))) + 'px'
})
</script>

<style scoped>
.dimg {
  position: relative;
  overflow: hidden;
  background: var(--secondary);
  flex-shrink: 0;
}
.img,
.ph {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
}
.ph-txt {
  font-weight: 700;
  color: var(--title);
  opacity: 0.45;
}
</style>
