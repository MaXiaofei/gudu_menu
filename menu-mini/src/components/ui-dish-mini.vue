<template>
  <!-- 横滑小菜卡（智荐：最近做过 / 你常做 / 季节鲜）：封面或首字块 + 菜名 + 副信息 + 覆盖率 -->
  <view class="mini" @click="$emit('tap')">
    <image v-if="cover && !failed" class="cover" :src="cover" mode="aspectFill" lazy-load @error="failed = true" />
    <view v-else class="cover cover-ph"><text class="cover-ph-txt">{{ initial }}</text></view>
    <text class="name">{{ name }}</text>
    <text v-if="meta" class="meta">{{ meta }}</text>
    <text v-if="coverage" class="cov">{{ coverage }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  name: string
  cover?: string
  meta?: string
  coverage?: string
}>()
defineEmits<{ (e: 'tap'): void }>()

const failed = ref(false)
const initial = computed(() => [...(props.name || '').trim()][0] || '菜')
</script>

<style scoped>
.mini {
  width: 112px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.cover {
  width: 112px;
  height: 84px;
  border-radius: var(--r-md);
  background: var(--secondary);
}
.cover-ph {
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-ph-txt {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  opacity: 0.45;
}
.name {
  margin-top: 3px;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  font-size: var(--fs-tiny);
  color: var(--caption);
}
.cov {
  font-size: var(--fs-tiny);
  font-weight: 700;
  color: var(--success);
}
</style>
