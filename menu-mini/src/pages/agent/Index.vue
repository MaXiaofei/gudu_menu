<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 咕嘟首屏（prototype Agent）：问候 → 咕嘟开场 → 今晚首推 → 快到期提醒 → 快捷提问 → 最近会话；底部输入栏。
       数据：/agent/suggestions（新后端）；不可用时回退 /recommend/home，页面不空白。 -->
  <view class="page">
    <ui-tab-header :title="title">
      <template #eyebrow>
        <text class="eyebrow">{{ eyebrow }}</text>
      </template>
      <ui-avatar-stack v-if="faces.length" :items="faces" :size="22" :max="4" />
      <view class="bell" aria-label="消息" data-testid="bell" @click="goNotice">
        <ui-icon name="bell" :size="18" />
        <text v-if="unread" class="bell-dot" />
      </view>
      <ui-me-avatar />
    </ui-tab-header>

    <ui-offline-bar />
    <scroll-view scroll-y class="body">
      <view class="pad">
        <!-- 咕嘟开场 -->
        <view class="hello">
          <image class="gudu" src="/static/brand/logo.png" mode="aspectFit" />
          <view class="hello-bubble"><text class="hello-txt">{{ greeting }}</text></view>
        </view>
        <!-- AI 身份说明（A.4） -->
        <view class="ai-intro" data-testid="ai-intro">
          <ui-ai-badge />
          <text class="ai-intro-txt">{{ AI_ASSISTANT_INTRO }}</text>
        </view>

        <ui-state v-if="loading" mode="loading" skeleton="hero" />
        <template v-else>
          <!-- 今晚首推 -->
          <view v-if="tonight" class="sec">
            <view class="sec-head">
              <text class="sec-title">今晚首推</text>
              <text class="sec-more" data-testid="go-smart" @click="go(PAGES.smart)">全部推荐 ›</text>
            </view>
            <view class="hero" data-testid="agent-hero" @click="goDish(tonight.id)">
              <ui-dish-image :src="tonight.coverUrl" :name="tonight.name" ratio="16:9" :radius="20" original>
                <!-- 首推菜没有封面：主色渐变 + 食材插画拼贴，不用巨大首字 -->
                <view v-if="!tonight.coverUrl" class="hero-nocover" data-testid="hero-nocover">
                  <view v-for="k in heroIngKeys" :key="k" class="nc-ing"><image class="nc-img" :src="ingIconSrc(k)" mode="aspectFit" /></view>
                </view>
                <view class="hero-shade" />
                <view class="hero-tags">
                  <text v-if="heroBadge" class="tag-red">{{ heroBadge }}</text>
                  <view class="flex1" />
                  <text class="tag-white">咕嘟推荐</text>
                </view>
                <view class="hero-info">
                  <text class="hero-name">{{ tonight.name }}</text>
                  <text class="hero-why">{{ heroWhy }}</text>
                  <view class="hero-acts">
                    <view class="hero-btn" @click.stop="goDish(tonight.id)"><text class="hero-btn-txt">现在就做</text></view>
                    <view class="hero-btn ghost" data-testid="hero-swap" @click.stop="ask('换一道今晚的菜')">
                      <text class="hero-btn-txt">换一道</text>
                    </view>
                  </view>
                </view>
              </ui-dish-image>
            </view>
          </view>

          <!-- 快到期提醒 -->
          <view v-if="expiring.length" class="exp-bar" data-testid="agent-expiring">
            <view class="exp-icons">
              <ui-ing-icon v-for="e in expiring.slice(0, 2)" :key="e.name" :icon-key="e.iconKey" :name="e.name" :size="28" />
            </view>
            <view class="exp-main">
              <text class="exp-title">还有 {{ expiring.length }} 样快到期</text>
              <text class="exp-sub">{{ expiringText }}</text>
            </view>
            <view class="exp-btn" @click="ask(`${expiring.map((e) => e.name).slice(0, 3).join('、')}快到期了，今晚怎么做？`)">
              <text class="exp-btn-txt">问咕嘟怎么做</text>
            </view>
          </view>
        </template>

        <!-- 快捷提问 -->
        <view class="sec">
          <view class="sec-head">
            <text class="sec-title">试试这样问</text>
            <text class="sec-hint">说话或打字都行</text>
          </view>
          <view class="prompts">
            <view v-for="(p, i) in prompts" :key="p" class="prompt" :data-testid="'prompt-' + i" @click="onPrompt(p)">
              <view class="prompt-ic" :class="'t' + (i % 4)"><ui-icon :name="promptIcon(p)" :size="16" /></view>
              <text class="prompt-txt">{{ p }}</text>
            </view>
          </view>
        </view>

        <!-- 最近会话 -->
        <view v-if="sessions.length" class="sec">
          <text class="sec-title">最近聊过</text>
          <view v-for="s in sessions" :key="s.sessionId" class="sess" @click="openSession(s.sessionId)">
            <ui-dish-image v-if="s.coverUrl" :src="s.coverUrl" :name="s.title" :width="44" :height="44" :radius="12" />
            <view v-else class="sess-ic"><ui-icon name="message" :size="18" /></view>
            <view class="sess-main">
              <text class="sess-title">{{ s.title }}</text>
              <text class="sess-sub">{{ s.lastMessage }}</text>
            </view>
            <text class="sess-time">{{ relativeDate(s.updatedAt) }}</text>
          </view>
        </view>
        <view style="height: 88px" />
      </view>
    </scroll-view>

    <!-- 输入栏（Tab 栏上方） -->
    <view class="composer">
      <view class="ipt-box">
        <input
          v-model="text"
          class="ipt"
          placeholder="问咕嘟：今晚吃什么、帮我加采购…"
          placeholder-class="ph"
          confirm-type="send"
          data-testid="agent-input"
          @confirm="send"
        />
        <view class="ipt-btn" aria-label="拍照识别" @click="go(PAGES.scan)"><ui-icon name="camera" :size="20" /></view>
      </view>
      <view v-if="text.trim()" class="mic" aria-label="发送" data-testid="agent-send" @click="send"><ui-icon name="send" tone="white" :size="20" /></view>
      <view v-else class="mic" aria-label="语音" data-testid="agent-voice" @click="go(PAGES.agentVoice)"><ui-icon name="mic" tone="white" :size="20" /></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { AI_ASSISTANT_INTRO } from '@/utils/aiLabel'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { agentSuggestions, listSessions, setCurrentSession, type AgentSessionItem } from '@/api/agent'
import { coverageLabel, type CDishCard } from '@/api/contract'
import { recommendHome } from '@/api/recommend'
import { listMembers } from '@/api/member'
import { unreadCount } from '@/api/notification'
import { relativeDate } from '@/utils/datetime'
import { PAGES, withQuery } from '@/utils/nav'
import { ingIconSrc, matchIngKey, resolveIngKey } from '@/utils/ingIcon'

const DEFAULT_PROMPTS = ['冰箱里的能做什么', '给朵朵安排这周辅食', '周六爸妈来吃饭怎么安排', '拍冰箱入库']

const loading = ref(true)
const greeting = ref('')
const tonight = ref<CDishCard | null>(null)
/** 无封面首推的插画：菜名能识别的食材 + 快到期食材，最多 3 个 */
const heroIngKeys = computed(() => {
  const keys: string[] = []
  const add = (k: string | null) => {
    if (k && !keys.includes(k) && keys.length < 3) keys.push(k)
  }
  add(matchIngKey(tonight.value?.name))
  for (const e of expiring.value) add(resolveIngKey({ iconKey: e.iconKey, name: e.name }))
  if (!keys.length) add('egg')
  return keys
})
const expiring = ref<{ name: string; iconKey: string | null; daysLeft: number }[]>([])
const prompts = ref<string[]>(DEFAULT_PROMPTS)
const sessions = ref<AgentSessionItem[]>([])
const faces = ref<{ name: string; avatarUrl?: string | null; tags?: string[] | null }[]>([])
const unread = ref(0)
const text = ref('')

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const now = ref(new Date())
const eyebrow = computed(() => {
  const d = now.value
  const h = d.getHours()
  const slot = h < 10 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  const name = uni.getStorageSync('nickname') || ''
  return `${slot}${name ? '，' + name : ''} · ${WEEK[d.getDay()]} ${String(h).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})
const title = computed(() => {
  const h = now.value.getHours()
  const meal = h < 10 ? '早饭' : h < 14 ? '午饭' : '今晚'
  return faces.value.length ? `${meal} ${faces.value.length} 口人` : `${meal}吃点什么`
})

const heroBadge = computed(() => {
  const e = expiring.value[0]
  if (!e) return ''
  return `${e.name}${e.daysLeft <= 0 ? '今天到期' : e.daysLeft === 1 ? '明天到期' : `${e.daysLeft} 天到期`}`
})
const heroWhy = computed(() => {
  const d = tonight.value
  if (!d) return ''
  return [d.minutes ? `${d.minutes} 分钟` : '', d.tags?.[0] || '', coverageLabel(d.coverage)].filter(Boolean).join(' · ')
})
const expiringText = computed(() =>
  expiring.value
    .slice(0, 3)
    .map((e) => `${e.name}剩 ${Math.max(0, e.daysLeft)} 天`)
    .join('、'),
)

onShow(() => {
  now.value = new Date()
  load()
  listMembers()
    .then((ms) => (faces.value = ms.map((m) => ({ name: m.name, avatarUrl: (m as any).avatarUrl ?? null, tags: m.audienceTags }))))
    .catch(() => {})
  unreadCount().then((n) => (unread.value = n))
  listSessions(null, 3)
    .then((p) => (sessions.value = p.items))
    .catch(() => (sessions.value = []))
})

async function load() {
  if (!greeting.value) loading.value = true
  try {
    const s = await agentSuggestions()
    greeting.value = s.greeting
    tonight.value = s.tonight
    expiring.value = s.expiring
    if (s.prompts.length) prompts.value = s.prompts
    if (!s.tonight) await fallback()
  } catch {
    await fallback()
  } finally {
    if (!greeting.value) greeting.value = expiring.value.length
      ? `我看了下家里，${expiring.value[0].name}快到期了，今晚先吃它？`
      : '想吃什么、家里还剩什么，直接问我就行。'
    loading.value = false
  }
}

/** 旧后端兜底：智荐首页聚合 → 首推 + 临期。 */
async function fallback() {
  try {
    const h = await recommendHome()
    const d = h.expiringPicks[0] || h.frequent[0]
    if (d && !tonight.value) {
      tonight.value = {
        id: d.dishId,
        name: d.name,
        coverUrl: d.coverUrl || d.coverImage || null,
        minutes: d.cookMinutes || null,
        difficulty: null,
        coverage: {
          have: d.coverage?.enough ?? 0,
          total: d.coverage?.total ?? 0,
          missing: (d.coverage?.missing || []).map((m) => m.name),
        },
        tags: [],
      }
    }
    if (!expiring.value.length) {
      expiring.value = h.expiring.map((e) => ({ name: e.ingredientName, iconKey: e.iconKey ?? null, daysLeft: e.daysLeft }))
    }
  } catch {
    // 两个都失败：只展示快捷提问与输入框
  }
}

function promptIcon(p: string): string {
  if (p.includes('拍')) return 'camera'
  if (p.includes('冰箱')) return 'fridge'
  if (p.includes('辅食') || p.includes('周')) return 'calendar'
  return 'users'
}

function onPrompt(p: string) {
  if (p.includes('拍') && p.includes('入库')) return go(PAGES.scan)
  ask(p)
}

function ask(q: string) {
  uni.navigateTo({ url: withQuery(PAGES.agentChat, { q }) })
}

function send() {
  const q = text.value.trim()
  if (!q) return
  text.value = ''
  ask(q)
}

function openSession(id: string) {
  setCurrentSession(id)
  uni.navigateTo({ url: withQuery(PAGES.agentChat, { sessionId: id }) })
}

function go(url: string) {
  uni.navigateTo({ url })
}
function goNotice() {
  uni.navigateTo({ url: '/pages/notice/Index' })
}
function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}
</script>

<style scoped>
.page {
  /* H5 的 Tab 页 100vh 含底部 tabBar，减去 --window-bottom；小程序该变量为 0 */
  height: calc(100vh - var(--window-bottom, 0px));
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.eyebrow {
  font-size: var(--fs-sm);
  color: var(--body);
  font-weight: 600;
}
.bell {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.bell-dot {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 7px;
  height: 7px;
  border-radius: var(--radius-pill);
  background: var(--error);
}
.hello {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-start;
  margin: var(--space-sm) 0 var(--space-lg);
}
.gudu {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  background: var(--card);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(var(--sh), 0.12);
}
.hello-bubble {
  flex: 1;
  padding: 10px 14px;
  border-radius: 4px 16px 16px 16px;
  background: var(--card);
  border: 1px solid var(--border);
}
.hello-txt {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--title);
}
.sec {
  margin-bottom: var(--space-xl);
}
.sec-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--space-md);
}
.sec-title {
  display: block;
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
  margin-bottom: var(--space-md);
}
.sec-head .sec-title {
  margin-bottom: 0;
}
.sec-more {
  font-size: var(--fs-sm);
  color: var(--accent);
  font-weight: 600;
}
.sec-hint {
  font-size: var(--fs-sm);
  color: var(--body);
}
.hero {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(var(--sh), 0.18);
}
.hero-nocover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 20rpx;
  padding: 96rpx 40rpx 0 0;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-deep) 100%);
}
.nc-ing {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(var(--sh), 0.22);
}
.nc-img {
  width: 68rpx;
  height: 68rpx;
}
.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.1) 0%, rgba(0, 0, 0, 0) 35%, rgba(40, 24, 12, 0.72) 100%);
}
.hero-tags {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 12px;
  display: flex;
  align-items: center;
}
.flex1 {
  flex: 1;
}
.tag-red {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--error);
  color: var(--on-primary);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.tag-white {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
  color: var(--title);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.hero-info {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hero-name {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--on-primary);
}
.hero-why {
  font-size: var(--fs-sm);
  color: rgba(255, 255, 255, 0.9);
}
.hero-acts {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.hero-btn {
  flex: 1;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-btn.ghost {
  flex: 0 0 96px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.6);
}
.hero-btn-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
.exp-bar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 12px;
  margin: calc(-1 * var(--space-sm)) 0 var(--space-xl);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.exp-icons {
  display: flex;
}
/* WXSS 不支持 * 通配选择器，按实际子元素 ui-ing-icon 写等价规则 */
.exp-icons > ui-ing-icon + ui-ing-icon {
  margin-left: -8px;
}
.exp-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.exp-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.exp-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.exp-btn {
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
}
.exp-btn-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
  white-space: nowrap;
}
.prompts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
}
.prompt {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 12px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.prompt-ic {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.t0 {
  background: var(--success-soft);
}
.t1 {
  background: var(--secondary);
}
.t2 {
  background: var(--info-soft);
}
.t3 {
  background: var(--warning-soft);
}
.prompt-txt {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--title);
  line-height: 1.4;
}
.sess {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.sess-ic {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.sess-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sess-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.sess-sub {
  font-size: var(--fs-sm);
  color: var(--body);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.sess-time {
  font-size: var(--fs-sm);
  color: var(--body);
}
.composer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px var(--space-lg);
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--border);
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
.mic {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(var(--sh), 0.3);
}
.ai-intro {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin-bottom: var(--sp-12);
}
.ai-intro-txt {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--caption);
  line-height: 1.5;
}
</style>
