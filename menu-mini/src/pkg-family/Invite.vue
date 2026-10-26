<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 家庭邀请落地页：邀请预览（游客可看）→ 未登录先登录（登录后回到本页）→ 加入。 -->
  <view class="page">
    <ui-back-header />
    <view class="body">
      <ui-state v-if="loading" mode="loading" />
      <ui-state
        v-else-if="!preview"
        mode="error"
        text="邀请没加载出来"
        hint="检查邀请码是否完整，或请家人重新发一次"
        :retry="true"
        @retry="load"
      />
      <template v-else>
        <view class="hero" data-testid="invite-hero">
          <image class="logo" src="/static/logo-gudu.png" mode="aspectFit" />
          <template v-if="preview.familyName">
            <text class="who">{{ preview.inviterNickname || '家人' }} 邀请你加入</text>
            <text class="fam" data-testid="invite-family">「{{ preview.familyName }}」</text>
          </template>
          <text v-else class="fam">邀请码无效</text>
          <text v-if="preview.expiresAt" class="row-sub">邀请有效期至 {{ fmt(preview.expiresAt) }}</text>
        </view>

        <view v-if="!preview.usable" class="callout" data-testid="invite-unusable">
          <text class="callout-txt">{{ unusableText }}</text>
        </view>
        <template v-else>
          <view class="card">
            <text class="note">加入后，你可以和家人一起看、改这个家的菜谱、食集、我家余量和采购清单。你在其他家庭的数据不受影响，随时可以在「我的 → 我的家庭」退出。</text>
          </view>
          <button v-if="loggedIn" class="btn" :disabled="busy" data-testid="invite-join" @click="onJoin">
            {{ busy ? '加入中…' : '加入这个家' }}
          </button>
          <button v-else class="btn" data-testid="invite-login" @click="toLogin">登录后加入</button>
        </template>
        <button class="btn ghost" data-testid="invite-skip" @click="onSkip">暂不加入</button>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { previewInvitation, type InvitationPreview } from '@/api/family'
import { useFamilyStore } from '@/store/family'
import { invitePath, PENDING_INVITE_KEY } from '@/utils/family'
import { goAfterJoin, loginUrl } from '@/utils/loginRedirect'
import { fmt } from './parts/dialog'

const store = useFamilyStore()
const code = ref('')
const loading = ref(true)
const busy = ref(false)
const preview = ref<InvitationPreview | null>(null)
const loggedIn = ref(!!uni.getStorageSync('token'))

const unusableText = computed(() => {
  const r = preview.value?.reason || ''
  if (/过期|EXPIRED/i.test(r)) return '这个邀请已过期，请家人重新生成一个。'
  if (/撤销|REVOKED/i.test(r)) return '这个邀请已被撤销，请联系家人。'
  if (/用尽|EXHAUSTED|次数/i.test(r)) return '这个邀请的使用次数已用完，请家人重新生成一个。'
  return r || '这个邀请已失效，请家人重新生成一个。'
})

onLoad((o) => {
  code.value = decodeURIComponent(String(o?.code || '')).trim()
  // 未登录：记下邀请码，登录后按「待加入邀请」优先回到本页（utils/loginRedirect.goAfterLogin），
  // 新微信用户登录时随 wx-login 带上，直接进邀请家庭；已登录则已回到本页，清掉避免下次登录又被带回来
  if (code.value && !uni.getStorageSync('token')) uni.setStorageSync(PENDING_INVITE_KEY, code.value)
  else uni.removeStorageSync(PENDING_INVITE_KEY)
  load()
})

async function load() {
  loggedIn.value = !!uni.getStorageSync('token')
  if (!code.value) {
    loading.value = false
    preview.value = null
    return
  }
  loading.value = true
  try {
    preview.value = await previewInvitation(code.value)
  } catch {
    preview.value = null
  }
  loading.value = false
}

function toLogin() {
  uni.navigateTo({ url: loginUrl(invitePath(code.value)) })
}

async function onJoin() {
  if (busy.value) return
  busy.value = true
  try {
    await store.join(code.value)
    uni.removeStorageSync(PENDING_INVITE_KEY)
    uni.showToast({ title: `已加入「${preview.value?.familyName || '家庭'}」`, icon: 'none' })
    // 加入时后端已自动建好本人在新家庭的档案 → 首次引导（先头像昵称、再成员档案，均可跳过）
    setTimeout(goAfterJoin, 400)
  } catch (e: any) {
    if (/已在该家庭/.test(e?.message || '')) {
      uni.removeStorageSync(PENDING_INVITE_KEY)
      uni.switchTab({ url: '/pages/dish/List' })
    }
  } finally {
    busy.value = false
  }
}

function onSkip() {
  uni.removeStorageSync(PENDING_INVITE_KEY)
  if (uni.getStorageSync('token')) uni.switchTab({ url: '/pages/dish/List' })
  else uni.reLaunch({ url: '/pages/login/Login' })
}
</script>

<style scoped>
@import './parts/family.css';
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-6);
  padding: var(--sp-24) 0 var(--sp-12);
}
.logo {
  width: 64px;
  height: 64px;
  border-radius: var(--r-lg);
}
.who {
  margin-top: var(--sp-8);
  font-size: var(--fs-md);
  color: var(--body);
}
.fam {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
</style>
