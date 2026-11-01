<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import type { FileNode } from '@/types/workspace'

const props = defineProps<{
  open: boolean
  mode: 'rename' | 'move' | 'delete'
  node: FileNode | null
  folders: Array<{ id: string; name: string; path: string; depth: number }>
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { name: string; parentId: string | null }]
  confirm: []
}>()

const name = ref('')
const parentId = ref('')
const error = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

const title = computed(() => {
  if (props.mode === 'rename') return `重命名${props.node?.type === 'folder' ? '文件夹' : '文件'}`
  if (props.mode === 'move') return '移动到文件夹'
  return '删除确认'
})

const description = computed(() => {
  if (props.mode === 'rename') return '同级目录下不能重名，文件名缺扩展名时会自动补 .md。'
  if (props.mode === 'move') return '选择目标文件夹，空表示移动到根目录。移动同级目录将不做任何更改。'
  if (props.node?.type === 'folder') return `将删除文件夹“${props.node?.name}”及其全部子文件，此操作不可撤销。`
  return `将删除文件“${props.node?.name}”，此操作不可撤销。`
})

const invalidMoveTargets = computed(() => {
  if (!props.node) return new Set<string>()
  const blocked = new Set<string>([props.node.id])
  if (props.node.type === 'folder') {
    const walk = (node: FileNode) => {
      for (const child of node.children ?? []) {
        blocked.add(child.id)
        walk(child)
      }
    }
    walk(props.node)
  }
  return blocked
})

function resetForm() {
  name.value = props.node?.name ?? ''
  parentId.value = ''
  error.value = ''
}

function close() {
  emit('close')
}

function submit() {
  const nextName = name.value.trim()
  if (!nextName) {
    error.value = '请输入名称'
    return
  }
  if (/[\\/]/.test(nextName)) {
    error.value = '名称不能包含斜杠'
    return
  }
  if (props.mode === 'move' && parentId.value && invalidMoveTargets.value.has(parentId.value)) {
    error.value = '不能移动到自身或子目录'
    return
  }
  error.value = ''
  if (props.mode === 'delete') {
    emit('confirm')
    return
  }
  emit('submit', { name: nextName, parentId: props.mode === 'move' ? parentId.value || null : null })
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    resetForm()
    if (props.mode === 'rename') {
      void nextTick(() => {
        inputRef.value?.focus()
        inputRef.value?.select()
      })
    }
  },
  { immediate: true },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="node-dialog">
      <div v-if="open" class="node-dialog" @click.self="close">
        <section
          class="node-dialog__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="node-dialog-title"
        >
          <header class="node-dialog__head">
            <h2 id="node-dialog-title" class="node-dialog__title">{{ title }}</h2>
            <button type="button" class="node-dialog__close" aria-label="关闭" @click="close">
              ×
            </button>
          </header>

          <p class="node-dialog__desc">{{ description }}</p>

          <form class="node-dialog__form" @submit.prevent="submit">
            <label v-if="mode === 'rename'" class="node-dialog__field">
              <span>名称</span>
              <input
                ref="inputRef"
                v-model="name"
                type="text"
                maxlength="120"
                @input="error = ''"
              />
            </label>

            <label v-if="mode === 'move'" class="node-dialog__field">
              <span>目标文件夹</span>
              <select v-model="parentId">
                <option value="">根目录</option>
                <option
                  v-for="folder in folders"
                  :key="folder.id"
                  :value="folder.id"
                  :disabled="invalidMoveTargets.has(folder.id)"
                >
                  {{ `${'　'.repeat(folder.depth)}${folder.name}` }}
                </option>
              </select>
            </label>

            <p v-if="error" class="node-dialog__error" role="alert">{{ error }}</p>

            <div class="node-dialog__actions">
              <button type="button" class="node-dialog__button" @click="close">取消</button>
              <button
                type="submit"
                class="node-dialog__button"
                :class="{
                  'node-dialog__button--primary': mode !== 'delete',
                  'node-dialog__button--danger': mode === 'delete',
                }"
              >
                {{ mode === 'delete' ? '确认删除' : '确认' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.node-dialog {
  position: fixed;
  inset: 0;
  z-index: 72;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(24, 21, 15, 0.4);
  backdrop-filter: blur(3px);
}

.node-dialog__panel {
  width: min(420px, 100%);
  padding: 22px 24px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.node-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.node-dialog__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
}

.node-dialog__close {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.node-dialog__desc {
  margin: 9px 0 6px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
  overflow-wrap: break-word;
}

.node-dialog__form {
  margin-top: 10px;
}

.node-dialog__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.node-dialog__field span {
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.node-dialog__field input,
.node-dialog__field select {
  width: 100%;
  height: 38px;
  padding: 0 11px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  outline: 0;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.node-dialog__field input:focus,
.node-dialog__field select:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.node-dialog__error {
  margin: 10px 0 0;
  color: var(--warn);
  font-size: 12px;
}

.node-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.node-dialog__button {
  padding: 8px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
  cursor: pointer;
}

.node-dialog__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.node-dialog__button--danger {
  border-color: var(--danger);
  background: var(--danger);
  color: #fff;
}
</style>
