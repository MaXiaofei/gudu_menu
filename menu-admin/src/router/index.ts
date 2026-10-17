import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import { clearAuth, hasAdminSession } from '@/utils/auth-storage'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '仪表盘', group: 'ops' },
      },
      {
        path: 'library',
        name: 'Library',
        component: () => import('@/views/library/Index.vue'),
        meta: { title: '公共菜谱库', group: 'ops' },
      },
      {
        path: 'ingredient',
        name: 'Ingredient',
        component: () => import('@/views/ingredient/Index.vue'),
        meta: { title: '食材与营养', group: 'ops' },
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/Index.vue'),
        meta: { title: '意见反馈', group: 'ops' },
      },
      {
        path: 'tenants',
        name: 'Tenants',
        component: () => import('@/views/tenants/Index.vue'),
        meta: { title: '账号与家庭', group: 'ops' },
      },
      {
        path: 'ai-log',
        name: 'AiLog',
        component: () => import('@/views/ai-log/Index.vue'),
        meta: { title: 'AI 用量与限额', group: 'ops' },
      },
      {
        path: 'ai-gateway',
        name: 'AiGateway',
        component: () => import('@/views/ai-gateway/Index.vue'),
        meta: { title: '模型网关', group: 'ops' },
      },
      {
        path: 'ops',
        name: 'Ops',
        component: () => import('@/views/ops/Index.vue'),
        meta: { title: '系统健康', group: 'ops' },
      },
      {
        path: 'agent-trace',
        name: 'AgentTrace',
        component: () => import('@/views/agent-trace/Index.vue'),
        meta: { title: 'Agent 调用链', group: 'agent' },
      },
      {
        path: 'agent-eval',
        name: 'AgentEval',
        component: () => import('@/views/agent-eval/Index.vue'),
        meta: { title: 'Agent 评测', group: 'agent' },
      },
      {
        path: 'community',
        name: 'Community',
        component: () => import('@/views/community/Index.vue'),
        meta: { title: '社区审核', group: 'agent' },
      },
      {
        path: 'dish',
        name: 'Dish',
        component: () => import('@/views/dish/Index.vue'),
        meta: { title: '菜谱', group: 'data' },
      },
      {
        path: 'member',
        name: 'Member',
        component: () => import('@/views/member/Index.vue'),
        meta: { title: '家庭成员', group: 'data' },
      },
      {
        path: 'dict',
        name: 'Dict',
        component: () => import('@/views/dict/Index.vue'),
        meta: { title: '配置中心', group: 'data' },
      },
      {
        path: 'menu',
        name: 'Menu',
        component: () => import('@/views/menu/Index.vue'),
        // 后台定位为管理辅助，菜单/采购/周计划等日常操作入口隐藏（路由保留，直接访问 URL 仍可达）
        meta: { title: '食集', hidden: true },
      },
      {
        path: 'mealplan',
        name: 'MealPlan',
        component: () => import('@/views/mealplan/Index.vue'),
        meta: { title: '周计划', hidden: true },
      },
      {
        path: 'pantry',
        name: 'Pantry',
        component: () => import('@/views/pantry/Index.vue'),
        meta: { title: '库存', group: 'data' },
      },
      {
        path: 'shopping',
        name: 'Shopping',
        component: () => import('@/views/shopping/Index.vue'),
        meta: { title: '采购清单', hidden: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** 路由守卫：非公开页要求 token 且 isAdmin===true，否则清理残留登录态并回登录页。 */
export function authGuard(to: Pick<RouteLocationNormalized, 'path' | 'meta'>) {
  if (to.meta.public || to.path === '/login') return true
  if (hasAdminSession()) return true
  clearAuth()
  return { path: '/login' }
}

router.beforeEach(authGuard)

export default router
