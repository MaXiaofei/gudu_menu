<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 主题外观：列出 tokens.json 注册的全部皮肤（持久化，全站即时生效） -->
  <view class="page">
    <ui-back-header title="主题外观" subtitle="切换后全站即时生效，下次打开保持" />

    <view class="body">
      <view
        v-for="opt in options"
        :key="opt.name"
        class="tcard"
        :class="{ on: current === opt.name }"
        :data-testid="`theme-${opt.name}`"
        @click="pick(opt.name)"
      >
        <!-- 色板预览（色值取自主题定义，非装饰硬编码） -->
        <view class="swatches">
          <view
            v-for="c in swatches(opt.name)"
            :key="c"
            class="swatch"
            :style="{ background: c }"
          />
        </view>
        <view class="tmain">
          <text class="tname">{{ opt.label }}</text>
          <text class="tdesc">{{ opt.desc }}</text>
        </view>
        <view class="radio" :class="{ on: current === opt.name }">
          <view v-if="current === opt.name" class="radio-dot" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { THEME_OPTIONS, THEMES, themeState, setTheme, type ThemeName } from '@/utils/theme'

const options = THEME_OPTIONS
const current = computed(() => themeState.name)

function swatches(name: ThemeName): string[] {
  const p = THEMES[name]
  return [p.primary, p.primarySoft, p.secondary, p.title]
}

function pick(name: ThemeName) {
  if (name === themeState.name) return
  setTheme(name)
  uni.showToast({ title: `已切换为${THEME_OPTIONS.find((o) => o.name === name)?.label}`, icon: 'none' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
}
.body {
  padding: var(--sp-8) var(--sp-16) var(--sp-24);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.tcard {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: 14px;
}
.tcard.on {
  border: 2px solid var(--primary);
  padding: 13px;
}
.swatches {
  display: flex;
  border-radius: var(--r-sm);
  overflow: hidden;
  flex-shrink: 0;
}
.swatch {
  width: 14px;
  height: 40px;
}
.tmain {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tname {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.tdesc {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.radio {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.radio.on {
  border-color: var(--primary);
}
.radio-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary);
}
</style>
