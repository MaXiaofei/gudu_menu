<template>
  <view class="login">
    <!-- 顶部渐变 header -->
    <view class="header">
      <view class="logo-circle">🍜</view>
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
          <text class="input-ico">👤</text>
          <input
            class="ipt"
            v-model="form.username"
            placeholder="手机号 或 admin"
            placeholder-class="ipt-ph"
          />
        </view>
      </view>

      <view class="field">
        <text class="field-label">密码</text>
        <view class="input-wrap">
          <text class="input-ico">🔒</text>
          <input
            class="ipt"
            v-model="form.password"
            :password="!showPwd"
            placeholder="请输入密码"
            placeholder-class="ipt-ph"
          />
          <text class="pwd-toggle" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁' }}</text>
        </view>
      </view>

      <button class="yh-btn-gradient login-btn" :disabled="loading" @click="onLogin">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)

async function onLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    uni.switchTab({ url: '/pages/index/Index' })
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
  font-size: 76rpx;
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
  font-size: 32rpx;
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
  font-size: 32rpx;
  padding: 0 4rpx;
}

.login-btn {
  margin-top: 60rpx;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
}
</style>
