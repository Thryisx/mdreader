import { buildQuery, request } from '@/api/http'
import type {
  AdminFilePageQuery,
  AdminFilePageResult,
  ChangePasswordPayload,
  CreateUserPayload,
  FilePageQuery,
  FilePageResult,
  LoginPayload,
  LoginVO,
  UpdateProfilePayload,
  UpdateUserPayload,
  UserPageQuery,
  UserPageResult,
  UserStatus,
  UserVO,
} from '@/types/api'

export function loginAdmin(payload: LoginPayload): Promise<LoginVO> {
  return request<LoginVO>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchAdminMe(): Promise<UserVO> {
  return request<UserVO>('/api/auth/me')
}

export function fetchAdminUsers(query: UserPageQuery): Promise<UserPageResult> {
  return request<UserPageResult>(`/api/users${buildQuery(query)}`)
}

export function fetchAdminFiles(query: FilePageQuery): Promise<FilePageResult> {
  return request<FilePageResult>(`/api/files${buildQuery(query)}`)
}

export function fetchAdminAllFiles(query: AdminFilePageQuery): Promise<AdminFilePageResult> {
  return request<AdminFilePageResult>(`/api/admin/files${buildQuery(query)}`)
}

export function changeAdminPassword(payload: ChangePasswordPayload): Promise<void> {
  return request<void>('/api/auth/me/password', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateAdminProfile(payload: UpdateProfilePayload): Promise<UserVO> {
  return request<UserVO>('/api/auth/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function uploadAdminAvatar(file: File): Promise<UserVO> {
  const body = new FormData()
  body.append('file', file)

  return request<UserVO>('/api/users/me/avatar', {
    method: 'POST',
    body,
  })
}

export function createAdminUser(payload: CreateUserPayload): Promise<UserVO> {
  return request<UserVO>('/api/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAdminUser(id: number, payload: UpdateUserPayload): Promise<UserVO> {
  return request<UserVO>(`/api/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateAdminUserStatus(id: number, status: UserStatus): Promise<UserVO> {
  return request<UserVO>(`/api/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function resetAdminUserPassword(id: number, password: string): Promise<void> {
  return request<void>(`/api/users/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export function deleteAdminUser(id: number): Promise<void> {
  return request<void>(`/api/users/${id}`, {
    method: 'DELETE',
  })
}
