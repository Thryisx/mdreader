<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import LoginForm from '@/components/LoginForm.vue'
import type { UserVO } from '@/types/api'

const props = withDefaults(
  defineProps<{
    role?: 'ADMIN' | 'USER'
  }>(),
  { role: 'ADMIN' },
)

const route = useRoute()
const router = useRouter()
const isAdmin = computed(() => props.role === 'ADMIN')
const registerTarget = computed(() => {
  const redirect = route.query.redirect
  return {
    name: 'register',
    query: typeof redirect === 'string' ? { redirect } : {},
  }
})

function resolveRedirect() {
  const redirect = route.query.redirect

  if (isAdmin.value) {
    if (
      typeof redirect === 'string' &&
      redirect.startsWith('/admin') &&
      redirect !== '/admin/login'
    ) {
      return redirect
    }

    return '/admin/users'
  }

  if (
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('//') &&
    redirect !== '/login' &&
    !redirect.startsWith('/admin')
  ) {
    return redirect
  }

  return '/'
}

async function handleSuccess(user: UserVO) {
  if (user.mustChangePassword) {
    await router.replace({
      name: 'change-password',
      query: {
        scope: isAdmin.value ? 'admin' : 'user',
        redirect: resolveRedirect(),
      },
    })
    return
  }

  await router.replace(resolveRedirect())
}

onMounted(() => {
  document.title = isAdmin.value ? '管理员登录 · MD Reader 管理后台' : '登录 · MD Reader'
})

onBeforeUnmount(() => {
  document.title = 'MD Reader'
})
</script>

<template>
  <main class="admin-login">
    <div class="admin-login__brand">
      <img class="admin-login__mark" src="/favicon.svg" alt="" aria-hidden="true" />
      <span>
        <strong>MD Reader</strong>
        <small>{{ isAdmin ? 'Admin Console' : 'Reader Account' }}</small>
      </span>
    </div>

    <section class="admin-login__card">
      <p class="admin-login__eyebrow">{{ isAdmin ? 'Admin Console' : 'Reader Account' }}</p>
      <h1 class="admin-login__title">{{ isAdmin ? '管理员登录' : '用户登录' }}</h1>
      <p class="admin-login__desc">
        <template v-if="isAdmin">
          登录后可查看用户与文件元数据。仅 <code>ADMIN</code> 角色可进入管理后台。
        </template>
        <template v-else>
          登录后可保存 Markdown 到云端，并在设备间同步。仅 <code>USER</code> 角色可登录阅读器。
        </template>
      </p>

      <LoginForm :role="props.role" @success="handleSuccess" />

      <p class="admin-login__hint">
        <template v-if="isAdmin">
          首次登录必须修改初始密码。
        </template>
        <template v-else>
          管理员账号请使用管理后台入口登录；还没有账号？
          <RouterLink :to="registerTarget">立即注册</RouterLink>
        </template>
      </p>
    </section>

    <RouterLink class="admin-login__back" to="/">← 返回阅读器</RouterLink>
  </main>
</template>

<style scoped>
.admin-login {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px 20px;
  background: var(--bg);
}

.admin-login__brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: var(--ink);
}

.admin-login__mark {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.admin-login__brand span:last-child {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.admin-login__brand strong {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
}

.admin-login__brand small {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.admin-login__card {
  width: min(420px, 100%);
  padding: 30px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.admin-login__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.admin-login__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.admin-login__desc {
  margin: 10px 0 24px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.admin-login__desc code {
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.admin-login__hint {
  margin: 18px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.admin-login__hint a {
  color: var(--accent-strong);
  text-decoration: none;
}

.admin-login__back {
  color: var(--muted);
  font-size: 12px;
  text-decoration: none;
}

.admin-login__back:hover {
  color: var(--accent-strong);
}

@media (max-width: 480px) {
  .admin-login__card {
    padding: 24px 20px;
  }
}
</style>
