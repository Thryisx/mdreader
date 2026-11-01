<script setup lang="ts">
import { computed, ref } from 'vue'

import UserMenu from '@/components/UserMenu.vue'
import { useCurrentUser } from '@/composables/useCurrentUser'
import type { UserStatus } from '@/composables/useCurrentUser'
import { useToast } from '@/composables/useToast'
import { useUserAuth } from '@/composables/useUserAuth'
import type { CurrentUser } from '@/types/workspace'

const props = defineProps<{
  user: CurrentUser | null
  status: UserStatus
}>()

const emit = defineEmits<{
  retry: []
  login: []
}>()

const menuOpen = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)
const { logout } = useUserAuth()
const { setUser } = useCurrentUser()
const { showToast } = useToast()

const displayName = computed(() => {
  if (props.status === 'loading') return '正在加载用户'
  if (props.status === 'error') return '用户信息加载失败'
  if (!props.user) return '未登录'
  return props.user.nickname || props.user.username || '未命名用户'
})

const secondary = computed(() => {
  if (props.status === 'error') return '点击重试，或点击空白处登录'
  if (!props.user) return '保存时可登录同步'
  if (props.user.nickname && props.user.username) return `@${props.user.username}`
  return props.user.username ?? ''
})

const initials = computed(() => {
  const name = props.user?.nickname || props.user?.username || ''
  return name ? name.slice(0, 1).toUpperCase() : '访'
})

function toggleMenu() {
  if (!props.user) return
  if (props.status === 'error') {
    menuOpen.value = false
    return
  }
  menuOpen.value = !menuOpen.value
}

function handleGuestLogin() {
  if (props.user) return
  menuOpen.value = false
  emit('login')
}

function handleIdentityClick() {
  if (!props.user) return
  toggleMenu()
}

function handleLogout() {
  menuOpen.value = false
  logout()
  setUser(null)
  showToast('已退出登录', 'success')
}
</script>

<template>
  <!-- 未登录：整条 user-bar.is-guest 即登录按钮，不使用 user-bar__dot -->
  <button
    v-if="!user && status !== 'error'"
    type="button"
    class="user-bar is-guest"
    aria-label="登录"
    title="登录"
    @click="handleGuestLogin"
  >
    <span class="user-bar__avatar" aria-hidden="true">
      <span v-if="status === 'loading'" class="user-bar__avatar-dot" />
      <span v-else>{{ initials || '访' }}</span>
    </span>

    <span class="user-bar__text">
      <span class="user-bar__name">{{ displayName }}</span>
      <span v-if="secondary" class="user-bar__secondary">{{ secondary }}</span>
    </span>

    <span class="user-bar__login" aria-hidden="true">登录</span>
  </button>

  <!-- 未登录 + 用户接口异常：外层仍是 user-bar.is-guest 可点登录，重试按钮阻止冒泡 -->
  <div
    v-else-if="!user"
    class="user-bar is-guest"
    role="button"
    tabindex="0"
    aria-label="登录"
    title="登录"
    @click="handleGuestLogin"
    @keydown.enter="handleGuestLogin"
    @keydown.space.prevent="handleGuestLogin"
  >
    <span class="user-bar__avatar" aria-hidden="true">
      <span>{{ initials || '访' }}</span>
    </span>

    <span class="user-bar__text">
      <span class="user-bar__name">{{ displayName }}</span>
      <span v-if="secondary" class="user-bar__secondary">{{ secondary }}</span>
    </span>

    <button
      type="button"
      class="user-bar__retry"
      @click.stop="emit('retry')"
    >
      重试
    </button>
  </div>

  <div v-else class="user-bar">
    <button
      type="button"
      class="user-bar__identity"
      aria-label="打开用户菜单"
      @click="handleIdentityClick"
    >
      <span class="user-bar__avatar" aria-hidden="true">
        <img v-if="user.avatar" :src="user.avatar" alt="" />
        <span v-else>{{ initials || '?' }}</span>
      </span>

      <span class="user-bar__text">
        <span class="user-bar__name">{{ displayName }}</span>
        <span v-if="secondary" class="user-bar__secondary">{{ secondary }}</span>
      </span>
    </button>

    <button
      v-if="status === 'error'"
      type="button"
      class="user-bar__retry"
      @click="emit('retry')"
    >
      重试
    </button>

    <button
      ref="menuButton"
      type="button"
      class="user-bar__more"
      aria-haspopup="menu"
      :aria-expanded="menuOpen"
      aria-label="打开用户菜单"
      title="用户菜单"
      @click="toggleMenu"
    >
      <span class="user-bar__dot" aria-hidden="true" />
      <span class="user-bar__dot" aria-hidden="true" />
      <span class="user-bar__dot" aria-hidden="true" />
    </button>

    <UserMenu
      :open="menuOpen"
      :anchor="menuButton"
      @close="menuOpen = false"
      @logout="handleLogout"
    />
  </div>
</template>

<style scoped>
.user-bar {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-top: 1px solid var(--line);
}

/* 未登录时整条即登录按钮 */
button.user-bar.is-guest {
  width: 100%;
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

div.user-bar.is-guest {
  cursor: pointer;
}

button.user-bar.is-guest:hover,
div.user-bar.is-guest:hover {
  background: var(--surface-sunken);
}

button.user-bar.is-guest:focus-visible,
div.user-bar.is-guest:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.user-bar__login {
  flex: none;
  padding: 4px 12px;
  border: 1px solid var(--accent);
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.user-bar__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.user-bar__identity:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.user-bar__identity.is-static {
  cursor: default;
}

.user-bar__avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: none;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
}

.user-bar__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-bar__avatar-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-line);
  animation: pulse 1.2s ease-in-out infinite;
}

.user-bar__text {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.user-bar__name,
.user-bar__secondary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-bar__name {
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
}

.user-bar__secondary {
  color: var(--muted);
  font-size: 11px;
}

.user-bar__retry {
  flex: none;
  padding: 3px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 11px;
  cursor: pointer;
}

.user-bar__retry:hover {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.user-bar__more {
  display: inline-flex;
  width: 28px;
  height: 28px;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 2.5px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted-strong);
  cursor: pointer;
}

.user-bar__more:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.user-bar__more:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.user-bar__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
</style>
