<script setup lang="ts">
import type { MarkdownMode } from '@/types/workspace'

const props = defineProps<{
  modelValue: MarkdownMode
}>()

defineEmits<{
  'update:modelValue': [mode: MarkdownMode]
}>()

const modes: Array<{ value: MarkdownMode; label: string; hint: string }> = [
  { value: 'wysiwyg', label: '所见即所得', hint: 'TinyMCE 富文本编辑' },
  { value: 'source', label: '源码', hint: '编辑 Markdown 原文' },
  { value: 'preview', label: '预览', hint: '只读渲染，复用现有安全过滤' },
]
</script>

<template>
  <div class="mode-switch" role="tablist" aria-label="Markdown 查看模式">
    <button
      v-for="mode in modes"
      :key="mode.value"
      type="button"
      class="mode-switch__button"
      :class="{ 'is-active': mode.value === props.modelValue }"
      role="tab"
      :aria-selected="mode.value === props.modelValue"
      :title="mode.hint"
      @click="$emit('update:modelValue', mode.value)"
    >
      {{ mode.label }}
    </button>
  </div>
</template>

<style scoped>
.mode-switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-sunken);
}

.mode-switch__button {
  padding: 5px 11px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted-strong);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.mode-switch__button:hover {
  color: var(--ink);
}

.mode-switch__button.is-active {
  background: var(--surface);
  color: var(--accent-strong);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.mode-switch__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

@media (max-width: 520px) {
  .mode-switch__button {
    padding: 5px 8px;
    font-size: 11.5px;
  }
}
</style>
