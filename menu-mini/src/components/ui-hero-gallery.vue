<template>
  <!-- 满宽主图轮播（菜谱详情）：页码 + 顶部渐变保证浮动按钮可读；无图回退大号首字占位。 -->
  <view class="uhero" :style="{ height: height + 'px' }">
    <swiper v-if="urls.length" class="sw" :circular="urls.length > 1" @change="onChange">
      <swiper-item v-for="(u, i) in urls" :key="i">
        <image class="img" :src="u" mode="aspectFill" @click="$emit('preview', i)" />
      </swiper-item>
    </swiper>
    <view v-else class="ph"><text class="ph-txt">{{ initial }}</text></view>
    <view class="shade-top" />
    <view class="shade-bottom" />
    <view v-if="urls.length > 1" class="pager"><text class="pager-txt">{{ cur + 1 }} / {{ urls.length }}</text></view>
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { toAbsolute } from '@/utils/image'

const props = withDefaults(defineProps<{ images: (string | null | undefined)[]; name?: string; height?: number }>(), {
  height: 280,
})
defineEmits<{ (e: 'preview', index: number): void }>()

const urls = computed(() => (props.images || []).filter(Boolean).map((u) => toAbsolute(u as string)))
const cur = ref(0)
function onChange(e: any) {
  cur.value = e?.detail?.current ?? 0
}
const initial = computed(() => [...(props.name || '').trim()][0] || '菜')
</script>

<style scoped>
.uhero {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--secondary);
}
.sw,
.img,
.ph {
  width: 100%;
  height: 100%;
}
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
}
.ph-txt {
  font-size: var(--fs-display);
  font-weight: 700;
  color: var(--title);
  opacity: 0.4;
}
.shade-top {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 120px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.32), rgba(0, 0, 0, 0));
  pointer-events: none;
}
.shade-bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.18), rgba(0, 0, 0, 0));
  pointer-events: none;
}
.pager {
  position: absolute;
  right: 16px;
  bottom: 28px;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.42);
}
.pager-txt {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--on-primary);
}
</style>
