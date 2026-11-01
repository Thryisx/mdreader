import { request, requestText } from '@/api/http'
import type { FileCategoryVO, FileDetailVO, FileVO, PageResult } from '@/types/api'

export interface PublicFilePageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
}

export interface PublicFileDetail extends FileDetailVO {
  content: string
}

export function fetchPublicFiles(query: PublicFilePageQuery = {}): Promise<PageResult<FileVO>> {
  const params = new URLSearchParams()
  if (query.pageNum != null) params.set('pageNum', String(query.pageNum))
  if (query.pageSize != null) params.set('pageSize', String(query.pageSize))
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.category) params.set('category', query.category)
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<PageResult<FileVO>>(`/api/files${suffix}`, {}, 'guest')
}

/** 查询公开已发布文章的分类及数量，未分类项的 category 为 null。 */
export function fetchPublicFileCategories(): Promise<FileCategoryVO[]> {
  return request<FileCategoryVO[]>('/api/files/categories', {}, 'guest')
}

export function fetchPublicFileDetail(id: number): Promise<FileDetailVO> {
  return request<FileDetailVO>(`/api/files/${id}`, {}, 'guest')
}

export function fetchPublicFileContent(id: number): Promise<string> {
  return requestText(`/api/files/${id}/content`, {}, 'guest')
}

export async function fetchPublicFileWithContent(id: number): Promise<PublicFileDetail> {
  const [detail, content] = await Promise.all([
    fetchPublicFileDetail(id),
    fetchPublicFileContent(id),
  ])
  return { ...detail, content }
}
