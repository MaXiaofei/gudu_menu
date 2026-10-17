<template>
  <!-- 离线 / 弱网顶部条：不挡操作；显示待同步条数。 -->
  <view v-if="offline || pending > 0" class="uoff" :class="{ syncing: !offline }" data-testid="offline-bar">
    <text class="dot" />
    <text class="txt">{{ offline ? '已离线，显示的是上次同步的数据' : '正在同步' }}</text>
    <text v-if="pending > 0" class="pd">待同步 {{ pending }}</text>
  </view>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

withDefaults(defineProps<{ pending?: number }>(), { pending: 0 })
const offline = ref(false)

function onNet(r: { isConnected: boolean }) {
  offline.value = !r.isConnected
}
onMounted(() => {
  try {
    uni.getNetworkType({ success: (r: any) => (offline.value = r.networkType === 'none') })
    uni.onNetworkStatusChange(onNet)
  } catch {
    /* 非 uni 运行时（单测）忽略 */
  }
})
onBeforeUnmount(() => {
  try {
    ;(uni as any).offNetworkStatusChange?.(onNet)
  } catch {
    /* noop */
  }
})
defineExpose({ offline })
</script>

<style scoped>
.uoff {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-lg);
  background: var(--title);
}
.uoff.syncing {
  background: var(--info-deep);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--warning);
}
.txt {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--on-primary);
}
.pd {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--warning);
}
</style>
