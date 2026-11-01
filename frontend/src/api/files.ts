import { ApiError, buildQuery, getAuthToken, request, requestText } from '@/api/http'
import type {
  CreateFilePayload,
  CreateFolderPayload,
  CurrentUserFileListData,
  CurrentUserFileListQuery,
  FileDetailVO,
  FileTreeNodeVO,
  MoveNodePayload,
  RenameNodePayload,
  UpdateFilePayload,
} from '@/types/api'
import type { SaveFilePayload } from '@/types/workspace'

export interface SaveFileResult {
  savedAt: string
  remoteId?: number
}

export function toRemoteId(id: string | number): number {
  const value = typeof id === 'number' ? id : Number(id)
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error('文件标识无效，请刷新文件树后重试')
  }
  return value
}

export function fetchCurrentUserFiles(
  query: CurrentUserFileListQuery = {},
): Promise<CurrentUserFileListData> {
  return request<CurrentUserFileListData>(`/api/files/list${buildQuery(query)}`, {}, 'user')
}

export function fetchFileTree(): Promise<FileTreeNodeVO[]> {
  return request<FileTreeNodeVO[]>('/api/files/tree', {}, 'user')
}

export function fetchFileDetail(id: number): Promise<FileDetailVO> {
  return request<FileDetailVO>(`/api/files/${id}`, {}, 'user')
}

export function fetchFileContent(id: number): Promise<string> {
  return requestText(`/api/files/${id}/content`, {}, 'user')
}

export function createFile(payload: CreateFilePayload): Promise<FileDetailVO> {
  return request<FileDetailVO>(
    '/api/files',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function updateFile(id: number, payload: UpdateFilePayload): Promise<FileDetailVO> {
  return request<FileDetailVO>(
    `/api/files/${id}`,
    {
      method: 'PUT',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function createFolder(payload: CreateFolderPayload): Promise<FileTreeNodeVO> {
  return request<FileTreeNodeVO>(
    '/api/files/folders',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function renameNode(id: number, payload: RenameNodePayload): Promise<FileTreeNodeVO> {
  return request<FileTreeNodeVO>(
    `/api/files/${id}/rename`,
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function moveNode(id: number, payload: MoveNodePayload): Promise<FileTreeNodeVO> {
  return request<FileTreeNodeVO>(
    `/api/files/${id}/move`,
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    },
    'user',
  )
}

export function deleteNode(id: number): Promise<void> {
  return request<void>(`/api/files/${id}`, { method: 'DELETE' }, 'user')
}

export async function saveFile(payload: SaveFilePayload): Promise<SaveFileResult> {
  if (!getAuthToken('user')) {
    throw new Error('请先登录后再保存')
  }

  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new Error('网络已断开，请检查网络后重试')
  }

  const remoteId = toRemoteId(payload.id)

  try {
    const file = await updateFile(remoteId, {
      title: payload.name,
      originalName: payload.name,
      content: payload.content,
    })
    return { savedAt: file.updatedAt, remoteId: file.id }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      throw new Error('文件已不存在，请刷新文件树后重试')
    }
    throw error
  }
}
