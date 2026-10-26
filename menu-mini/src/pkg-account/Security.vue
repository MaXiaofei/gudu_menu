<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 账号与安全（隐私政策第七节入口：我的 → 账号与安全）：账号信息、合并手机号账号、导出我的数据、注销账号。 -->
  <view class="page">
    <ui-back-header title="账号与安全" />
    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!acc" mode="error" :retry="true" @retry="load" />
    <view v-else class="body">
      <view class="card">
        <text class="group-title">账号</text>
        <view class="row">
          <text class="row-label grow">昵称</text>
          <text class="row-value">{{ acc.account.nickname || '未设置' }}</text>
        </view>
        <view class="row">
          <text class="row-label grow">手机号</text>
          <text class="row-value" data-testid="acc-phone">{{ acc.account.maskedPhone || '未绑定' }}</text>
        </view>
        <view class="row">
          <text class="row-label grow">微信</text>
          <text class="row-value">{{ acc.account.wechatBound ? '已绑定' : '未绑定' }}</text>
        </view>
      </view>

      <!-- 合并手机号账号：仅「纯微信账号」（无手机号）可发起 -->
      <view v-if="canMerge" class="card" data-testid="merge-card">
        <text class="group-title">合并手机号账号</text>
        <text class="note">
          如果你之前用手机号和密码登录过（比如在 App 上），可以把当前微信账号并入那个手机号账号：
          以手机号账号为主，两边加入的家庭和数据都会保留；合并后用微信登录会直接进入手机号账号。合并不可撤销。
        </text>
        <view class="input-wrap gap">
          <input v-model="mergeForm.phone" class="ipt" type="number" maxlength="11" data-testid="merge-phone" placeholder="手机号" placeholder-class="ipt-ph" />
        </view>
        <view class="input-wrap gap">
          <input v-model="mergeForm.password" class="ipt" password data-testid="merge-password" placeholder="该手机号账号的密码" placeholder-class="ipt-ph" />
        </view>
        <text v-if="mergeErr" class="err-txt">{{ mergeErr }}</text>
        <button class="btn gap" :disabled="merging" data-testid="merge-submit" @click="onMerge">{{ merging ? '合并中…' : '验证并合并' }}</button>
      </view>

      <view class="card">
        <text class="group-title">我的数据</text>
        <view class="row" data-testid="export-entry" @click="onExport">
          <view class="row-main">
            <text class="row-label">导出我的数据</text>
            <text class="row-sub">下载你的账号、家庭成员档案、菜谱与记录副本</text>
          </view>
          <ui-icon name="chevron" :size="16" />
        </view>
        <view class="row" data-testid="deactivate-entry" @click="showDeactivate = !showDeactivate">
          <text class="row-label grow danger-txt">注销账号</text>
          <ui-icon name="chevron" :size="16" />
        </view>
      </view>

      <view v-if="showDeactivate" class="card" data-testid="deactivate-card">
        <text class="group-title">注销账号</text>
        <view v-if="blocking.length" class="callout" data-testid="deactivate-blocked">
          <text class="callout-txt">
            你是「{{ blocking.map((f) => f.name).join('」「') }}」的主理人，家里还有其他成员。请先把主理人转让给家人，或解散该家庭，再来注销。
          </text>
          <text class="row-act" @click="goFamily">去处理</text>
        </view>
        <template v-else>
          <text class="note">注销后：</text>
          <text class="note">· 你的手机号、微信身份、昵称和头像将被删除或匿名化，所有设备立即退出登录，无法恢复；</text>
          <text class="note">· 你会退出所有家庭；只有你一个人的家庭会随之解散；</text>
          <text class="note">· 家里共享的菜谱、余量、食集等数据随家庭保留，家人仍可使用；</text>
          <text class="note">· 之后再用同一微信登录，会是一个全新的账号。</text>
          <view class="agree" data-testid="deactivate-agree" @click="understood = !understood">
            <view class="box" :class="{ on: understood }"><ui-icon v-if="understood" name="check" tone="white" :size="12" /></view>
            <text class="note">我已了解以上后果</text>
          </view>
          <button class="btn danger" :disabled="!understood || deactivating" data-testid="deactivate-submit" @click="onDeactivate">
            {{ deactivating ? '注销中…' : '注销账号' }}
          </button>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  accountMe,
  deactivateAccount,
  mergePhoneAccount,
  ownedFamiliesBlockingDeactivation,
  type AccountMe,
} from '@/api/account'
import { createExportLink, copyExportLink } from '@/api/privacy'
import { useAuthStore } from '@/store/auth'
import { useFamilyStore } from '@/store/family'
import { saveFamilyContext } from '@/utils/family'

const auth = useAuthStore()
const family = useFamilyStore()
const loading = ref(true)
const acc = ref<AccountMe | null>(null)
const mergeForm = reactive({ phone: '', password: '' })
const mergeErr = ref('')
const merging = ref(false)
const showDeactivate = ref(false)
const understood = ref(false)
const deactivating = ref(false)

const canMerge = computed(() => !!acc.value && acc.value.account.wechatBound && !acc.value.account.maskedPhone)
const blocking = computed(() => ownedFamiliesBlockingDeactivation(acc.value?.families || []))

onShow(load)

async function load() {
  loading.value = !acc.value
  try {
    acc.value = await accountMe()
    saveFamilyContext({ families: acc.value.families, currentFamilyId: acc.value.currentFamilyId })
    family.syncFromStorage()
  } catch {
    acc.value = null
  }
  loading.value = false
}

function ask(title: string, content: string, confirmText: string): Promise<boolean> {
  return new Promise((resolve) =>
    uni.showModal({ title, content, confirmText, success: (r) => resolve(!!r.confirm), fail: () => resolve(false) }),
  )
}

async function onMerge() {
  mergeErr.value = ''
  if (!/^1\d{10}$/.test(String(mergeForm.phone).trim())) {
    mergeErr.value = '请输入 11 位手机号'
    return
  }
  if (!mergeForm.password) {
    mergeErr.value = '请输入密码'
    return
  }
  const ok = await ask(
    '确认合并',
    `当前微信账号将并入手机号 ${String(mergeForm.phone).trim()} 的账号，两边的家庭和数据都会保留，合并后不可撤销。`,
    '合并',
  )
  if (!ok) return
  merging.value = true
  try {
    const r = await mergePhoneAccount(mergeForm.phone, mergeForm.password)
    // 旧 token 已被服务端下线：storage 已写入新 token，这里同步 store
    auth.replaceToken(r.token, r.nickname)
    family.syncFromStorage()
    mergeForm.phone = ''
    mergeForm.password = ''
    uni.showToast({ title: '合并成功', icon: 'none' })
    await load()
  } catch (e: any) {
    mergeErr.value = e?.message || '合并失败'
  } finally {
    merging.value = false
  }
}

let exporting = false
async function onExport() {
  if (exporting) return
  exporting = true
  try {
    const link = await createExportLink()
    await copyExportLink(link)
  } catch {
    // request.ts 已提示（如每日次数用完）
  } finally {
    exporting = false
  }
}

function goFamily() {
  uni.navigateTo({ url: '/pkg-family/Index' })
}

async function onDeactivate() {
  if (!understood.value || deactivating.value) return
  const ok = await ask('最后确认', '注销后账号无法恢复，确定注销吗？', '确定注销')
  if (!ok) return
  deactivating.value = true
  try {
    await deactivateAccount()
    auth.$reset?.()
    uni.showToast({ title: '账号已注销', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/login/Login' }), 400)
  } catch {
    // 后端拒绝（如仍是有成员家庭的主理人）：request.ts 已提示，刷新状态
    load()
  } finally {
    deactivating.value = false
  }
}
</script>

<style scoped>
@import '../pkg-family/parts/family.css';
.grow {
  flex: 1;
}
.gap {
  margin-top: var(--sp-8);
}
.soon {
  padding: 2px 8px;
  border-radius: var(--r-pill);
  background: var(--secondary);
  font-size: var(--fs-tiny);
  font-weight: 700;
  color: var(--caption);
}
.danger-txt {
  color: var(--error-deep);
}
.note {
  display: block;
}
.callout {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.callout-txt {
  flex: 1;
}
.agree {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin: var(--sp-12) 0;
}
.box {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1.5px solid var(--body);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}
.box.on {
  background: var(--error);
  border-color: var(--error);
}
</style>
