import { createRouter, createWebHistory } from 'vue-router'

import adminRoutes from '@/admin/router'
import { useUserAuth } from '@/composables/useUserAuth'
import NotFoundView from '@/views/NotFoundView.vue'
import ProfileView from '@/views/ProfileView.vue'
import SettingsView from '@/views/SettingsView.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'articles',
      component: () => import('@/views/ArticleListView.vue'),
      meta: { title: '公开文章' },
    },
    {
      path: '/read/:id',
      name: 'read',
      component: () => import('@/views/ArticleReaderView.vue'),
      meta: { title: '阅读文章' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/admin/views/AdminLoginView.vue'),
      props: { role: 'USER' },
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/ChangePasswordView.vue'),
      meta: { title: '修改密码' },
    },
    ...adminRoutes,
    {
      path: '/workspace',
      name: 'workspace',
      component: WorkspaceView,
    },
    {
      path: '/books/:id/chapters/:chapterId',
      redirect: (to) => ({ name: 'read', params: { id: String(to.params.chapterId) } }),
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView,
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.name === 'login' || to.name === 'register') {
    const { ensureSession } = useUserAuth()
    const currentUser = await ensureSession()

    if (!currentUser) return true

    if (currentUser.mustChangePassword) {
      return { name: 'change-password', query: { scope: 'user' } }
    }

    const redirect = to.query.redirect
    if (
      typeof redirect === 'string' &&
      redirect.startsWith('/') &&
      !redirect.startsWith('//') &&
      redirect !== '/login' &&
      redirect !== '/register' &&
      !redirect.startsWith('/admin')
    ) {
      return redirect
    }

    return '/'
  }

  if (to.name === 'change-password') {
    const scope = to.query.scope === 'admin' ? 'admin' : 'user'
    const currentUser =
      scope === 'admin'
        ? await (await import('@/admin/composables/useAdminAuth')).useAdminAuth().ensureSession()
        : await useUserAuth().ensureSession()

    if (!currentUser) {
      return {
        name: scope === 'admin' ? 'admin-login' : 'login',
        query: { redirect: to.fullPath },
      }
    }

    if (!currentUser.mustChangePassword) {
      return scope === 'admin' ? { name: 'admin-users' } : '/'
    }

    return true
  }

  if (!to.path.startsWith('/admin')) return true

  const { useAdminAuth } = await import('@/admin/composables/useAdminAuth')
  const { ensureSession } = useAdminAuth()
  const currentUser = await ensureSession()

  if (to.name === 'admin-login') {
    if (!currentUser) return true
    if (currentUser.mustChangePassword) {
      return { name: 'change-password', query: { scope: 'admin' } }
    }
    return { name: 'admin-users' }
  }

  if (!currentUser) {
    return {
      name: 'admin-login',
      query: { redirect: to.fullPath },
    }
  }

  if (currentUser.mustChangePassword) {
    return { name: 'change-password', query: { scope: 'admin' } }
  }

  return true
})

export default router
