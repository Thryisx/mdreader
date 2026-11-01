<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchCaptcha } from '@/api/user'
import { useUserAuth } from '@/composables/useUserAuth'
import type { CaptchaVO } from '@/types/api'
import { PASSWORD_MESSAGE, isValidPassword } from '@/utils/password'

const route = useRoute()
const router = useRouter()
const { register, loading } = useUserAuth()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
  nickname: '',
  email: '',
})

const captcha = ref<CaptchaVO | null>(null)
const captchaLoading = ref(false)
const error = ref('')

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,50}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const loginTarget = computed(() => {
  const redirect = route.query.redirect
  return {
    name: 'login',
    query: typeof redirect === 'string' ? { redirect } : {},
  }
})

function resolveRedirect() {
  const redirect = route.query.redirect

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

function validate() {
  const username = form.username.trim()
  const nickname = form.nickname.trim()
  const email = form.email.trim()

  if (!USERNAME_PATTERN.test(username)) {
    return '用户名必须为3-50位字母、数字或下划线'
  }
  if (!isValidPassword(form.password)) {
    return PASSWORD_MESSAGE
  }
  if (form.confirmPassword !== form.password) {
    return '两次输入的密码不一致'
  }
  if (!form.captchaCode.trim()) {
    return '请输入验证码'
  }
  if (nickname.length > 50) {
    return '昵称不能超过50个字符'
  }
  if (email && !EMAIL_PATTERN.test(email)) {
    return '邮箱格式不正确'
  }

  return ''
}

async function loadCaptcha() {
  captchaLoading.value = true

  try {
    captcha.value = await fetchCaptcha()
    form.captchaCode = ''
  } catch (cause) {
    captcha.value = null
    error.value = cause instanceof Error ? cause.message : '验证码加载失败，请刷新重试'
  } finally {
    captchaLoading.value = false
  }
}

async function handleSubmit() {
  error.value = ''
  const message = validate()

  if (message) {
    error.value = message
    return
  }
  if (!captcha.value) {
    error.value = '验证码未加载，请刷新后重试'
    return
  }

  try {
    await register({
      username: form.username.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
      captchaId: captcha.value.captchaId,
      captchaCode: form.captchaCode.trim(),
      nickname: form.nickname.trim() || undefined,
      email: form.email.trim() || undefined,
    })
    await router.replace(resolveRedirect())
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败，请稍后重试'
    await loadCaptcha()
  }
}

onMounted(() => {
  document.title = '注册 · MD Reader'
  void loadCaptcha()
})

onBeforeUnmount(() => {
  document.title = 'MD Reader'
})
</script>

<template>
  <main class="register">
    <div class="register__brand">
      <img class="register__mark" src="/favicon.svg" alt="" aria-hidden="true" />
      <span>
        <strong>MD Reader</strong>
      </span>
    </div>

    <section class="register__card">
      <h1 class="register__title">注册账号</h1>  

      <form class="register__form" @submit.prevent="handleSubmit">
        <label class="register__field">
          <span>用户名</span>
          <input
            v-model="form.username"
            type="text"
            name="username"
            maxlength="50"
            autocomplete="username"
            placeholder="3-50 位字母、数字或下划线"
            :disabled="loading"
          />
        </label>
        <p class="register__captcha-hint">使用用户名注册阅读器账号。</p>

        <label class="register__field">
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            name="password"
            maxlength="10"
            autocomplete="new-password"
            placeholder="8 位数字 + 2 位英文字母"
            :disabled="loading"
          />
        </label>
        <p class="register__captcha-hint">密码需为 10 位，包含 8 位数字和 2 位英文字母，顺序不限。</p>

        <label class="register__field">
          <span>确认密码</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            name="confirmPassword"
            maxlength="10"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            :disabled="loading"
          />
        </label>

        <div class="register__captcha">
          <label class="register__field">
            <span>验证码</span>
            <input
              v-model="form.captchaCode"
              type="text"
              name="captchaCode"
              maxlength="4"
              autocomplete="off"
              placeholder="请输入图中字符"
              :disabled="loading"
            />
          </label>

          <button
            class="register__captcha-image"
            type="button"
            :disabled="captchaLoading || loading"
            aria-label="刷新验证码"
            title="点击刷新验证码"
            @click="loadCaptcha"
          >
            <img v-if="captcha" :src="captcha.image" alt="图形验证码" />
            <span v-else>{{ captchaLoading ? '加载中…' : '点击刷新' }}</span>
          </button>
        </div>

        <p class="register__captcha-hint">
          看不清？
          <button type="button" :disabled="captchaLoading || loading" @click="loadCaptcha">
            换一张
          </button>
        </p>

        <div class="register__optional">
          <label class="register__field">
            <span>昵称（可选）</span>
            <input
              v-model="form.nickname"
              type="text"
              name="nickname"
              maxlength="50"
              autocomplete="nickname"
              placeholder="不填则使用用户名"
              :disabled="loading"
            />
          </label>

          <label class="register__field">
            <span>邮箱（可选）</span>
            <input
              v-model="form.email"
              type="email"
              name="email"
              maxlength="255"
              autocomplete="email"
              placeholder="用于后续找回密码"
              :disabled="loading"
            />
          </label>
        </div>

        <p v-if="error" class="register__error" role="alert">{{ error }}</p>

        <button class="register__submit" type="submit" :disabled="loading || captchaLoading">
          {{ loading ? '注册中…' : '注册并登录' }}
        </button>
      </form>

      <p class="register__hint">
        已有账号？
        <RouterLink :to="loginTarget">返回登录</RouterLink>
      </p>
    </section>

    <RouterLink class="register__back" to="/">← 返回阅读器</RouterLink>
  </main>
</template>

<style scoped>
.register {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px 20px;
  background: var(--bg);
}

.register__brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: var(--ink);
}

.register__mark {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.register__brand span:last-child {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.register__brand strong {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
}

.register__brand small {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.register__card {
  width: min(440px, 100%);
  padding: 30px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.register__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.register__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.register__desc {
  margin: 10px 0 22px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.register__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.register__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
}

.register__field input {
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

.register__field input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent);
}

.register__field input::placeholder {
  color: var(--muted);
}

.register__captcha {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 10px;
  align-items: end;
}

.register__captcha-image {
  display: grid;
  height: 40px;
  padding: 0;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-sunken);
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}

.register__captcha-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.register__captcha-image:disabled {
  cursor: progress;
  opacity: 0.7;
}

.register__captcha-hint {
  margin: -6px 0 0;
  color: var(--muted);
  font-size: 10px;
}

.register__captcha-hint button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-strong);
  font-size: inherit;
  cursor: pointer;
}

.register__captcha-hint button:disabled {
  cursor: progress;
  opacity: 0.7;
}

.register__optional {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.register__error {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid var(--danger-line);
  border-radius: 7px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12px;
  line-height: 1.5;
}

.register__submit {
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

.register__submit:hover:not(:disabled) {
  background: var(--accent-strong);
}

.register__submit:disabled {
  cursor: progress;
  opacity: 0.7;
}

.register__hint {
  margin: 18px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.register__hint a {
  color: var(--accent-strong);
  text-decoration: none;
}

.register__back {
  color: var(--muted);
  font-size: 12px;
  text-decoration: none;
}

.register__back:hover {
  color: var(--accent-strong);
}

@media (max-width: 520px) {
  .register__card {
    padding: 24px 20px;
  }

  .register__captcha,
  .register__optional {
    grid-template-columns: 1fr;
  }
}
</style>
