<template>
  <!-- 写菜谱预览页（数据来自 preview store，无接口；发布回调写菜谱页） -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <view style="flex: 1" />
      <text class="edit" @click="goBack">编辑</text>
    </view>

    <scroll-view scroll-y class="body">
      <view v-if="d" class="inner">
        <!-- 封面 -->
        <image v-if="d.coverLocal || d.coverUrl" class="cover" :src="d.coverLocal || thumbOf(d.coverUrl)" mode="aspectFill" />
        <view v-else class="cover ph"><text class="ph-txt">{{ initial }}</text></view>

        <text class="name">{{ d.name || '未命名菜谱' }}</text>
        <text class="meta">备料 {{ d.prepTime || '-' }} 分 · 烹饪 {{ d.cookTime || '-' }} 分 · 难度 {{ stars }}</text>

        <view v-if="d.tags.length" class="tags">
          <view v-for="t in d.tags" :key="t" class="tag">{{ t }}</view>
        </view>

        <text class="sec-label">用料</text>
        <view class="card">
          <view v-for="(ing, i) in d.ingredients" :key="i" class="ing-row">
            <text class="ing-name">{{ ing.name }}</text>
            <text class="ing-amount">{{ ing.amount }}</text>
          </view>
          <view v-if="!d.ingredients.length" class="none">还没有加用料</view>
        </view>

        <template v-if="d.note">
          <text class="sec-label">介绍</text>
          <view class="card"><text class="note">{{ d.note }}</text></view>
        </template>

        <text class="sec-label">做法</text>
        <view class="card">
          <view v-for="(s, i) in d.steps" :key="i" class="step-row">
            <view class="step-no"><text class="step-no-txt">{{ i + 1 }}</text></view>
            <text class="step-text">{{ s.text }}</text>
          </view>
          <view v-if="!d.steps.length" class="none">还没有写步骤</view>
        </view>
        <view style="height: 96px" />
      </view>
    </scroll-view>

    <view class="bottom">
      <button class="btn-primary" :disabled="publishing" @click="publish">
        {{ publishing ? '发布中…' : `发布「${d?.name || '未命名菜谱'}」` }}
      </button>
      <text class="bottom-hint">发布后进菜谱库 · 不满意点左上角返回改</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { usePreviewStore } from '@/store/preview'
import { thumbOf } from '@/utils/image'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const preview = usePreviewStore()
const d = computed(() => preview.data)
const publishing = ref(false)

const initial = computed(() => {
  const n = d.value?.name || ''
  return [...n][0] || '菜'
})
const stars = computed(() => {
  const n = Math.min(5, Math.max(0, d.value?.difficulty ?? 0))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
})

function goBack() {
  uni.navigateBack()
}

/** 发布：通过页面栈回调写菜谱页的发布方法（onPublish）。 */
function publish() {
  publishing.value = true
  const pages = getCurrentPages()
  const prev = pages[pages.length - 2] as any
  if (prev?.$vm?.onPublishFromPreview) {
    prev.$vm.onPublishFromPreview()
    publishing.value = false
  } else {
    publishing.value = false
    uni.showToast({ title: '发布失败，请返回重试', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  display: flex;
  align-items: center;
  padding: 0 14px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 8px;
}
.edit {
  font-size: 13px;
  font-weight: 800;
  color: var(--primary);
}
.body {
  flex: 1;
  min-height: 0;
}
.inner { padding: 0 16px; }
.cover {
  width: 100%;
  height: 150px;
  border-radius: var(--r-lg);
  margin-top: 6px;
  background: var(--primary-soft);
}
.cover.ph { display: flex; align-items: center; justify-content: center; }
.ph-txt { font-size: 44px; font-weight: 700; color: var(--title); opacity: 0.45; }
.name {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: var(--title);
  margin-top: 12px;
}
.meta { display: block; font-size: 11px; color: var(--caption); margin-top: 4px; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.tag {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  background: var(--primary-soft);
  color: var(--title);
  font-size: 10px;
}
.sec-label {
  display: block;
  font-size: 12px;
  font-weight: 800;
  color: var(--title);
  margin: 16px 0 8px;
}
.card {
  background: var(--card);
  border-radius: var(--r-md);
  padding: 6px 14px;
}
.ing-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}
.ing-row + .ing-row { border-top: 1px solid var(--bg); }
.ing-name { font-size: 12px; font-weight: 700; color: var(--title); }
.ing-amount { font-size: 12px; font-weight: 800; color: var(--title); }
.note { font-size: 12px; color: var(--body); line-height: 1.6; padding: 8px 0; }
.step-row {
  display: flex;
  gap: 10px;
  padding: 8px 0;
}
.step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-no-txt { color: #FFFFFF; font-size: 11px; font-weight: 700; }
.step-text { flex: 1; font-size: 12px; color: var(--body); line-height: 1.6; }
.none { font-size: 12px; color: var(--caption); padding: 10px 0; }
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bottom-hint { text-align: center; font-size: 10px; color: var(--caption); }
</style>
