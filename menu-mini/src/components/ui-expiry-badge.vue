<template>
  <!-- 保质期徽标：已过期 / 今天到期 / X 天后到期（daysLeft 为空不渲染）。
       已过期、≤2 天红色，≤7 天黄色，更久灰色。 -->
  <text v-if="text" class="exp" :class="'exp-' + level" data-testid="expiry-badge">{{ text }}</text>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { expiryLevel, expiryText } from '@/utils/freshness'

const props = defineProps<{ daysLeft?: number | null }>()
const text = computed(() => expiryText(props.daysLeft))
const level = computed(() => expiryLevel(props.daysLeft))
</script>

<style scoped>
.exp {
  flex-shrink: 0;
  padding: 1px var(--sp-6);
  border-radius: var(--r-pill);
  border: 1px solid currentColor;
  font-size: var(--fs-tiny);
  font-weight: 800;
  line-height: 1.5;
}
.exp-expired {
  color: var(--on-primary);
  background: var(--error);
  border-color: var(--error);
}
.exp-urgent {
  color: var(--error);
}
.exp-soon {
  color: var(--warning-text);
}
.exp-ok {
  color: var(--caption);
}
</style>
