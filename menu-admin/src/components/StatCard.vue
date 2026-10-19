<script setup lang="ts">
import type { Component } from 'vue'

/** 指标卡（原型 .kpi）：图标 + 标签 + 大数字 + 说明；to 存在时可点击。tone 控制数字颜色。 */
withDefaults(
  defineProps<{
    label: string
    value: string | number
    hint?: string
    hintTone?: 'ok' | 'warn' | 'err' | 'muted'
    tone?: 'default' | 'ok' | 'warn' | 'err'
    icon?: Component
    clickable?: boolean
  }>(),
  { hint: '', hintTone: 'muted', tone: 'default', icon: undefined, clickable: false },
)
defineEmits<{ (e: 'click'): void }>()
</script>

<template>
  <div class="stat" :class="{ clickable }" :role="clickable ? 'button' : undefined" @click="clickable && $emit('click')">
    <span v-if="icon" class="ico"><el-icon :size="16"><component :is="icon" /></el-icon></span>
    <span class="label">{{ label }}</span>
    <b class="value" :class="tone">{{ value }}</b>
    <span v-if="hint" class="hint" :class="hintTone">{{ hint }}</span>
  </div>
</template>

<style scoped>
.stat {
  background: var(--yh-panel);
  border: 1px solid var(--yh-border);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: 0 2px 10px rgba(var(--yh-shadow-rgb), 0.08);
  min-width: 0;
  transition: border-color 0.15s;
}
.stat.clickable {
  cursor: pointer;
}
.stat.clickable:hover {
  border-color: var(--el-color-primary);
}
.ico {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--yh-highlight);
  color: var(--yh-primary-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}
.label {
  font-size: 13px;
  color: var(--yh-body);
}
.value {
  font-size: 28px;
  font-weight: 700;
  color: var(--yh-title);
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
}
.value.ok {
  color: var(--yh-success-text);
}
.value.warn {
  color: var(--yh-warning-text);
}
.value.err {
  color: var(--yh-error-text);
}
.hint {
  font-size: 12px;
  color: var(--yh-body);
}
.hint.ok {
  color: var(--yh-success-text);
  font-weight: 700;
}
.hint.warn {
  color: var(--yh-warning-text);
  font-weight: 700;
}
.hint.err {
  color: var(--yh-error-text);
  font-weight: 700;
}
</style>
