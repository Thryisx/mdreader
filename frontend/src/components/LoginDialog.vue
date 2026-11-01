<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import LoginForm from '@/components/LoginForm.vue'
import type { UserVO } from '@/types/api'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string
  }>(),
  {
    title: '登录后保存',
    description: '登录成功后将自动把本地暂存内容同步到云端。',
  },
)

const emit = defineEmits<{
  close: []
  success: [user: UserVO]
}>()

const route = useRoute()
const router = useRouter()
const registerTarget = computed(() => ({
  name: 'register',
  query: { redirect: route.fullPath },
}))

function handleSuccess(user: UserVO) {
  if (user.mustChangePassword) {
    emit('close')
    void router.replace({
      name: 'change-password',
      query: { scope: 'user', redirect: route.fullPath },
    })
    return
  }

  emit('success', user)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="login-dialog">
      <div
        v-if="open"
        class="login-dialog"
        role="presentation"
        @mousedown.self="emit('close')"
      >
        <section
          class="login-dialog__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="login-dialog-title"
        >
          <button
            type="button"
            class="login-dialog__close"
            aria-label="关闭登录框"
            @click="emit('close')"
          >
            ×
          </button>

          <h2 id="login-dialog-title" class="login-dialog__title">{{ props.title }}</h2>
          <p class="login-dialog__desc">{{ props.description }}</p>

          <LoginForm role="USER" @success="handleSuccess" />

          <p class="login-dialog__hint">
            没有账号，
            <RouterLink class="login-dialog__register" :to="registerTarget">去注册~</RouterLink>
          </p>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.login-dialog {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 20px;
  background: color-mix(in srgb, #0f172a 38%, transparent);
  backdrop-filter: blur(3px);
}

.login-dialog__panel {
  position: relative;
  width: min(400px, 100%);
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.login-dialog__close {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.login-dialog__close:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.login-dialog__close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.login-dialog__eyebrow {
  margin: 0 0 6px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.login-dialog__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.login-dialog__desc {
  margin: 8px 0 20px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.login-dialog__hint {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.login-dialog__register {
  color: var(--accent-strong);
  font-weight: 600;
  text-decoration: none;
}

.login-dialog__register:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.login-dialog-enter-active,
.login-dialog-leave-active {
  transition: opacity 0.16s ease;
}

.login-dialog-enter-active .login-dialog__panel,
.login-dialog-leave-active .login-dialog__panel {
  transition: transform 0.16s ease;
}

.login-dialog-enter-from,
.login-dialog-leave-to {
  opacity: 0;
}

.login-dialog-enter-from .login-dialog__panel,
.login-dialog-leave-to .login-dialog__panel {
  transform: translateY(6px) scale(0.98);
}

@media (max-width: 480px) {
  .login-dialog__panel {
    padding: 24px 20px;
  }
}
</style>
