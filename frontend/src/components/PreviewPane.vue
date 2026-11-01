<script setup lang="ts">
import { MarkdownRenderer } from 'x-markdown-vue'

defineProps<{
  markdown: string
  isDark: boolean
  /** 为 true 时只做纯正文渲染，不显示“只读预览”工具条，与公开阅读页共用同一套渲染机制。 */
  bare?: boolean
}>()

const customAttrs = {
  a: () => ({ target: '_blank', rel: 'noopener noreferrer' }),
  img: () => ({ loading: 'lazy', decoding: 'async' }),
  heading: (node: unknown, context: { level?: number; key?: string }) => {
    // 工作台预览与公开阅读共用此组件：不在正文里写死 id，
    // 标题锚点由调用方在渲染后按顺序回填，避免 `{#id}` 原样显示。
    void node
    void context
    return {}
  },
}
</script>

<template>
  <section class="preview" :class="{ 'is-bare': bare }" aria-label="Markdown 只读预览">
    <div v-if="!bare" class="preview__bar">
      <span class="preview__label">只读预览</span>
      <span class="preview__note">sanitize 已开启 · 代码块支持复制 · 图片懒加载</span>
    </div>

    <div class="preview__body markdown-body">
      <MarkdownRenderer
        :markdown="markdown"
        allow-html
        :sanitize="true"
        :enable-gfm="true"
        :enable-breaks="true"
        :enable-shiki="true"
        :is-dark="isDark"
        :custom-attrs="customAttrs"
        code-max-height="520px"
      />
    </div>
  </section>
</template>

<style scoped>
.preview {
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

.preview__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-sunken);
}

.preview__label {
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.preview__note {
  color: var(--muted);
  font-size: 11px;
}

.preview__body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 28px 34px 48px;
}

.preview.is-bare {
  min-height: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.preview.is-bare .preview__body {
  overflow: visible;
  padding: 0;
}

@media (max-width: 640px) {
  .preview__body {
    padding: 20px 18px 32px;
  }
}
</style>
