<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 全部推荐（二级页）：内容由 parts/RecommendView 提供，底部第三个 Tab 在游客 / 咕嘟关停时也复用它 -->
  <recommend-view ref="viewRef" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RecommendView from './parts/RecommendView.vue'

const viewRef = ref<InstanceType<typeof RecommendView> | null>(null)
onShow(() => viewRef.value?.refresh())
</script>
