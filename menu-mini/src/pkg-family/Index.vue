<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 家庭页（multi-tenant-family F.1）：当前家庭、成员与角色、邀请、退出 / 转让 / 解散。 -->
  <view class="page">
    <ui-back-header title="我的家庭" subtitle="一起做饭吃饭的一家人，数据只在家里共享">
      <template #action>
        <text class="row-act" data-testid="family-switch-entry" @click="goSwitch">切换</text>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="error" :retry="true" @retry="load" />
    <view v-else class="body">
      <view class="fam-card" data-testid="family-card">
        <view class="fam-main">
          <text class="fam-name" data-testid="family-name">{{ detail.name }}</text>
          <text class="fam-sub">{{ detail.members.length }} 位成员 · 我是{{ roleLabel(detail.myRole) }}</text>
        </view>
        <text v-if="manage" class="row-act" data-testid="family-rename" @click="onRename">改名</text>
      </view>

      <view class="card">
        <text class="group-title">成员与角色</text>
        <view v-if="pickTransfer" class="callout">
          <text class="callout-txt">点选要接任主理人的家人。转让后你会成为管理员。</text>
          <text class="row-act" @click="pickTransfer = false">取消</text>
        </view>
        <view
          v-for="m in detail.members"
          :key="m.accountId"
          class="row"
          :data-testid="'family-member-' + m.accountId"
          @click="onMember(m)"
        >
          <ui-member-avatar :name="m.nickname" :size="40" />
          <view class="row-main">
            <text class="row-label strong">{{ m.nickname || '家人' }}<text v-if="m.accountId === me" class="row-sub">（我）</text></text>
            <text v-if="m.joinedAt" class="row-sub">{{ fmt(m.joinedAt) }} 加入</text>
          </view>
          <text class="role" :class="m.role">{{ roleLabel(m.role) }}</text>
        </view>
        <view class="row" @click="go('/pages/member/Index')">
          <view class="row-main">
            <text class="row-label">成员档案</text>
            <text class="row-sub">口味、忌口和健康信息；宝宝、老人没有账号也能建档</text>
          </view>
          <ui-icon name="chevron" :size="16" />
        </view>
      </view>

      <view v-if="manage" class="card" data-testid="invite-card">
        <text class="group-title">邀请家人</text>
        <text class="note">生成邀请码发给家人，7 天内有效、最多 10 人使用；对方打开卡片登录后即可加入。</text>
        <view v-if="fresh" class="invite-box" data-testid="invite-fresh">
          <text class="code" data-testid="invite-code">{{ fresh.code }}</text>
          <text class="row-sub">有效期至 {{ fmt(fresh.expiresAt) }}</text>
          <view class="invite-acts">
            <button class="btn ghost half" data-testid="invite-copy" @click="copy(fresh.code)">复制邀请码</button>
            <!-- #ifdef MP-WEIXIN -->
            <button class="btn half" open-type="share" :data-code="fresh.code">发给微信家人</button>
            <!-- #endif -->
          </view>
        </view>
        <button v-else class="btn" :disabled="busy" data-testid="invite-create" @click="onInvite">生成邀请</button>

        <view v-for="iv in activeInvites" :key="iv.code" class="row">
          <view class="row-main">
            <text class="row-label">{{ iv.code }}</text>
            <text class="row-sub">已用 {{ iv.usedCount }}/{{ iv.maxUses }} · {{ fmt(iv.expiresAt) }} 到期</text>
          </view>
          <text class="row-act" @click="copy(iv.code)">复制</text>
          <text class="row-act danger" :data-testid="'invite-revoke-' + iv.code" @click="onRevoke(iv.code)">撤销</text>
        </view>
      </view>

      <view class="card">
        <text class="group-title">家庭管理</text>
        <template v-if="detail.myRole === 'OWNER'">
          <view class="row" @click="startTransfer">
            <text class="row-label">转让主理人</text>
            <ui-icon name="chevron" :size="16" />
          </view>
          <view class="row" data-testid="family-dissolve" @click="onDissolve">
            <text class="row-label danger-txt">解散家庭</text>
            <ui-icon name="chevron" :size="16" />
          </view>
        </template>
        <view v-else class="row" data-testid="family-leave" @click="onLeave">
          <text class="row-label danger-txt">退出这个家庭</text>
          <ui-icon name="chevron" :size="16" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import {
  changeMemberRole,
  createInvitation,
  currentFamilyDetail,
  dissolveFamily,
  leaveFamily,
  removeMember,
  renameFamily,
  revokeInvitation,
  transferOwnership,
  type FamilyDetail,
  type FamilyMemberView,
  type InvitationView,
} from '@/api/family'
import { ensureAccountId } from '@/api/account'
import { useFamilyStore } from '@/store/family'
import { canManage, FAMILY_SWITCH_PAGE, invitePath, roleLabel } from '@/utils/family'
import { confirm, fmt, inputModal } from './parts/dialog'

const store = useFamilyStore()
const loading = ref(true)
const busy = ref(false)
const detail = ref<FamilyDetail | null>(null)
const fresh = ref<InvitationView | null>(null)
const me = ref<number | null>(null)
const pickTransfer = ref(false)

const manage = computed(() => canManage(detail.value?.myRole))
const activeInvites = computed(() =>
  (detail.value?.invitations || []).filter(
    (i) => !i.revoked && i.usedCount < i.maxUses && new Date(i.expiresAt.replace(' ', 'T')).getTime() > Date.now() && i.code !== fresh.value?.code,
  ),
)

onShow(load)

async function load() {
  loading.value = !detail.value
  try {
    me.value = await ensureAccountId()
    detail.value = await currentFamilyDetail()
  } catch {
    detail.value = null
  }
  loading.value = false
}

onShareAppMessage((res: any) => {
  const code = res?.target?.dataset?.code || fresh.value?.code
  const name = detail.value?.name || '我家'
  return code
    ? { title: `邀请你加入「${name}」，一起管家里的菜谱和余量`, path: invitePath(code) }
    : { title: '咕嘟小食单', path: '/pages/dish/List' }
})

function go(url: string) {
  uni.navigateTo({ url })
}
function goSwitch() {
  uni.navigateTo({ url: FAMILY_SWITCH_PAGE })
}

function copy(code: string) {
  uni.setClipboardData({
    data: code,
    success: () => uni.showToast({ title: '邀请码已复制', icon: 'none' }),
  })
}

async function onRename() {
  if (!detail.value) return
  const name = await inputModal('修改家庭名', detail.value.name, '1–20 个字')
  if (name === null) return
  const v = name.trim()
  if (!v || v.length > 20) {
    uni.showToast({ title: '家庭名需 1–20 个字', icon: 'none' })
    return
  }
  await renameFamily(detail.value.id, v)
  uni.showToast({ title: '已改名', icon: 'none' })
  await Promise.all([load(), store.refresh().catch(() => {})])
}

async function onInvite() {
  if (!detail.value || busy.value) return
  busy.value = true
  try {
    fresh.value = await createInvitation(detail.value.id)
  } finally {
    busy.value = false
  }
}

async function onRevoke(code: string) {
  if (!detail.value) return
  if (!(await confirm('撤销邀请', `撤销后邀请码 ${code} 立即失效，还没加入的人将无法再用它加入。`))) return
  await revokeInvitation(detail.value.id, code)
  if (fresh.value?.code === code) fresh.value = null
  uni.showToast({ title: '已撤销', icon: 'none' })
  load()
}

/** 点成员：转让选择模式 → 确认转让；否则按权限弹操作。 */
async function onMember(m: FamilyMemberView) {
  const d = detail.value
  if (!d || m.accountId === me.value) return
  if (pickTransfer.value) {
    pickTransfer.value = false
    if (!(await confirm('转让主理人', `确定把主理人转给「${m.nickname}」？转让后你将成为管理员，不能撤回。`))) return
    await transferOwnership(d.id, m.accountId)
    uni.showToast({ title: '已转让', icon: 'none' })
    await Promise.all([load(), store.refresh().catch(() => {})])
    return
  }
  const acts: { label: string; run: () => Promise<void> }[] = []
  if (d.myRole === 'OWNER') {
    if (m.role === 'MEMBER') acts.push({ label: '设为管理员', run: () => setRole(m, 'ADMIN') })
    if (m.role === 'ADMIN') acts.push({ label: '设为普通成员', run: () => setRole(m, 'MEMBER') })
    acts.push({ label: '转让主理人给 TA', run: async () => { pickTransfer.value = true; await onMember(m) } })
  }
  if ((d.myRole === 'OWNER' && m.role !== 'OWNER') || (d.myRole === 'ADMIN' && m.role === 'MEMBER')) {
    acts.push({ label: '移出家庭', run: () => onRemove(m) })
  }
  if (!acts.length) return
  uni.showActionSheet({
    itemList: acts.map((a) => a.label),
    success: ({ tapIndex }) => acts[tapIndex]?.run().catch(() => {}),
  })
}

async function setRole(m: FamilyMemberView, role: 'ADMIN' | 'MEMBER') {
  await changeMemberRole(detail.value!.id, m.accountId, role)
  uni.showToast({ title: '已调整', icon: 'none' })
  load()
}

async function onRemove(m: FamilyMemberView) {
  if (!(await confirm('移出家庭', `确定把「${m.nickname}」移出家庭？TA 将无法再看到家里的菜谱、余量和食集，TA 的成员档案保留。`))) return
  await removeMember(detail.value!.id, m.accountId)
  uni.showToast({ title: '已移出', icon: 'none' })
  await Promise.all([load(), store.refresh().catch(() => {})])
}

function startTransfer() {
  if ((detail.value?.members.length || 0) < 2) {
    uni.showToast({ title: '家里还没有其他成员，先邀请家人加入', icon: 'none' })
    return
  }
  pickTransfer.value = true
}

async function onLeave() {
  const d = detail.value
  if (!d) return
  if (!(await confirm('退出家庭', `退出「${d.name}」后将看不到这个家的菜谱、余量和食集，需要重新被邀请才能回来。`))) return
  await leaveFamily(d.id)
  await afterGone('已退出家庭')
}

/** 解散：两次确认，第二次需输入家庭名。 */
async function onDissolve() {
  const d = detail.value
  if (!d) return
  if (!(await confirm('解散家庭', `解散后所有成员（共 ${d.members.length} 人）都将无法再访问「${d.name}」的菜谱、余量、食集和记录，且不能恢复。`, '继续'))) return
  const typed = await inputModal('再次确认', '', `请输入家庭名「${d.name}」确认解散`)
  if (typed === null) return
  if (typed.trim() !== d.name) {
    uni.showToast({ title: '家庭名不一致，已取消', icon: 'none' })
    return
  }
  await dissolveFamily(d.id)
  await afterGone('家庭已解散')
}

/** 离开 / 解散的是当前家庭：服务端已清会话家庭 → 刷新列表并回到家庭选择。 */
async function afterGone(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
  try {
    await store.refresh()
  } catch {}
  uni.reLaunch({ url: `${FAMILY_SWITCH_PAGE}?reason=left` })
}
</script>

<style scoped>
@import './parts/family.css';
.fam-card {
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
  font-size: var(--fs-subtitle);
  font-weight: 800;
  color: var(--title);
}
.fam-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.callout {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-8);
}
.callout-txt {
  flex: 1;
}
.invite-box {
  margin: var(--sp-12) 0 var(--sp-4);
  padding: var(--sp-16);
  border-radius: var(--r-md);
  background: var(--secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-6);
}
.code {
  font-size: var(--fs-h2);
  font-weight: 800;
  letter-spacing: 4px;
  color: var(--primary-deep);
}
.invite-acts {
  width: 100%;
  display: flex;
  gap: var(--sp-8);
  margin-top: var(--sp-8);
}
.half {
  flex: 1;
  font-size: var(--fs-md);
}
.card > .btn {
  margin-top: var(--sp-12);
}
.danger-txt {
  flex: 1;
  color: var(--error-deep);
}
.row > .row-label {
  flex: 1;
}
</style>
