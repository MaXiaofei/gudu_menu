<script setup lang="ts">
import { computed } from 'vue'

/** 服务健康卡（原型 AdminOps 顶部）：名称 + 状态标签 + 一行说明；pending=接口待上线。 */
const props = defineProps<{
  title: string
  status: 'ok' | 'warn' | 'err' | 'pending'
  detail?: string
}>()

const label = computed(() => ({ ok: '正常', warn: '偏慢', err: '异常', pending: '待接入' })[props.status])
const tagClass = computed(() => ({ ok: 'ok', warn: 'warn', err: 'err', pending: 'mute' })[props.status])
</script>

<template>
  <div class="health yh-card" :class="status">
    <span class="row">
      <b>{{ title }}</b>
      <span class="yh-tag" :class="tagClass">{{ label }}</span>
    </span>
    <span class="yh-muted">{{ detail || (status === 'pending' ? '接口待上线' : '') }}</span>
  </div>
</template>

<style scoped>
.health {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
}
.health.err {
  border-color: var(--yh-error-soft);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  color: var(--yh-title);
}
</style>
