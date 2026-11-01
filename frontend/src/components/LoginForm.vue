<script setup lang="ts">
import { ref } from 'vue'

import { useAdminAuth } from '@/admin/composables/useAdminAuth'
import { useUserAuth } from '@/composables/useUserAuth'
import type { UserVO } from '@/types/api'

const props = defineProps<{
  role: 'ADMIN' | 'USER'
}>()

const emit = defineEmits<{
  success: [user: UserVO]
}>()

const auth = props.role === 'ADMIN' ? useAdminAuth() : useUserAuth()
const { login, loading } = auth

const username = ref('')
const password = ref('')
const error = ref('')

async function handleSubmit() {
  error.value = ''

  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  try {
    const currentUser = await login({
      username: username.value.trim(),
      password: password.value,
    })
    emit('success', currentUser)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败，请稍后重试'
  }
}
</script>

<template>
  <form class="login-form" @submit.prevent="handleSubmit">
    <label class="login-form__field">
      <span>用户名</span>
      <input
        v-model="username"
        type="text"
        name="username"
        autocomplete="username"
        :placeholder="props.role === 'ADMIN' ? '请输入管理员用户名' : '请输入用户名'"
        :disabled="loading"
        autofocus
      />
    </label>

    <label class="login-form__field">
      <span>密码</span>
      <input
        v-model="password"
        type="password"
        name="password"
        autocomplete="current-password"
        placeholder="请输入密码"
        :disabled="loading"
      />
    </label>

    <p v-if="error" class="login-form__error" role="alert">{{ error }}</p>

    <button class="login-form__submit" type="submit" :disabled="loading">
      {{ loading ? '登录中…' : '登录' }}
    </button>
  </form>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login-form__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
}

.login-form__field input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  background: var(--surface-sunken);
  color: var(--ink);
  font-size: 14px;
}

.login-form__field input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent);
}

.login-form__field input::placeholder {
  color: var(--muted);
}

.login-form__error {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid var(--danger-line);
  border-radius: 7px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12px;
  line-height: 1.5;
}

.login-form__submit {
  height: 40px;
  margin-top: 2px;
  border: 0;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
}

.login-form__submit:hover:not(:disabled) {
  background: var(--accent-strong);
}

.login-form__submit:disabled {
  cursor: progress;
  opacity: 0.7;
}
</style>
