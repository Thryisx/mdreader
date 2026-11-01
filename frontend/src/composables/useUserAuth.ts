import { computed, readonly, ref } from 'vue'

import {
  changeUserPassword,
  fetchUserMe,
  loginUser,
  registerUser,
  toCurrentUser,
} from '@/api/user'
import {
  AUTH_EXPIRED_EVENT,
  clearAuthToken,
  getAuthToken,
  setAuthToken,
} from '@/api/http'
import { clearWorkspace } from '@/storage/workspaceDb'
import { useCurrentUser } from '@/composables/useCurrentUser'
import { useFileExplorer } from '@/composables/useFileExplorer'
import type {
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  UserVO,
} from '@/types/api'

const token = ref(getAuthToken('user'))
const user = ref<UserVO | null>(null)
const ready = ref(false)
const loading = ref(false)
let sessionPromise: Promise<UserVO | null> | null = null

function reset() {
  token.value = ''
  user.value = null
  ready.value = true
  sessionPromise = null
  clearAuthToken('user')
  syncSessionStores(null)
  void clearWorkspace()
  try {
    useFileExplorer().reset()
  } catch {
    return
  }
}

function syncTokenFromStorage() {
  const next = getAuthToken('user')
  if (next === token.value) return
  token.value = next
  if (!next) {
    reset()
  }
}

function syncSessionStores(currentUser: UserVO | null) {
  try {
    useCurrentUser().setUser(currentUser ? toCurrentUser(currentUser) : null)
  } catch {
    return
  }
}

async function ensureSession(): Promise<UserVO | null> {
  if (!token.value) {
    token.value = getAuthToken('user')
    if (!token.value) {
      ready.value = true
      syncSessionStores(null)
      return null
    }
  }

  if (ready.value && user.value) return user.value
  if (sessionPromise) return sessionPromise

  sessionPromise = (async () => {
    try {
      const currentUser = await fetchUserMe()

      if (currentUser.role !== 'USER') {
        reset()
        return null
      }

      user.value = currentUser
      ready.value = true
      syncSessionStores(currentUser)
      return currentUser
    } catch {
      reset()
      return null
    } finally {
      sessionPromise = null
    }
  })()

  return sessionPromise
}

async function applyLogin(result: Awaited<ReturnType<typeof loginUser>>): Promise<UserVO> {
  if (result.user.role !== 'USER') {
    throw new Error('该账号不是普通用户，请使用管理员入口登录')
  }

  token.value = result.token
  setAuthToken(result.token, 'user')
  user.value = result.user
  ready.value = true
  syncSessionStores(result.user)
  return result.user
}

async function login(payload: LoginPayload): Promise<UserVO> {
  loading.value = true

  try {
    return await applyLogin(await loginUser(payload))
  } finally {
    loading.value = false
  }
}

async function register(payload: RegisterPayload): Promise<UserVO> {
  loading.value = true

  try {
    return await applyLogin(await registerUser(payload))
  } finally {
    loading.value = false
  }
}

async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await changeUserPassword(payload)
  reset()
}

function logout() {
  reset()
}

if (typeof window !== 'undefined') {
  window.addEventListener(AUTH_EXPIRED_EVENT, (event) => {
    const scope = (event as CustomEvent<{ scope?: string }>).detail?.scope
    if (scope && scope !== 'user') return
    reset()
  })
  window.addEventListener('storage', (event) => {
    if (event.key === null || event.key === 'md-reader:user-token') syncTokenFromStorage()
  })
}

export function useUserAuth() {
  return {
    token: readonly(token),
    user: readonly(user),
    ready: readonly(ready),
    loading: readonly(loading),
    isAuthenticated: computed(() => Boolean(token.value)),
    isUser: computed(() => user.value?.role === 'USER'),
    ensureSession,
    login,
    register,
    changePassword,
    logout,
  }
}
