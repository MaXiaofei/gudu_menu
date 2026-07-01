<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Brush, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

interface MenuEntry {
  path: string
  title: string
  icon: string
}

// icon 与路由的视觉映射，路径 -> 图标组件名
const iconByPath: Record<string, string> = {
  '/dict': 'Setting',
  '/member': 'User',
  '/ingredient': 'Apple',
  '/dish': 'Food',
  '/menu': 'List',
  '/mealplan': 'Calendar',
  '/pantry': 'Box',
  '/shopping': 'ShoppingCart',
  '/backup': 'FolderOpened',
}

// 菜单由路由表驱动：取 layout 子路由，跳过 meta.hidden（日常操作页入口隐藏），home 不进菜单
const menus = computed<MenuEntry[]>(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === '/')
  const children = (layoutRoute?.children ?? []) as Array<{
    path: string
    meta?: { title?: string; hidden?: boolean }
  }>
  return children
    .filter((c) => c.path !== 'home' && !c.meta?.hidden)
    .map<MenuEntry>((c) => ({
      path: `/${c.path}`,
      title: c.meta?.title ?? c.path,
      icon: iconByPath[`/${c.path}`] ?? 'Menu',
    }))
})

async function onLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">
        <span class="dot"></span>咕嘟小食单
      </div>
      <el-menu class="side-menu" router :default-active="route.path">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="main">
      <header class="topbar">
        <div class="crumb">{{ (route.meta.title as string) || '工作台' }}</div>
        <div class="spacer"></div>

        <el-dropdown trigger="click" @command="themeStore.apply">
          <el-button text style="font-size: 13px">
            <el-icon><Brush /></el-icon>
            <span style="margin: 0 3px 0 4px">主题</span>
            <span class="sw-dot" :style="{ background: themeStore.currentTheme().primary }"></span>
            {{ themeStore.currentTheme().name }}
            <el-icon style="margin-left: 2px"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in themeStore.themes"
                :key="t.key"
                :command="t.key"
              >
                <span class="sw-dot" :style="{ background: t.primary }"></span>
                {{ t.name }}
                <span
                  v-if="t.key === themeStore.current"
                  style="color: var(--el-color-primary); margin-left: 8px; font-size: 12px"
                >✓ 当前</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button text :icon="SwitchButton" @click="onLogout">退出</el-button>
      </header>

      <div class="content">
        <router-view />
      </div>
    </section>
  </div>
</template>

<style scoped>
.layout {
  height: 100%;
  display: flex;
  background: var(--yh-bg);
}
.sidebar {
  width: 210px;
  background: var(--yh-sidebar);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar .logo {
  padding: 18px 18px 16px;
  font-size: 16px;
  font-weight: 700;
  color: var(--yh-sidebar-text);
  display: flex;
  align-items: center;
  gap: 9px;
  letter-spacing: 0.5px;
}
.sidebar .logo .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--el-color-primary);
  transition: background 0.2s;
}
.side-menu {
  border-right: none;
  background: transparent;
}
.side-menu :deep(.el-menu-item) {
  color: var(--yh-sidebar-text);
  height: 44px;
}
.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--yh-sidebar-active-bg);
  color: #fff;
  font-weight: 700;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topbar {
  height: 54px;
  background: var(--yh-panel, #fff);
  border-bottom: 1px solid #f0e8de;
  display: flex;
  align-items: center;
  padding: 0 18px;
  gap: 14px;
}
.topbar .crumb {
  font-size: 14px;
  color: #8c8073;
}
.topbar .spacer {
  flex: 1;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 12px;
}
</style>
