<template>
  <!-- 线性图标（stroke 2 round；scripts/gen-icons.cjs 生成 static/icons/<name>[-w|-p].png）。
       tone：dark 标题色 / white 白 / primary 主色深。 -->
  <image class="uic" :src="src" mode="aspectFit" :style="{ width: size + 'px', height: size + 'px' }" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: number; tone?: 'dark' | 'white' | 'primary' }>(), {
  size: 20,
  tone: 'dark',
})
const src = computed(() => `/static/icons/${props.name}${props.tone === 'white' ? '-w' : props.tone === 'primary' ? '-p' : ''}.png`)
</script>

<style scoped>
.uic {
  flex-shrink: 0;
  display: block;
}
</style>
