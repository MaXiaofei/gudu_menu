<template>
  <!-- 厨房圈暂停服务（A.12）：后端紧急关停时盖住整个厨房圈页面，给友好提示而不是报错。 -->
  <view v-if="communityStatus.suspended" class="cp" data-testid="community-paused">
    <view v-if="back" class="cp-top" :style="{ paddingTop: inset + 'px' }">
      <view class="cp-back" @click="goBack"><ui-icon name="back" :size="20" /></view>
    </view>
    <ui-state
      mode="empty"
      :text="MSG_COMMUNITY_SUSPENDED"
      hint="我们正在处理一些内容，稍后再来看看"
      action-text="再看看"
      :illus="['egg', 'tomato', 'fish']"
      @action="retry"
    />
  </view>
</template>

<script setup lang="ts">
import { communityStatus, MSG_COMMUNITY_SUSPENDED } from '@/utils/communityStatus'
import { statusBarHeight } from '@/utils/token'
import { buildTarget } from '@/utils/loginRedirect'

withDefaults(defineProps<{ back?: boolean }>(), { back: true })
const inset = statusBarHeight() + 6

/** 重新展示页面并请求：仍在暂停会再次回到本提示。 */
function retry() {
  communityStatus.suspended = false
  const pages = getCurrentPages()
  const cur: any = pages[pages.length - 1]
  if (cur?.route) uni.reLaunch({ url: buildTarget(cur.route, cur.options) })
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/dish/List' })
}
</script>

<style scoped>
.cp {
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
.cp-top {
  position: absolute;
  left: var(--sp-12);
  top: 0;
}
.cp-back {
  width: 36px;
  height: 36px;
  border-radius: var(--r-pill);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
