<template>
  <!-- 咕嘟回复（prototype AgentChat）：工具调用链（可折叠）→ 卡片 → 文字（流式光标）→ 待确认动作 → 👍👎 + 报错编号。 -->
  <view class="am">
    <image class="gudu" src="/static/brand/logo.png" mode="aspectFit" />
    <view class="col">
      <!-- 工具调用链 -->
      <view v-if="msg.tools.length" class="tools" data-testid="tool-chain">
        <view class="tools-head" @click="open = !open">
          <ui-icon name="sparkle" tone="primary" :size="14" />
          <text class="tools-title">{{ toolsTitle }}</text>
          <text class="tools-toggle">{{ open ? '收起' : '展开' }}</text>
        </view>
        <view v-if="open" class="tools-list">
          <view v-for="(t, i) in msg.tools" :key="i" class="tool">
            <view class="dot" :class="{ ok: !t.running && t.ok, bad: !t.running && !t.ok, run: t.running }">
              <ui-icon v-if="!t.running && t.ok" name="check" tone="white" :size="10" />
            </view>
            <view class="tool-main">
              <text class="tool-label">{{ t.label }}{{ t.summary ? ' · ' + t.summary : t.running ? '…' : '' }}</text>
              <text class="tool-name">{{ t.name }}</text>
            </view>
            <text v-if="!t.running" class="tool-ms">{{ (t.ms / 1000).toFixed(1) }}s</text>
          </view>
        </view>
      </view>

      <!-- 卡片 -->
      <view v-for="(c, ci) in msg.cards" :key="ci" class="card-wrap">
        <scroll-view v-if="c.type === 'recipes'" scroll-x class="hs" :show-scrollbar="false">
          <view class="hrow">
            <view v-for="d in c.items" :key="d.id" class="rcard" :data-testid="'rcard-' + d.id" @click="goDish(d.id)">
              <ui-dish-image :src="d.coverUrl" :name="d.name" ratio="4:3" :radius="0" />
              <view class="rcard-body">
                <text class="rcard-name">{{ d.name }}</text>
                <text class="cov" :class="coverageTone(d.coverage)">{{ coverageLabel(d.coverage) || '—' }}</text>
                <text class="rcard-meta">{{ [d.minutes ? d.minutes + ' 分钟' : '', d.tags[0] || ''].filter(Boolean).join(' · ') }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
        <view v-else-if="c.type === 'menu'" class="mcard" @click="goMenu(c.menuId)">
          <view class="mthumbs">
            <ui-dish-image v-for="(u, i) in c.coverUrls.slice(0, 3)" :key="i" :src="u" :width="40" :height="40" :radius="10" />
          </view>
          <view class="mmain">
            <text class="mname">{{ c.name }}</text>
            <text class="msub">{{ c.date }} · {{ c.dishCount }} 道菜</text>
          </view>
          <ui-icon name="chevron" :size="16" />
        </view>
        <view v-else-if="c.type === 'pantry'" class="pcard">
          <view v-for="it in c.items" :key="it.name" class="pit">
            <ui-ing-icon :icon-key="it.iconKey" :name="it.name" :size="32" :out="it.status === 'OUT'" />
            <text class="pit-name">{{ it.name }}</text>
            <ui-expiry-badge v-if="it.daysLeft !== null" :days-left="it.daysLeft" />
          </view>
        </view>
        <view v-else-if="c.type === 'plan'" class="mcard" @click="goPlan(c.draftId)">
          <view class="plan-ic"><ui-icon name="calendar" tone="white" :size="18" /></view>
          <view class="mmain">
            <text class="mname">一周餐程草稿</text>
            <text class="msub">{{ c.range }} · {{ c.days }} 天</text>
          </view>
          <text class="go">去看看 ›</text>
        </view>
      </view>

      <!-- 文字 -->
      <view v-if="msg.text || msg.streaming" class="bubble">
        <text class="txt">{{ msg.text }}</text>
        <text v-if="msg.streaming" class="cursor">▍</text>
      </view>
      <view v-if="msg.error" class="err">
        <text class="err-txt">{{ msg.error.message }}</text>
        <text v-if="msg.error.errorId" class="err-id">报错编号 {{ msg.error.errorId }}</text>
      </view>
      <text v-if="msg.degraded" class="degraded">咕嘟有点忙，先给你一个简单版</text>

      <!-- 待确认动作 -->
      <template v-for="a in msg.actions" :key="a.actionId">
        <cooked-card
          v-if="a.tool === 'mark_cooked'"
          :action="a"
          :family="family"
          :busy="busy === a.actionId"
          @confirm="$emit('confirm', $event)"
          @undo="$emit('undo', $event)"
          @revise="(a, args) => $emit('revise', a, args)"
        />
        <action-card
          v-else
          :action="a"
          :busy="busy === a.actionId"
          @confirm="$emit('confirm', $event)"
          @cancel="$emit('cancel', $event)"
          @undo="$emit('undo', $event)"
          @expire="$emit('expire', $event)"
        />
      </template>

      <!-- 评价 + 报错编号 -->
      <view v-if="!msg.streaming && msg.messageId" class="foot">
        <view class="rate" :class="{ on: msg.rating === 1 }" data-testid="rate-up" @click="$emit('rate', msg, 1)"><ui-icon name="thumbup" :size="14" /></view>
        <view class="rate" :class="{ on: msg.rating === -1 }" data-testid="rate-down" @click="$emit('rate', msg, -1)"><ui-icon name="thumbdown" :size="14" /></view>
        <text v-if="msg.traceId" class="trace">报错编号 {{ formatErrorId(msg.traceId) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { toolsSeconds, type AgentMessage, type PendingAction } from '@/api/agent'
import { coverageLabel, coverageTone } from '@/api/contract'
import { formatErrorId } from '@/utils/request'
import { PAGES, withQuery } from '@/utils/nav'
import ActionCard from './ActionCard.vue'
import CookedCard from './CookedCard.vue'

const props = defineProps<{
  msg: AgentMessage
  family: { id?: number; name: string; avatarUrl?: string | null; tags?: string[] | null }[]
  busy?: string
}>()
defineEmits<{
  (e: 'confirm', a: PendingAction): void
  (e: 'cancel', a: PendingAction): void
  (e: 'undo', a: PendingAction): void
  (e: 'expire', a: PendingAction): void
  (e: 'revise', a: PendingAction, args: Record<string, unknown>): void
  (e: 'rate', m: AgentMessage, r: 1 | -1): void
}>()

// 进行中展开；完成后、工具多于 2 个时默认折叠
const userToggled = ref<boolean | null>(null)
const open = computed({
  get: () => (userToggled.value ?? (props.msg.streaming || props.msg.tools.length <= 3)),
  set: (v) => (userToggled.value = v),
})
const toolsTitle = computed(() => {
  const running = props.msg.tools.some((t) => t.running)
  if (running) return `正在${props.msg.tools[props.msg.tools.length - 1].label}…`
  return `查了 ${props.msg.tools.length} 项 · 用时 ${toolsSeconds(props.msg.tools)} 秒`
})

function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}
function goMenu(id: number) {
  uni.navigateTo({ url: `/pages/menu/Detail?id=${id}` })
}
function goPlan(id: number) {
  uni.navigateTo({ url: withQuery(PAGES.weekPlan, { draftId: id }) })
}
</script>

<style scoped>
.am {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-start;
}
.gudu {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-pill);
  background: var(--card);
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(var(--sh), 0.12);
}
.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.tools {
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--border);
  padding: 8px 10px;
}
.tools-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tools-title {
  flex: 1;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.tools-toggle {
  font-size: var(--fs-xs);
  color: var(--accent);
}
.tools-list {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tool {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}
.dot {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border-radius: var(--radius-pill);
  background: var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.dot.ok {
  background: var(--success);
}
.dot.bad {
  background: var(--error);
}
.dot.run {
  border: 2px solid var(--primary);
  background: transparent;
  animation: pulse 1s ease-in-out infinite alternate;
}
@keyframes pulse {
  to {
    opacity: 0.3;
  }
}
.tool-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.tool-label {
  font-size: var(--fs-sm);
  color: var(--title);
}
.tool-name {
  align-self: flex-start;
  margin-top: 2px;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--secondary);
  font-family: Menlo, monospace;
  font-size: var(--fs-tiny);
  color: var(--body);
}
.tool-ms {
  font-size: var(--fs-xs);
  color: var(--body);
}
.hs {
  white-space: nowrap;
}
.hrow {
  display: flex;
  gap: var(--space-sm);
  padding-bottom: 4px;
}
.rcard {
  width: 150px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
}
.rcard-body {
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  white-space: normal;
}
.rcard-name {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.cov {
  align-self: flex-start;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  font-weight: 700;
  background: var(--secondary);
  color: var(--body);
}
.cov.full {
  background: var(--success-soft);
  color: var(--success-deep);
}
.cov.partial {
  background: var(--warning-soft);
  color: var(--warning-deep);
}
.rcard-meta {
  font-size: var(--fs-xs);
  color: var(--body);
}
.mcard {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--border);
}
.mthumbs {
  display: flex;
}
.mthumbs > * + * {
  margin-left: -10px;
}
.plan-ic {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mmain {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.mname {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.msub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.go {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--accent);
}
.pcard {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--border);
}
.pit {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.pit-name {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.bubble {
  padding: 10px 14px;
  border-radius: 4px 16px 16px 16px;
  background: var(--card);
  border: 1px solid var(--border);
}
.txt {
  font-size: var(--fs-md);
  line-height: 1.65;
  color: var(--title);
  white-space: pre-wrap;
}
.cursor {
  color: var(--primary);
  animation: blink 0.9s steps(2) infinite;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.err {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--error-soft);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.err-txt {
  font-size: var(--fs-md);
  color: var(--error-deep);
}
.err-id,
.trace {
  font-size: var(--fs-xs);
  color: var(--body);
}
.degraded {
  font-size: var(--fs-xs);
  color: var(--warning-deep);
}
.foot {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.rate {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.55;
}
.rate.on {
  opacity: 1;
  background: var(--secondary);
}
.trace {
  margin-left: auto;
}
</style>
