<template>
  <view class="login">
    <!-- 顶部渐变 header -->
    <view class="header">
      <view class="logo-circle"><text class="logo-text">食</text></view>
      <text class="brand">小食单</text>
      <text class="slogan">小火慢炖，咕嘟出家的味道</text>
    </view>

    <!-- 表单卡片 -->
    <view class="form-card">
      <text class="welcome">欢迎回来</text>
      <text class="welcome-sub">登录开始管理全家菜谱</text>

      <view class="field">
        <text class="field-label">手机号 / 账号</text>
        <view class="input-wrap">
          <u-icon class="input-ico" name="account" :size="20" color="#9C8C7A" />
          <input
            class="ipt"
            v-model="form.username"
            placeholder="手机号 / 账号"
            placeholder-class="ipt-ph"
          />
        </view>
      </view>

      <view class="field">
        <text class="field-label">密码</text>
        <view class="input-wrap">
          <u-icon class="input-ico" name="lock" :size="20" color="#9C8C7A" />
          <input
            class="ipt"
            v-model="form.password"
            :password="!showPwd"
            placeholder="请输入密码"
            placeholder-class="ipt-ph"
          />
          <u-icon class="pwd-toggle" :name="showPwd ? 'eye' : 'eye-off'" :size="20" color="#9C8C7A" @click="showPwd = !showPwd" />
        </view>
      </view>

      <button class="yh-btn-gradient login-btn" :disabled="loading" @click="onLogin">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
      <view class="wx-divider"><text class="wx-divider-txt">或</text></view>
      <button class="wx-btn" :disabled="wxLoading" @click="onWxLogin">
        {{ wxLoading ? '微信登录中…' : '微信一键登录' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'
import { silentLogin } from '@/api/auth'

const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)
const wxLoading = ref(false)

/** 微信一键登录：静默 wx.login → wx-login → 进首页（新用户自动建号）。 */
async function onWxLogin() {
  wxLoading.value = true
  try {
    const r = await silentLogin()
    if (r) {
      uni.switchTab({ url: '/pages/dish/List' })
    } else {
      uni.showToast({ title: '微信登录失败，可先用账号登录', icon: 'none' })
    }
  } finally {
    wxLoading.value = false
  }
}

async function onLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    uni.switchTab({ url: '/pages/dish/List' })
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
  background: #FDFAF4;
  display: flex;
  flex-direction: column;
}

/* 渐变 header */
.header {
  background: linear-gradient(180deg, #E89150, #D17A3C);
  padding: 80rpx 0 70rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-bottom-left-radius: 64rpx;
  border-bottom-right-radius: 64rpx;
}
.logo-circle {
  width: 150rpx;
  height: 150rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-text {
  font-size: 76rpx;
  font-weight: bold;
  color: #FFFFFF;
}
.brand {
  margin-top: 30rpx;
  font-size: 56rpx;
  font-weight: bold;
  color: #FFFFFF;
}
.slogan {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.92);
}

/* 表单卡片 */
.form-card {
  margin: -40rpx 36rpx 0;
  background: #FFFFFF;
  border-radius: 36rpx;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.08);
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
}
.welcome {
  font-size: 44rpx;
  font-weight: bold;
  color: #4A382A;
}
.welcome-sub {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #9C8C7A;
}

.field {
  margin-top: 40rpx;
}
.field-label {
  font-size: 26rpx;
  color: #9C8C7A;
}
.input-wrap {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  background: #FDFAF4;
  border-radius: 28rpx;
  padding: 0 24rpx;
  border: 2rpx solid transparent;
}
.input-wrap:focus-within {
  border-color: #E89150;
}
.input-ico {
  margin-right: 16rpx;
}
.ipt {
  flex: 1;
  height: 88rpx;
  font-size: 30rpx;
  color: #4A382A;
}
.ipt-ph {
  color: #9C8C7A;
  font-size: 28rpx;
}
.pwd-toggle {
  padding: 0 4rpx;
}

.login-btn {
  margin-top: 60rpx;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  padding: 0;
}


/* 微信一键登录 */
.wx-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0;
}
.wx-divider::before,
.wx-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #F0E6D6;
}
.wx-divider-txt {
  font-size: 11px;
  color: #9C8C7A;
}
.wx-btn {
  background: #07C160;
  color: #FFFFFF;
  border: none;
  border-radius: 14px;
  padding: 14px 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}
.wx-btn::after { border: none; }
.wx-btn[disabled] {
  background: rgba(7, 193, 96, 0.5);
  color: rgba(255, 255, 255, 0.9);
}

</style>
