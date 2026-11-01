<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { toCurrentUser, updateUserProfile, uploadUserAvatar } from '@/api/user'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { useCurrentUser } from '@/composables/useCurrentUser'
import { useToast } from '@/composables/useToast'
import { useUserAuth } from '@/composables/useUserAuth'
import type { ChangePasswordPayload } from '@/types/api'

const router = useRouter()
const { showToast } = useToast()
const { changePassword } = useUserAuth()
const { user, status, setUser, reload } = useCurrentUser()

const avatarInput = ref<HTMLInputElement | null>(null)
const nicknameInput = ref<HTMLInputElement | null>(null)
const nickname = ref('')
const email = ref('')
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const avatarBroken = ref(false)
const passwordOpen = ref(false)
const passwordSaving = ref(false)

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const displayName = computed(() => user.value?.nickname || user.value?.username || '未命名用户')
const initials = computed(() => displayName.value.slice(0, 1).toUpperCase())
const avatarUrl = computed(() => (avatarBroken.value ? '' : user.value?.avatar || ''))
const hasChanges = computed(() => {
  if (!user.value) return false

  return (
    nickname.value.trim() !== (user.value.nickname ?? '') ||
    email.value.trim() !== (user.value.email ?? '')
  )
})

watch(
  user,
  (value) => {
    if (!value) {
      editing.value = false
      nickname.value = ''
      email.value = ''
      error.value = ''
      return
    }

    if (!editing.value) {
      nickname.value = value.nickname ?? ''
      email.value = value.email ?? ''
    }
  },
  { immediate: true },
)

watch(
  () => user.value?.avatar,
  () => {
    avatarBroken.value = false
  },
)

function resetForm() {
  nickname.value = user.value?.nickname ?? ''
  email.value = user.value?.email ?? ''
  error.value = ''
}

function startEditing() {
  resetForm()
  editing.value = true
  void nextTick(() => nicknameInput.value?.focus())
}

function cancelEditing() {
  editing.value = false
  resetForm()
}

function validateForm() {
  const nextNickname = nickname.value.trim()
  const nextEmail = email.value.trim()

  if (!nextNickname) {
    error.value = '请输入昵称'
    return false
  }

  if (nextNickname.length > 20) {
    error.value = '昵称不能超过 20 个字符'
    return false
  }

  if (nextEmail.length > 120) {
    error.value = '邮箱不能超过 120 个字符'
    return false
  }

  if (nextEmail && !emailPattern.test(nextEmail)) {
    error.value = '请输入有效的邮箱地址'
    return false
  }

  error.value = ''
  return true
}

async function saveProfile() {
  if (saving.value || !user.value || !validateForm()) return

  saving.value = true

  try {
    const updated = await updateUserProfile({
      nickname: nickname.value.trim(),
      email: email.value.trim(),
    })

    setUser(toCurrentUser(updated))
    editing.value = false
    showToast('资料已更新', 'success')
  } catch (err) {
    const message = err instanceof Error ? err.message : '资料保存失败，请稍后重试'
    error.value = message
    showToast(message, 'warn')
  } finally {
    saving.value = false
  }
}

function openAvatarPicker() {
  if (uploading.value) return
  avatarInput.value?.click()
}

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件', 'warn')
    return
  }

  if (file.size > 2 * 1024 * 1024) {
    showToast('头像大小不能超过 2MB', 'warn')
    return
  }

  uploading.value = true

  try {
    const updated = await uploadUserAvatar(file)
    setUser(toCurrentUser(updated))
    avatarBroken.value = false
    showToast('头像已更新', 'success')
  } catch (err) {
    const message = err instanceof Error ? err.message : '头像上传失败，请稍后重试'
    showToast(message, 'warn')
  } finally {
    uploading.value = false
  }
}

async function handleChangePassword(payload: ChangePasswordPayload) {
  if (passwordSaving.value) return

  passwordSaving.value = true

  try {
    await changePassword(payload)
    passwordOpen.value = false
    showToast('密码已修改，请使用新密码重新登录', 'success')
    await router.replace({ name: 'login', query: { redirect: '/profile' } })
  } catch (err) {
    const message = err instanceof Error ? err.message : '密码修改失败，请稍后重试'
    showToast(message, 'warn')
  } finally {
    passwordSaving.value = false
  }
}

function goLogin() {
  void router.push({ name: 'login', query: { redirect: '/profile' } })
}

function goBack() {
  const state = window.history.state as { back?: string } | null
  if (state?.back) {
    router.back()
    return
  }

  void router.push('/')
}

onMounted(() => {
  if (status.value === 'ready' && !user.value) {
    void reload()
  }
})
</script>

<template>
  <div class="profile">
    <header class="profile__top">
      <button type="button" class="profile__back" @click="goBack">
        <span aria-hidden="true">←</span>
        返回阅读页
      </button>
      <span class="profile__brand">MD Reader</span>
    </header>

    <main class="profile__main">
      <div class="profile__intro">
        <p class="profile__eyebrow">个人信息</p>
        <h1 class="profile__title">账号资料</h1>
        <p class="profile__desc">查看并修改昵称、邮箱与头像，保存后同步到云端。</p>
      </div>

      <section v-if="status === 'loading'" class="profile__card" aria-busy="true">
        <span class="profile__skeleton profile__skeleton--avatar" />
        <div class="profile__skeleton-lines">
          <span class="profile__skeleton profile__skeleton--name" />
          <span class="profile__skeleton profile__skeleton--line" />
        </div>
      </section>

      <section v-else-if="status === 'error'" class="profile__card profile__card--state">
        <p class="profile__state-title">用户信息加载失败</p>
        <p class="profile__state-desc">请检查网络后重试。</p>
        <button type="button" class="profile__button" @click="reload">重新加载</button>
      </section>

      <section v-else-if="!user" class="profile__card profile__card--state">
        <p class="profile__state-title">当前未登录</p>
        <p class="profile__state-desc">登录后可查看和编辑个人资料。</p>
        <button type="button" class="profile__button" @click="goLogin">去登录</button>
      </section>

      <section v-else class="profile__card profile__card--profile">
        <div class="profile__avatar-wrap">
          <span class="profile__avatar" aria-hidden="true">
            <img v-if="avatarUrl" :src="avatarUrl" alt="" @error="avatarBroken = true" />
            <span v-else>{{ initials }}</span>
          </span>
          <button
            type="button"
            class="profile__avatar-action"
            :disabled="uploading"
            @click="openAvatarPicker"
          >
            {{ uploading ? '上传中…' : '更换头像' }}
          </button>
          <input
            ref="avatarInput"
            class="profile__file"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            @change="handleAvatarChange"
          />
        </div>

        <div class="profile__identity">
          <p class="profile__name">{{ displayName }}</p>
          <!-- <p class="profile__username">{{ user.username ? `@${user.username}` : '未提供用户名' }}</p>-->
        </div>

        <button v-if="!editing" type="button" class="profile__edit" @click="startEditing">
          编辑资料
        </button>

        <form v-if="editing" class="profile__form" @submit.prevent="saveProfile">
          <div class="profile__form-grid">
            <label class="profile__field">
              <span>昵称</span>
              <input
                ref="nicknameInput"
                v-model="nickname"
                type="text"
                maxlength="20"
                placeholder="请输入昵称"
                autocomplete="nickname"
                :disabled="saving"
              />
            </label>

            <label class="profile__field">
              <span>邮箱</span>
              <input
                v-model="email"
                type="email"
                maxlength="120"
                placeholder="name@example.com"
                autocomplete="email"
                :disabled="saving"
              />
            </label>
          </div>

          <p v-if="error" class="profile__error" role="alert">{{ error }}</p>

          <div class="profile__form-actions">
            <button
              type="button"
              class="profile__button profile__button--ghost"
              :disabled="saving"
              @click="cancelEditing"
            >
              取消
            </button>
            <button type="submit" class="profile__button" :disabled="saving || !hasChanges">
              {{ saving ? '保存中…' : '保存资料' }}
            </button>
          </div>
        </form>

        <dl v-else class="profile__fields">
          <div class="profile__field">
            <dt>昵称</dt>
            <dd>{{ user.nickname || '未提供' }}</dd>
          </div>
          <div class="profile__field">
            <dt>用户名</dt>
            <dd>{{ user.username || '未提供' }}</dd>
          </div>
          <div class="profile__field">
            <dt>邮箱</dt>
            <dd>{{ user.email || '未提供' }}</dd>
          </div>
          <div class="profile__field">
            <dt>用户 ID</dt>
            <dd>{{ user.id || '未提供' }}</dd>
          </div>
        </dl>

        <div v-if="!editing" class="profile__security">
          <div>
            <p class="profile__security-title">登录密码</p>
            <p class="profile__security-desc">修改后当前登录状态会失效，需要重新登录。</p>
          </div>
          <button
            type="button"
            class="profile__button profile__button--ghost"
            @click="passwordOpen = true"
          >
            修改密码
          </button>
        </div>
      </section>

    </main>

    <ChangePasswordDialog
      :open="passwordOpen"
      :saving="passwordSaving"
      @close="passwordOpen = false"
      @save="handleChangePassword"
    />
  </div>
</template>

<style scoped>
.profile {
  min-height: 100dvh;
  overflow-y: auto;
}

.profile__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(920px, 100% - 40px);
  margin: 0 auto;
  padding: 18px 0;
}

.profile__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--muted-strong);
  font-size: 12.5px;
  cursor: pointer;
  backdrop-filter: blur(10px);
}

.profile__back:hover {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.profile__brand {
  color: var(--muted);
  font-family: var(--font-serif);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.profile__main {
  width: min(920px, 100% - 40px);
  margin: 0 auto;
  padding: 18px 0 64px;
}

.profile__intro {
  padding: 22px 0 26px;
  border-bottom: 1px solid var(--line);
}

.profile__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.profile__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(26px, 3.4vw, 36px);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.profile__desc {
  max-width: 640px;
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13.5px;
  line-height: 1.75;
}

.profile__card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  margin-top: 26px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.profile__card--state {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.profile__card--profile {
  align-items: flex-start;
}

.profile__avatar-wrap {
  display: flex;
  width: 64px;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.profile__avatar {
  display: grid;
  width: 64px;
  height: 64px;
  flex: none;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--accent-line);
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-family: var(--font-serif);
  font-size: 24px;
}

.profile__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile__avatar-action {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-strong);
  font-size: 11px;
  cursor: pointer;
}

.profile__avatar-action:hover:not(:disabled) {
  text-decoration: underline;
}

.profile__avatar-action:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.profile__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.profile__identity {
  min-width: 0;
  flex: 1;
}

.profile__name {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 36px;
  font-weight: 600;
}

.profile__username {
  margin: 4px 0 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.profile__edit {
  flex: none;
  padding: 7px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 12px;
  cursor: pointer;
}

.profile__edit:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.profile__form {
  width: 100%;
  margin-top: 8px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.profile__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.profile__fields {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 8px 0 0;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.profile__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.profile__field span,
.profile__field dt {
  color: var(--muted);
  font-size: 11px;
}

.profile__field dd {
  margin: 0;
  overflow: hidden;
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile__field input {
  width: 100%;
  height: 38px;
  padding: 0 11px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.profile__field input:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.profile__field input:disabled {
  background: var(--surface-sunken);
  color: var(--muted);
  cursor: not-allowed;
}

.profile__error {
  margin: 10px 0 0;
  color: var(--warn);
  font-size: 12px;
}

.profile__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.profile__state-title {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.profile__state-desc {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
}

.profile__button {
  margin-top: 8px;
  padding: 7px 14px;
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.profile__button:hover:not(:disabled) {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.profile__button--ghost {
  border-color: var(--line-strong);
  background: var(--surface);
  color: var(--ink);
}

.profile__button--ghost:hover:not(:disabled) {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.profile__button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.profile__form-actions .profile__button {
  margin-top: 0;
}

.profile__note {
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.profile__skeleton {
  display: block;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--surface-sunken), var(--line), var(--surface-sunken));
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

.profile__skeleton--avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
}

.profile__skeleton-lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.profile__skeleton--name {
  width: 140px;
  height: 20px;
}

.profile__skeleton--line {
  width: 220px;
  height: 12px;
}

.profile__security {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  width: 100%;
  margin-top: 4px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.profile__security-title {
  margin: 0 0 4px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.profile__security-desc {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (max-width: 640px) {
  .profile__form-grid,
  .profile__fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
