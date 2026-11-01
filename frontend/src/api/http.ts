import type { ApiResponse } from '@/types/api'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? import.meta.env.VITE_API_BASE ?? '').replace(
  /\/$/,
  '',
)
export type AuthScope = 'admin' | 'user' | 'guest'

const AUTH_TOKEN_KEYS: Record<AuthScope, string> = {
  admin: 'md-reader:admin-token',
  user: 'md-reader:user-token',
  guest: 'md-reader:guest-token',
}

export const AUTH_EXPIRED_EVENT = 'md-reader:auth-expired'

export function getAuthToken(scope: AuthScope = 'admin') {
  if (typeof window === 'undefined') return ''

  try {
    return window.localStorage.getItem(AUTH_TOKEN_KEYS[scope]) ?? ''
  } catch {
    return ''
  }
}

export function setAuthToken(token: string, scope: AuthScope = 'admin') {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.setItem(AUTH_TOKEN_KEYS[scope], token)
  } catch {
    throw new Error('登录状态保存失败，请检查浏览器存储权限')
  }
}

export function clearAuthToken(scope: AuthScope = 'admin') {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.removeItem(AUTH_TOKEN_KEYS[scope])
  } catch {
    return
  }
}

export class ApiError extends Error {
  readonly status: number
  readonly code: number

  constructor(message: string, status: number, code: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function buildQuery(params: object) {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (typeof value !== 'string' && typeof value !== 'number') return
    if (value === '') return
    query.set(key, String(value))
  })

  const text = query.toString()
  return text ? `?${text}` : ''
}

export async function requestText(
  path: string,
  init: RequestInit = {},
  scope: AuthScope = 'admin',
): Promise<string> {
  const token = getAuthToken(scope)
  const headers = new Headers(init.headers)

  headers.set('Accept', 'text/markdown, text/plain;q=0.9, */*;q=0.8')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response

  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers,
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认服务已启动', 0, 0)
  }

  if (!response.ok) {
    if (response.status === 401) {
      clearAuthToken(scope)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { scope } }),
        )
      }
    }

    let body: ApiResponse<unknown> | null = null

    try {
      body = (await response.json()) as ApiResponse<unknown>
    } catch {
      body = null
    }

    const fallback =
      response.status === 502 || response.status === 503 || response.status === 504
        ? '后端服务不可用，请确认服务已启动后重试'
        : `请求失败（HTTP ${response.status}）`

    throw new ApiError(
      body?.message || fallback,
      response.status,
      body?.code ?? response.status,
    )
  }

  return response.text()
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  scope: AuthScope = 'admin',
): Promise<T> {
  let response: Response
  const token = getAuthToken(scope)
  const headers = new Headers(init.headers)

  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers,
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认服务已启动', 0, 0)
  }

  let body: ApiResponse<T> | null = null

  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    body = null
  }

  if (!response.ok || !body || body.code !== 200) {
    if (response.status === 401) {
      clearAuthToken(scope)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { scope } }),
        )
      }
    }

    const fallback =
      response.status === 502 || response.status === 503 || response.status === 504
        ? '后端服务不可用，请确认服务已启动后重试'
        : `请求失败（HTTP ${response.status}）`

    throw new ApiError(
      body?.message || fallback,
      response.status,
      body?.code ?? response.status,
    )
  }

  return body.data
}
