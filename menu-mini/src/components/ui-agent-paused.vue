<template>
  <!-- 咕嘟助手暂停服务（紧急关停）：盖住咕嘟页 / 对话页，给友好提示而不是报错；tabBar 仍可切到其他功能。 -->
  <view v-if="!featureStatus.agentChat" class="ap" data-testid="agent-paused">
    <view v-if="back" class="ap-top" :style="{ paddingTop: inset + 'px' }">
      <view class="ap-back" @click="goBack"><ui-icon name="back" :size="20" /></view>
    </view>
    <ui-state
      mode="empty"
      :text="MSG_AGENT_SUSPENDED"
      hint="我们正在处理一些内容，稍后再来看看"
      action-text="再看看"
      :illus="['egg', 'tomato', 'fish']"
      @action="retry"
    />
  </view>
</template>

<script setup lang="ts">
import { featureStatus, loadFeatureSwitches, MSG_AGENT_SUSPENDED } from '@/utils/featureSwitch'
import { statusBarHeight } from '@/utils/token'

withDefaults(defineProps<{ back?: boolean }>(), { back: true })
const inset = statusBarHeight() + 6

/** 重新读取开关：已恢复则提示消失，仍在暂停保持提示。 */
async function retry() {
  await loadFeatureSwitches()
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/dish/List' })
}
</script>

<style scoped>
.ap {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 900;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.ap-top {
  position: absolute;
  left: var(--sp-12);
  top: 0;
}
.ap-back {
  width: 36px;
  height: 36px;
  border-radius: var(--r-pill);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
