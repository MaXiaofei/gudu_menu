<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 意见反馈（录入页：只有返回箭头）：POST /feedback，最多 3 张图 -->
  <view class="page">
    <ui-back-header title="意见反馈" />

    <view v-if="done" class="done" data-testid="feedback-done">
      <view class="done-badge"><text class="done-tick">✓</text></view>
      <text class="done-title">已收到，谢谢你的反馈</text>
      <text class="done-sub">我们会认真看每一条意见</text>
      <button class="btn-ghost done-btn" @click="goBack">返回</button>
    </view>

    <template v-else>
      <view class="body">
        <text class="sec-label">是关于什么的</text>
        <view class="types">
          <view
            v-for="t in types"
            :key="t.key"
            class="type"
            :class="{ on: type === t.key }"
            :data-testid="'fb-type-' + t.key"
            @click="type = t.key"
          >
            <ui-icon :name="t.icon" :size="22" :tone="type === t.key ? 'primary' : 'dark'" />
            <text class="type-txt">{{ t.label }}</text>
          </view>
        </view>

        <text class="sec-label">说说看</text>
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
        <view class="attach" @click="attach = !attach">
          <view class="attach-box" :class="{ on: attach }"><ui-icon v-if="attach" name="check" tone="white" :size="12" /></view>
          <text class="attach-txt">附带设备型号、App 版本<template v-if="errorId">和报错编号 {{ errorId }}</template>，帮我们更快定位</text>
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
import { onLoad } from '@dcloudio/uni-app'
import { lastErrorId } from '@/utils/request'
import { submitFeedback } from '@/api/feedback'
import { chooseImages, uploadImage } from '@/api/upload'
import {
  validateFeedback, charLen,
  FEEDBACK_MAX, FEEDBACK_CONTACT_MAX, FEEDBACK_IMAGES_MAX,
} from '@/utils/validate'
import { APP_VERSION } from '@/config/app'
import { aiSourceTag } from '@/utils/aiLabel'

const content = ref('')
const contact = ref('')
const images = ref<{ local: string; url: string }[]>([])
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')
const done = ref(false)

// 反馈类型（后端 type 字段 P3 上线前也写进 clientInfo，信息不丢）
const TYPES = [
  { key: 'bug', label: '出错了', icon: 'bug' },
  { key: 'feature', label: '想要功能', icon: 'bulb' },
  { key: 'recipe', label: '菜谱有误', icon: 'book' },
  { key: 'other', label: '其他', icon: 'message' },
]
// AI 内容反馈（A.4）：从 AI 内容旁的「反馈」入口进来时追加，来源标记写进 clientInfo 开头（后端 type 字段上线前不丢）
const AI_TYPE = { key: 'ai', label: 'AI 内容', icon: 'sparkle' }
const type = ref('bug')
const attach = ref(true)
const errorId = ref('')
const aiSource = ref('')
const aiRef = ref('')
const types = computed(() => (aiSource.value ? [AI_TYPE, ...TYPES] : TYPES))
onLoad((o) => {
  if (o?.source) aiSource.value = String(o.source).slice(0, 20)
  if (o?.ref) aiRef.value = decodeURIComponent(String(o.ref)).slice(0, 40)
  // 从 AI 内容反馈进来不带上一次请求的报错编号
  errorId.value = (o?.errorId && decodeURIComponent(String(o.errorId))) || (aiSource.value ? '' : lastErrorId())
  if (o?.type && types.value.some((t) => t.key === o.type)) type.value = String(o.type)
})

const contentLen = computed(() => charLen(content.value.trim()))

async function addImages() {
  if (uploading.value) return
  const paths = await chooseImages(FEEDBACK_IMAGES_MAX - images.value.length)
  if (!paths.length) return
  uploading.value = true
  try {
    for (const p of paths) {
      if (images.value.length >= FEEDBACK_IMAGES_MAX) break
      const r = await uploadImage(p, 'feedback')
      images.value.push({ local: p, url: r.url })
    }
  } catch {
    uni.showToast({ title: '图片上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

function clientInfo(): string {
  const label = types.value.find((t) => t.key === type.value)?.label || ''
  const head = [aiSourceTag(aiSource.value, aiRef.value), label && `类型:${label}`,
    attach.value && errorId.value ? `报错:${errorId.value}` : ''].filter(Boolean)
  if (!attach.value) return head.join(' / ')
  try {
    const s = uni.getSystemInfoSync()
    return [...head, `v${APP_VERSION}`, s.uniPlatform || s.platform, s.model, s.system, s.version ? `wx${s.version}` : '']
      .filter(Boolean)
      .join(' / ')
      .slice(0, 200)
  } catch {
    return [...head, `v${APP_VERSION}`].join(' / ')
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
    await submitFeedback({ ...payload, clientInfo: clientInfo(), type: type.value, errorId: attach.value ? errorId.value : '' })
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
.types {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(60px, 1fr));
  gap: var(--sp-8);
}
.type {
  height: 72px;
  border-radius: var(--r-md);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.type.on {
  border: 2px solid var(--primary-deep);
  background: var(--highlight);
}
.type-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.type.on .type-txt {
  font-weight: 700;
}
.attach {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-8);
  margin-top: var(--sp-12);
}
.attach-box {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1.5px solid var(--body);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.attach-box.on {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.attach-txt {
  flex: 1;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
</style>
