<template>
  <!-- 异常态三件套（状态规范 · prototype States）：
       loading = 骨架屏（不用转圈）/ empty = 插画 + 文案 + 下一步 / error = 插画 + 重试 + 报错编号。 -->
  <view class="ustate">
    <ui-skeleton v-if="mode === 'loading'" :variant="skeleton" :count="3" />

    <view v-else class="box" :data-testid="'state-' + mode">
      <view class="illus">
        <view class="plate" :class="{ err: mode === 'error' }">
          <ui-ing-icon v-for="(k, i) in keys" :key="k" class="ill-ic" :class="'p' + i" :icon-key="k" :size="i === 0 ? 44 : 32" bare />
          <text v-if="mode === 'error'" class="bang">!</text>
        </view>
      </view>
      <text class="main">{{ text || (mode === 'empty' ? '这里还空着' : '没加载出来') }}</text>
      <text v-if="hint || mode === 'error'" class="hint">{{ hint || '网络不太好，或者服务器开小差了' }}</text>
      <view class="acts">
        <view v-if="mode === 'error' && retry" class="btn primary" data-testid="state-retry" @click="$emit('retry')">
          <text class="btn-txt on">重试</text>
        </view>
        <view v-if="actionText" class="btn" :class="{ primary: mode === 'empty' }" @click="$emit('action')">
          <text class="btn-txt" :class="{ on: mode === 'empty' }">{{ actionText }}</text>
        </view>
      </view>
      <text v-if="mode === 'error' && errId" class="eid" data-testid="state-error-id" @click="toFeedback">
        报错编号 {{ errId }} · 反馈时附上它
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiSkeleton from './ui-skeleton.vue'
import UiIngIcon from './ui-ing-icon.vue'
import { lastErrorId } from '@/utils/request'

const props = withDefaults(
  defineProps<{
    mode: 'loading' | 'empty' | 'error'
    text?: string
    hint?: string
    retry?: boolean
    /** 空态下一步（主按钮）/ 错误态次按钮 */
    actionText?: string
    /** 插画用的食材 key（最多 3 个） */
    illus?: string[]
    skeleton?: 'list' | 'hero' | 'grid'
    /** 指定报错编号；不传取最近一次失败请求 */
    errorId?: string
  }>(),
  { skeleton: 'list' },
)
defineEmits<{ (e: 'retry'): void; (e: 'action'): void }>()

const keys = computed(() => (props.illus && props.illus.length ? props.illus : props.mode === 'error' ? ['rice', 'egg'] : ['tomato', 'egg', 'broccoli']).slice(0, 3))
const errId = computed(() => props.errorId ?? lastErrorId())

function toFeedback() {
  uni.navigateTo({ url: `/pages/profile/Feedback?errorId=${encodeURIComponent(errId.value)}` })
}
</script>

<style scoped>
.ustate {
  padding: var(--space-sm) 0;
}
.box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-xxl) var(--space-xl);
  gap: var(--space-sm);
}
.illus {
  margin-bottom: var(--space-sm);
}
.plate {
  position: relative;
  width: 120px;
  height: 96px;
  border-radius: 48px 48px 40px 40px;
  background: var(--secondary);
}
.plate.err {
  background: var(--error-soft);
}
.ill-ic {
  position: absolute;
}
.p0 { left: 38px; top: 18px; }
.p1 { left: 12px; top: 46px; opacity: 0.85; }
.p2 { right: 12px; top: 44px; opacity: 0.85; }
.bang {
  position: absolute;
  right: -6px;
  top: -6px;
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: var(--radius-pill);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-lg);
  font-weight: 800;
}
.main {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.hint {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
  text-align: center;
}
.acts {
  margin-top: var(--space-sm);
  display: flex;
  gap: var(--space-sm);
}
.btn {
  height: 40px;
  padding: 0 var(--space-xl);
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.primary {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.btn-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.btn-txt.on {
  color: var(--on-primary);
}
.eid {
  margin-top: var(--space-xs);
  font-size: var(--fs-xs);
  color: var(--body);
}
</style>
