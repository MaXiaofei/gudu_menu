<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="login">
    <!-- 氛围拼贴（prototype Login 为菜品照片；包内不放第三方照片，改用自绘食材插画砖块），渐隐到底色 -->
    <view class="collage" :style="{ height: collageH + 'px' }">
      <view class="tilt">
        <view v-for="(col, ci) in COLS" :key="ci" class="col" :style="{ marginTop: ci === 1 ? '-36px' : ci === 2 ? '-12px' : '0' }">
          <view v-for="(k, i) in col" :key="i" class="tile" :class="'t' + ((ci + i) % 3)">
            <image class="tile-ic" :src="`/static/ing/${k}.png`" mode="aspectFit" />
          </view>
        </view>
      </view>
      <view class="fade" />
    </view>

    <view class="brand-box">
      <image class="logo" src="/static/logo-gudu.png" mode="aspectFit" />
      <text class="brand">咕嘟小食单</text>
      <text class="slogan">家里有什么，今晚就吃什么</text>
    </view>

    <view class="form">
      <!-- #ifdef MP-WEIXIN -->
      <button class="wx-btn" :disabled="wxLoading" data-testid="wx-login" @click="onWxLogin">
        {{ wxLoading ? '微信登录中…' : '微信一键登录' }}
      </button>
      <!-- #endif -->

      <!-- 小程序只提供微信登录（privacy/terms §二.1）：账号密码表单仅 H5 构建保留 -->
      <!-- #ifndef MP-WEIXIN -->
      <view v-if="showAccount" class="account">
        <text class="welcome">欢迎回来</text>
        <view class="input-wrap">
          <ui-icon name="users" :size="18" />
          <input class="ipt" v-model="form.username" placeholder="手机号 / 账号" placeholder-class="ipt-ph" />
        </view>
        <view class="input-wrap">
          <ui-icon name="shield" :size="18" />
          <input class="ipt" v-model="form.password" :password="!showPwd" placeholder="请输入密码" placeholder-class="ipt-ph" />
          <text class="pwd-toggle" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</text>
        </view>
        <button class="login-btn" :disabled="loading" @click="onLogin">{{ loading ? '登录中…' : '登 录' }}</button>
        <button class="wx-btn ghost" :disabled="wxLoading" @click="onWxLogin">{{ wxLoading ? '微信登录中…' : '微信一键登录' }}</button>
      </view>
      <!-- #endif -->

      <view v-if="!agreed" class="hint" data-testid="agree-hint">
        <text class="hint-txt">请先阅读并勾选下方协议。勾选前不会发起任何登录，也不会读取微信身份。</text>
      </view>

      <view class="agree" data-testid="agree-row">
        <view class="agree-box" :class="{ on: agreed }" data-testid="agree-box" @click="agreed = !agreed">
          <ui-icon v-if="agreed" name="check" tone="white" :size="12" />
        </view>
        <text class="agree-txt" @click="agreed = !agreed">我已阅读并同意</text>
        <text class="agree-link" @click="openLegal('terms')">《用户协议》</text>
        <text class="agree-txt">和</text>
        <text class="agree-link" @click="openLegal('privacy')">《隐私政策》</text>
        <text class="agree-txt">，了解小食单会保存我的菜谱、余量与做饭记录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { goAfterLogin, resetLoginRedirect, resumeIfLoggedIn } from '@/utils/loginRedirect'
import { navInset } from '@/utils/token'
import { useAuthStore } from '@/store/auth'
import { silentLogin } from '@/api/auth'
import { agreementError } from '@/utils/validate'
import { hasConsent, reportConsent, setConsent } from '@/utils/consent'

const auth = useAuthStore()
const nav = navInset()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)
const wxLoading = ref(false)
const agreed = ref(hasConsent())
/** 登录后回跳目标（分享落地页带来的，goAfterLogin 内做白名单校验）。 */
const redirect = ref('')
onLoad((options) => {
  redirect.value = String(options?.redirect || '')
  // 已登录（或启动时静默登录成功）不停在登录页
  resumeIfLoggedIn(redirect.value)
})

/** 登录成功：统一去向（待加入家庭邀请 > redirect 回跳 > 首次引导 > 首页），见 utils/loginRedirect.goAfterLogin。 */
function afterLogin(accountId?: number | null) {
  resetLoginRedirect()
  return goAfterLogin(redirect.value, accountId)
}
// 小程序只有微信登录；H5 直接展示账号表单
const showAccount = ref(true)
const collageH = nav + 230

// 拼贴砖块用的食材插画 key（static/ing）
const COLS = [
  ['tomato', 'broccoli', 'shrimp'],
  ['egg', 'fish', 'pumpkin', 'carrot'],
  ['mushroom', 'ribs', 'spinach'],
]

/** 未勾选协议：提示并拦截（返回 false）。 */
function checkAgreed(): boolean {
  const err = agreementError(agreed.value)
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return false
  }
  setConsent() // 勾选后才记同意，之后才允许 wx.login（含后续静默自愈）
  return true
}

function openLegal(type: 'terms' | 'privacy') {
  uni.navigateTo({ url: `/pages/profile/Legal?type=${type}` })
}

/** 微信一键登录：静默 wx.login → wx-login → 进首页（新用户自动建号）。 */
async function onWxLogin() {
  if (!checkAgreed()) return
  wxLoading.value = true
  try {
    const r = await silentLogin()
    if (r) {
      reportConsent() // B.1：登录成功即上报协议同意（静默，不阻塞跳转）
      await afterLogin(r.accountId)
    } else {
      uni.showToast({ title: '微信登录失败，请稍后重试', icon: 'none' })
    }
  } finally {
    wxLoading.value = false
  }
}

async function onLogin() {
  if (!checkAgreed()) return
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const r = await auth.login(form.username, form.password)
    reportConsent() // B.1：登录成功即上报协议同意（静默，不阻塞跳转）
    await afterLogin(r?.accountId)
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}
.collage {
  position: relative;
  overflow: hidden;
  background: var(--secondary);
}
.tilt {
  position: absolute;
  left: -40px;
  right: -40px;
  top: -30px;
  display: flex;
  gap: 10px;
  transform: rotate(-8deg);
}
.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tile {
  width: 100%;
  height: 112px;
  border-radius: var(--r-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.t0 { background: var(--secondary); }
.t1 { background: var(--primary-soft); }
.t2 { background: var(--card); border: 1px solid var(--border); box-sizing: border-box; }
.tile-ic {
  width: 64px;
  height: 64px;
}
.fade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 140px;
  background: linear-gradient(180deg, rgba(253, 250, 244, 0), var(--bg));
}
.brand-box {
  margin-top: -56px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-6);
}
.logo {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  box-shadow: 0 10px 24px rgba(var(--sh), 0.3);
}
.brand {
  margin-top: var(--sp-8);
  font-size: var(--fs-h2);
  font-weight: 800;
  color: var(--title);
}
.slogan {
  font-size: var(--fs-md);
  color: var(--body);
}
.form {
  padding: var(--sp-24) var(--sp-24) calc(var(--sp-24) + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.wx-btn {
  height: 50px;
  line-height: 50px;
  margin: 0;
  border-radius: var(--r-md);
  background: var(--success-deep);
  color: var(--on-primary);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.wx-btn.ghost {
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
}
.wx-btn::after,
.login-btn::after {
  border: none;
}
.wx-btn[disabled] {
  opacity: 0.6;
}
.or {
  display: flex;
  justify-content: center;
  padding: var(--sp-4) 0;
}
.or-txt {
  font-size: var(--fs-md);
  color: var(--body);
}
.account {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.welcome {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.input-wrap {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  height: 50px;
  padding: 0 var(--sp-12);
  border-radius: var(--r-md);
  background: var(--card);
  border: 1px solid var(--border);
}
.input-wrap:focus-within {
  border-color: var(--primary);
}
.ipt {
  flex: 1;
  font-size: var(--fs-lg);
  color: var(--title);
}
.ipt-ph {
  color: var(--caption);
  font-size: var(--fs-md);
}
.pwd-toggle {
  font-size: var(--fs-sm);
  color: var(--primary-deep);
  padding: 4px;
}
.login-btn {
  height: 50px;
  line-height: 50px;
  margin: 0;
  border-radius: var(--r-md);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-lg);
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(var(--sh), 0.3);
}
.login-btn[disabled] {
  background: var(--primary-soft);
}
.hint {
  padding: var(--sp-12);
  border-radius: var(--r-md);
  background: var(--title);
}
.hint-txt {
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--on-primary);
}
.agree {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
}
.agree-box {
  width: 18px;
  height: 18px;
  margin-right: var(--sp-4);
  border-radius: 5px;
  border: 1.5px solid var(--body);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}
.agree-box.on {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.agree-txt {
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--body);
}
.agree-link {
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
