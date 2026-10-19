<template>
  <!-- 横滑步骤卡：图 + STEP n + 文本；无图步骤为纯文字卡。点图全屏预览。 -->
  <scroll-view class="sc" scroll-x :show-scrollbar="false" enhanced>
    <view class="row">
      <view v-for="(s, i) in steps" :key="i" class="card" :style="{ width: cardWidth + 'px' }">
        <view v-if="firstImg(s)" class="pic" @click="$emit('preview', { step: i, url: firstImg(s) })">
          <image class="pic-img" :src="firstImg(s)" mode="aspectFill" lazy-load />
          <view class="badge"><text class="badge-txt">{{ i + 1 }}</text></view>
        </view>
        <view class="body">
          <text v-if="!firstImg(s)" class="no">STEP {{ i + 1 }}</text>
          <text class="txt">{{ s.text }}</text>
        </view>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
import { thumbList } from '@/utils/image'

export interface StepItem {
  text: string
  images?: string | null
}
withDefaults(defineProps<{ steps: StepItem[]; cardWidth?: number }>(), { cardWidth: 220 })
defineEmits<{ (e: 'preview', p: { step: number; url: string }): void }>()

function firstImg(s: StepItem): string {
  return thumbList(s.images)[0] || ''
}
</script>

<style scoped>
.sc {
  width: 100%;
  white-space: nowrap;
}
.row {
  display: inline-flex;
  gap: var(--space-md);
  padding: 0 var(--space-lg) var(--space-xs);
}
.card {
  display: inline-flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  white-space: normal;
  vertical-align: top;
}
.pic {
  position: relative;
  width: 100%;
  height: 140px;
  background: var(--secondary);
}
.pic-img {
  width: 100%;
  height: 100%;
}
.badge {
  position: absolute;
  left: 8px;
  top: 8px;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.badge-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
}
.body {
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.no {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--primary-deep);
  letter-spacing: 1px;
}
.txt {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--title);
}
</style>
