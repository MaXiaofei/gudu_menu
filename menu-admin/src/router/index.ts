import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

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
        meta: { title: '首页' },
      },
      {
        path: 'dict',
        name: 'Dict',
        component: () => import('@/views/dict/Index.vue'),
        meta: { title: '配置中心' },
      },
      {
        path: 'member',
        name: 'Member',
        component: () => import('@/views/member/Index.vue'),
        meta: { title: '家庭成员' },
      },
      {
        path: 'ingredient',
        name: 'Ingredient',
        component: () => import('@/views/ingredient/Index.vue'),
        meta: { title: '食材库' },
      },
      {
        path: 'dish',
        name: 'Dish',
        component: () => import('@/views/dish/Index.vue'),
        meta: { title: '菜品' },
      },
      {
        path: 'menu',
        name: 'Menu',
        component: () => import('@/views/menu/Index.vue'),
        // 后台定位为管理辅助，菜单/采购/周计划等日常操作入口隐藏（路由保留，直接访问 URL 仍可达）
        meta: { title: '菜单', hidden: true },
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
        meta: { title: '食材库存' },
      },
      {
        path: 'shopping',
        name: 'Shopping',
        component: () => import('@/views/shopping/Index.vue'),
        meta: { title: '采购清单', hidden: true },
      },
      {
        path: 'ai-log',
        name: 'AiLog',
        component: () => import('@/views/ai-log/Index.vue'),
        meta: { title: 'AI 用量' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('gudu-token')
  if (!token && !to.meta.public && to.path !== '/login') {
    return { path: '/login' }
  }
  return true
})

export default router
