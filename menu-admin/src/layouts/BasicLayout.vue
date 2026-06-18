<script setup lang="ts">
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

const menus: MenuEntry[] = [
  { path: '/dict', title: '配置中心', icon: 'Setting' },
  { path: '/member', title: '家庭成员', icon: 'User' },
  { path: '/ingredient', title: '食材库', icon: 'Apple' },
  { path: '/dish', title: '菜品', icon: 'Food' },
  { path: '/menu', title: '菜单', icon: 'List' },
  { path: '/mealplan', title: '周计划', icon: 'Calendar' },
  { path: '/pantry', title: '食材库存', icon: 'Box' },
  { path: '/backup', title: '数据备份', icon: 'FolderOpened' },
]

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
        <span class="dot"></span>烟火小食单
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
  color: #fff;
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
  color: #cdbfb1;
  height: 44px;
}
.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
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
  padding: 18px;
}
</style>
