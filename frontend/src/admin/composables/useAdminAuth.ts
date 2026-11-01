import { computed, readonly, ref } from 'vue'

import { changeAdminPassword, fetchAdminMe, loginAdmin } from '@/api/admin'
import { clearAuthToken, getAuthToken, setAuthToken } from '@/api/http'
import type { ChangePasswordPayload, LoginPayload, UserVO } from '@/types/api'

const token = ref(getAuthToken())
const user = ref<UserVO | null>(null)
const ready = ref(false)
const loading = ref(false)
let sessionPromise: Promise<UserVO | null> | null = null

function reset() {
  token.value = ''
  user.value = null
  ready.value = true
  sessionPromise = null
  clearAuthToken()
}

async function ensureSession(): Promise<UserVO | null> {
  if (!token.value) {
    ready.value = true
    return null
  }

  if (ready.value && user.value) return user.value
  if (sessionPromise) return sessionPromise

  sessionPromise = (async () => {
    try {
      const currentUser = await fetchAdminMe()

      if (currentUser.role !== 'ADMIN') {
        reset()
        return null
      }

      user.value = currentUser
      ready.value = true
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

async function login(payload: LoginPayload): Promise<UserVO> {
  loading.value = true

  try {
    const result = await loginAdmin(payload)

    if (result.user.role !== 'ADMIN') {
      throw new Error('该账号不是管理员，无法进入管理后台')
    }

    token.value = result.token
    setAuthToken(result.token)
    user.value = result.user
    ready.value = true
    return result.user
  } finally {
    loading.value = false
  }
}

async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await changeAdminPassword(payload)
  reset()
}

function logout() {
  reset()
}

function setUser(next: UserVO) {
  user.value = next
  ready.value = true
}

export function useAdminAuth() {
  return {
    token: readonly(token),
    user: readonly(user),
    ready: readonly(ready),
    loading: readonly(loading),
    isAuthenticated: computed(() => Boolean(token.value)),
    isAdmin: computed(() => user.value?.role === 'ADMIN'),
    ensureSession,
    login,
    changePassword,
    logout,
    setUser,
  }
}
