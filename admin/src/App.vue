<script setup lang="ts">
import { ref } from 'vue'

const count = ref(0)
</script>

<template>
  <div style="padding: 40px; font-family: sans-serif">
    <h1>烟火小食单</h1>
    <p>前端骨架启动成功 (Vue3 + Element Plus)</p>
    <el-button type="primary" @click="count++">点击计数：{{ count }}</el-button>
  </div>
</template>
