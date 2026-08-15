<template>
  <!-- 组尾「加载更多 · 还有 N 项」胶囊（库存分页定稿样式）：白底 + 本组色描边/文字。
       loading=true 时变「加载中…」禁用。color 缺省用 caption 灰。 -->
  <view class="more" :class="{ 'is-loading': loading }" :style="moreStyle" @click="onClick">
    <text>{{ loading ? '加载中…' : `加载更多 · 还有 ${remain} 项` }}</text>
    <text v-if="!loading" class="caret">▾</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    remain: number
    color?: string
    loading?: boolean
  }>(),
  { color: 'var(--caption)', loading: false },
)

const emit = defineEmits<{ (e: 'more'): void }>()

const moreStyle = computed(() =>
  props.loading ? '' : `color:${props.color};border-color:${props.color}59`,
)

function onClick() {
  if (!props.loading) emit('more')
}
</script>

<style scoped>
.more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 8px auto;
  padding: 6px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-pill);
  font-size: 10px;
  font-weight: 700;
}
.caret {
  font-size: 9px;
}
.is-loading {
  color: var(--caption) !important;
}
</style>
