import { computed, ref, watch } from 'vue'

import {
  createFile as createRemoteFile,
  createFolder as createRemoteFolder,
  deleteNode as deleteRemoteNode,
  fetchFileContent,
  fetchFileTree,
  moveNode as moveRemoteNode,
  renameNode as renameRemoteNode,
  toRemoteId,
} from '@/api/files'
import { getAuthToken } from '@/api/http'
import { useToast } from '@/composables/useToast'
import { readWorkspaceSnapshot, writeWorkspaceSnapshot } from '@/storage/workspaceDb'
import type { FileTreeNodeVO } from '@/types/api'
import type { FileNode, ImportCandidate } from '@/types/workspace'

const ALLOWED_FILE = /\.(md|markdown|txt)$/i

const tree = ref<FileNode[]>([])
const selectedId = ref<string | null>(null)
const expandedIds = ref<string[]>([])
const initialized = ref(false)
const restored = ref(false)
const ready = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

const { showToast } = useToast()

const selectedFile = computed<FileNode | null>(() =>
  selectedId.value ? findFile(tree.value, selectedId.value) : null,
)

const dirtyFiles = computed(() => flattenFiles(tree.value).filter((file) => isFileDirty(file)))
const hasDirty = computed(() => dirtyFiles.value.length > 0)

export interface FolderOption {
  id: string
  name: string
  path: string
  depth: number
}

const folderOptions = computed<FolderOption[]>(() => {
  const output: FolderOption[] = []

  const walk = (nodes: FileNode[], depth: number) => {
    for (const node of nodes) {
      if (node.type !== 'folder') continue
      output.push({ id: node.id, name: node.name, path: node.path, depth })
      walk(node.children ?? [], depth + 1)
    }
  }

  walk(tree.value, 0)
  return output
})

let persistTimer: ReturnType<typeof setTimeout> | undefined

function flattenFiles(nodes: FileNode[]): FileNode[] {
  return nodes.flatMap((node) => (node.type === 'file' ? [node] : flattenFiles(node.children ?? [])))
}

function firstFileId(nodes: FileNode[]): string | null {
  return flattenFiles(nodes)[0]?.id ?? null
}

function findNode(nodes: FileNode[], id: string): FileNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children) {
      const found = findNode(node.children, id)
      if (found) return found
    }
  }

  return null
}

function findFile(nodes: FileNode[], id: string): FileNode | null {
  const node = findNode(nodes, id)
  return node?.type === 'file' ? node : null
}

function collectFolderIds(nodes: FileNode[]): string[] {
  return nodes.flatMap((node) =>
    node.type === 'folder' ? [node.id, ...collectFolderIds(node.children ?? [])] : [],
  )
}

function normalizeFileName(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) throw new Error('请输入文件名')
  if (/[\\/]/.test(trimmed)) throw new Error('文件名不能包含斜杠')

  const withExtension = ALLOWED_FILE.test(trimmed) ? trimmed : `${trimmed}.md`
  const base = withExtension.replace(ALLOWED_FILE, '').trim()
  if (!base) throw new Error('请输入有效的文件名')
  if (withExtension.length > 120) throw new Error('文件名不能超过 120 个字符')

  return withExtension
}

function normalizeFolderName(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) throw new Error('请输入文件夹名称')
  if (/[\\/]/.test(trimmed)) throw new Error('文件夹名称不能包含斜杠')
  if (trimmed.length > 80) throw new Error('文件夹名称不能超过 80 个字符')

  return trimmed
}

function uniqueName(children: FileNode[], name: string, type: 'file' | 'folder'): string {
  const used = new Set(children.map((node) => node.name.toLowerCase()))
  if (!used.has(name.toLowerCase())) return name

  const dot = type === 'file' ? name.lastIndexOf('.') : -1
  const base = dot > 0 ? name.slice(0, dot) : name
  const extension = dot > 0 ? name.slice(dot) : ''
  let index = 2

  while (used.has(`${base} (${index})${extension}`.toLowerCase())) {
    index += 1
  }

  return `${base} (${index})${extension}`
}

function toFileNode(node: FileTreeNodeVO): FileNode {
  return {
    id: String(node.id),
    parentId: node.parentId == null ? null : String(node.parentId),
    name: node.name,
    path: node.path,
    type: node.type,
    children: node.children.map(toFileNode),
    size: node.size,
    updatedAt: node.updatedAt,
  }
}

function mergeNodeContent(nodes: FileNode[], previous: Map<string, FileNode>) {
  for (const node of nodes) {
    const old = previous.get(node.id)
    if (old?.content !== undefined) {
      node.content = old.content
      node.savedContent = old.savedContent
    }
    if (node.children && node.children.length > 0) mergeNodeContent(node.children, previous)
  }
}

function flattenNodeMap(nodes: FileNode[], output = new Map<string, FileNode>()) {
  for (const node of nodes) {
    output.set(node.id, node)
    flattenNodeMap(node.children ?? [], output)
  }
  return output
}

function insertNode(parentId: string | null | undefined, node: FileNode) {
  node.parentId = parentId ?? null

  if (!parentId) {
    tree.value.push(node)
  } else {
    const parent = findNode(tree.value, parentId)
    if (parent?.type === 'folder') {
      parent.children ??= []
      parent.children.push(node)
      expandFolder(parent.id)
    }
  }
}

function resolveTarget(parentId?: string): { children: FileNode[]; path: string } {
  if (parentId) {
    const node = findNode(tree.value, parentId)
    if (node?.type === 'folder') {
      node.children ??= []
      expandFolder(node.id)
      return { children: node.children, path: node.path }
    }
  }

  return { children: tree.value, path: '' }
}

function reset() {
  tree.value = []
  selectedId.value = null
  expandedIds.value = []
  ready.value = false
  restored.value = false
  error.value = null
  initialized.value = false
}

async function persistNow() {
  if (persistTimer) {
    clearTimeout(persistTimer)
    persistTimer = undefined
  }

  await writeWorkspaceSnapshot({
    tree: tree.value,
    selectedId: selectedId.value,
    expandedIds: expandedIds.value,
    updatedAt: new Date().toISOString(),
  })
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => {
    void persistNow()
  }, 260)
}

async function restoreSession(): Promise<boolean> {
  const stored = await readWorkspaceSnapshot()
  if (!stored) return false

  if (!Array.isArray(stored.tree)) return false

  tree.value = stored.tree
  selectedId.value = typeof stored.selectedId === 'string' ? stored.selectedId : null
  expandedIds.value = Array.isArray(stored.expandedIds)
    ? stored.expandedIds.filter((id): id is string => typeof id === 'string')
    : collectFolderIds(tree.value)
  restored.value = true

  if (!selectedId.value || !findFile(tree.value, selectedId.value)) {
    selectedId.value = firstFileId(tree.value)
  }

  return true
}

async function refresh(options: { silent?: boolean } = {}) {
  if (!getAuthToken('user')) {
    ready.value = true
    return
  }

  loading.value = true
  error.value = null

  try {
    const nodes = (await fetchFileTree()).map(toFileNode)
    const previous = flattenNodeMap(tree.value)
    mergeNodeContent(nodes, previous)

    tree.value = nodes
    const existingExpanded = expandedIds.value.filter((id) => Boolean(findNode(nodes, id)))
    expandedIds.value = existingExpanded.length > 0 ? existingExpanded : collectFolderIds(nodes)

    if (!selectedId.value || !findFile(nodes, selectedId.value)) {
      selectedId.value = firstFileId(nodes)
    }

    ready.value = true
    await persistNow()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '文件树加载失败'
    ready.value = true
    if (!options.silent) showToast(error.value, 'warn')
  } finally {
    loading.value = false
  }
}

async function init(preferredId?: string) {
  if (!initialized.value) {
    initialized.value = true
    await restoreSession()
    await refresh({ silent: true })
  }

  if (preferredId && findFile(tree.value, preferredId)) {
    selectedId.value = preferredId
  } else if (!selectedId.value || !findFile(tree.value, selectedId.value)) {
    selectedId.value = firstFileId(tree.value)
  }

  await persistNow()
}

function selectFile(id: string) {
  if (!findFile(tree.value, id)) return
  selectedId.value = id
}

function toggleFolder(id: string) {
  const node = findNode(tree.value, id)
  if (!node || node.type !== 'folder') return

  expandedIds.value = expandedIds.value.includes(id)
    ? expandedIds.value.filter((item) => item !== id)
    : [...expandedIds.value, id]
}

function expandFolder(id: string) {
  if (!expandedIds.value.includes(id)) {
    expandedIds.value = [...expandedIds.value, id]
  }
}

function updateContent(id: string, content: string) {
  const file = findFile(tree.value, id)
  if (!file) return
  if (file.content === content) return
  file.content = content
}

function markSaved(id: string) {
  const file = findFile(tree.value, id)
  if (!file) return
  file.savedContent = file.content ?? ''
}

function isFileDirty(file: FileNode | null): boolean {
  if (!file || file.type !== 'file' || file.content === undefined) return false
  if (file.savedContent === null) return true
  return file.content !== (file.savedContent ?? '')
}

async function hydrateContent(id: string): Promise<string | null> {
  const file = findFile(tree.value, id)
  if (!file) return null
  if (file.content !== undefined) return file.content

  const content = await fetchFileContent(toRemoteId(id))
  const current = findFile(tree.value, id)
  if (!current || current.content !== undefined) return current?.content ?? content

  current.content = content
  current.savedContent = content
  current.size = content.length
  return content
}

async function createFile(name: string, parentId?: string): Promise<FileNode> {
  if (!getAuthToken('user')) throw new Error('请先登录后新建文件')

  const target = resolveTarget(parentId)
  const finalName = uniqueName(target.children, normalizeFileName(name), 'file')
  const content = `# ${finalName.replace(ALLOWED_FILE, '')}\n`

  const created = await createRemoteFile({
    title: finalName,
    originalName: finalName,
    parentId: parentId ? toRemoteId(parentId) : null,
    content,
    visibility: 'PRIVATE',
    status: 'DRAFT',
  })

  await refresh({ silent: true })
  const node = findFile(tree.value, String(created.id))
  if (!node) throw new Error('文件已创建，但文件树刷新失败')

  selectedId.value = node.id
  return node
}

async function createFolder(name: string, parentId?: string): Promise<FileNode> {
  if (!getAuthToken('user')) throw new Error('请先登录后新建文件夹')

  const target = resolveTarget(parentId)
  const finalName = uniqueName(target.children, normalizeFolderName(name), 'folder')
  const created = await createRemoteFolder({
    name: finalName,
    parentId: parentId ? toRemoteId(parentId) : null,
  })

  const node = toFileNode(created)
  insertNode(parentId, node)
  await persistNow()
  return node
}

async function renameFileNode(id: string, name: string): Promise<FileNode> {
  if (!getAuthToken('user')) throw new Error('请先登录后重命名')

  const node = findNode(tree.value, id)
  if (!node) throw new Error('文件或文件夹不存在')

  const finalName =
    node.type === 'file' ? normalizeFileName(name) : normalizeFolderName(name)
  await renameRemoteNode(toRemoteId(id), { name: finalName })
  await refresh({ silent: true })

  const next = findNode(tree.value, id)
  if (!next) throw new Error('重命名成功，但文件树刷新失败')
  return next
}

async function moveFileNode(id: string, parentId?: string): Promise<FileNode> {
  if (!getAuthToken('user')) throw new Error('请先登录后移动')

  await moveRemoteNode(toRemoteId(id), {
    parentId: parentId ? toRemoteId(parentId) : null,
  })
  await refresh({ silent: true })

  const next = findNode(tree.value, id)
  if (!next) throw new Error('移动成功，但文件树刷新失败')
  return next
}

async function deleteFileNode(id: string): Promise<void> {
  if (!getAuthToken('user')) throw new Error('请先登录后删除')

  await deleteRemoteNode(toRemoteId(id))
  await refresh({ silent: true })

  if (!selectedId.value || !findFile(tree.value, selectedId.value)) {
    selectedId.value = firstFileId(tree.value)
  }
}

export interface ImportSummary {
  imported: number
  failed: number
  fileIds: string[]
}

async function ensureFolderPath(
  segments: string[],
  parentId: string | undefined,
): Promise<string | undefined> {
  let currentParentId = parentId

  for (const segment of segments) {
    const target = resolveTarget(currentParentId)
    const existing = target.children.find(
      (node) => node.type === 'folder' && node.name === segment,
    )

    if (existing) {
      currentParentId = existing.id
      continue
    }

    const folder = await createFolder(segment, currentParentId)
    currentParentId = folder.id
  }

  return currentParentId
}

async function importCandidates(
  candidates: ImportCandidate[],
  parentId?: string,
): Promise<ImportSummary> {
  if (!getAuthToken('user')) throw new Error('请先登录后导入文件')

  const fileIds: string[] = []
  let failed = 0

  for (const candidate of candidates) {
    if (!ALLOWED_FILE.test(candidate.file.name)) {
      failed += 1
      continue
    }

    try {
      const content = await candidate.file.text()
      const targetParentId = await ensureFolderPath(candidate.relativePath, parentId)
      const target = resolveTarget(targetParentId)
      const finalName = uniqueName(target.children, candidate.file.name, 'file')

      const created = await createRemoteFile({
        title: finalName,
        originalName: finalName,
        parentId: targetParentId ? toRemoteId(targetParentId) : null,
        content,
        visibility: 'PRIVATE',
        status: 'DRAFT',
      })

      fileIds.push(String(created.id))
    } catch (err) {
      failed += 1
      console.warn('[文件] 导入失败', candidate.file.name, err)
    }
  }

  if (fileIds.length > 0) {
    await refresh({ silent: true })
    const first = findFile(tree.value, fileIds[0] ?? '')
    if (first) selectedId.value = first.id
  }

  return { imported: fileIds.length, failed, fileIds }
}

async function importFiles(files: File[], parentId?: string): Promise<ImportSummary> {
  return importCandidates(
    files.map((file) => ({ file, relativePath: [] })),
    parentId,
  )
}

function readEntry(entry: FileSystemEntry, relativePath: string[], output: ImportCandidate[]): Promise<void> {
  if (entry.isFile) {
    const fileEntry = entry as FileSystemFileEntry
    return new Promise((resolve, reject) => {
      fileEntry.file(
        (file) => {
          output.push({ file, relativePath })
          resolve()
        },
        (error) => reject(error),
      )
    })
  }

  if (entry.isDirectory) {
    const directory = entry as FileSystemDirectoryEntry
    const reader = directory.createReader()

    return new Promise((resolve, reject) => {
      const readBatch = () => {
        reader.readEntries(
          (entries) => {
            if (entries.length === 0) {
              resolve()
              return
            }

            void Promise.all(
              entries.map((child) => readEntry(child, [...relativePath, entry.name], output)),
            ).then(readBatch, reject)
          },
          (error) => reject(error),
        )
      }

      readBatch()
    })
  }

  return Promise.resolve()
}

async function collectFromDataTransfer(dataTransfer: DataTransfer): Promise<ImportCandidate[]> {
  const output: ImportCandidate[] = []
  const entries: FileSystemEntry[] = []

  if (dataTransfer.items) {
    for (const item of Array.from(dataTransfer.items)) {
      if (item.kind !== 'file') continue
      const entry = item.webkitGetAsEntry?.()
      if (entry) entries.push(entry)
    }
  }

  if (entries.length === 0) {
    return Array.from(dataTransfer.files).map((file) => ({ file, relativePath: [] }))
  }

  for (const entry of entries) {
    try {
      await readEntry(entry, [], output)
    } catch (err) {
      console.warn('[文件] 读取拖入内容失败', entry.name, err)
    }
  }

  return output
}

watch([tree, selectedId, expandedIds], schedulePersist, { deep: true })

export function useFileExplorer() {
  return {
    tree,
    selectedId,
    selectedFile,
    expandedIds,
    folderOptions,
    dirtyFiles,
    hasDirty,
    restored,
    ready,
    loading,
    error,
    init,
    refresh,
    reset,
    selectFile,
    toggleFolder,
    updateContent,
    markSaved,
    isFileDirty,
    hydrateContent,
    createFile,
    createFolder,
    renameFileNode,
    moveFileNode,
    deleteFileNode,
    importFiles,
    importCandidates,
    collectFromDataTransfer,
    persistNow,
  }
}
