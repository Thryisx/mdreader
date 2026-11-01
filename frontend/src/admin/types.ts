import type { UserRole, UserStatus } from '@/types/api'

export type AdminNavName = 'admin-users' | 'admin-files'

export interface AdminNavItem {
  name: AdminNavName
  label: string
  hint: string
  short: string
}

export interface AdminColumn<T> {
  key: (keyof T & string) | 'actions'
  label: string
  width?: string
  align?: 'left' | 'center' | 'right'
}

export type AdminBadgeTone = 'neutral' | 'accent' | 'ok' | 'warn' | 'danger' | 'muted'

export type AdminUserDialogMode = 'create' | 'edit'

export interface AdminUserFormValue {
  username: string
  password: string
  nickname: string
  email: string
  role: UserRole
  status: UserStatus
}
