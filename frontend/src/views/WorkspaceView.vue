<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { saveFile, toRemoteId, updateFile } from '@/api/files'
import FileCreateDialog from '@/components/FileCreateDialog.vue'
import FileNodeActionDialog from '@/components/FileNodeActionDialog.vue'
import ImportConfirmDialog from '@/components/ImportConfirmDialog.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import ModeSwitch from '@/components/ModeSwitch.vue'
import MultiFunctionBar from '@/components/MultiFunctionBar.vue'
import PreviewPane from '@/components/PreviewPane.vue'
import SourceEditor from '@/components/SourceEditor.vue'
import TopLeftEntry from '@/components/TopLeftEntry.vue'
import WysiwygEditor from '@/components/WysiwygEditor.vue'
import { useBackground } from '@/composables/useBackground'
import { useCurrentUser } from '@/composables/useCurrentUser'
import { useFileExplorer } from '@/composables/useFileExplorer'
import { useToast } from '@/composables/useToast'
import { useUserAuth } from '@/composables/useUserAuth'
import { deleteDraft, readDraft, readLatestDraft, writeDraft } from '@/storage/workspaceDb'
import type { ImportSummary } from '@/composables/useFileExplorer'
import type { DraftRecord } from '@/storage/workspaceDb'
import type { ImportCandidate, MarkdownMode } from '@/types/workspace'
import { countWords, countWordsFromHtml, htmlToMarkdown, markdownToHtml } from '@/utils/markdown'

const VALID_MODES: MarkdownMode[] = ['wysiwyg', 'source', 'preview']
const LOGIN_PROMPT_KEY = 'md-reader:login-prompt-seen:v1'
const EDITOR_AUTOSAVE_DELAY_MS = 4000
const FILE_AUTOSAVE_INTERVAL_MS = 10 * 60 * 1000

type LoginIntent = 'save' | 'welcome'

interface PendingImport {
  kind: 'file' | 'folder'
  candidates: ImportCandidate[]
}

type SaveFeedback = 'idle' | 'saving' | 'saved' | 'error'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const { isDark } = useBackground()
const { user, status: userStatus, reload: reloadUser } = useCurrentUser()
const explorer = useFileExplorer()

const preferredFileId = typeof route.query.file === 'string' ? route.query.file : undefined

function parseMode(value: unknown): MarkdownMode {
  return typeof value === 'string' && VALID_MODES.includes(value as MarkdownMode)
    ? (value as MarkdownMode)
    : 'wysiwyg'
}

async function restoreLocalDraft() {
  const draft = await readLatestDraft()
  const file = explorer.selectedFile.value
  if (!draft || !file || draft.fileId !== file.id) return

  explorer.updateContent(file.id, draft.content)
  if (mode.value === 'wysiwyg') html.value = markdownToHtml(draft.content)
  console.info('[草稿] 已恢复本地暂存内容', { id: file.id, savedAt: draft.savedAt })
}

async function persistLocalDraftNow() {
  if (localDraftTimer) {
    clearTimeout(localDraftTimer)
    localDraftTimer = undefined
  }

  const file = selectedFile.value
  if (!file || !explorer.isFileDirty(file)) return

  const draft: DraftRecord = {
    fileId: file.id,
    name: file.name,
    path: file.path,
    content: file.content ?? '',
    savedAt: new Date().toISOString(),
  }

  await writeDraft(draft)
}

function scheduleLocalDraft() {
  if (localDraftTimer) clearTimeout(localDraftTimer)
  localDraftTimer = setTimeout(() => {
    void persistLocalDraftNow()
  }, EDITOR_AUTOSAVE_DELAY_MS)
}

async function clearLocalDraft(fileId: string) {
  if (localDraftTimer) {
    clearTimeout(localDraftTimer)
    localDraftTimer = undefined
  }

  await deleteDraft(fileId)
}

const initialMode = parseMode(route.query.mode)
const mode = ref<MarkdownMode>(initialMode)
const html = ref(markdownToHtml(explorer.selectedFile.value?.content ?? ''))
const collapsed = ref(window.matchMedia('(max-width: 820px)').matches)
const saving = ref(false)
const saveFeedback = ref<SaveFeedback>('idle')
const editorDragDepth = ref(0)
const pendingImport = ref<PendingImport | null>(null)
const createDialogOpen = ref(false)
const createMode = ref<'file' | 'folder'>('file')
const createParentId = ref<string | null>(null)
const creating = ref(false)
const nodeDialogOpen = ref(false)
const nodeDialogMode = ref<'rename' | 'move' | 'delete'>('rename')
const nodeTargetId = ref<string | null>(null)
const nodeDialogTarget = computed(() => {
  if (!nodeTargetId.value) return null
  const walk = (nodes: typeof explorer.tree.value): (typeof nodes)[number] | null => {
    for (const node of nodes) {
      if (node.id === nodeTargetId.value) return node
      const found = node.children ? walk(node.children) : null
      if (found) return found
    }
    return null
  }
  return walk(explorer.tree.value)
})
const nodeBusy = ref(false)
const loginDialogOpen = ref(false)
const loginIntent = ref<LoginIntent>('save')
const publishOpen = ref(false)
const publishing = ref(false)
const { isAuthenticated: isUserAuthenticated } = useUserAuth()

const publicReadTarget = computed(() => {
  // 工作台文件多为私有草稿，直接进 /read/:id 会 404（仅 PUBLIC+PUBLISHED 可读），
  // 统一回到公开列表，由列表进入已发布的文章。
  return '/'
})

const loginDialogTitle = computed(() =>
  loginIntent.value === 'save' ? '登录后保存' : '请登录',
)

const loginDialogDescription = computed(() =>
  loginIntent.value === 'save'
    ? '登录成功后将自动把本地暂存内容同步到云端。'
    : '登录后即可保存 Markdown 并同步到云端；也可以关闭后继续本地编辑。',
)

function openLogin(intent: LoginIntent) {
  loginIntent.value = intent
  loginDialogOpen.value = true
}

function promptLoginOnFirstVisit() {
  if (isUserAuthenticated.value) return

  try {
    if (window.sessionStorage.getItem(LOGIN_PROMPT_KEY) === '1') return
    window.sessionStorage.setItem(LOGIN_PROMPT_KEY, '1')
  } catch {
    return
  }

  openLogin('welcome')
}

let htmlTimer: ReturnType<typeof setTimeout> | undefined
let saveFeedbackTimer: ReturnType<typeof setTimeout> | undefined
let localDraftTimer: ReturnType<typeof setTimeout> | undefined
let remoteAutosaveTimer: ReturnType<typeof setInterval> | undefined
const hydratedRemoteFiles = new Set<string>()
const wysiwygDirty = ref(false)

const selectedFile = explorer.selectedFile
const isDirty = computed(
  () =>
    explorer.isFileDirty(selectedFile.value) ||
    (mode.value === 'wysiwyg' && wysiwygDirty.value),
)
const wordCount = computed(() => {
  if (!selectedFile.value) return 0
  return mode.value === 'wysiwyg'
    ? countWordsFromHtml(html.value)
    : countWords(selectedFile.value.content ?? '')
})
const saveState = computed(() => {
  if (saveFeedback.value === 'saving') return { label: '保存中…', tone: 'saving' }
  if (saveFeedback.value === 'error') return { label: '保存失败', tone: 'error' }
  if (isDirty.value) return { label: '未保存', tone: 'dirty' }
  if (saveFeedback.value === 'saved') return { label: '已保存', tone: 'saved' }
  return null
})
const editorDragOver = computed(() => editorDragDepth.value > 0)

const importNames = computed(() =>
  (pendingImport.value?.candidates ?? []).map((candidate) =>
    candidate.relativePath.length > 0
      ? `${candidate.relativePath.join('/')}/${candidate.file.name}`
      : candidate.file.name,
  ),
)

const importTitle = computed(() =>
  pendingImport.value?.kind === 'folder' ? '导入文件夹' : '导入文件',
)

const importDescription = computed(() => {
  const count = pendingImport.value?.candidates.length ?? 0
  if (pendingImport.value?.kind === 'folder') {
    return `检测到 ${count} 个文件，将保留文件夹结构导入到左侧资源管理器。`
  }
  return `检测到 ${count} 个文件，是否导入到左侧资源管理器？`
})

if (route.query.mode !== initialMode) {
  void router.replace({ query: { ...route.query, mode: initialMode } })
}

function commitWysiwyg() {
  if (htmlTimer) {
    clearTimeout(htmlTimer)
    htmlTimer = undefined
  }

  if (!wysiwygDirty.value) return

  const file = selectedFile.value
  if (!file) {
    wysiwygDirty.value = false
    return
  }

  const markdown = htmlToMarkdown(html.value)
  explorer.updateContent(file.id, markdown)
  wysiwygDirty.value = false
}

function handleHtmlUpdate(value: string) {
  html.value = value

  if (htmlTimer) clearTimeout(htmlTimer)
  htmlTimer = setTimeout(commitWysiwyg, 260)
}

function handleWysiwygEdited() {
  noteEdit()
  wysiwygDirty.value = true
}

function applyMode(next: MarkdownMode, syncUrl: boolean) {
  if (next === mode.value) return

  if (mode.value === 'wysiwyg') commitWysiwyg()

  mode.value = next

  if (next === 'wysiwyg') {
    html.value = markdownToHtml(selectedFile.value?.content ?? '')
    wysiwygDirty.value = false
  }

  if (syncUrl) {
    void router.replace({ query: { ...route.query, mode: next } })
  }

  console.info('[模式] 切换', next)
}

function resetSaveFeedback() {
  if (saveFeedbackTimer) {
    clearTimeout(saveFeedbackTimer)
    saveFeedbackTimer = undefined
  }
  saveFeedback.value = 'idle'
}

function noteEdit() {
  if (saveFeedback.value !== 'saved' && saveFeedback.value !== 'error') return
  resetSaveFeedback()
}

function handleSourceUpdate(value: string) {
  if (!selectedFile.value) return
  noteEdit()
  explorer.updateContent(selectedFile.value.id, value)
}

function handleSelectFile(id: string) {
  if (id === selectedFile.value?.id) return
  if (mode.value === 'wysiwyg') commitWysiwyg()
  void persistLocalDraftNow()
  explorer.selectFile(id)
  console.info('[文件] 选择文件', id)
}

function handleToggleFolder(id: string) {
  explorer.toggleFolder(id)
}

function toggleBar() {
  collapsed.value = !collapsed.value
  console.info('[按钮] 切换多功能栏', collapsed.value ? '收起' : '展开')
}

function reportImport(summary: ImportSummary) {
  console.info('[导入] 结果', summary)

  if (summary.imported > 0) {
    showToast(
      summary.failed > 0
        ? `已导入 ${summary.imported} 个文件，${summary.failed} 个未通过`
        : `已导入 ${summary.imported} 个文件`,
      summary.failed > 0 ? 'warn' : 'success',
    )
    return
  }

  showToast('导入失败，请确认文件为 .md / .markdown / .txt', 'warn')
}

async function handleImportFiles(files: File[]) {
  if (files.length === 0) return
  if (!isUserAuthenticated.value) {
    openLogin('welcome')
    return
  }

  if (mode.value === 'wysiwyg') commitWysiwyg()

  try {
    const summary = await explorer.importFiles(files)
    reportImport(summary)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '导入失败，请稍后重试', 'warn')
  }
}

function directoryRelativePath(file: File): string[] {
  const parts = (file.webkitRelativePath || '').split('/').filter(Boolean)
  return parts.length > 1 ? parts.slice(0, -1) : []
}

function handleImportDirectory(files: File[]) {
  if (files.length === 0) return
  if (!isUserAuthenticated.value) {
    openLogin('welcome')
    return
  }

  pendingImport.value = {
    kind: 'folder',
    candidates: files.map((file) => ({ file, relativePath: directoryRelativePath(file) })),
  }
}

function openCreateDialog(nextMode: 'file' | 'folder') {
  if (!isUserAuthenticated.value) {
    openLogin('welcome')
    return
  }

  if (mode.value === 'wysiwyg') commitWysiwyg()
  void persistLocalDraftNow()
  createMode.value = nextMode
  createParentId.value = null
  createDialogOpen.value = true
}

function closeCreateDialog() {
  if (creating.value) return
  createDialogOpen.value = false
}

function openNodeDialog(mode: 'rename' | 'move' | 'delete', id: string) {
  if (!isUserAuthenticated.value) {
    openLogin('welcome')
    return
  }
  nodeDialogMode.value = mode
  nodeTargetId.value = id
  nodeDialogOpen.value = true
}

function closeNodeDialog() {
  if (nodeBusy.value) return
  nodeDialogOpen.value = false
  nodeTargetId.value = null
}

async function handleNodeSubmit(payload: { name: string; parentId: string | null }) {
  const id = nodeTargetId.value
  if (!id || nodeBusy.value) return
  nodeBusy.value = true
  try {
    if (nodeDialogMode.value === 'rename') {
      await explorer.renameFileNode(id, payload.name)
      showToast('重命名成功', 'success')
    } else if (nodeDialogMode.value === 'move') {
      await explorer.moveFileNode(id, payload.parentId ?? undefined)
      showToast('移动成功', 'success')
    }
    nodeDialogOpen.value = false
    nodeTargetId.value = null
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败，请重试', 'warn')
  } finally {
    nodeBusy.value = false
  }
}

async function handleNodeConfirm() {
  const id = nodeTargetId.value
  if (!id || nodeBusy.value) return
  nodeBusy.value = true
  try {
    const target = nodeDialogTarget.value
    await explorer.deleteFileNode(id)
    await explorer.persistNow()
    showToast(target?.type === 'folder' ? '文件夹已删除' : '文件已删除', 'success')
    nodeDialogOpen.value = false
    nodeTargetId.value = null
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败，请重试', 'warn')
  } finally {
    nodeBusy.value = false
  }
}

async function handleCreateSubmit(payload: { name: string; parentId: string | null }) {
  if (creating.value) return
  creating.value = true

  try {
    const node =
      createMode.value === 'file'
        ? await explorer.createFile(payload.name, payload.parentId ?? undefined)
        : await explorer.createFolder(payload.name, payload.parentId ?? undefined)

    createDialogOpen.value = false
    showToast(
      createMode.value === 'file' ? `已新建文件 ${node.name}` : `已新建文件夹 ${node.name}`,
      'success',
    )
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败，请重试', 'warn')
  } finally {
    creating.value = false
  }
}

function exportMarkdownName(name: string) {
  const base = name.replace(/\.(md|markdown|txt)$/i, '').trim() || '未命名'
  return `${base}.md`
}

function handleExportMarkdown() {
  if (mode.value === 'wysiwyg') commitWysiwyg()

  const file = selectedFile.value
  if (!file) {
    showToast('请先选择要导出的 Markdown 文件', 'warn')
    return
  }

  const blob = new Blob([file.content ?? ''], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = exportMarkdownName(file.name)
  anchor.style.display = 'none'

  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)

  showToast(`已导出 ${anchor.download}`, 'success')
}

async function handleExplorerDrop(dataTransfer: DataTransfer) {
  const candidates = await explorer.collectFromDataTransfer(dataTransfer)
  if (candidates.length === 0) return

  pendingImport.value = {
    kind: candidates.some((candidate) => candidate.relativePath.length > 0) ? 'folder' : 'file',
    candidates,
  }
}

function handleEditorDragEnter(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  editorDragDepth.value += 1
}

function handleEditorDragOver(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
}

function handleEditorDragLeave(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  editorDragDepth.value = Math.max(0, editorDragDepth.value - 1)
}

function requestFileImport(files: File[]) {
  if (files.length === 0) return
  pendingImport.value = {
    kind: 'file',
    candidates: files.map((file) => ({ file, relativePath: [] })),
  }
}

function handleEditorDrop(event: DragEvent) {
  editorDragDepth.value = 0
  if (!event.dataTransfer) return

  const files = Array.from(event.dataTransfer.files)
  if (files.length === 0) return

  event.preventDefault()
  requestFileImport(files)
}

async function confirmImport() {
  const pending = pendingImport.value
  if (!pending) return

  if (!isUserAuthenticated.value) {
    pendingImport.value = null
    openLogin('welcome')
    return
  }

  pendingImport.value = null
  if (mode.value === 'wysiwyg') commitWysiwyg()

  try {
    const summary = await explorer.importCandidates(pending.candidates)
    reportImport(summary)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '导入失败，请稍后重试', 'warn')
  }
}

function cancelImport() {
  pendingImport.value = null
}

async function hydrateRemoteContent(fileId: string) {
  if (!isUserAuthenticated.value || hydratedRemoteFiles.has(fileId)) return

  const file = selectedFile.value
  if (!file || file.id !== fileId || explorer.isFileDirty(file)) return

  const draft = await readDraft(fileId)
  if (draft) {
    const current = selectedFile.value
    if (!current || current.id !== fileId || explorer.isFileDirty(current)) return
    explorer.updateContent(fileId, draft.content)
    if (mode.value === 'wysiwyg') html.value = markdownToHtml(draft.content)
    return
  }

  try {
    const content = await explorer.hydrateContent(fileId)
    if (content === null) return

    const current = selectedFile.value
    if (!current || current.id !== fileId || explorer.isFileDirty(current)) return

    if (mode.value === 'wysiwyg') html.value = markdownToHtml(content)
    hydratedRemoteFiles.add(fileId)
  } catch (error) {
    console.warn('[同步] 云端正文读取失败', error)
  }
}

async function saveCurrent(options: { silent?: boolean } = {}) {
  if (mode.value === 'wysiwyg') commitWysiwyg()

  const file = selectedFile.value
  if (!file || saving.value) return

  if (!isUserAuthenticated.value) {
    void persistLocalDraftNow()
    if (!options.silent) openLogin('save')
    return
  }

  if (!explorer.isFileDirty(file)) {
    if (!options.silent) showToast('当前文件没有未保存更改')
    return
  }

  const contentAtSave = file.content ?? ''
  if (saveFeedbackTimer) {
    clearTimeout(saveFeedbackTimer)
    saveFeedbackTimer = undefined
  }
  saving.value = true
  saveFeedback.value = 'saving'

  try {
    await saveFile({ id: file.id, name: file.name, path: file.path, content: contentAtSave })

    if ((file.content ?? '') !== contentAtSave) {
      saveFeedback.value = 'idle'
      if (!options.silent) showToast('保存期间内容有更新，请再次保存', 'warn')
      return
    }

    explorer.markSaved(file.id)
    await clearLocalDraft(file.id)

    if (selectedFile.value?.id === file.id) {
      saveFeedback.value = 'saved'
      saveFeedbackTimer = setTimeout(() => {
        if (saveFeedback.value === 'saved') saveFeedback.value = 'idle'
      }, 1800)
    }

    if (!options.silent) showToast('已保存并同步到云端', 'success')
    console.info('[保存] 成功', { id: file.id, path: file.path, silent: options.silent })
  } catch (error) {
    const message = error instanceof Error ? error.message : '保存失败，请稍后重试'
    saveFeedback.value = 'error'
    saveFeedbackTimer = setTimeout(() => {
      if (saveFeedback.value === 'error') saveFeedback.value = 'idle'
    }, 3200)
    if (!options.silent) showToast(message, 'warn')
    console.error('[保存] 失败', error)
  } finally {
    saving.value = false
  }
}

async function handleLoginSuccess() {
  const intent = loginIntent.value
  loginDialogOpen.value = false
  await reloadUser()
  await explorer.refresh({ silent: true })

  if (selectedFile.value) await hydrateRemoteContent(selectedFile.value.id)

  if (intent === 'welcome') {
    showToast('登录成功', 'success')
    return
  }

  showToast('登录成功，正在同步本地暂存…', 'success')
  await saveCurrent()
}

function openPublish() {
  if (!isUserAuthenticated.value) {
    openLogin('save')
    return
  }
  const file = selectedFile.value
  if (!file) {
    showToast('请先选择要发布的文件', 'warn')
    return
  }
  if (mode.value === 'wysiwyg') commitWysiwyg()
  publishOpen.value = true
}

function closePublish() {
  if (publishing.value) return
  publishOpen.value = false
}

async function confirmPublish() {
  const file = selectedFile.value
  if (!file || publishing.value) return
  publishing.value = true
  try {
    if (mode.value === 'wysiwyg') commitWysiwyg()
    if (explorer.isFileDirty(file)) {
      await saveFile({ id: file.id, name: file.name, path: file.path, content: file.content ?? '' })
      explorer.markSaved(file.id)
      await clearLocalDraft(file.id)
    }
    const remoteId = toRemoteId(file.id)
    await updateFile(remoteId, { visibility: 'PUBLIC', status: 'PUBLISHED' })
    await explorer.refresh({ silent: true })
    publishOpen.value = false
    showToast('已发布，所有人可见', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发布失败，请稍后重试', 'warn')
  } finally {
    publishing.value = false
  }
}

async function copySource() {
  const markdown = selectedFile.value?.content ?? ''

  try {
    await navigator.clipboard.writeText(markdown)
    showToast('Markdown 源码已复制', 'success')
    console.info('[复制] 源码', { length: markdown.length })
  } catch {
    showToast('复制失败，请手动选择文本复制', 'warn')
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (mode.value === 'wysiwyg') commitWysiwyg()
  void persistLocalDraftNow()
  void explorer.persistNow()

  if (!explorer.hasDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function startRemoteAutosave() {
  if (remoteAutosaveTimer) clearInterval(remoteAutosaveTimer)
  remoteAutosaveTimer = window.setInterval(() => {
    if (!isUserAuthenticated.value || saving.value) return
    const file = selectedFile.value
    if (!file || !explorer.isFileDirty(file)) return
    void saveCurrent({ silent: true })
  }, FILE_AUTOSAVE_INTERVAL_MS)
}

function handleOffline() {
  showToast('网络已断开，保存将失败', 'warn')
}

function handleOnline() {
  showToast('网络已恢复', 'success')
}

watch(
  () => route.query.mode,
  (value) => {
    if (route.name !== 'workspace') return
    const next = parseMode(value)
    if (next !== mode.value) applyMode(next, false)
  },
)

watch(
  selectedFile,
  (file) => {
    document.title = file ? `${file.name} · MD Reader` : 'MD Reader'
    wysiwygDirty.value = false
    resetSaveFeedback()

    if (mode.value === 'wysiwyg') {
      html.value = markdownToHtml(file?.content ?? '')
    }
  },
  { immediate: true },
)

watch(
  () => selectedFile.value?.id,
  (fileId) => {
    if (fileId) void hydrateRemoteContent(fileId)
  },
  { immediate: true },
)

watch(
  () => selectedFile.value?.content,
  () => {
    const file = selectedFile.value
    if (!file || !explorer.isFileDirty(file)) return
    scheduleLocalDraft()
  },
)

watch(isUserAuthenticated, (authenticated) => {
  if (!authenticated) explorer.reset()
})

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('online', handleOnline)

  startRemoteAutosave()
  await explorer.init(preferredFileId)
  await restoreLocalDraft()

  if (explorer.restored.value) {
    console.info('[会话] 已恢复上次打开的文件树')
  }

  if (isUserAuthenticated.value) void reloadUser()

  promptLoginOnFirstVisit()

  void nextTick(() => {
    console.info('[布局] 多功能栏默认状态', collapsed.value ? '收起（移动端）' : '展开（桌面端）')
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('online', handleOnline)

  if (mode.value === 'wysiwyg') commitWysiwyg()
  void persistLocalDraftNow()
  void explorer.persistNow()

  if (remoteAutosaveTimer) {
    clearInterval(remoteAutosaveTimer)
    remoteAutosaveTimer = undefined
  }

  if (htmlTimer) {
    clearTimeout(htmlTimer)
    htmlTimer = undefined
  }

  if (localDraftTimer) {
    clearTimeout(localDraftTimer)
    localDraftTimer = undefined
  }

  if (saveFeedbackTimer) {
    clearTimeout(saveFeedbackTimer)
    saveFeedbackTimer = undefined
  }
})
</script>

<template>
  <div class="workspace">
    <TopLeftEntry :collapsed="collapsed" @toggle="toggleBar" />

    <MultiFunctionBar
      :collapsed="collapsed"
      :tree="explorer.tree.value"
      :selected-id="explorer.selectedId.value"
      :expanded-ids="explorer.expandedIds.value"
      :user="user"
      :user-status="userStatus"
      @select="handleSelectFile"
      @toggle="handleToggleFolder"
      @rename="openNodeDialog('rename', $event)"
      @move="openNodeDialog('move', $event)"
      @remove="openNodeDialog('delete', $event)"
      @import-files="handleImportFiles"
      @import-directory="handleImportDirectory"
      @create-file="openCreateDialog('file')"
      @create-folder="openCreateDialog('folder')"
      @export-file="handleExportMarkdown"
      @drop-items="handleExplorerDrop"
      @retry-user="reloadUser"
      @login="openLogin('welcome')"
    />

    <main class="workspace__main" :class="{ 'is-bar-collapsed': collapsed }">
      <header class="workspace__head">
        <div class="workspace__title-group">
          <h1 class="workspace__title">{{ selectedFile?.name ?? '未打开文件' }}</h1>
        </div>

        <div class="workspace__tools">
          <span
            v-if="saveState"
            class="workspace__status"
            :class="[`is-${saveState.tone}`, { 'workspace__dirty': saveState.tone === 'dirty' }]"
          >
            {{ saveState.label }}
          </span>
          <RouterLink class="workspace__public-link" :to="publicReadTarget" title="打开公开阅读">
            公开阅读
          </RouterLink>
          <span class="workspace__words">{{ wordCount }} 字</span>
          <span class="workspace__actions">
            <button
              type="button"
              class="workspace__save"
              :disabled="!isDirty || saving"
              @click="saveCurrent()"
            >
              {{ saving ? '保存中…' : '保存' }}
            </button>
            <button
              type="button"
              class="workspace__publish"
              :disabled="!selectedFile || publishing"
              @click="openPublish"
            >
              {{ publishing ? '发布中…' : '发布' }}
            </button>
          </span>
          <ModeSwitch :model-value="mode" @update:model-value="applyMode($event, true)" />
        </div>
      </header>

      <div
        class="workspace__body"
        :class="{ 'is-dragover': editorDragOver }"
        @dragenter="handleEditorDragEnter"
        @dragover="handleEditorDragOver"
        @dragleave="handleEditorDragLeave"
        @drop="handleEditorDrop"
      >
        <div v-if="!selectedFile" class="workspace__empty">
          <p class="workspace__empty-title">还没有打开的文件</p>
          <p class="workspace__empty-desc">从左侧资源管理器选择文件，或点击左下角 + 导入 Markdown。</p>
        </div>

        <WysiwygEditor
          v-else-if="mode === 'wysiwyg'"
          :key="isDark ? 'dark' : 'light'"
          :model-value="html"
          :is-dark="isDark"
          @update:model-value="handleHtmlUpdate"
          @edited="handleWysiwygEdited"
          @import-files="requestFileImport"
        />

        <SourceEditor
          v-else-if="mode === 'source'"
          :model-value="selectedFile.content ?? ''"
          :dirty="isDirty"
          @update:model-value="handleSourceUpdate"
          @copy="copySource"
          @save="saveCurrent()"
        />

        <PreviewPane v-else :markdown="selectedFile.content ?? ''" :is-dark="isDark" />

        <Transition name="drop">
          <div v-if="editorDragOver" class="workspace__drop">
            <span class="workspace__drop-title">松开以导入文件</span>
            <span class="workspace__drop-desc">确认后会加入左侧文件资源管理器</span>
          </div>
        </Transition>
      </div>
    </main>

    <ImportConfirmDialog
      :open="pendingImport !== null"
      :title="importTitle"
      :description="importDescription"
      :names="importNames"
      :count="pendingImport?.candidates.length ?? 0"
      @confirm="confirmImport"
      @cancel="cancelImport"
    />

    <FileCreateDialog
      :open="createDialogOpen"
      :mode="createMode"
      :folders="explorer.folderOptions.value"
      :default-parent-id="createParentId"
      @close="closeCreateDialog"
      @submit="handleCreateSubmit"
    />

    <FileNodeActionDialog
      :open="nodeDialogOpen"
      :mode="nodeDialogMode"
      :node="nodeDialogTarget"
      :folders="explorer.folderOptions.value"
      @close="closeNodeDialog"
      @submit="handleNodeSubmit"
      @confirm="handleNodeConfirm"
    />

    <ImportConfirmDialog
      :open="publishOpen"
      title="发布到公开阅读"
      :description="`发布后所有人可见并可阅读《${selectedFile?.name ?? ''}》。确认发布吗？`"
      :names="selectedFile ? [selectedFile.name] : []"
      :count="selectedFile ? 1 : 0"
      @confirm="confirmPublish"
      @cancel="closePublish"
    />

    <LoginDialog
      :open="loginDialogOpen"
      :title="loginDialogTitle"
      :description="loginDialogDescription"
      @close="loginDialogOpen = false"
      @success="handleLoginSuccess"
    />
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100dvh;
  overflow: hidden;
  background: transparent;
}

.workspace__main {
  display: flex;
  min-width: 0;
  height: 100dvh;
  flex: 1;
  flex-direction: column;
  padding: 16px 22px 20px;
  transition: padding-left 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace__main.is-bar-collapsed .workspace__head {
  padding-left: 58px;
}

.workspace__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px 20px;
  padding: 4px 2px 14px;
  transition: padding-left 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace__title-group {
  min-width: 0;
}

.workspace__title {
  margin: 0;
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(20px, 2.2vw, 28px);
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.workspace__tools .mode-switch {
  margin-left: auto;
}

.workspace__status {
  padding: 3px 9px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--muted-strong);
  font-size: 10.5px;
  font-weight: 600;
}

.workspace__status.is-dirty {
  border-color: var(--warn-line, var(--line-strong));
  background: var(--warn-soft, var(--surface-sunken));
  color: var(--warn);
}

.workspace__status.is-saving,
.workspace__status.is-saved {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.workspace__status.is-error {
  border-color: var(--danger-line);
  background: var(--danger-soft);
  color: var(--danger);
}

.workspace__words {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.workspace__public-link {
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.workspace__public-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.workspace__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.workspace__save,
.workspace__publish {
  padding: 6px 13px;
  border: 1px solid var(--accent);
  border-radius: 7px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    opacity 0.15s ease;
}

.workspace__publish {
  border-color: var(--line-strong);
  background: var(--surface);
  color: var(--ink);
}

.workspace__publish:hover:not(:disabled) {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.workspace__save:hover:not(:disabled) {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.workspace__publish:disabled,
.workspace__save:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.workspace__save:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.workspace__body {
  position: relative;
  min-height: 0;
  flex: 1;
}

.workspace__empty {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  text-align: center;
}

.workspace__empty-title {
  margin: 0;
  color: var(--muted-strong);
  font-family: var(--font-serif);
  font-size: 20px;
}

.workspace__empty-desc {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}

.workspace__drop {
  position: absolute;
  inset: 0;
  z-index: 12;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 2px dashed var(--accent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--surface) 86%, transparent);
  backdrop-filter: blur(3px);
  pointer-events: none;
}

.workspace__drop-title {
  color: var(--accent-strong);
  font-size: 16px;
  font-weight: 600;
}

.workspace__drop-desc {
  color: var(--muted);
  font-size: 12px;
}

.drop-enter-active,
.drop-leave-active {
  transition: opacity 0.16s ease;
}

.drop-enter-from,
.drop-leave-to {
  opacity: 0;
}

@media (max-width: 820px) {
  .workspace__main {
    padding: 12px 14px 14px;
  }

  .workspace__head {
    padding-left: 58px;
  }

  .workspace__title {
    font-size: 20px;
  }

  .workspace__tools {
    width: 100%;
    justify-content: space-between;
  }
}

@media (max-width: 520px) {
  .workspace__words {
    display: none;
  }

  .workspace__head {
    gap: 8px 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace__main,
  .workspace__head {
    transition: none;
  }
}
</style>
