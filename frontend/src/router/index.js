import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录 - 星芽花园' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { public: true, title: '注册 - 星芽花园' },
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      { path: '', redirect: '/tasks' },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('../views/Tasks.vue'),
        meta: { title: '任务大厅 · 星芽花园' },
      },
      {
        path: 'garden',
        name: 'Garden',
        component: () => import('../views/Garden.vue'),
        meta: { title: '我的花园 · 星芽花园' },
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('../views/Report.vue'),
        meta: { title: '数据报告 · 星芽花园' },
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('../views/History.vue'),
        meta: { title: '专注历史 · 星芽花园' },
      },
      {
        path: 'encyclopedia',
        name: 'Encyclopedia',
        component: () => import('../views/Encyclopedia.vue'),
        meta: { title: '植物图鉴 · 星芽花园' },
      },
      {
        path: 'achievements',
        name: 'Achievements',
        component: () => import('../views/Achievements.vue'),
        meta: { title: '成就 · 星芽花园' },
      },
      {
        path: 'friends',
        name: 'Friends',
        component: () => import('../views/Friends.vue'),
        meta: { title: '好友花园 · 星芽花园' },
      },
      {
        path: 'rooms',
        name: 'Rooms',
        component: () => import('../views/Rooms.vue'),
        meta: { title: '协作房间 · 星芽花园' },
      },
      {
        path: 'annual',
        name: 'Annual',
        component: () => import('../views/Annual.vue'),
        meta: { title: '年度报告 · 星芽花园' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { title: '个人资料 · 星芽花园' },
      },
      {
        path: 'admin/eval',
        name: 'AdminEval',
        component: () => import('../views/AdminEval.vue'),
        meta: { title: '算法评估 · 星芽花园', requiresAdmin: true },
      },
      {
        path: 'admin/tasks',
        name: 'AdminTasks',
        component: () => import('../views/AdminTasks.vue'),
        meta: { title: '任务管理 · 星芽花园', requiresAdmin: true },
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('../views/AdminUsers.vue'),
        meta: { title: '用户管理 · 星芽花园', requiresAdmin: true },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/tasks' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  // 管理员页面守卫：非 ADMIN 角色直接送回任务大厅
  if (to.meta.requiresAdmin && auth.role !== 'ADMIN') {
    return { name: 'Tasks' }
  }
})

router.afterEach((to) => {
  if (to.meta.title) document.title = to.meta.title
})

export default router
