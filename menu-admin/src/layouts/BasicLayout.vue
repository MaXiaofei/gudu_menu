<script setup lang="ts">
import { computed, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Brush, ArrowDown, SwitchButton, Setting, Apple, Food, DataBoard, Notebook,
  ChatDotRound, House, MagicStick, Odometer, Menu as MenuIcon, Connection, Share, Histogram, Opportunity,
  UserFilled, Warning, Tickets,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { useBadgeStore } from '@/store/badges'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const badgeStore = useBadgeStore()

type MenuGroup = 'ops' | 'agent' | 'data'

interface MenuEntry {
  path: string
  title: string
  icon: Component
  group: MenuGroup
  badge?: number
}

// icon 与路由的视觉映射，路径 -> 图标组件（按需引入，不再全局注册全部图标）
const iconByPath: Record<string, Component> = {
  '/home': DataBoard,
  '/library': Notebook,
  '/ingredient': Apple,
  '/feedback': ChatDotRound,
  '/tenants': House,
  '/deactivation': UserFilled,
  '/content-review': Warning,
  '/oplog': Tickets,
  '/ai-log': MagicStick,
  '/ops': Odometer,
  '/ai-gateway': Connection,
  '/agent-trace': Share,
  '/agent-eval': Histogram,
  '/community': Opportunity,
  '/dish': Food,
  '/dict': Setting,
}

// 菜单由路由表驱动：取 layout 子路由，跳过 meta.hidden（日常操作页入口隐藏）；按 meta.group 分「运营」「Agent 与社区」「内容与基础数据」
const menus = computed<MenuEntry[]>(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === '/')
  const children = (layoutRoute?.children ?? []) as Array<{
    path: string
    meta?: { title?: string; hidden?: boolean; group?: MenuGroup; superAdmin?: boolean }
  }>
  return children
    .filter((c) => !c.meta?.hidden)
    // 超管专属页（后端 @RequireAdmin）对平台运营隐藏
    .filter((c) => !c.meta?.superAdmin || authStore.isSuperAdmin)
    .map<MenuEntry>((c) => ({
      path: `/${c.path}`,
      title: c.meta?.title ?? c.path,
      icon: iconByPath[`/${c.path}`] ?? MenuIcon,
      group: c.meta?.group ?? 'data',
      badge: c.path === 'feedback' ? badgeStore.feedbackOpen : c.path === 'community' ? badgeStore.communityPending : undefined,
    }))
})
const opsMenus = computed(() => menus.value.filter((m) => m.group === 'ops'))
const agentMenus = computed(() => menus.value.filter((m) => m.group === 'agent'))
const dataMenus = computed(() => menus.value.filter((m) => m.group === 'data'))

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function onLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

// 仪表盘自己会拉 /ops/stats 并回填角标，其他页进入时补拉一次
onMounted(() => {
  if (route.path !== '/home') badgeStore.refresh()
})
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <router-link to="/home" class="brand">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 12h18a9 9 0 0 1-18 0z" />
          <path d="M8 8c0-1.5 1-2 1-3.5M12 8c0-1.5 1-2 1-3.5M16 8c0-1.5 1-2 1-3.5" />
        </svg>
        小食单后台
      </router-link>

      <nav class="nav" aria-label="后台导航">
        <router-link
          v-for="m in opsMenus"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.path) }"
          :aria-current="isActive(m.path) ? 'page' : undefined"
        >
          <el-icon class="ico"><component :is="m.icon" /></el-icon>
          <span class="lbl">{{ m.title }}</span>
          <span v-if="m.badge" class="badge" :data-testid="`badge-${m.path.slice(1)}`">{{ m.badge > 99 ? '99+' : m.badge }}</span>
        </router-link>

        <span class="group">Agent 与社区</span>
        <router-link
          v-for="m in agentMenus"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.path) }"
          :aria-current="isActive(m.path) ? 'page' : undefined"
        >
          <el-icon class="ico"><component :is="m.icon" /></el-icon>
          <span class="lbl">{{ m.title }}</span>
          <span v-if="m.badge" class="badge" :data-testid="`badge-${m.path.slice(1)}`">{{ m.badge > 99 ? '99+' : m.badge }}</span>
        </router-link>

        <span class="group">内容与基础数据</span>
        <router-link
          v-for="m in dataMenus"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.path) }"
          :aria-current="isActive(m.path) ? 'page' : undefined"
        >
          <el-icon class="ico"><component :is="m.icon" /></el-icon>
          <span class="lbl">{{ m.title }}</span>
        </router-link>
      </nav>

      <div class="foot">
        <span class="me-ava" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z" /><path d="m9 12 2 2 4-4" /></svg>
        </span>
        <span class="me-txt">
          <b>{{ authStore.nickname || '管理员' }}</b>
          <span>{{ authStore.roleLabel }}</span>
        </span>
        <el-button class="logout" text :icon="SwitchButton" aria-label="退出登录" title="退出登录" @click="onLogout" />
      </div>
    </aside>

    <section class="main">
      <header class="topbar">
        <div class="crumb">小食单后台 <span class="sep">/</span> <b>{{ (route.meta.title as string) || '仪表盘' }}</b></div>
        <div class="spacer"></div>

        <el-dropdown trigger="click" @command="themeStore.apply">
          <el-button class="theme-btn">
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
  width: 232px;
  flex-shrink: 0;
  padding: 22px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--yh-sidebar-text);
  background: linear-gradient(180deg, var(--yh-sidebar) 0%, color-mix(in srgb, var(--yh-sidebar) 88%, black) 100%);
  overflow-y: auto;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px 18px;
  font-size: 17px;
  font-weight: 900;
  color: var(--yh-sidebar-text);
  text-decoration: none;
  letter-spacing: 0.5px;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--yh-sidebar-text);
  font-size: 15px;
  text-decoration: none;
  transition: background 0.15s;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
}
.nav-item.active {
  background: rgba(255, 255, 255, 0.22);
  font-weight: 700;
}
.nav-item .ico {
  font-size: 18px;
  opacity: 0.92;
}
.nav-item .lbl {
  flex: 1;
}
.badge {
  min-width: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--yh-panel);
  color: var(--yh-error-text);
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}
.group {
  margin-top: 16px;
  padding: 10px 12px 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--yh-sidebar-text);
  opacity: 0.8;
}
.foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 13px;
}
.me-ava {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: var(--yh-title);
  color: var(--yh-panel);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.me-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.me-txt b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.me-txt span {
  font-size: 12px;
  opacity: 0.9;
}
.logout {
  color: var(--yh-sidebar-text) !important;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topbar {
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 32px;
  gap: 10px;
  border-bottom: 1px solid var(--yh-border);
  background: var(--yh-panel);
}
.crumb {
  font-size: 13px;
  color: var(--yh-body);
}
.crumb b {
  color: var(--yh-title);
}
.crumb .sep {
  margin: 0 4px;
  opacity: 0.6;
}
.spacer {
  flex: 1;
}
.theme-btn {
  border-radius: 999px;
  font-size: 13px;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 24px 32px 40px;
}
</style>
