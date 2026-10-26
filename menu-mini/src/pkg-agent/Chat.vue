<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 咕嘟对话（prototype AgentChat）：SSE 流式；工具调用链、内嵌卡片、确认卡、撤销、👍👎。 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: inset + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <image class="gudu" src="/static/brand/logo.png" mode="aspectFit" />
      <view class="top-main">
        <text class="top-title">{{ title }}</text>
        <text class="top-sub"><text class="online">●</text> 咕嘟 · 已连接我家的厨房</text>
      </view>
      <view class="new" aria-label="新对话" data-testid="new-session" @click="newSession"><ui-icon name="plus" :size="18" /></view>
    </view>

    <scroll-view scroll-y class="body" :scroll-into-view="anchor" scroll-with-animation>
      <view class="pad">
        <text class="day">今天 {{ hm }}</text>
        <view class="ai-intro" data-testid="ai-intro">
          <ui-ai-badge />
          <text class="ai-intro-txt">{{ AI_ASSISTANT_INTRO }}</text>
        </view>
        <view v-if="!chat.messages.length" class="empty">
          <text class="empty-txt">问问咕嘟：冰箱里的能做什么、帮我把菜加进食集、做完了帮我扣余量。</text>
        </view>
        <view v-for="(m, i) in chat.messages" :id="'m' + i" :key="m.messageId + i" class="msg">
          <view v-if="m.role === 'user'" class="user">
            <text class="user-txt">{{ m.text }}</text>
          </view>
          <assistant-message
            v-else
            :msg="m"
            :family="family"
            :busy="chat.busyAction.value"
            @confirm="chat.confirm"
            @cancel="chat.cancel"
            @undo="chat.undo"
            @expire="chat.expire"
            @revise="chat.revise"
            @rate="chat.rate"
          />
        </view>
        <view id="bottom" style="height: 12px" />
      </view>
    </scroll-view>

    <view class="composer">
      <scroll-view v-if="!chat.sending.value && followUps.length" scroll-x class="chips" :show-scrollbar="false">
        <view class="chip-row">
          <view v-for="f in followUps" :key="f" class="chip" @click="ask(f)"><text class="chip-txt">{{ f }}</text></view>
        </view>
      </scroll-view>
      <view class="bar">
        <view class="ipt-box">
          <input
            v-model="text"
            class="ipt"
            placeholder="继续问咕嘟…"
            placeholder-class="ph"
            confirm-type="send"
            data-testid="chat-input"
            :disabled="chat.sending.value"
            @confirm="onSend"
          />
          <view class="ipt-btn" @click="goScan"><ui-icon name="camera" :size="20" /></view>
        </view>
        <view v-if="chat.sending.value" class="send stop" data-testid="chat-stop" @click="chat.stop()"><ui-icon name="pause" tone="white" :size="18" /></view>
        <view v-else-if="text.trim()" class="send" data-testid="chat-send" @click="onSend"><ui-icon name="send" tone="white" :size="18" /></view>
        <view v-else-if="FEATURES.voice" class="send" @click="goVoice"><ui-icon name="mic" tone="white" :size="18" /></view>
        <!-- 小程序不开放语音（A.9）：空输入时发送键置灰 -->
        <view v-else class="send off" data-testid="chat-send"><ui-icon name="send" tone="white" :size="18" /></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { listMembers } from '@/api/member'
import { ensureSession } from '@/api/agent'
import { useAgentChat } from '@/composables/useAgentChat'
import { FEATURES } from '@/config/features'
import { navInset } from '@/utils/token'
import { PAGES } from '@/utils/nav'
import AssistantMessage from './parts/AssistantMessage.vue'
import { AI_ASSISTANT_INTRO } from '@/utils/aiLabel'

const inset = navInset()
const chat = useAgentChat()
const text = ref('')
const anchor = ref('')
const family = ref<{ id?: number; name: string; avatarUrl?: string | null; tags?: string[] | null }[]>([])
const now = new Date()
const hm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`

const title = computed(() => {
  const first = chat.messages.find((m) => m.role === 'user')
  return first ? first.text.slice(0, 12) : '和咕嘟聊聊'
})

/** 追问建议：看最后一条回复里有什么。 */
const followUps = computed(() => {
  const last = [...chat.messages].reverse().find((m) => m.role === 'assistant')
  if (!last) return []
  if (last.cards.some((c) => c.type === 'recipes')) return ['就这几道，加进今晚的食集', '换一道', '朵朵能吃哪道？']
  if (last.actions.some((a) => a.status === 'DONE')) return ['缺的加进采购清单', '提醒我几点开始做']
  return ['冰箱里的能做什么', '这周给朵朵安排辅食']
})

onLoad(async (q: any) => {
  listMembers()
    .then((ms) => (family.value = ms.map((m) => ({ id: m.id, name: m.name, avatarUrl: (m as any).avatarUrl ?? null, tags: m.audienceTags }))))
    .catch(() => {})
  try {
    if (q?.sessionId) await chat.init(q.sessionId, true)
    else await chat.init(undefined, false)
  } catch {
    // 会话创建失败：发送时会再试
  }
  const first = q?.q ? decodeURIComponent(q.q) : ''
  if (first) ask(first)
})
onUnload(() => chat.stop())

watch(
  () => [chat.messages.length, chat.messages[chat.messages.length - 1]?.text.length, chat.messages[chat.messages.length - 1]?.cards.length],
  () => nextTick(() => (anchor.value = anchor.value === 'bottom' ? '' : 'bottom')).then(() => nextTick(() => (anchor.value = 'bottom'))),
)

function ask(q: string) {
  chat.send(q).catch(() => {})
}

function onSend() {
  const q = text.value.trim()
  if (!q) return
  text.value = ''
  ask(q)
}

async function newSession() {
  chat.stop()
  chat.messages.splice(0)
  const id = await ensureSession(true).catch(() => '')
  if (id) await chat.init(id)
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/agent/Index' })
}
function goScan() {
  uni.navigateTo({ url: PAGES.scan })
}
function goVoice() {
  uni.navigateTo({ url: PAGES.agentVoice })
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
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}
.back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  padding: 0 6px;
}
.gudu {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  background: var(--card);
}
.top-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.top-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.top-sub {
  font-size: var(--fs-xs);
  color: var(--body);
}
.online {
  color: var(--success);
}
.new {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: var(--space-md) var(--space-lg);
}
.day {
  display: block;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--body);
  margin-bottom: var(--space-md);
}
.empty {
  padding: var(--space-xl) var(--space-lg);
}
.empty-txt {
  font-size: var(--fs-md);
  color: var(--body);
  line-height: 1.7;
}
.msg {
  margin-bottom: var(--space-lg);
}
.user {
  margin-left: auto;
  max-width: 78%;
  width: fit-content;
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--primary-deep);
}
.user-txt {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--on-primary);
}
.composer {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid var(--border);
  padding: 8px 0 calc(10px + env(safe-area-inset-bottom));
}
.chips {
  white-space: nowrap;
  margin-bottom: 8px;
}
.chip-row {
  display: flex;
  gap: var(--space-sm);
  padding: 0 var(--space-lg);
}
.chip {
  flex-shrink: 0;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--primary-soft);
  background: var(--highlight);
}
.chip-txt {
  font-size: var(--fs-sm);
  color: var(--accent);
  font-weight: 600;
}
.bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 var(--space-lg);
}
.ipt-box {
  flex: 1;
  height: 44px;
  border-radius: var(--radius-pill);
  background: var(--bg);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 6px 0 16px;
}
.ipt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--body);
}
.ipt-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.send {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.send.stop {
  background: var(--title);
}
.send.off {
  opacity: 0.4;
}
/* AI 身份说明（A.4）：首屏常驻 */
.ai-intro {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin: 0 auto var(--sp-12);
  padding: var(--sp-6) var(--sp-10);
  border-radius: var(--r-md);
  background: var(--card);
  border: 1px solid var(--border);
}
.ai-intro-txt {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--caption);
  line-height: 1.5;
}
</style>
