<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 本家成员档案（multi-tenant-family）：当前家庭里一起吃饭的人。有账号的家人 + 无账号成员（宝宝、老人）。
       点行编辑档案；「设为就餐」切换当前就餐成员（智荐 / 营养按 TA 计算）。 -->
  <view class="page">
    <ui-back-header title="成员档案" subtitle="口味、忌口和健康信息会用于智荐、备菜和营养计算">
      <template #action>
        <text v-if="manage" class="add" data-testid="member-add" @click="goEdit(0)">+ 添加</text>
      </template>
    </ui-back-header>
    <view class="fam-card" @click="goFamily">
      <ui-avatar-stack :items="store.members.map((m) => ({ name: m.name, tags: audiencesOf(m) }))" :size="36" :max="5" />
      <view class="fam-main">
        <text class="fam-name">{{ family.current?.name || '我的家' }}</text>
        <text class="fam-sub">{{ store.members.length }} 份档案 · 邀请家人、管理成员在「我的家庭」</text>
      </view>
      <ui-icon name="chevron" :size="16" />
    </view>

    <ui-state v-if="loading" mode="loading" />
    <scroll-view v-else scroll-y class="body">
      <view
        v-for="m in store.members"
        :key="m.id"
        class="row"
        :data-testid="'member-row-' + m.id"
        @click="goEdit(m.id)"
      >
        <ui-member-avatar :name="m.name" :tags="audiencesOf(m)" :size="44" />
        <view class="main">
          <text class="name">{{ m.name }}<text v-if="me && m.accountId === me" class="kind">（我）</text></text>
          <text class="tags">{{ [m.accountId ? '家人账号' : '无账号成员', ...tagsOf(m)].join(' · ') }}</text>
        </view>
        <view v-if="store.currentId === m.id" class="cur"><text class="cur-txt">就餐中</text></view>
        <text v-else class="switch" @click.stop="switchTo(m)">设为就餐</text>
      </view>
      <ui-state
        v-if="!store.members.length"
        mode="empty"
        text="还没有成员档案"
        :hint="manage ? '给宝宝、老人建个档，智荐会照顾 TA 的口味' : '请主理人添加'"
        :action-text="manage ? '添加档案' : ''"
        :illus="['rice', 'egg']"
        @action="goEdit(0)"
      />

      <!-- 敏感信息单独同意（B.2）：可随时撤回，撤回即删除对应信息。默认收起成一行，不抢档案列表的视线 -->
      <view v-if="consent" class="consent">
        <view class="consent-head" data-testid="consent-toggle" @click="consentOpen = !consentOpen">
          <view class="main">
            <text class="consent-title">敏感信息授权</text>
            <text class="consent-sub">{{ consentSummary }}</text>
          </view>
          <view class="chev" :class="{ open: consentOpen }"><ui-icon name="chevron" :size="14" /></view>
        </view>
        <template v-if="consentOpen">
          <text class="consent-hint">健康档案与儿童信息仅用于营养计算、菜品过滤与 AI 回答，加密存储；撤回后删除对应信息</text>
          <view v-for="c in consentRows" :key="c.type" class="consent-row">
            <view class="main">
              <text class="consent-name">{{ c.label }}</text>
              <text class="consent-sub">{{ c.granted ? `已同意${c.at ? ' · ' + c.at.slice(0, 10) : ''}` : '未同意（填写时会单独询问）' }}</text>
            </view>
            <text v-if="c.granted" class="withdraw" :data-testid="'consent-withdraw-' + c.type" @click="withdraw(c.type)">撤回</text>
          </view>
        </template>
      </view>
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import { useFamilyStore } from '@/store/family'
import { ensureAccountId } from '@/api/account'
import { canManage } from '@/utils/family'
import type { Member } from '@/api/member'
import {
  getSensitiveConsent,
  withdrawSensitiveConsent,
  type ConsentStatus,
  type SensitiveDocType,
} from '@/api/privacy'
import { askModal, withdrawDialog } from '@/utils/sensitiveConsent'

const store = useMemberStore()
const family = useFamilyStore()
const loading = ref(true)
const me = ref<number | null>(null)
const manage = computed(() => canManage(family.currentRole))

const consent = ref<ConsentStatus | null>(null)
const consentOpen = ref(false)

const consentRows = computed(() => {
  const c = consent.value
  if (!c) return []
  return [
    { type: 'sensitive_health' as SensitiveDocType, label: '健康信息', ...c.sensitive_health },
    { type: 'child_info' as SensitiveDocType, label: '不满 14 周岁儿童信息', ...c.child_info },
  ]
})
const consentSummary = computed(() => {
  const n = consentRows.value.filter((c) => c.granted).length
  return n ? `已同意 ${n} 项 · 可随时撤回` : '未授权 · 填写健康信息时会单独询问'
})

async function loadConsent() {
  try {
    consent.value = await getSensitiveConsent()
  } catch {
    consent.value = null
  }
}

onShow(async () => {
  loading.value = !store.members.length
  try {
    me.value = await ensureAccountId()
    await Promise.all([store.load(), family.refresh().catch(() => {})])
  } catch {}
  loading.value = false
  loadConsent()
})

function audiencesOf(m: Member): string[] {
  return m.audienceTags ?? m.healthProfile?.audiences ?? []
}

function tagsOf(m: Member): string[] {
  // roleTags 后端是逗号分隔字符串（旧类型标注为数组），两种都兼容
  const r: unknown = m.roleTags
  const roles = Array.isArray(r) ? r : typeof r === 'string' ? r.split(/[,，]/).filter(Boolean) : []
  return [...audiencesOf(m), ...roles]
}

function goEdit(id: number) {
  uni.navigateTo({ url: id ? `/pkg-family/MemberEdit?id=${id}` : '/pkg-family/MemberEdit' })
}

function goFamily() {
  uni.navigateTo({ url: '/pkg-family/Index' })
}

async function withdraw(type: SensitiveDocType) {
  const { title, content } = withdrawDialog(type)
  if (!(await askModal(title, content, '确认撤回', '取消'))) return
  try {
    consent.value = await withdrawSensitiveConsent(type)
    await store.load().catch(() => {})
    uni.showToast({ title: '已撤回，相关信息已删除', icon: 'none' })
  } catch {}
}

async function switchTo(m: Member) {
  if (store.currentId === m.id) return
  try {
    await store.switchTo(m.id)
    uni.showToast({ title: `已切换为 ${m.name}`, icon: 'none' })
  } catch {
    // request.ts 已提示（如普通成员不能切到别人的账号档案）
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
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px 14px;
  margin-bottom: 8px;
}
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.name { font-size: var(--fs-md); font-weight: 700; color: var(--title); }
.tags { font-size: var(--fs-tiny); color: var(--caption); }
.cur {
  background: var(--success);
  border-radius: var(--r-pill);
  padding: 3px 10px;
}
.cur-txt { color: var(--on-primary); font-size: var(--fs-tiny); font-weight: 700; }
.switch { font-size: var(--fs-sm); color: var(--primary-deep); font-weight: 700; padding: 4px 0 4px 8px; }
.kind { font-size: var(--fs-sm); font-weight: 400; color: var(--caption); }
.add { font-size: var(--fs-md); font-weight: 700; color: var(--primary-deep); }
.consent {
  margin-top: var(--sp-16);
  padding: 10px 14px;
  border-radius: var(--r-md);
  border: 1px dashed var(--border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.consent-head { display: flex; align-items: center; gap: 8px; }
.consent-title { font-size: var(--fs-sm); color: var(--body); }
.consent-name { font-size: var(--fs-sm); color: var(--body); }
.consent-sub { font-size: var(--fs-tiny); color: var(--caption); }
.consent-hint { font-size: var(--fs-tiny); color: var(--caption); line-height: 1.5; }
.consent-row { display: flex; align-items: center; gap: 12px; padding-top: 8px; border-top: 1px solid var(--border); }
.chev { transition: transform 0.2s; }
.chev.open { transform: rotate(90deg); }
.withdraw { font-size: var(--fs-sm); color: var(--caption); padding: 4px 0 4px 8px; text-decoration: underline; }
.fam-card {
  margin: var(--sp-8) var(--sp-16) var(--sp-12);
  padding: var(--sp-16);
  border-radius: var(--r-xl);
  background: linear-gradient(135deg, var(--primary-soft), var(--secondary));
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.fam-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fam-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.fam-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
