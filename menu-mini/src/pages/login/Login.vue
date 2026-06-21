<template>
  <view class="login">
    <view class="logo">小食单</view>
    <view class="field-label">手机号 / 账号</view>
    <input class="ipt" v-model="form.username" placeholder="手机号 或 admin" />
    <view class="field-label">密码</view>
    <input class="ipt" v-model="form.password" type="password" placeholder="密码" />
    <button class="btn" :disabled="loading" @click="onLogin">{{ loading ? '登录中...' : '登录' }}</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入手机号/账号和密码', icon: 'none' })
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
  padding: 40rpx;
}
.logo {
  font-size: 48rpx;
  font-weight: bold;
  text-align: center;
  color: #ff8c42;
  margin: 60rpx 0;
}
.field-label {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #7a6f60;
}
.ipt {
  margin-top: 8rpx;
  padding: 16rpx 20rpx;
  border: 1px solid #dcdfe6;
  border-radius: 8rpx;
  font-size: 28rpx;
  width: 100%;
  box-sizing: border-box;
}
.btn {
  margin-top: 24rpx;
  background: #ff8c42;
  color: #fff;
  border: none;
  padding: 16rpx;
  border-radius: 8rpx;
  font-size: 30rpx;
  width: 100%;
}
</style>
