import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

import type { FileNode } from '@/types/workspace'

const DB_NAME = 'md-reader'
const DB_VERSION = 1
const WORKSPACE_KEY = 'snapshot'
const LEGACY_DRAFT_KEY = 'md-reader:local-draft:v1'

export interface WorkspaceSnapshot {
  tree: FileNode[]
  selectedId: string | null
  expandedIds: string[]
  updatedAt: string
}

export interface DraftRecord {
  fileId: string
  name: string
  path: string
  content: string
  savedAt: string
}

interface WorkspaceDB extends DBSchema {
  workspace: {
    key: string
    value: WorkspaceSnapshot
  }
  drafts: {
    key: string
    value: DraftRecord
  }
  meta: {
    key: string
    value: unknown
  }
}

let dbPromise: Promise<IDBPDatabase<WorkspaceDB>> | null = null

function getDb() {
  if (!dbPromise) {
    dbPromise = openDB<WorkspaceDB>(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('workspace')) {
          db.createObjectStore('workspace')
        }
        if (!db.objectStoreNames.contains('drafts')) {
          db.createObjectStore('drafts', { keyPath: 'fileId' })
        }
        if (!db.objectStoreNames.contains('meta')) {
          db.createObjectStore('meta')
        }
      },
    })
  }

  return dbPromise
}

export async function readWorkspaceSnapshot(): Promise<WorkspaceSnapshot | null> {
  try {
    return (await (await getDb()).get('workspace', WORKSPACE_KEY)) ?? null
  } catch {
    return null
  }
}

export async function writeWorkspaceSnapshot(snapshot: WorkspaceSnapshot): Promise<void> {
  try {
    await (await getDb()).put('workspace', snapshot, WORKSPACE_KEY)
  } catch {
    return
  }
}

export async function readDraft(fileId: string): Promise<DraftRecord | null> {
  try {
    return (await (await getDb()).get('drafts', fileId)) ?? null
  } catch {
    return null
  }
}

export async function readLatestDraft(): Promise<DraftRecord | null> {
  try {
    const drafts = await (await getDb()).getAll('drafts')
    if (drafts.length > 0) {
      return drafts.sort((a, b) => b.savedAt.localeCompare(a.savedAt))[0] ?? null
    }
  } catch {
    return null
  }

  return migrateLegacyDraft()
}

async function migrateLegacyDraft(): Promise<DraftRecord | null> {
  try {
    const raw = window.localStorage.getItem(LEGACY_DRAFT_KEY)
    if (!raw) return null

    const parsed = JSON.parse(raw) as Partial<DraftRecord>
    if (typeof parsed.fileId !== 'string' || typeof parsed.content !== 'string') return null

    const draft: DraftRecord = {
      fileId: parsed.fileId,
      name: typeof parsed.name === 'string' ? parsed.name : '',
      path: typeof parsed.path === 'string' ? parsed.path : '',
      content: parsed.content,
      savedAt: typeof parsed.savedAt === 'string' ? parsed.savedAt : new Date().toISOString(),
    }

    await writeDraft(draft)
    window.localStorage.removeItem(LEGACY_DRAFT_KEY)
    return draft
  } catch {
    return null
  }
}

export async function writeDraft(draft: DraftRecord): Promise<void> {
  try {
    await (await getDb()).put('drafts', draft)
  } catch {
    return
  }
}

export async function deleteDraft(fileId: string): Promise<void> {
  try {
    await (await getDb()).delete('drafts', fileId)
  } catch {
    return
  }
}

export async function clearWorkspace(): Promise<void> {
  try {
    const db = await getDb()
    await db.clear('workspace')
    await db.clear('drafts')
  } catch {
    return
  }
}
