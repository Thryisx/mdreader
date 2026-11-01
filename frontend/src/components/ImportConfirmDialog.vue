<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title: string
  description: string
  names: string[]
  count: number
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') emit('cancel')
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
  },
)

onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="dialog" @click.self="$emit('cancel')">
        <div class="dialog__panel" role="dialog" aria-modal="true" aria-labelledby="import-dialog-title">
          <h2 id="import-dialog-title" class="dialog__title">{{ title }}</h2>
          <p class="dialog__desc">{{ description }}</p>

          <ul v-if="names.length > 0" class="dialog__list">
            <li v-for="name in names.slice(0, 5)" :key="name" class="dialog__item">{{ name }}</li>
            <li v-if="count > 5" class="dialog__item dialog__item--more">等 {{ count }} 项</li>
          </ul>

          <div class="dialog__actions">
            <button type="button" class="dialog__button" @click="$emit('cancel')">取消</button>
            <button type="button" class="dialog__button dialog__button--primary" @click="$emit('confirm')">
              确认导入
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(24, 21, 15, 0.4);
  backdrop-filter: blur(3px);
}

.dialog__panel {
  width: min(420px, 100%);
  padding: 22px 24px 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.dialog__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
}

.dialog__desc {
  margin: 9px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.dialog__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 150px;
  margin: 14px 0 0;
  padding: 10px 12px;
  overflow-y: auto;
  border-radius: 8px;
  background: var(--surface-sunken);
  list-style: none;
}

.dialog__item {
  overflow: hidden;
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog__item--more {
  color: var(--muted);
  font-family: var(--font-sans);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}

.dialog__button {
  padding: 7px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
  cursor: pointer;
}

.dialog__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.dialog__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.dialog__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.dialog__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.18s ease;
}

.dialog-enter-active .dialog__panel,
.dialog-leave-active .dialog__panel {
  transition: transform 0.18s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog__panel,
.dialog-leave-to .dialog__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
