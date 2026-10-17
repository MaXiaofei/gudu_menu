<template>
  <!-- 食集 · 一起吃：邀请卡（口令 + 分享）+ 成员头像 + 动态；进入 Tab 每 10 秒轮询。 -->
  <view class="pane">
    <ui-state v-if="loading" mode="loading" />
    <template v-else-if="together">
      <view v-if="!isDone" class="invite-card">
        <view class="invite-top">
          <ui-avatar-stack v-if="together.members.length" :items="faces" :size="28" />
          <view class="invite-info">
            <text class="invite-title">{{ invite ? `口令 ${invite.code}` : '邀请朋友一起点菜' }}</text>
            <text class="invite-sub">{{ invite ? '点分享发给微信好友，或把口令发给他' : '对方只能看到菜名和忌口，看不到你家余量' }}</text>
          </view>
        </view>
        <view v-if="invite" class="invite-ops">
          <view class="op ghost" @click="copyCode"><text class="op-txt dark">复制口令</text></view>
          <button class="op share-btn" open-type="share"><text class="op-txt">分享给好友</text></button>
        </view>
        <view v-else class="op full" @click="$emit('create')"><text class="op-txt">{{ inviting ? '生成中…' : '生成邀请' }}</text></view>
      </view>

      <text class="sec-label">一起吃的人 · {{ together.members.length }}</text>
      <view v-if="!together.members.length" class="empty-txt">还没有人加入，先邀请朋友吧</view>
      <view v-else class="members">
        <view v-for="(m, i) in together.members" :key="i" class="member">
          <ui-member-avatar :name="m.nickname" :size="40" />
          <text class="m-name">{{ m.nickname || '朋友' }}</text>
          <text class="m-time">{{ relativeTime(m.lastActiveAt) }}</text>
        </view>
      </view>

      <text class="sec-label">动态</text>
      <view v-if="!together.activities.length" class="empty-txt">暂无动态</view>
      <view v-else class="acts">
        <view v-for="(a, i) in together.activities" :key="i" class="act">
          <ui-member-avatar :name="a.nickname" :size="24" />
          <text class="act-txt">{{ a.nickname || '朋友' }} {{ a.action === 'remove' ? '删了' : '点了' }}「{{ a.dishName }}」</text>
          <text class="m-time">{{ relativeTime(a.createTime) }}</text>
        </view>
      </view>
      <text class="note">朋友加的菜直接进「菜」，标「XX点的」；谁都能删，会记下谁删的。</text>
    </template>
    <ui-state v-else mode="error" text="一起吃没加载出来" :retry="true" @retry="$emit('retry')" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TogetherVO } from '@/api/together'

const props = defineProps<{ together: TogetherVO | null; loading: boolean; isDone: boolean; invite: { code: string; token: string } | null; inviting: boolean }>()
defineEmits<{ (e: 'create'): void; (e: 'retry'): void }>()

const faces = computed(() => (props.together?.members ?? []).map((m) => ({ name: m.nickname })))

function copyCode() {
  if (!props.invite) return
  uni.setClipboardData({
    data: `聚餐口令：${props.invite.code}（打开小程序，输入口令加入）`,
    success: () => uni.showToast({ title: '口令已复制', icon: 'none' }),
  })
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / M/D。 */
function relativeTime(s?: string | null): string {
  if (!s) return ''
  const d = new Date(s.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.invite-card {
  padding: var(--sp-16);
  border-radius: var(--r-lg);
  background: var(--card);
  border: 2px solid var(--primary-soft);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.invite-top {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.invite-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.invite-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.invite-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.invite-ops {
  display: flex;
  gap: var(--sp-10);
}
.op {
  flex: 1;
  height: 44px;
  margin: 0;
  padding: 0;
  border-radius: var(--r-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.op::after {
  border: none;
}
.op.ghost {
  background: var(--card);
  border: 1px solid var(--border);
}
.op-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
.op-txt.dark {
  color: var(--title);
}
.sec-label {
  margin-top: var(--sp-8);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.empty-txt {
  padding: var(--sp-12);
  font-size: var(--fs-md);
  color: var(--body);
  text-align: center;
}
.members {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-16);
}
.member {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 56px;
}
.m-name {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--title);
}
.m-time {
  font-size: var(--fs-xs);
  color: var(--body);
}
.acts {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-4) var(--sp-12);
}
.act {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  padding: var(--sp-10) 0;
}
.act + .act {
  border-top: 1px solid var(--border);
}
.act-txt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.note {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--body);
}
</style>
