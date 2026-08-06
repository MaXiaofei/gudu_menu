<script setup lang="ts">
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
</script>

<template>
  <div class="home">
    <div class="welcome">
      <h2>你好，{{ authStore.nickname || '掌柜' }}</h2>
      <p>欢迎来到咕嘟小食单管理后台</p>
    </div>
  </div>
</template>

<style scoped>
.home {
  max-width: 960px;
}
.welcome h2 {
  margin: 0 0 8px;
  color: #333;
}
.welcome p {
  color: #7a6f60;
  margin: 0 0 18px;
}
</style>
