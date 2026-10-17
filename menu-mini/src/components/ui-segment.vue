<template>
  <!-- 分段 Tab（食集 4 Tab 等）：secondary 底，选中白块；可带计数。 -->
  <view class="useg" :style="{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }">
    <view
      v-for="it in items"
      :key="it.key"
      class="it"
      :class="{ on: it.key === modelValue }"
      :data-testid="'seg-' + it.key"
      @click="$emit('update:modelValue', it.key)"
    >
      <text class="lb">{{ it.label }}</text>
      <text v-if="it.badge !== undefined && it.badge !== null && it.badge !== ''" class="bd">{{ it.badge }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface SegItem {
  key: string
  label: string
  badge?: string | number | null
}
defineProps<{ items: SegItem[]; modelValue: string }>()
defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<style scoped>
.useg {
  display: grid;
  padding: 4px;
  border-radius: var(--radius-md);
  background: var(--secondary);
}
.it {
  height: 36px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.it.on {
  background: var(--card);
  box-shadow: 0 1px 4px rgba(var(--sh), 0.12);
}
.lb {
  font-size: var(--fs-md);
  color: var(--body);
}
.on .lb {
  font-weight: 700;
  color: var(--title);
}
.bd {
  font-size: var(--fs-sm);
  color: var(--body);
}
.on .bd {
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
