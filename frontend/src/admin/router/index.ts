import type { RouteRecordRaw } from 'vue-router'

const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/admin/views/AdminLoginView.vue'),
    meta: { title: '管理员登录' },
  },
  {
    path: '/admin',
    component: () => import('@/admin/layouts/AdminShell.vue'),
    children: [
      {
        path: '',
        redirect: { name: 'admin-users' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/admin/views/AdminUsersView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'files',
        name: 'admin-files',
        component: () => import('@/admin/views/AdminFilesView.vue'),
        meta: { title: '文件管理' },
      },
    ],
  },
]

export default adminRoutes
