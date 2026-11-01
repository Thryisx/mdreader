<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { UserVO } from '@/types/api'
import { PASSWORD_MESSAGE, isValidPassword } from '@/utils/password'

const props = withDefaults(
  defineProps<{
    open: boolean
    user?: UserVO | null
    saving?: boolean
  }>(),
  {
    user: null,
    saving: false,
  },
)

const emit = defineEmits<{
  close: []
  save: [password: string]
}>()

const passwordInput = ref<HTMLInputElement | null>(null)
const password = ref('')
const confirmPassword = ref('')
const error = ref('')

function resetForm() {
  password.value = ''
  confirmPassword.value = ''
  error.value = ''
}

function close() {
  emit('close')
}

function submit() {
  if (props.saving) return

  if (!isValidPassword(password.value)) {
    error.value = PASSWORD_MESSAGE
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  error.value = ''
  emit('save', password.value)
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''

    if (!open) return

    resetForm()
    void nextTick(() => passwordInput.value?.focus())
  },
  { immediate: true },
)

onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="admin-password">
      <div v-if="open" class="admin-password" @click.self="close">
        <section
          class="admin-password__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="admin-password-title"
        >
          <header class="admin-password__head">
            <div>
              <p class="admin-password__eyebrow">安全操作</p>
              <h2 id="admin-password-title" class="admin-password__title">重置密码</h2>
            </div>
            <button
              type="button"
              class="admin-password__close"
              aria-label="关闭重置密码"
              :disabled="saving"
              @click="close"
            >
              ×
            </button>
          </header>

          <form class="admin-password__form" @submit.prevent="submit">
            <p class="admin-password__desc">
              正在为 <strong>{{ user?.username ?? '该用户' }}</strong> 设置新密码。
            </p>

            <label class="admin-password__field">
              <span>新密码</span>
              <input
                ref="passwordInput"
                v-model="password"
                type="password"
                maxlength="10"
                placeholder="10 位，8 数字 + 2 字母"
                autocomplete="new-password"
                :disabled="saving"
              />
            </label>

            <label class="admin-password__field">
              <span>确认新密码</span>
              <input
                v-model="confirmPassword"
                type="password"
                maxlength="10"
                placeholder="再次输入新密码"
                autocomplete="new-password"
                :disabled="saving"
              />
            </label>

            <p v-if="error" class="admin-password__error" role="alert">{{ error }}</p>

            <div class="admin-password__actions">
              <button
                type="button"
                class="admin-password__button"
                :disabled="saving"
                @click="close"
              >
                取消
              </button>
              <button
                type="submit"
                class="admin-password__button admin-password__button--primary"
                :disabled="saving"
              >
                {{ saving ? '重置中…' : '确认重置' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.admin-password {
  position: fixed;
  inset: 0;
  z-index: 82;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(24, 21, 15, 0.44);
  backdrop-filter: blur(3px);
}

.admin-password__panel {
  width: min(440px, 100%);
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.admin-password__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 12px;
}

.admin-password__eyebrow {
  margin: 0 0 4px;
  color: var(--warn);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.admin-password__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
}

.admin-password__close {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.admin-password__close:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-password__form {
  display: grid;
  gap: 13px;
  padding: 0 22px 22px;
}

.admin-password__desc {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.admin-password__field {
  display: grid;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
  font-weight: 600;
}

.admin-password__field input {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  background: var(--surface-sunken);
  color: var(--ink);
  font: inherit;
  font-weight: 400;
}

.admin-password__field input:focus {
  border-color: var(--accent-line);
  background: var(--surface);
}

.admin-password__field input:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.admin-password__error {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
}

.admin-password__actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 4px;
}

.admin-password__button {
  padding: 8px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted-strong);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.admin-password__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.admin-password__button--primary {
  border-color: var(--warn);
  background: var(--warn);
  color: #fff;
}

.admin-password__button--primary:hover {
  border-color: var(--danger);
  background: var(--danger);
}

.admin-password__button:disabled,
.admin-password__close:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-password-enter-active,
.admin-password-leave-active {
  transition: opacity 0.18s ease;
}

.admin-password-enter-active .admin-password__panel,
.admin-password-leave-active .admin-password__panel {
  transition: transform 0.18s ease;
}

.admin-password-enter-from,
.admin-password-leave-to {
  opacity: 0;
}

.admin-password-enter-from .admin-password__panel,
.admin-password-leave-to .admin-password__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
