<template>
  <!-- 食集封面拼图：1 张满幅 / 2 张左右 / ≥3 张左大右二（最后一格叠「+N」）。 -->
  <view class="cl" :style="{ height: height + 'px', borderRadius: radius + 'px' }">
    <template v-if="covers.length <= 1">
      <ui-dish-image :src="covers[0]" :name="names[0]" :height="height" :radius="0" original />
    </template>
    <template v-else-if="covers.length === 2">
      <view class="half"><ui-dish-image :src="covers[0]" :name="names[0]" :height="height" :radius="0" /></view>
      <view class="half"><ui-dish-image :src="covers[1]" :name="names[1]" :height="height" :radius="0" /></view>
    </template>
    <template v-else>
      <view class="big"><ui-dish-image :src="covers[0]" :name="names[0]" :height="height" :radius="0" original /></view>
      <view class="side">
        <ui-dish-image :src="covers[1]" :name="names[1]" :height="(height - 3) / 2" :radius="0" />
        <ui-dish-image :src="covers[2]" :name="names[2]" :height="(height - 3) / 2" :radius="0">
          <view v-if="more > 0" class="more"><text class="more-txt">+{{ more }}</text></view>
        </ui-dish-image>
      </view>
    </template>
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiDishImage from './ui-dish-image.vue'

const props = withDefaults(
  defineProps<{ items: { cover?: string | null; name?: string | null }[]; height?: number; radius?: number }>(),
  { height: 150, radius: 16 },
)
const covers = computed(() => props.items.map((i) => i.cover || ''))
const names = computed(() => props.items.map((i) => i.name || ''))
const more = computed(() => Math.max(0, props.items.length - 3))
</script>

<style scoped>
.cl {
  position: relative;
  display: flex;
  gap: 3px;
  overflow: hidden;
  background: var(--secondary);
}
.half {
  flex: 1;
}
.big {
  flex: 1.6;
}
.side {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.more {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
}
.more-txt {
  color: var(--on-primary);
  font-size: var(--fs-lg);
  font-weight: 700;
}
</style>
