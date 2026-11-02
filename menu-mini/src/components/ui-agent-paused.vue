<template>
  <!-- 咕嘟助手暂停服务：ui-feature-paused 的咕嘟助手特化（保留原用法与 data-testid="agent-paused"）。
       状态未知时由 ui-feature-paused 渲染加载态，不先闪暂停提示。 -->
  <view v-if="paused" data-testid="agent-paused">
    <ui-feature-paused feature="agentChat" :back="back" />
  </view>
  <ui-feature-paused v-else feature="agentChat" :back="back" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { featureCover } from '@/utils/featureSwitch'

withDefaults(defineProps<{ back?: boolean }>(), { back: true })
const paused = computed(() => featureCover('agentChat') === 'paused')
</script>
