<template>
  <!-- 头像叠放：最多 max 个 + 「+N」，白描边负间距。 -->
  <view class="ustack">
    <view v-for="(m, i) in shown" :key="i" class="it" :style="{ marginLeft: i ? -Math.round(size * 0.3) + 'px' : '0', zIndex: shown.length - i }">
      <ui-member-avatar :name="m.name" :avatar-url="m.avatarUrl" :tags="m.tags" :size="size" ring />
    </view>
    <view v-if="rest > 0" class="more" :style="{ width: size + 'px', height: size + 'px', marginLeft: -Math.round(size * 0.3) + 'px' }">
      <text class="more-txt">+{{ rest }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UiMemberAvatar from './ui-member-avatar.vue'

export interface StackMember {
  name?: string | null
  avatarUrl?: string | null
  tags?: string[] | null
}

const props = withDefaults(defineProps<{ items: StackMember[]; max?: number; size?: number }>(), { max: 4, size: 24 })
const shown = computed(() => (props.items || []).slice(0, props.max))
const rest = computed(() => Math.max(0, (props.items || []).length - props.max))
</script>

<style scoped>
.ustack {
  display: flex;
  align-items: center;
}
.it {
  position: relative;
}
.more {
  border-radius: var(--radius-pill);
  background: var(--secondary);
  border: 2px solid var(--card);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}
.more-txt {
  font-size: var(--fs-tiny);
  font-weight: 700;
  color: var(--body);
}
</style>
