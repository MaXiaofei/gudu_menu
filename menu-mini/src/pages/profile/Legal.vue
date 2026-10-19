<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 用户协议 / 隐私政策静态页（?type=terms|privacy；正文来自 static/legal/*.ts） -->
  <view class="page">
    <ui-back-header :title="title" />

    <ui-state v-if="!blocks.length" mode="empty" text="暂无内容" />
    <view v-else class="doc">
      <template v-for="(b, i) in blocks" :key="i">
        <!-- 首个 h1 与页面标题重复，跳过 -->
        <text v-if="b.type === 'h1' && i > 0" class="h1">{{ b.text }}</text>
        <text v-else-if="b.type === 'h2'" class="h2">{{ b.text }}</text>
        <text v-else-if="b.type === 'h3'" class="h3">{{ b.text }}</text>
        <view v-else-if="b.type === 'li'" class="li">
          <text class="li-marker">{{ b.marker }}</text>
          <text class="li-text">{{ b.text }}</text>
        </view>
        <text v-else-if="b.type === 'p'" class="p">{{ b.text }}</text>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { TERMS_MD } from '@/static/legal/terms'
import { PRIVACY_MD } from '@/static/legal/privacy'
import { parseLegal } from '@/utils/legal'

const type = ref<'terms' | 'privacy'>('terms')
onLoad((options) => {
  type.value = options?.type === 'privacy' ? 'privacy' : 'terms'
})

const title = computed(() => (type.value === 'privacy' ? '隐私政策' : '用户协议'))
const blocks = computed(() => parseLegal(type.value === 'privacy' ? PRIVACY_MD : TERMS_MD))
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.doc {
  padding: var(--sp-4) var(--sp-16) var(--sp-24);
  display: flex;
  flex-direction: column;
}
.h1 {
  margin: var(--sp-16) 0 var(--sp-8);
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.h2 {
  margin: var(--sp-16) 0 var(--sp-6);
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.h3 {
  margin: var(--sp-12) 0 var(--sp-4);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--body);
}
.p {
  margin: var(--sp-4) 0;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--body);
}
.li {
  display: flex;
  gap: var(--sp-6);
  margin: 3px 0;
}
.li-marker {
  flex-shrink: 0;
  min-width: 14px;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--caption);
}
.li-text {
  flex: 1;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--body);
}
</style>
