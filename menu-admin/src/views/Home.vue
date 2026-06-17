<script setup lang="ts">
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
</script>

<template>
  <div class="home">
    <div class="welcome">
      <h2>你好，{{ authStore.nickname || '掌柜' }} 👋</h2>
      <p>欢迎回到烟火小食单管理后台。各业务模块将在后续任务上线。</p>
      <el-card shadow="never" class="tip">
        <div class="row"><b>当前位置：</b>Task 18 前端骨架（登录 / 布局 / 双主题换肤）</div>
        <div class="row"><b>下一步：</b>菜品、菜单、食材库等业务管理页（Task 19）</div>
      </el-card>
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
.tip .row {
  font-size: 13px;
  color: #5a4f42;
  line-height: 1.9;
}
</style>
