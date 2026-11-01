<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ChangePasswordPayload } from '@/types/api'
import { PASSWORD_MESSAGE, isValidPassword } from '@/utils/password'

const props = withDefaults(
  defineProps<{
    open: boolean
    saving?: boolean
    closable?: boolean
  }>(),
  {
    saving: false,
    closable: true,
  },
)

const emit = defineEmits<{
  close: []
  save: [payload: ChangePasswordPayload]
}>()

const oldPasswordInput = ref<HTMLInputElement | null>(null)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')

function resetForm() {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  error.value = ''
}

function close() {
  if (!props.closable) return
  emit('close')
}

function submit() {
  if (props.saving) return

  if (!oldPassword.value) {
    error.value = '请输入当前密码'
    return
  }

  if (!isValidPassword(newPassword.value)) {
    error.value = PASSWORD_MESSAGE
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  if (newPassword.value === oldPassword.value) {
    error.value = '新密码不能与当前密码相同'
    return
  }

  error.value = ''
  emit('save', {
    oldPassword: oldPassword.value,
    newPassword: newPassword.value,
    confirmPassword: confirmPassword.value,
  })
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && props.closable && event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''

    if (!open) return

    resetForm()
    void nextTick(() => oldPasswordInput.value?.focus())
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
    <Transition name="change-password">
      <div v-if="open" class="change-password" @click.self="close">
        <section
          class="change-password__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="change-password-title"
        >
          <header class="change-password__head">
            <div>
              <p class="change-password__eyebrow">安全设置</p>
              <h2 id="change-password-title" class="change-password__title">修改密码</h2>
            </div>
            <button
              v-if="closable"
              type="button"
              class="change-password__close"
              aria-label="关闭修改密码"
              :disabled="saving"
              @click="close"
            >
              ×
            </button>
          </header>

          <form class="change-password__form" @submit.prevent="submit">
            <p class="change-password__desc">
              修改成功后当前登录状态会失效，需要使用新密码重新登录。
            </p>

            <label class="change-password__field">
              <span>当前密码</span>
              <input
                ref="oldPasswordInput"
                v-model="oldPassword"
                type="password"
                maxlength="64"
                placeholder="请输入当前密码"
                autocomplete="current-password"
                :disabled="saving"
              />
            </label>

            <label class="change-password__field">
              <span>新密码</span>
              <input
                v-model="newPassword"
                type="password"
                maxlength="10"
                placeholder="10 位，8 数字 + 2 字母"
                autocomplete="new-password"
                :disabled="saving"
              />
            </label>

            <label class="change-password__field">
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

            <p v-if="error" class="change-password__error" role="alert">{{ error }}</p>

            <div class="change-password__actions">
              <button
                v-if="closable"
                type="button"
                class="change-password__button"
                :disabled="saving"
                @click="close"
              >
                取消
              </button>
              <button
                type="submit"
                class="change-password__button change-password__button--primary"
                :disabled="saving"
              >
                {{ saving ? '修改中…' : '确认修改' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.change-password {
  position: fixed;
  inset: 0;
  z-index: 82;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(24, 21, 15, 0.44);
  backdrop-filter: blur(3px);
}

.change-password__panel {
  width: min(440px, 100%);
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.change-password__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 12px;
}

.change-password__eyebrow {
  margin: 0 0 4px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.change-password__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
}

.change-password__close {
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

.change-password__close:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.change-password__form {
  display: grid;
  gap: 13px;
  padding: 0 22px 22px;
}

.change-password__desc {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.change-password__field {
  display: grid;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
  font-weight: 600;
}

.change-password__field input {
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

.change-password__field input:focus {
  border-color: var(--accent-line);
  background: var(--surface);
}

.change-password__field input:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.change-password__error {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
}

.change-password__actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 4px;
}

.change-password__button {
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

.change-password__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.change-password__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.change-password__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.change-password__button:disabled,
.change-password__close:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.change-password-enter-active,
.change-password-leave-active {
  transition: opacity 0.18s ease;
}

.change-password-enter-active .change-password__panel,
.change-password-leave-active .change-password__panel {
  transition: transform 0.18s ease;
}

.change-password-enter-from,
.change-password-leave-to {
  opacity: 0;
}

.change-password-enter-from .change-password__panel,
.change-password-leave-to .change-password__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
