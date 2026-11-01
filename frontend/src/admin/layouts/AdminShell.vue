<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import '@/admin/admin.css'
import AdminProfileDialog from '@/admin/components/AdminProfileDialog.vue'
import { useAdminAuth } from '@/admin/composables/useAdminAuth'
import type { AdminNavItem } from '@/admin/types'
import { AUTH_EXPIRED_EVENT } from '@/api/http'
import { updateAdminProfile, uploadAdminAvatar } from '@/api/admin'
import { useToast } from '@/composables/useToast'

const MAX_AVATAR_SIZE = 2 * 1024 * 1024

interface AdminProfileDraft {
  nickname: string
  email: string
}

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const { user: adminUser, ensureSession, logout, setUser } = useAdminAuth()

const primaryNav: AdminNavItem[] = [
  { name: 'admin-users', label: '用户管理', hint: '账号与角色', short: 'USR' },
  { name: 'admin-files', label: '文件管理', hint: '文件与正文', short: 'DOC' },
]

const reservedNav = [
  { label: '预留导航 1', hint: '名称与功能待确认', short: '01' },
  { label: '预留导航 2', hint: '名称与功能待确认', short: '02' },
  { label: '预留导航 3', hint: '名称与功能待确认', short: '03' },
]

const avatar = ref('')
const avatarMenuOpen = ref(false)
const avatarWrap = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const profile = ref<AdminProfileDraft>({ nickname: '', email: '' })
const profileDialogOpen = ref(false)
const profileSaving = ref(false)
const avatarUploading = ref(false)

const currentTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '管理后台',
)

const adminName = computed(() => adminUser.value?.username || '管理员')
const displayName = computed(() => profile.value.nickname || adminName.value)

watch(
  adminUser,
  (user) => {
    if (!user) {
      avatar.value = ''
      return
    }

    avatar.value = user.avatar ?? ''
    profile.value = {
      nickname: user.nickname?.trim() || user.username,
      email: user.email ?? '',
    }
  },
  { immediate: true },
)

function closeAvatarMenu() {
  avatarMenuOpen.value = false
}

function toggleAvatarMenu() {
  avatarMenuOpen.value = !avatarMenuOpen.value
}

function openProfileEditor() {
  closeAvatarMenu()
  profileDialogOpen.value = true
}

function closeProfileEditor() {
  profileDialogOpen.value = false
}

async function handleProfileSave(payload: AdminProfileDraft) {
  if (profileSaving.value) return

  profileSaving.value = true

  try {
    const updated = await updateAdminProfile(payload)
    setUser(updated)
    profile.value = {
      nickname: updated.nickname?.trim() || updated.username,
      email: updated.email ?? '',
    }
    avatar.value = updated.avatar ?? ''
    closeProfileEditor()
    showToast('资料已保存', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '资料保存失败，请稍后重试', 'warn')
  } finally {
    profileSaving.value = false
  }
}

function pickAvatar() {
  closeAvatarMenu()
  fileInput.value?.click()
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

  if (file.size > MAX_AVATAR_SIZE) {
    showToast('头像图片不能超过 2MB', 'warn')
    return
  }

  if (avatarUploading.value) return

  avatarUploading.value = true
  showToast('头像上传中…', 'success')

  try {
    const updated = await uploadAdminAvatar(file)
    setUser(updated)
    avatar.value = updated.avatar ?? ''
    showToast('头像已更新', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '头像上传失败，请稍后重试', 'warn')
  } finally {
    avatarUploading.value = false
  }
}

function handlePointerDown(event: PointerEvent) {
  if (!avatarMenuOpen.value) return

  const target = event.target as Node | null
  if (target && avatarWrap.value?.contains(target)) return

  closeAvatarMenu()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || profileDialogOpen.value) return

  closeAvatarMenu()
}

function handleLogout() {
  closeAvatarMenu()
  closeProfileEditor()
  logout()
  void router.replace({ name: 'admin-login' })
}

function handleAuthExpired(event: Event) {
  const scope = (event as CustomEvent<{ scope?: string }>).detail?.scope
  if (scope && scope !== 'admin') return

  closeAvatarMenu()
  closeProfileEditor()
  logout()

  if (route.name !== 'admin-login') {
    void router.replace({
      name: 'admin-login',
      query: { redirect: route.fullPath },
    })
  }
}

watch(currentTitle, (title) => {
  document.title = `${title} · MD Reader 管理后台`
}, { immediate: true })

watch(
  () => route.fullPath,
  () => {
    closeAvatarMenu()
    closeProfileEditor()
  },
)

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
  void ensureSession()
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
  document.title = 'MD Reader'
})
</script>

<template>
  <div class="admin-shell">
    <header class="admin-topbar">
      <RouterLink class="admin-brand" to="/admin/users" aria-label="返回管理后台首页">
        <span class="admin-brand__mark" aria-hidden="true">
          <img src="/favicon.svg" alt="" />
        </span>
        <span class="admin-brand__text">
          <strong>MD Reader</strong>
        </span>
      </RouterLink>

      <p class="admin-topbar__crumb">
        管理后台 / <span>{{ currentTitle }}</span>
      </p>

      <div ref="avatarWrap" class="admin-avatar">
        <button
          type="button"
          class="admin-avatar__button"
          :aria-expanded="avatarMenuOpen"
          aria-haspopup="menu"
          aria-label="管理员头像菜单"
          @click="toggleAvatarMenu"
        >
          <img v-if="avatar" :src="avatar" alt="" />
          <span v-else aria-hidden="true">管</span>
        </button>

        <Transition name="admin-avatar-menu">
          <div v-if="avatarMenuOpen" class="admin-avatar__menu" role="menu">
            <p class="admin-avatar__menu-title">管理员账号</p>
            <p class="admin-avatar__menu-name">{{ displayName }}</p>
            <button type="button" role="menuitem" @click="openProfileEditor">编辑资料</button>
            <button type="button" role="menuitem" @click="pickAvatar">更换头像</button>
            <RouterLink to="/" role="menuitem" @click="closeAvatarMenu">返回阅读器</RouterLink>
            <button type="button" role="menuitem" @click="handleLogout">退出登录</button>
          </div>
        </Transition>

        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          hidden
          :disabled="avatarUploading"
          @change="handleAvatarChange"
        />
      </div>
    </header>

    <AdminProfileDialog
      :open="profileDialogOpen"
      :username="adminName"
      :nickname="profile.nickname"
      :email="profile.email"
      :saving="profileSaving"
      @close="closeProfileEditor"
      @save="handleProfileSave"
    />

    <div class="admin-body">
      <aside class="admin-sidebar" aria-label="管理后台导航">
        <nav class="admin-nav">
          <RouterLink
            v-for="item in primaryNav"
            :key="item.name"
            class="admin-nav__item"
            :to="{ name: item.name }"
          >
            <span class="admin-nav__icon" aria-hidden="true">{{ item.short }}</span>
            <span class="admin-nav__text">
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </span>
          </RouterLink>

          <button
            v-for="item in reservedNav"
            :key="item.short"
            type="button"
            class="admin-nav__item"
            aria-disabled="true"
            :title="item.hint"
          >
            <span class="admin-nav__icon" aria-hidden="true">{{ item.short }}</span>
            <span class="admin-nav__text">
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </span>
          </button>
        </nav>

        
      </aside>

      <main class="admin-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
