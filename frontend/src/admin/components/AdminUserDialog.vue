<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { AdminUserDialogMode, AdminUserFormValue } from '@/admin/types'
import type { UserRole, UserStatus, UserVO } from '@/types/api'
import { PASSWORD_MESSAGE, isValidPassword } from '@/utils/password'

const props = withDefaults(
  defineProps<{
    open: boolean
    mode: AdminUserDialogMode
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
  save: [value: AdminUserFormValue]
}>()

const usernameInput = ref<HTMLInputElement | null>(null)
const username = ref('')
const password = ref('')
const nickname = ref('')
const email = ref('')
const role = ref<UserRole>('USER')
const status = ref<UserStatus>(1)
const error = ref('')

const usernamePattern = /^[A-Za-z0-9_]{3,50}$/
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const isEdit = () => props.mode === 'edit'

function resetForm() {
  const user = props.user

  username.value = user?.username ?? ''
  password.value = ''
  nickname.value = user?.nickname ?? ''
  email.value = user?.email ?? ''
  role.value = user?.role ?? 'USER'
  status.value = user?.status ?? 1
  error.value = ''
}

function close() {
  emit('close')
}

function submit() {
  if (props.saving) return

  const nextUsername = isEdit() ? (props.user?.username ?? '') : username.value.trim()
  const nextPassword = password.value
  const nextNickname = nickname.value.trim()
  const nextEmail = email.value.trim()

  if (!isEdit() && !usernamePattern.test(nextUsername)) {
    error.value = '用户名需为 3-50 位字母、数字或下划线'
    return
  }

  if (!isEdit() && !isValidPassword(nextPassword)) {
    error.value = PASSWORD_MESSAGE
    return
  }

  if (nextEmail && !emailPattern.test(nextEmail)) {
    error.value = '请输入有效的邮箱地址'
    return
  }

  error.value = ''
  emit('save', {
    username: nextUsername,
    password: nextPassword,
    nickname: nextNickname,
    email: nextEmail,
    role: role.value,
    status: status.value,
  })
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') close()
}

watch(
  () => [props.open, props.mode, props.user] as const,
  ([open]) => {
    document.body.style.overflow = open ? 'hidden' : ''

    if (!open) return

    resetForm()
    void nextTick(() => usernameInput.value?.focus())
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
    <Transition name="admin-user">
      <div v-if="open" class="admin-user" @click.self="close">
        <section
          class="admin-user__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="admin-user-title"
        >
          <header class="admin-user__head">
            <div>
              <p class="admin-user__eyebrow">{{ isEdit() ? '用户资料' : '新建账号' }}</p>
              <h2 id="admin-user-title" class="admin-user__title">
                {{ isEdit() ? '编辑用户' : '新增用户' }}
              </h2>
            </div>
            <button
              type="button"
              class="admin-user__close"
              aria-label="关闭用户编辑"
              :disabled="saving"
              @click="close"
            >
              ×
            </button>
          </header>

          <form class="admin-user__form" @submit.prevent="submit">
            <label class="admin-user__field">
              <span>用户名</span>
              <input
                ref="usernameInput"
                v-model="username"
                type="text"
                maxlength="50"
                placeholder="3-50 位字母、数字或下划线"
                autocomplete="username"
                :disabled="isEdit() || saving"
              />
            </label>

            <label v-if="!isEdit()" class="admin-user__field">
              <span>初始密码</span>
              <input
                v-model="password"
                type="password"
                maxlength="10"
                placeholder="10 位，8 数字 + 2 字母"
                autocomplete="new-password"
                :disabled="saving"
              />
            </label>

            <label class="admin-user__field">
              <span>昵称</span>
              <input
                v-model="nickname"
                type="text"
                maxlength="50"
                placeholder="可选"
                :disabled="saving"
              />
            </label>

            <label class="admin-user__field">
              <span>邮箱</span>
              <input
                v-model="email"
                type="email"
                maxlength="255"
                placeholder="可选，name@example.com"
                autocomplete="email"
                :disabled="saving"
              />
            </label>

            <div class="admin-user__grid">
              <label class="admin-user__field">
                <span>角色</span>
                <select v-model="role" :disabled="saving">
                  <option value="USER">普通用户</option>
                  <option value="ADMIN">管理员</option>
                </select>
              </label>

              <label class="admin-user__field">
                <span>状态</span>
                <select v-model="status" :disabled="saving">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </label>
            </div>

            <p v-if="error" class="admin-user__error" role="alert">{{ error }}</p>

            <div class="admin-user__actions">
              <button
                type="button"
                class="admin-user__button"
                :disabled="saving"
                @click="close"
              >
                取消
              </button>
              <button
                type="submit"
                class="admin-user__button admin-user__button--primary"
                :disabled="saving"
              >
                {{ saving ? '保存中…' : isEdit() ? '保存修改' : '创建用户' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.admin-user {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(24, 21, 15, 0.44);
  backdrop-filter: blur(3px);
}

.admin-user__panel {
  width: min(520px, 100%);
  max-height: min(88dvh, 720px);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.admin-user__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 14px;
}

.admin-user__eyebrow {
  margin: 0 0 4px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.admin-user__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
}

.admin-user__close {
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

.admin-user__close:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-user__form {
  display: grid;
  gap: 13px;
  padding: 0 22px 22px;
}

.admin-user__field {
  display: grid;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
  font-weight: 600;
}

.admin-user__field input,
.admin-user__field select {
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

.admin-user__field input:focus,
.admin-user__field select:focus {
  border-color: var(--accent-line);
  background: var(--surface);
}

.admin-user__field input:disabled,
.admin-user__field select:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.admin-user__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.admin-user__error {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
}

.admin-user__actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 4px;
}

.admin-user__button {
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

.admin-user__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.admin-user__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.admin-user__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.admin-user__button:disabled,
.admin-user__close:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-user-enter-active,
.admin-user-leave-active {
  transition: opacity 0.18s ease;
}

.admin-user-enter-active .admin-user__panel,
.admin-user-leave-active .admin-user__panel {
  transition: transform 0.18s ease;
}

.admin-user-enter-from,
.admin-user-leave-to {
  opacity: 0;
}

.admin-user-enter-from .admin-user__panel,
.admin-user-leave-to .admin-user__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
