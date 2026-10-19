<template>
  <!-- 菜品小圆图叠放（食集行）：最多 max 张，白描边。 -->
  <view class="ts">
    <view v-for="(it, i) in shown" :key="i" class="it" :style="{ marginLeft: i ? -Math.round(size * 0.32) + 'px' : '0', zIndex: shown.length - i }">
      <ui-dish-image :src="it.cover" :name="it.name" :width="size" :height="size" :radius="size" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiDishImage from './ui-dish-image.vue'

const props = withDefaults(defineProps<{ items: { cover?: string | null; name?: string | null }[]; max?: number; size?: number }>(), {
  max: 3,
  size: 36,
})
const shown = computed(() => props.items.slice(0, props.max))
</script>

<style scoped>
.ts {
  display: flex;
  align-items: center;
}
.it {
  position: relative;
  border: 2px solid var(--card);
  border-radius: var(--radius-pill);
  overflow: hidden;
}
</style>
