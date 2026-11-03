<script setup lang="ts">
/**
 * 「加载失败 + 重试」共享组件：接口失败时不要把列表静默置空，显示原因并给重试入口。
 */
withDefaults(
  defineProps<{
    /** 失败原因（可空） */
    message?: string
    /** 主文案 */
    title?: string
    compact?: boolean
  }>(),
  { message: '', title: '加载失败', compact: false },
)
defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div class="load-error" :class="{ compact }" role="alert" data-testid="load-error">
    <b>{{ title }}</b>
    <span v-if="message" class="yh-muted" data-testid="load-error-msg">{{ message }}</span>
    <el-button type="primary" size="small" data-testid="load-error-retry" @click="$emit('retry')">重试</el-button>
  </div>
</template>

<style scoped>
.load-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 16px;
  text-align: center;
}
.load-error.compact {
  padding: 14px 12px;
  flex-direction: row;
  justify-content: center;
}
.load-error b {
  color: var(--yh-error-text, #c0392b);
}
</style>
