<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 设置（A.8）：个性化推荐开关（个人信息保护法第 24 条：提供不针对个人特征的选项）。 -->
  <view class="page">
    <ui-back-header title="设置" />

    <view class="body">
      <text class="sec-label">推荐</text>
      <view class="card">
        <view class="row" data-testid="personalized-row">
          <view class="row-main">
            <text class="row-title">个性化推荐</text>
            <text class="row-sub" data-testid="personalized-hint">{{ on ? PERSONALIZED_HINT_ON : PERSONALIZED_HINT_OFF }}</text>
          </view>
          <view
            class="toggle"
            :class="{ on, busy: saving || loading }"
            role="switch"
            :aria-checked="on"
            data-testid="personalized-toggle"
            @click="toggle"
          >
            <view class="knob" />
          </view>
        </view>
      </view>
      <text v-if="error" class="err" data-testid="settings-error">{{ error }}</text>
      <text class="foot">关闭后不影响菜谱搜索、余量、采购清单等功能；你主动发给咕嘟的问题仍会结合家里的情况回答。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getPreference, setPersonalizedRecommend, PERSONALIZED_HINT_OFF, PERSONALIZED_HINT_ON,
} from '@/api/preference'

const on = ref(true)
const loading = ref(true)
const saving = ref(false)
const error = ref('')

onShow(async () => {
  try {
    on.value = (await getPreference()).personalizedRecommend
    error.value = ''
  } catch {
    error.value = '设置没加载出来，请稍后再试'
  } finally {
    loading.value = false
  }
})

async function toggle() {
  if (saving.value || loading.value) return
  const next = !on.value
  saving.value = true
  error.value = ''
  on.value = next // 乐观更新，失败回滚
  try {
    on.value = (await setPersonalizedRecommend(next)).personalizedRecommend
    uni.showToast({ title: next ? '已开启个性化推荐' : '已关闭个性化推荐', icon: 'none' })
  } catch (e: any) {
    on.value = !next
    error.value = e?.message || '保存失败，请稍后再试'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.body {
  padding: var(--sp-4) var(--sp-16) var(--sp-24);
}
.sec-label {
  display: block;
  margin: var(--sp-16) 0 var(--sp-8);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--caption);
}
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-12);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.row-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.row-sub {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
.toggle {
  position: relative;
  flex-shrink: 0;
  width: 48px;
  height: 28px;
  border-radius: var(--r-pill);
  background: var(--border);
  transition: background 0.2s;
}
.toggle.on {
  background: var(--primary-deep);
}
.toggle.busy {
  opacity: 0.6;
}
.knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: var(--r-pill);
  background: var(--card);
  box-shadow: 0 1px 3px rgba(var(--sh), 0.2);
  transition: transform 0.2s;
}
.toggle.on .knob {
  transform: translateX(20px);
}
.err {
  display: block;
  margin-top: var(--sp-10);
  font-size: var(--fs-sm);
  color: var(--error);
}
.foot {
  display: block;
  margin-top: var(--sp-12);
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--caption);
}
</style>
