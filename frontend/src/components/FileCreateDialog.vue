<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { FolderOption } from '@/composables/useFileExplorer'

const props = withDefaults(
  defineProps<{
    open: boolean
    mode: 'file' | 'folder'
    folders: FolderOption[]
    defaultParentId?: string | null
  }>(),
  {
    defaultParentId: null,
  },
)

const emit = defineEmits<{
  close: []
  submit: [payload: { name: string; parentId: string | null }]
}>()

const nameInput = ref<HTMLInputElement | null>(null)
const name = ref('')
const parentId = ref('')
const error = ref('')

const title = computed(() => (props.mode === 'file' ? '新建文件' : '新建文件夹'))
const description = computed(() =>
  props.mode === 'file'
    ? '文件名未带扩展名时自动添加 .md，创建后可直接编辑。'
    : '在所选位置创建文件夹，之后可以在其中继续新建文件或文件夹。',
)

function resetForm() {
  name.value = props.mode === 'file' ? '未命名.md' : '新建文件夹'
  parentId.value = props.defaultParentId ?? ''
  error.value = ''
}

function close() {
  emit('close')
}

function submit() {
  const nextName = name.value.trim()

  if (!nextName) {
    error.value = props.mode === 'file' ? '请输入文件名' : '请输入文件夹名称'
    return
  }

  if (/[\\/]/.test(nextName)) {
    error.value = '名称不能包含斜杠'
    return
  }

  error.value = ''
  emit('submit', { name: nextName, parentId: parentId.value || null })
}

function folderLabel(folder: FolderOption) {
  return `${'　'.repeat(folder.depth)}${folder.name}`
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return

    resetForm()
    void nextTick(() => {
      nameInput.value?.focus()
      nameInput.value?.select()
    })
  },
  { immediate: true },
)

onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="file-create">
      <div v-if="open" class="file-create" @click.self="close">
        <section
          class="file-create__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="file-create-title"
        >
          <header class="file-create__head">
            <h2 id="file-create-title" class="file-create__title">{{ title }}</h2>
            <button type="button" class="file-create__close" aria-label="关闭" @click="close">
              ×
            </button>
          </header>

          <p class="file-create__desc">{{ description }}</p>

          <form class="file-create__form" @submit.prevent="submit">
            <label class="file-create__field">
              <span>名称</span>
              <input
                ref="nameInput"
                v-model="name"
                type="text"
                maxlength="120"
                :placeholder="mode === 'file' ? '例如：读书笔记.md' : '例如：读书笔记'"
                @input="error = ''"
              />
            </label>

            <label class="file-create__field">
              <span>创建位置</span>
              <select v-model="parentId">
                <option value="">根目录</option>
                <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                  {{ folderLabel(folder) }}
                </option>
              </select>
            </label>

            <p v-if="error" class="file-create__error" role="alert">{{ error }}</p>

            <div class="file-create__actions">
              <button type="button" class="file-create__button" @click="close">取消</button>
              <button type="submit" class="file-create__button file-create__button--primary">
                创建
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.file-create {
  position: fixed;
  inset: 0;
  z-index: 72;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(24, 21, 15, 0.4);
  backdrop-filter: blur(3px);
}

.file-create__panel {
  width: min(420px, 100%);
  padding: 22px 24px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.file-create__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-create__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
}

.file-create__close {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
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

.file-create__close:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.file-create__desc {
  margin: 9px 0 18px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.file-create__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.file-create__field span {
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.file-create__field input,
.file-create__field select {
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

.file-create__field input:focus,
.file-create__field select:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.file-create__error {
  margin: 10px 0 0;
  color: var(--warn);
  font-size: 12px;
}

.file-create__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.file-create__button {
  padding: 8px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
  cursor: pointer;
}

.file-create__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.file-create__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.file-create__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.file-create__button:focus-visible,
.file-create__close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.file-create-enter-active,
.file-create-leave-active {
  transition: opacity 0.18s ease;
}

.file-create-enter-active .file-create__panel,
.file-create-leave-active .file-create__panel {
  transition: transform 0.18s ease;
}

.file-create-enter-from,
.file-create-leave-to {
  opacity: 0;
}

.file-create-enter-from .file-create__panel,
.file-create-leave-to .file-create__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
