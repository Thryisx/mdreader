<script setup lang="ts">
import { computed } from 'vue'

import { countWords } from '@/utils/markdown'

const props = defineProps<{
  modelValue: string
  dirty: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
  copy: []
  save: []
}>()

const wordCount = computed(() => countWords(props.modelValue))
const lineCount = computed(() => props.modelValue.split('\n').length)
</script>

<template>
  <section class="source" aria-label="Markdown 源码编辑">
    <div class="source__bar">
      <span class="source__meta">{{ lineCount }} 行 · {{ wordCount }} 字</span>
      <div class="source__actions">
        <button type="button" class="source__button" @click="$emit('copy')">复制 Markdown</button>
        <button
          type="button"
          class="source__button source__button--primary"
          :disabled="!dirty"
          @click="$emit('save')"
        >
          保存
        </button>
      </div>
    </div>

    <textarea
      class="source__editor"
      :value="modelValue"
      spellcheck="false"
      aria-label="Markdown 源码"
      placeholder="在这里编辑 Markdown 原文"
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
  </section>
</template>

<style scoped>
.source {
  display: flex;
  height: 100%;
  min-height: 480px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.source__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-sunken);
}

.source__meta {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.source__actions {
  display: flex;
  gap: 6px;
}

.source__button {
  padding: 4px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

.source__button:hover:not(:disabled) {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.source__button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.source__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.source__button--primary:hover:not(:disabled) {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
  color: #fff;
}

.source__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.source__editor {
  min-height: 0;
  flex: 1;
  width: 100%;
  padding: 18px 20px 28px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 13.5px;
  line-height: 1.8;
  resize: none;
  outline: none;
  tab-size: 2;
}

.source__editor::placeholder {
  color: var(--muted);
}
</style>
