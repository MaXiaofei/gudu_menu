<template>
  <view class="login">
    <view class="logo">小食单</view>
    <u-input
      v-model="form.username"
      placeholder="用户名"
      border="surround"
      clearable
    />
    <u-input
      v-model="form.password"
      type="password"
      placeholder="密码"
      border="surround"
      clearable
    />
    <u-button type="primary" :loading="loading" @click="onLogin">登录</u-button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    uni.switchTab({ url: '/pages/index/Index' })
  } catch {
    // request.ts 已弹 toast，这里不重复
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
.u-input {
  margin-top: 24rpx;
}
.u-button {
  margin-top: 24rpx;
}
</style>
