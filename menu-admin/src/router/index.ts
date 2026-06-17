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
        meta: { title: '菜单' },
      },
      {
        path: 'backup',
        name: 'Backup',
        component: () => import('@/views/backup/Index.vue'),
        meta: { title: '数据备份' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('yanhuo-token')
  if (!token && !to.meta.public && to.path !== '/login') {
    return { path: '/login' }
  }
  return true
})

export default router
