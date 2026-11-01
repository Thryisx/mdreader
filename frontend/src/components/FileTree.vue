<script setup lang="ts">
import { computed, ref } from 'vue'

import type { FileNode } from '@/types/workspace'

const props = defineProps<{
  nodes: FileNode[]
  selectedId: string | null
  expandedIds: string[]
  filter: string
}>()

const emit = defineEmits<{
  select: [id: string]
  toggle: [id: string]
  rename: [id: string]
  move: [id: string]
  remove: [id: string]
}>()

interface Row {
  node: FileNode
  depth: number
}

const menuFor = ref<string | null>(null)
const menuPosition = ref({ x: 0, y: 0 })

function matches(node: FileNode, keyword: string): boolean {
  if (!keyword) return true
  if (node.name.toLowerCase().includes(keyword)) return true
  return (node.children ?? []).some((child) => matches(child, keyword))
}

const rows = computed<Row[]>(() => {
  const keyword = props.filter.trim().toLowerCase()
  const output: Row[] = []

  const walk = (nodes: FileNode[], depth: number) => {
    for (const node of nodes) {
      if (keyword && !matches(node, keyword)) continue
      output.push({ node, depth })
      const expanded = keyword ? true : props.expandedIds.includes(node.id)
      if (node.type === 'folder' && expanded) {
        walk(node.children ?? [], depth + 1)
      }
    }
  }

  walk(props.nodes, 0)
  return output
})

function isExpanded(id: string) {
  if (props.filter.trim()) return true
  return props.expandedIds.includes(id)
}

function openMenu(event: MouseEvent, id: string) {
  event.preventDefault()
  event.stopPropagation()
  menuFor.value = id
  const maxX = typeof window === 'undefined' ? event.clientX : window.innerWidth - 180
  const maxY = typeof window === 'undefined' ? event.clientY : window.innerHeight - 140
  menuPosition.value = { x: Math.min(event.clientX, maxX), y: Math.min(event.clientY, maxY) }
}

function closeMenu() {
  menuFor.value = null
}

function act(action: 'rename' | 'move' | 'remove', id: string) {
  closeMenu()
  if (action === 'rename') emit('rename', id)
  if (action === 'move') emit('move', id)
  if (action === 'remove') emit('remove', id)
}
</script>

<template>
  <div class="tree-wrap" @click="closeMenu">
    <ul v-if="rows.length > 0" class="tree" role="tree" aria-label="文件资源管理器">
      <li v-for="row in rows" :key="row.node.id" class="tree__item" role="none">
        <button
          v-if="row.node.type === 'folder'"
          type="button"
          class="tree__row tree__row--folder"
          role="treeitem"
          :aria-expanded="isExpanded(row.node.id)"
          :style="{ paddingLeft: `${10 + row.depth * 14}px` }"
          @click="$emit('toggle', row.node.id)"
          @contextmenu="openMenu($event, row.node.id)"
        >
          <svg
            class="tree__chevron"
            :class="{ 'is-open': isExpanded(row.node.id) }"
            viewBox="0 0 16 16"
            aria-hidden="true"
          >
            <path d="M6 3.5 10.5 8 6 12.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg class="tree__icon" viewBox="0 0 20 20" aria-hidden="true">
            <path
              d="M2.5 5.5A1.5 1.5 0 0 1 4 4h3.2l1.4 1.6H16a1.5 1.5 0 0 1 1.5 1.5v7.4A1.5 1.5 0 0 1 16 16H4a1.5 1.5 0 0 1-1.5-1.5Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.3"
              stroke-linejoin="round"
            />
          </svg>
          <span class="tree__name">{{ row.node.name }}</span>
        </button>

        <button
          v-else
          type="button"
          class="tree__row"
          :class="{ 'is-selected': row.node.id === selectedId }"
          role="treeitem"
          :aria-selected="row.node.id === selectedId"
          :style="{ paddingLeft: `${28 + row.depth * 14}px` }"
          @click="$emit('select', row.node.id)"
          @contextmenu="openMenu($event, row.node.id)"
        >
          <svg class="tree__icon tree__icon--file" viewBox="0 0 20 20" aria-hidden="true">
            <path
              d="M5 2.8h6.2L15 6.6v10.6H5Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.3"
              stroke-linejoin="round"
            />
            <path d="M11 3v4h4" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
          </svg>
          <span class="tree__name">{{ row.node.name }}</span>
          <span v-if="row.node.savedContent === null" class="tree__dot" aria-label="未保存" title="未保存" />
        </button>
      </li>
    </ul>
    <p v-else class="tree__empty">没有匹配的文件</p>

    <Teleport to="body">
      <div
        v-if="menuFor"
        class="tree-menu"
        role="menu"
        :style="{ left: `${menuPosition.x}px`, top: `${menuPosition.y}px` }"
        @click.stop
      >
        <button type="button" role="menuitem" @click="act('rename', menuFor)">重命名</button>
        <button type="button" role="menuitem" @click="act('move', menuFor)">移动</button>
        <button type="button" role="menuitem" class="is-danger" @click="act('remove', menuFor)">
          删除
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.tree-wrap {
  position: relative;
  min-height: 120px;
}

.tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0;
  padding: 0 6px;
  list-style: none;
}

.tree__item {
  min-width: 0;
}

.tree__row {
  display: flex;
  width: 100%;
  min-height: 30px;
  align-items: center;
  gap: 6px;
  padding-top: 4px;
  padding-right: 8px;
  padding-bottom: 4px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted-strong);
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.14s ease,
    color 0.14s ease;
}

.tree__row:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.tree__row:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.tree__row.is-selected {
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-weight: 600;
}

.tree__chevron {
  width: 12px;
  height: 12px;
  flex: none;
  color: var(--muted);
  transition: transform 0.18s ease;
}

.tree__chevron.is-open {
  transform: rotate(90deg);
}

.tree__icon {
  width: 15px;
  height: 15px;
  flex: none;
  color: var(--muted);
}

.tree__icon--file {
  color: color-mix(in srgb, var(--muted) 80%, var(--accent));
}

.tree__row.is-selected .tree__icon,
.tree__row.is-selected .tree__chevron {
  color: var(--accent);
}

.tree__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree__dot {
  width: 6px;
  height: 6px;
  flex: none;
  margin-left: auto;
  border-radius: 50%;
  background: var(--warn);
}

.tree__empty {
  padding: 16px;
  color: var(--muted);
  font-size: 12.5px;
}

.tree-menu {
  position: fixed;
  z-index: 80;
  display: flex;
  min-width: 150px;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.tree-menu button {
  padding: 7px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--ink);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.tree-menu button:hover {
  background: var(--surface-sunken);
}

.tree-menu button.is-danger {
  color: var(--danger);
}
</style>
