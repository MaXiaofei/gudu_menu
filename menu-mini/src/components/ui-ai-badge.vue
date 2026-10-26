<template>
  <!-- AI 生成标识（A.4）：「AI 生成」胶囊；disclaimer=true 时紧跟免责提示；传 source 时右侧给「反馈」入口
       （复用意见反馈页，带来源标记）。颜色全部走主题 token。 -->
  <view class="ai" :class="{ block: disclaimer }" data-testid="ai-badge">
    <text class="ai-chip" data-testid="ai-badge-chip">{{ AI_LABEL }}</text>
    <text v-if="disclaimer" class="ai-note" data-testid="ai-disclaimer">{{ AI_DISCLAIMER_TAIL }}</text>
    <text v-if="source" class="ai-fb" data-testid="ai-feedback" @click.stop="feedback">反馈</text>
  </view>
</template>

<script setup lang="ts">
import { AI_LABEL, AI_DISCLAIMER_TAIL, aiFeedbackUrl, type AiSource } from '@/utils/aiLabel'

const props = defineProps<{
  /** 显示免责提示（营养报告 / 特殊人群推荐 / 周餐程） */
  disclaimer?: boolean
  /** 反馈来源；不传则不显示反馈入口 */
  source?: AiSource
  /** 反馈关联的消息 / 记录 id */
  feedbackRef?: string | number
}>()

function feedback() {
  if (!props.source) return
  uni.navigateTo({ url: aiFeedbackUrl(props.source, props.feedbackRef) })
}
</script>

<style scoped>
.ai {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-6);
  flex-wrap: wrap;
}
.ai.block {
  display: flex;
  padding: var(--sp-8) var(--sp-10);
  border-radius: var(--r-sm);
  background: var(--info-soft);
}
.ai-chip {
  flex-shrink: 0;
  padding: 1px var(--sp-6);
  border-radius: var(--r-pill);
  border: 1px solid var(--info);
  color: var(--info-deep);
  font-size: var(--fs-tiny);
  font-weight: 700;
  line-height: 1.5;
}
.ai-note {
  flex: 1;
  min-width: 0;
  color: var(--caption);
  font-size: var(--fs-xs);
  line-height: 1.5;
}
.ai-fb {
  flex-shrink: 0;
  color: var(--caption);
  font-size: var(--fs-xs);
  text-decoration: underline;
}
</style>
