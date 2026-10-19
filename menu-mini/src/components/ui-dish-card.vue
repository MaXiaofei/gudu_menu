<template>
  <!-- 竖版菜品卡（智荐横滑）：菜品图 + 菜名 + 副信息 + 覆盖率徽章。 -->
  <view class="dc" :style="{ width: width + 'px' }" @click="$emit('tap')">
    <ui-dish-image :src="cover" :name="name" :width="width" :height="imgHeight" :radius="12">
      <text v-if="badge" class="badge">{{ badge }}</text>
    </ui-dish-image>
    <text class="name">{{ name }}</text>
    <text v-if="coverageLabel" class="cov" :class="'cov-' + (coverageTone || 'partial')">{{ coverageLabel }}</text>
    <text v-if="meta" class="meta">{{ meta }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiDishImage from './ui-dish-image.vue'

const props = withDefaults(
  defineProps<{
    name: string
    cover?: string | null
    meta?: string
    coverageLabel?: string
    coverageTone?: 'full' | 'partial' | 'none' | ''
    badge?: string
    width?: number
    /** 图片高宽比（默认 3:4 → 0.75） */
    ratio?: number
  }>(),
  { width: 140, ratio: 0.75 },
)
defineEmits<{ (e: 'tap'): void }>()
const imgHeight = computed(() => Math.round(props.width * props.ratio))
</script>

<style scoped>
.dc {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
  vertical-align: top;
  white-space: normal;
}
.badge {
  position: absolute;
  left: 6px;
  top: 6px;
  padding: 2px 6px;
  border-radius: var(--radius-xs);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
  font-weight: 700;
}
.name {
  margin-top: 4px;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cov {
  align-self: flex-start;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.cov-full { background: var(--success-soft); color: var(--success-deep); }
.cov-partial { background: var(--warning-soft); color: var(--warning-deep); }
.cov-none { background: var(--error-soft); color: var(--error-deep); }
.meta {
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
