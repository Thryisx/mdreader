import { ref } from 'vue'

import { getAuthToken } from '@/api/http'
import { fetchCurrentUser } from '@/api/user'
import type { CurrentUser } from '@/types/workspace'

export type UserStatus = 'idle' | 'loading' | 'ready' | 'error'

const user = ref<CurrentUser | null>(null)
const status = ref<UserStatus>('idle')
let started = false

async function load(force = false) {
  if (status.value === 'loading') return
  if (started && !force) return

  started = true
  status.value = 'loading'

  if (!getAuthToken('user')) {
    user.value = null
    status.value = 'ready'
    return
  }

  try {
    user.value = await fetchCurrentUser()
    status.value = 'ready'
  } catch (error) {
    user.value = null
    status.value = 'error'
    console.error('[用户] 用户信息加载失败', error)
  }
}

function setUser(next: CurrentUser | null) {
  user.value = next
  status.value = 'ready'
  started = true
}

export function useCurrentUser() {
  if (!started) {
    void load()
  }

  return {
    user,
    status,
    setUser,
    reload: () => load(true),
  }
}
