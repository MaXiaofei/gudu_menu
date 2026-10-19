<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { initialOf, thumbOf } from '@/utils/image'

/**
 * 菜品图（原型 .thumb / 卡片封面）：有图显示缩略图，缺图或加载失败回退首字占位（secondary 底）。
 * size 为数字时是正方形边长；传 ratio 时宽度 100%、按比例撑高（卡片封面）。
 */
const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    size?: number
    ratio?: string
    radius?: number
    /** 用原图（大图位置），默认取缩略图 */
    original?: boolean
  }>(),
  { src: '', name: '', size: 44, ratio: '', radius: 10, original: false },
)

const failed = ref(false)
watch(() => props.src, () => (failed.value = false))

const url = computed(() => (props.original ? (props.src ?? '').trim() : thumbOf(props.src)))
const showImg = computed(() => !!url.value && !failed.value)
const boxStyle = computed(() =>
  props.ratio
    ? { width: '100%', aspectRatio: props.ratio, borderRadius: `${props.radius}px`, fontSize: '28px' }
    : {
        width: `${props.size}px`,
        height: `${props.size}px`,
        borderRadius: `${props.radius}px`,
        fontSize: `${Math.round(props.size * 0.4)}px`,
      },
)
</script>

<template>
  <span class="dish-thumb" :style="boxStyle" :title="name || undefined">
    <img v-if="showImg" :src="url" :alt="name || '菜品图'" loading="lazy" @error="failed = true" />
    <span v-else class="initial" data-testid="dish-thumb-initial">{{ initialOf(name) }}</span>
  </span>
</template>

<style scoped>
.dish-thumb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--yh-secondary);
  vertical-align: middle;
}
.dish-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.initial {
  font-weight: 700;
  color: var(--yh-title);
  opacity: 0.45;
}
</style>
