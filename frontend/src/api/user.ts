import { request } from '@/api/http'
import type {
  CaptchaVO,
  ChangePasswordPayload,
  LoginPayload,
  LoginVO,
  RegisterPayload,
  UpdateProfilePayload,
  UserVO,
} from '@/types/api'
import type { CurrentUser } from '@/types/workspace'

export function loginUser(payload: LoginPayload): Promise<LoginVO> {
  return request<LoginVO>(
    '/api/auth/login',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function fetchCaptcha(): Promise<CaptchaVO> {
  return request<CaptchaVO>('/api/auth/captcha', {}, 'user')
}

export function registerUser(payload: RegisterPayload): Promise<LoginVO> {
  return request<LoginVO>(
    '/api/auth/register',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function fetchUserMe(): Promise<UserVO> {
  return request<UserVO>('/api/auth/me', {}, 'user')
}

export function updateUserProfile(payload: UpdateProfilePayload): Promise<UserVO> {
  return request<UserVO>(
    '/api/auth/me',
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function changeUserPassword(payload: ChangePasswordPayload): Promise<void> {
  return request<void>(
    '/api/auth/me/password',
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function uploadUserAvatar(file: File): Promise<UserVO> {
  const body = new FormData()
  body.append('file', file)

  return request<UserVO>(
    '/api/users/me/avatar',
    {
      method: 'POST',
      body,
    },
    'user',
  )
}

export function toCurrentUser(user: UserVO): CurrentUser {
  return {
    id: String(user.id),
    nickname: user.nickname || user.username,
    username: user.username,
    email: user.email || '',
    avatar: user.avatar || '',
  }
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return toCurrentUser(await fetchUserMe())
}
