<script setup lang="ts">
/**
 * 「接口待上线」空态：页面/区块已按原型搭好，但依赖的后端能力尚未提供。
 * 不展示任何示例数据，只说明缺什么、对应哪个 OpenSpec 任务，避免运营误以为是真实数据。
 */
withDefaults(
  defineProps<{
    title?: string
    /** 依赖的后端能力，如「投稿审核状态 dish_submission」 */
    needs?: string
    /** OpenSpec 任务号，如 prototype-ui-rollout 3.7 */
    task?: string
    compact?: boolean
  }>(),
  { title: '接口待上线', needs: '', task: '', compact: false },
)
</script>

<template>
  <div class="pending" :class="{ compact }" data-testid="pending-api">
    <svg class="ill" viewBox="0 0 64 64" aria-hidden="true">
      <rect x="10" y="14" width="44" height="36" rx="8" fill="var(--yh-secondary)" />
      <path d="M20 28h24M20 36h16" stroke="var(--yh-primary-soft)" stroke-width="4" stroke-linecap="round" />
      <circle cx="46" cy="44" r="10" fill="var(--yh-panel)" stroke="var(--yh-primary-deep)" stroke-width="3" />
      <path d="M46 39v5l3 2" stroke="var(--yh-primary-deep)" stroke-width="3" stroke-linecap="round" fill="none" />
    </svg>
    <b>{{ title }}</b>
    <span v-if="needs" class="yh-muted">需要：{{ needs }}</span>
    <span v-if="task" class="yh-muted">计划：{{ task }}</span>
    <slot />
  </div>
</template>

<style scoped>
.pending {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 36px 16px;
  text-align: center;
  color: var(--yh-title);
}
.pending.compact {
  padding: 16px 12px;
}
.pending.compact .ill {
  width: 40px;
  height: 40px;
}
.ill {
  width: 64px;
  height: 64px;
  margin-bottom: 4px;
}
</style>
