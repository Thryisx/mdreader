export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export type UserRole = 'ADMIN' | 'USER'
export type UserStatus = 0 | 1

export type FileVisibility = 'PUBLIC' | 'PRIVATE'
export type FileStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED'
export type StorageProvider = 'ALIYUN_OSS' | 'TENCENT_COS' | 'S3' | 'LOCAL'

export interface UserVO {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  email: string | null
  role: UserRole
  status: UserStatus
  mustChangePassword: boolean
  createdAt: string
  updatedAt: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  confirmPassword: string
  captchaId: string
  captchaCode: string
  nickname?: string
  email?: string
}

export interface CaptchaVO {
  captchaId: string
  image: string
  expiresIn: number
}

export interface UpdateProfilePayload {
  nickname?: string
  email?: string
  avatar?: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface CreateUserPayload {
  username: string
  password: string
  nickname?: string
  email?: string
  role?: UserRole
  status?: UserStatus
}

export interface UpdateUserPayload {
  nickname?: string
  email?: string
  role?: UserRole
  status?: UserStatus
}

export interface LoginVO {
  token: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserVO
}

export interface ImageInfoVO {
  url: string
  objectKey?: string | null
  provider?: string | null
  alt?: string | null
}

export interface FileVO {
  id: number
  userId: number
  title: string
  originalName: string | null
  mdUrl: string
  storageProvider: StorageProvider
  coverUrl: string | null
  fileSize: number
  visibility: FileVisibility
  status: FileStatus
  category: string | null
  summary: string | null
  authorNickname: string | null
  authorUsername: string | null
  createdAt: string
  updatedAt: string
}

export interface FileDetailVO extends Omit<FileVO, 'coverUrl'> {
  images: ImageInfoVO[]
}

export interface AdminFileVO {
  id: number
  userId: number
  title: string
  originalName: string | null
  storageProvider: StorageProvider
  coverUrl: string | null
  fileSize: number
  visibility: FileVisibility
  status: FileStatus
  category: string | null
  authorNickname: string | null
  createdAt: string
  updatedAt: string
}

export interface FileCategoryVO {
  category: string | null
  count: number
}

export interface UserPageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  role?: UserRole
  status?: UserStatus
}

export interface FilePageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
}

export interface AdminFilePageQuery extends FilePageQuery {
  userId?: number
  visibility?: FileVisibility
  status?: FileStatus
}

export interface FileListItem {
  id: number
  title: string
  originalName: string | null
  fileSize: number
  createdAt: string
  updatedAt: string
}

export interface FileTreeNodeVO {
  id: number
  parentId: number | null
  name: string
  type: 'file' | 'folder'
  path: string
  size: number
  updatedAt: string
  children: FileTreeNodeVO[]
}

export interface CreateFilePayload {
  title: string
  originalName?: string
  parentId?: number | null
  content?: string
  visibility?: FileVisibility
  status?: FileStatus
  category?: string
  summary?: string
}

export interface UpdateFilePayload {
  title?: string
  originalName?: string
  content?: string
  visibility?: FileVisibility
  status?: FileStatus
  category?: string
  summary?: string
}

export interface CreateFolderPayload {
  name: string
  parentId?: number | null
}

export interface RenameNodePayload {
  name: string
}

export interface MoveNodePayload {
  parentId?: number | null
}

export interface CurrentUserFileListData {
  total: number
  records: FileListItem[]
}

export interface CurrentUserFileListQuery {
  page?: number
  size?: number
}

export type UserPageResult = PageResult<UserVO>
export type FilePageResult = PageResult<FileVO>
export type AdminFilePageResult = PageResult<AdminFileVO>
export type CurrentUserFileListResponse = ApiResponse<CurrentUserFileListData>
