<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 咕嘟 · 语音（prototype AgentVoice）：按住说话 → 上传语音 → 咕嘟识别并给出「做完回写」确认卡。
       小程序用 RecorderManager；H5 无录音权限时降级为打字。 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: inset + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <text class="top-title">咕嘟 · 语音</text>
      <text class="top-hint">说「做完了」我来回写余量</text>
    </view>

    <scroll-view scroll-y class="body" :scroll-into-view="anchor">
      <view class="pad">
        <!-- 聆听面板 -->
        <view class="listen" :class="{ on: recording }">
          <view class="listen-head">
            <text class="state-dot" />
            <text class="state">{{ stateText }}</text>
            <view class="flex1" />
            <text class="tip">{{ canRecord ? '松开发送 · 上滑取消' : '这里用打字代替说话' }}</text>
          </view>
          <text class="transcript">{{ transcript || '「麻婆豆腐做完了，我们三个人吃的」' }}<text v-if="recording" class="cursor">▍</text></text>
          <view class="wave-row">
            <view class="wave">
              <view v-for="i in 22" :key="i" class="bar" :class="{ live: recording }" :style="{ height: barH(i) + 'px', animationDelay: (i % 7) * 0.08 + 's' }" />
            </view>
            <view
              v-if="canRecord"
              class="mic"
              :class="{ on: recording }"
              data-testid="voice-hold"
              @touchstart.prevent="startRec"
              @touchend.prevent="stopRec(false)"
              @touchmove="onMove"
            >
              <ui-icon name="mic" tone="white" :size="26" />
            </view>
          </view>
          <view v-if="!canRecord" class="type-row">
            <input v-model="typed" class="type-ipt" placeholder="麻婆豆腐做完了，我们三个人吃的" placeholder-class="type-ph" confirm-type="send" data-testid="voice-text" @confirm="sendTyped" />
            <view class="type-send" data-testid="voice-send" @click="sendTyped"><ui-icon name="send" tone="white" :size="18" /></view>
          </view>
        </view>

        <!-- 咕嘟理解步骤 -->
        <view v-if="steps.length" class="steps">
          <view v-for="s in steps" :key="s.name" class="step" :class="{ run: s.running }">
            <ui-icon v-if="!s.running" name="check" tone="primary" :size="12" />
            <text class="step-txt">{{ s.label }}</text>
          </view>
        </view>

        <view v-for="(m, i) in replies" :id="'r' + i" :key="i" class="reply">
          <assistant-message
            :msg="m"
            :family="family"
            :busy="chat.busyAction.value"
            @confirm="chat.confirm"
            @cancel="chat.cancel"
            @undo="chat.undo"
            @expire="chat.expire"
            @revise="send"
            @rate="chat.rate"
          />
        </view>
        <view id="vbottom" style="height: 24px" />
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { uploadAudio } from '@/api/agent'
import { listMembers } from '@/api/member'
import { useAgentChat } from '@/composables/useAgentChat'
import { navInset } from '@/utils/token'
import AssistantMessage from './parts/AssistantMessage.vue'

const inset = navInset()
const chat = useAgentChat({ context: { page: 'voice' } })
const family = ref<{ name: string; avatarUrl?: string | null; tags?: string[] | null }[]>([])
const recording = ref(false)
const uploading = ref(false)
const transcript = ref('')
const typed = ref('')
const anchor = ref('')
let cancelled = false
let startY = 0
let recorder: any = null

// 小程序有录音；H5 降级打字
let canRecord = false
// #ifdef MP-WEIXIN
canRecord = true
// #endif

const replies = computed(() => chat.messages.filter((m) => m.role === 'assistant'))
const steps = computed(() => replies.value[replies.value.length - 1]?.tools || [])
const stateText = computed(() =>
  recording.value ? '正在听 · 松开发送' : uploading.value ? '上传中…' : chat.sending.value ? '咕嘟在想…' : canRecord ? '按住麦克风说话' : '说给咕嘟听',
)

function barH(i: number) {
  return 8 + ((i * 37) % 23)
}

onLoad(() => {
  listMembers()
    .then((ms) => (family.value = ms.map((m) => ({ name: m.name, avatarUrl: (m as any).avatarUrl ?? null, tags: m.audienceTags }))))
    .catch(() => {})
  chat.init().catch(() => {})
  if (canRecord) {
    recorder = uni.getRecorderManager()
    recorder.onStop(async (res: { tempFilePath: string; duration: number }) => {
      recording.value = false
      if (cancelled || res.duration < 600) {
        if (!cancelled) uni.showToast({ title: '说话时间太短', icon: 'none' })
        return
      }
      uploading.value = true
      try {
        const id = await uploadAudio(res.tempFilePath)
        transcript.value = '（语音已发送）'
        await chat.send('', { audioFileId: id })
      } catch {
        uni.showToast({ title: '语音没发出去，试试打字', icon: 'none' })
      } finally {
        uploading.value = false
        scrollDown()
      }
    })
    recorder.onError(() => {
      recording.value = false
      uni.showToast({ title: '没拿到麦克风权限', icon: 'none' })
    })
  }
})
onUnload(() => {
  chat.stop()
  if (recording.value) recorder?.stop()
})

function startRec(e: any) {
  if (!recorder || chat.sending.value) return
  cancelled = false
  startY = e.touches?.[0]?.clientY ?? 0
  transcript.value = ''
  recording.value = true
  recorder.start({ format: 'mp3', duration: 60000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000 })
}
function onMove(e: any) {
  const y = e.touches?.[0]?.clientY ?? 0
  cancelled = startY - y > 60
  if (cancelled) transcript.value = '松开取消'
}
function stopRec(cancel: boolean) {
  if (!recording.value) return
  if (cancel) cancelled = true
  recorder?.stop()
}

async function send(text: string) {
  transcript.value = text
  await chat.send(text)
  scrollDown()
}
function sendTyped() {
  const t = (typed.value || '麻婆豆腐做完了，我们三个人吃的').trim()
  typed.value = ''
  send(t)
}
function scrollDown() {
  nextTick(() => {
    anchor.value = ''
    nextTick(() => (anchor.value = 'vbottom'))
  })
}
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/agent/Index' })
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
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 var(--space-lg) var(--space-sm) 10px;
}
.back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  padding: 0 6px;
}
.top-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.top-hint {
  margin-left: auto;
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--success-deep);
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  background: var(--success-soft);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: var(--space-sm) var(--space-lg);
}
.listen {
  border-radius: var(--radius-xl);
  padding: 16px;
  background: var(--title);
  box-shadow: 0 10px 28px rgba(var(--sh), 0.28);
}
.listen-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.state-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--error);
}
.state {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--highlight);
}
.flex1 {
  flex: 1;
}
.tip {
  font-size: var(--fs-xs);
  color: var(--primary-soft);
}
.transcript {
  display: block;
  margin: 12px 0 14px;
  font-size: var(--fs-subtitle);
  font-weight: 700;
  line-height: 1.5;
  color: var(--on-primary);
}
.cursor {
  color: var(--primary);
}
.wave-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.wave {
  flex: 1;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.bar {
  width: 4px;
  border-radius: 2px;
  background: var(--primary);
  opacity: 0.55;
}
.bar.live {
  opacity: 1;
  animation: wv 0.8s ease-in-out infinite alternate;
}
@keyframes wv {
  to {
    transform: scaleY(0.35);
  }
}
.mic {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 0 8px rgba(232, 145, 80, 0.2);
}
.mic.on {
  transform: scale(1.08);
  box-shadow: 0 0 0 14px rgba(232, 145, 80, 0.25);
}
.type-row {
  margin-top: 12px;
  display: flex;
  gap: var(--space-sm);
}
.type-ipt {
  flex: 1;
  height: 42px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.12);
  color: var(--on-primary);
  font-size: var(--fs-md);
}
.type-ph {
  color: rgba(255, 255, 255, 0.6);
}
.type-send {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: var(--space-md) 0 var(--space-sm);
}
.step {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
}
.step.run {
  border-color: var(--primary-soft);
}
.step-txt {
  font-size: var(--fs-xs);
  color: var(--title);
}
.reply {
  margin-top: var(--space-md);
}
</style>
