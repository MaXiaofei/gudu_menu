<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 意见反馈（录入页：只有返回箭头）：POST /feedback，最多 3 张图 -->
  <view class="page">
    <ui-back-header />

    <view v-if="done" class="done" data-testid="feedback-done">
      <view class="done-badge"><text class="done-tick">✓</text></view>
      <text class="done-title">已收到，谢谢你的反馈</text>
      <text class="done-sub">我们会认真看每一条意见</text>
      <button class="btn-ghost done-btn" @click="goBack">返回</button>
    </view>

    <template v-else>
      <view class="body">
        <text class="sec-label">遇到的问题或建议</text>
        <view class="card">
          <textarea
            v-model="content"
            class="ta"
            :maxlength="FEEDBACK_MAX"
            placeholder="说说哪里不好用、想要什么功能（至少 5 个字）"
            placeholder-class="ph"
            data-testid="feedback-content"
          />
          <text class="count">{{ contentLen }}/{{ FEEDBACK_MAX }}</text>
        </view>

        <text class="sec-label">截图（选填，最多 {{ FEEDBACK_IMAGES_MAX }} 张）</text>
        <view class="imgs">
          <view v-for="(img, i) in images" :key="img.url" class="img-cell">
            <image class="img" :src="img.local" mode="aspectFill" />
            <text class="img-del" @click="images.splice(i, 1)">✕</text>
          </view>
          <view v-if="images.length < FEEDBACK_IMAGES_MAX" class="img-add" @click="addImages">
            <text class="img-add-txt">{{ uploading ? '上传中' : '＋' }}</text>
          </view>
        </view>

        <text class="sec-label">联系方式（选填）</text>
        <view class="card">
          <input
            v-model="contact"
            class="ipt"
            :maxlength="FEEDBACK_CONTACT_MAX"
            placeholder="手机号 / 微信号，方便我们回复你"
            placeholder-class="ph"
            data-testid="feedback-contact"
          />
        </view>
        <text v-if="error" class="err" data-testid="feedback-error">{{ error }}</text>
      </view>

      <view class="bottom">
        <button class="btn-primary" :disabled="submitting || uploading" data-testid="feedback-submit" @click="submit">
          {{ submitting ? '提交中…' : '提交' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { submitFeedback } from '@/api/feedback'
import { chooseImages, uploadImage } from '@/api/upload'
import {
  validateFeedback, charLen,
  FEEDBACK_MAX, FEEDBACK_CONTACT_MAX, FEEDBACK_IMAGES_MAX,
} from '@/utils/validate'
import { APP_VERSION } from '@/config/app'

const content = ref('')
const contact = ref('')
const images = ref<{ local: string; url: string }[]>([])
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')
const done = ref(false)

const contentLen = computed(() => charLen(content.value.trim()))

async function addImages() {
  if (uploading.value) return
  const paths = await chooseImages(FEEDBACK_IMAGES_MAX - images.value.length)
  if (!paths.length) return
  uploading.value = true
  try {
    for (const p of paths) {
      if (images.value.length >= FEEDBACK_IMAGES_MAX) break
      const r = await uploadImage(p)
      images.value.push({ local: p, url: r.url })
    }
  } catch {
    uni.showToast({ title: '图片上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

function clientInfo(): string {
  try {
    const s = uni.getSystemInfoSync()
    return [`v${APP_VERSION}`, s.uniPlatform || s.platform, s.model, s.system, s.version ? `wx${s.version}` : '']
      .filter(Boolean)
      .join(' / ')
      .slice(0, 200)
  } catch {
    return `v${APP_VERSION}`
  }
}

async function submit() {
  const payload = {
    content: content.value,
    contact: contact.value,
    images: images.value.map((i) => i.url),
  }
  error.value = validateFeedback(payload)
  if (error.value) return
  submitting.value = true
  try {
    await submitFeedback({ ...payload, clientInfo: clientInfo() })
    done.value = true
  } catch (e: any) {
    error.value = e?.message || '提交失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

function goBack() {
  uni.navigateBack()
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  padding: var(--sp-4) var(--sp-16) 0;
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
  border-radius: var(--r-md);
  padding: var(--sp-10) var(--sp-12);
}
.ta {
  width: 100%;
  height: 120px;
  font-size: var(--fs-md);
  color: var(--title);
  line-height: 1.6;
}
.count {
  display: block;
  text-align: right;
  font-size: var(--fs-xs);
  color: var(--caption);
}
.ipt {
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--caption);
}
.imgs {
  display: flex;
  gap: var(--sp-8);
}
.img-cell {
  position: relative;
  width: 72px;
  height: 72px;
}
.img {
  width: 72px;
  height: 72px;
  border-radius: var(--r-sm);
  background: var(--secondary);
}
.img-del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 50%;
  background: var(--title);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
}
.img-add {
  width: 72px;
  height: 72px;
  border-radius: var(--r-sm);
  border: 1px dashed var(--primary-soft);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-add-txt {
  font-size: var(--fs-sm);
  color: var(--primary);
}
.err {
  display: block;
  margin-top: var(--sp-10);
  font-size: var(--fs-sm);
  color: var(--error);
}
.bottom {
  padding: var(--sp-8) var(--sp-16) calc(12px + env(safe-area-inset-bottom));
}
.done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-8);
  padding: 64px var(--sp-24);
}
.done-badge {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--success);
  display: flex;
  align-items: center;
  justify-content: center;
}
.done-tick {
  color: var(--on-primary);
  font-size: var(--fs-h2);
  font-weight: 800;
}
.done-title {
  margin-top: var(--sp-8);
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.done-sub {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.done-btn {
  margin-top: var(--sp-16);
  width: 160px;
}
</style>
