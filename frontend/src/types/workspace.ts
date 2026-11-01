export type FileNodeType = 'file' | 'folder'

export interface FileNode {
  id: string
  parentId?: string | null
  name: string
  path: string
  type: FileNodeType
  children?: FileNode[]
  content?: string
  savedContent?: string | null
  size?: number
  updatedAt?: string
}

export interface CurrentUser {
  id?: string
  nickname?: string
  username?: string
  email?: string
  avatar?: string
}

export type BackgroundPreset = 'light' | 'dark' | 'system' | 'custom'

export type MarkdownMode = 'wysiwyg' | 'source' | 'preview'

export interface SaveFilePayload {
  id: string
  name: string
  path: string
  content: string
}

export interface ImportCandidate {
  file: File
  relativePath: string[]
}
