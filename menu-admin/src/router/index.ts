import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import { clearAuth, hasAdminSession, isSuperAdminStored } from '@/utils/auth-storage'

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
        path: 'deactivation',
        name: 'Deactivation',
        component: () => import('@/views/deactivation/Index.vue'),
        meta: { title: '注销处理', group: 'ops' },
      },
      {
        path: 'content-review',
        name: 'ContentReview',
        component: () => import('@/views/content-review/Index.vue'),
        meta: { title: '内容安全复核', group: 'ops' },
      },
      {
        path: 'oplog',
        name: 'OpLog',
        component: () => import('@/views/oplog/Index.vue'),
        meta: { title: '操作日志', group: 'ops' },
      },
      {
        path: 'ai-log',
        name: 'AiLog',
        component: () => import('@/views/ai-log/Index.vue'),
        meta: { title: 'AI 用量与限额', group: 'ops', superAdmin: true },
      },
      {
        path: 'ai-gateway',
        name: 'AiGateway',
        component: () => import('@/views/ai-gateway/Index.vue'),
        meta: { title: '模型网关', group: 'ops', superAdmin: true },
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
        meta: { title: 'Agent 调用链', group: 'agent', superAdmin: true },
      },
      {
        path: 'agent-eval',
        name: 'AgentEval',
        component: () => import('@/views/agent-eval/Index.vue'),
        meta: { title: 'Agent 评测', group: 'agent', superAdmin: true },
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
        path: 'dict',
        name: 'Dict',
        component: () => import('@/views/dict/Index.vue'),
        meta: { title: '配置中心', group: 'data', superAdmin: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 路由守卫：非公开页要求 token 且为平台运营 / 超级管理员（multi-tenant-family F.3），否则清理残留登录态并回登录页；
 * 超管专属页（meta.superAdmin，后端 @RequireAdmin）对平台运营回到首页。
 * 家庭私有数据页（库存、家庭成员、食集、采购、周计划）已下线（mini-release-compliance F.5）。
 */
export function authGuard(to: Pick<RouteLocationNormalized, 'path' | 'meta'>) {
  if (to.meta.public || to.path === '/login') return true
  if (!hasAdminSession()) {
    clearAuth()
    return { path: '/login' }
  }
  if (to.meta.superAdmin && !isSuperAdminStored()) return { path: '/home' }
  return true
}

router.beforeEach(authGuard)

export default router
