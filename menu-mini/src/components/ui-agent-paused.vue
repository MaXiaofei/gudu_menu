<template>
  <!-- 咕嘟助手暂停服务：ui-feature-paused 的咕嘟助手特化（保留原用法与 data-testid="agent-paused"）。 -->
  <view v-if="!featureStatus.agentChat" data-testid="agent-paused">
    <ui-feature-paused feature="agentChat" :back="back" />
  </view>
</template>

<script setup lang="ts">
import { featureStatus } from '@/utils/featureSwitch'

withDefaults(defineProps<{ back?: boolean }>(), { back: true })
</script>
