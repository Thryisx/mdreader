<script setup lang="ts">
import { computed, ref } from 'vue'

import FileTree from '@/components/FileTree.vue'
import UserBar from '@/components/UserBar.vue'
import type { UserStatus } from '@/composables/useCurrentUser'
import type { CurrentUser, FileNode } from '@/types/workspace'

const props = defineProps<{
  collapsed: boolean
  tree: FileNode[]
  selectedId: string | null
  expandedIds: string[]
  user: CurrentUser | null
  userStatus: UserStatus
}>()

const emit = defineEmits<{
  select: [id: string]
  toggle: [id: string]
  rename: [id: string]
  move: [id: string]
  remove: [id: string]
  'import-files': [files: File[]]
  'import-directory': [files: File[]]
  'create-file': []
  'create-folder': []
  'export-file': []
  'drop-items': [dataTransfer: DataTransfer]
  'retry-user': []
  login: []
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const directoryInput = ref<HTMLInputElement | null>(null)
const dragDepth = ref(0)
const filter = ref('')

const fileCount = computed(() => countFiles(props.tree))
const isDragOver = computed(() => dragDepth.value > 0)

function countFiles(nodes: FileNode[]): number {
  return nodes.reduce(
    (total, node) => total + (node.type === 'file' ? 1 : countFiles(node.children ?? [])),
    0,
  )
}

function openFilePicker() {
  fileInput.value?.click()
}

function openDirectoryPicker() {
  directoryInput.value?.click()
}

function handleFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length > 0) emit('import-files', files)
  input.value = ''
}

function handleDirectoryInput(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length > 0) emit('import-directory', files)
  input.value = ''
}

function handleDragEnter(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragDepth.value += 1
}

function handleDragOver(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
}

function handleDragLeave(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragDepth.value = Math.max(0, dragDepth.value - 1)
}

function handleDrop(event: DragEvent) {
  dragDepth.value = 0
  if (!event.dataTransfer) return
  event.preventDefault()
  emit('drop-items', event.dataTransfer)
}
</script>

<template>
  <aside
    id="multifunction-bar"
    class="mfb"
    :class="{ 'is-collapsed': collapsed, 'is-dragover': isDragOver }"
    :aria-hidden="collapsed"
    :inert="collapsed"
    @dragenter="handleDragEnter"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
  >
    <div class="mfb__inner">
      <header class="mfb__head">
        <span class="mfb__title">文件</span>
        <span v-if="fileCount > 0" class="mfb__count">{{ fileCount }}</span>
      </header>

      <div class="mfb__filter">
        <input
          v-model="filter"
          type="search"
          placeholder="过滤文件名"
          aria-label="过滤文件名"
        />
      </div>

      <div class="mfb__tree">
        <FileTree
          :nodes="tree"
          :selected-id="selectedId"
          :expanded-ids="expandedIds"
          :filter="filter"
          @select="$emit('select', $event)"
          @toggle="$emit('toggle', $event)"
          @rename="$emit('rename', $event)"
          @move="$emit('move', $event)"
          @remove="$emit('remove', $event)"
        />
      </div>

      <div class="mfb__actions">
        <button
          type="button"
          class="mfb__action"
          :disabled="!selectedId"
          title="导出当前 Markdown"
          @click="$emit('export-file')"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M10 3v9m0 0 3.5-3.5M10 12 6.5 8.5M4 14v2h12v-2" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>导出 Markdown</span>
        </button>

        <button type="button" class="mfb__action" title="导入 Markdown 文件" @click="openFilePicker">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M10 13V4m0 0 3.5 3.5M10 4 6.5 7.5M4 14v2h12v-2" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>导入文件</span>
        </button>

        <button type="button" class="mfb__action" title="导入整个目录" @click="openDirectoryPicker">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M2.5 5.5A1.5 1.5 0 0 1 4 4h3.2l1.4 1.6H16a1.5 1.5 0 0 1 1.5 1.5v7.4A1.5 1.5 0 0 1 16 16H4a1.5 1.5 0 0 1-1.5-1.5Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
            <path d="M10 8v5m-2.5-2.5h5" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
          </svg>
          <span>导入目录</span>
        </button>

        <button type="button" class="mfb__action" title="新建 Markdown 文件" @click="$emit('create-file')">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M5 2.8h6.2L15 6.6v10.6H5Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
            <path d="M11 3v4h4M10 10v4m-2-2h4" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>新建文件</span>
        </button>

        <button type="button" class="mfb__action" title="新建文件夹" @click="$emit('create-folder')">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M2.5 5.5A1.5 1.5 0 0 1 4 4h3.2l1.4 1.6H16a1.5 1.5 0 0 1 1.5 1.5v7.4A1.5 1.5 0 0 1 16 16H4a1.5 1.5 0 0 1-1.5-1.5Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
            <path d="M10 8v5m-2.5-2.5h5" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
          </svg>
          <span>新建文件夹</span>
        </button>
      </div>

      <UserBar
        :user="user"
        :status="userStatus"
        @retry="$emit('retry-user')"
        @login="$emit('login')"
      />
    </div>

    <input
      ref="fileInput"
      class="mfb__file"
      type="file"
      accept=".md,.markdown,.txt,text/markdown,text/plain"
      multiple
      @change="handleFileInput"
    />
    <input
      ref="directoryInput"
      class="mfb__file"
      type="file"
      webkitdirectory
      multiple
      @change="handleDirectoryInput"
    />
  </aside>
</template>

<style scoped>
.mfb {
  width: 264px;
  height: 100dvh;
  flex: none;
  overflow: hidden;
  transition:
    width 0.28s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.2s ease;
}

.mfb.is-collapsed {
  width: 0;
  opacity: 0;
  pointer-events: none;
}

.mfb__inner {
  display: flex;
  width: 264px;
  height: 100%;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 78%, transparent);
  padding-top: 64px;
  backdrop-filter: blur(16px);
}

.mfb__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px 10px;
}

.mfb__title {
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.mfb__count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.mfb__filter {
  padding: 0 12px 10px;
}

.mfb__filter input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
}

.mfb__filter input:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.mfb__tree {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
  transition: background 0.15s ease;
}

.mfb.is-dragover .mfb__tree {
  background: var(--accent-soft);
  outline: 2px dashed var(--accent-line);
  outline-offset: -6px;
}

.mfb__actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px 8px;
  border-top: 1px solid var(--line);
}

.mfb__action {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted-strong);
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.14s ease,
    color 0.14s ease;
}

.mfb__action:hover:not(:disabled) {
  background: var(--surface-sunken);
  color: var(--ink);
}

.mfb__action:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.mfb__action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.mfb__action svg {
  width: 15px;
  height: 15px;
  flex: none;
  color: var(--muted);
}

.mfb__action:hover:not(:disabled) svg {
  color: var(--accent);
}

.mfb__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 820px) {
  .mfb {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 45;
    width: 280px;
    height: 100dvh;
    box-shadow: var(--shadow-lg);
    transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .mfb.is-collapsed {
    width: 280px;
    opacity: 1;
    transform: translateX(-100%);
  }

  .mfb__inner {
    width: 280px;
    padding-top: 58px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mfb {
    transition: none;
  }
}
</style>
