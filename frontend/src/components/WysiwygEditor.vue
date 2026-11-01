<script setup lang="ts">
import { ref } from 'vue'
import Editor from '@tinymce/tinymce-vue'
import type { Editor as TinyMceEditor } from 'tinymce'

import { useToast } from '@/composables/useToast'

const props = defineProps<{
  modelValue: string
  isDark: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'import-files': [files: File[]]
  edited: []
}>()

const { showToast } = useToast()
const editorInstance = ref<TinyMceEditor | null>(null)
const diagramInput = ref<HTMLInputElement | null>(null)

const MAX_IMAGE_SIZE = 4 * 1024 * 1024
const IMPORTABLE_FILE = /\.(md|markdown|txt)$/i
const tinymceBase = `${import.meta.env.BASE_URL}tinymce`

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeAttribute(value: string): string {
  return escapeHtml(value).replace(/"/g, '&quot;')
}

function openCodeDialog(editor: TinyMceEditor) {
  editor.windowManager.open({
    title: '导入代码块',
    size: 'medium',
    body: {
      type: 'panel',
      items: [
        {
          type: 'input',
          name: 'language',
          label: '语言',
          placeholder: '例如 ts、java、bash',
        },
        {
          type: 'textarea',
          name: 'code',
          label: '代码',
          placeholder: '粘贴代码内容',
        },
      ],
    },
    buttons: [
      { type: 'cancel', text: '取消' },
      { type: 'submit', text: '插入', primary: true },
    ],
    initialData: { language: 'text', code: '' },
    onSubmit: (api) => {
      const data = api.getData() as { language?: string; code?: string }
      const code = data.code ?? ''

      if (!code.trim()) {
        showToast('请先填写代码内容', 'warn')
        return
      }

      const language = (data.language ?? 'text').trim().toLowerCase()
      const safeLanguage = /^[a-z0-9+#._-]+$/.test(language) ? language : 'text'

      editor.insertContent(
        `<pre><code class="language-${safeLanguage}">${escapeHtml(code)}</code></pre><p>&nbsp;</p>`,
      )
      emit('edited')
      api.close()
    },
  })
}

const init = {
  license_key: 'gpl',
  base_url: tinymceBase,
  suffix: '.min',
  height: '100%',
  min_height: 420,
  menubar: false,
  branding: false,
  promotion: false,
  statusbar: false,
  resize: false,
  elementpath: false,
  plugins: 'lists link image table codesample searchreplace autolink',
  toolbar: 'undo redo | mdbold mdthin | importdiagram importcode | bullist numlist | removeformat',
  skin: props.isDark ? 'oxide-dark' : 'oxide',
  content_css: props.isDark ? 'dark' : 'default',
  content_style: props.isDark
    ? 'body { background: #1c1b18; color: #ece6da; font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; font-size: 16px; line-height: 1.85; padding: 6px 18px 24px; outline: none; } a { color: #8fcdb2; } code { background: #2b2924; padding: 2px 5px; border-radius: 4px; } pre { background: #24221e; padding: 14px; border-radius: 8px; }'
    : 'body { background: #fffdf9; color: #211e18; font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; font-size: 16px; line-height: 1.85; padding: 6px 18px 24px; outline: none; } a { color: #2f6b57; } code { background: #f0eee7; padding: 2px 5px; border-radius: 4px; } pre { background: #f4f1e9; padding: 14px; border-radius: 8px; }',
  convert_urls: false,
  paste_data_images: true,
  setup: (editor: TinyMceEditor) => {
    editorInstance.value = editor

    const markEdited = () => emit('edited')
    editor.on('input', markEdited)
    editor.on('ExecCommand', (event) => {
      if (event.command === 'mceFocus') return
      markEdited()
    })
    editor.on('Undo Redo', markEdited)

    editor.on('drop', (event) => {
      const dropEvent = event as unknown as DragEvent
      const files = Array.from(dropEvent.dataTransfer?.files ?? [])
      if (!files.some((file) => IMPORTABLE_FILE.test(file.name))) return

      dropEvent.preventDefault()
      emit('import-files', files)
    })

    editor.ui.registry.addButton('mdbold', {
      text: '加粗',
      tooltip: '加粗',
      onAction: () => editor.execCommand('Bold'),
    })

    editor.ui.registry.addButton('mdthin', {
      text: '加细',
      tooltip: '加细（按常规落为斜体）',
      onAction: () => editor.execCommand('Italic'),
    })

    editor.ui.registry.addButton('importdiagram', {
      text: '导入导图',
      tooltip: '导入导图图片',
      onAction: () => diagramInput.value?.click(),
    })

    editor.ui.registry.addButton('importcode', {
      text: '导入代码块',
      tooltip: '插入带语言标记的代码块',
      onAction: () => openCodeDialog(editor),
    })
  },
}

function handleDiagramFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file) return

  if (!file.type.startsWith('image/')) {
    showToast('导图仅支持图片文件', 'warn')
    return
  }

  if (file.size > MAX_IMAGE_SIZE) {
    showToast('导图图片不能超过 4 MB', 'warn')
    return
  }

  const reader = new FileReader()

  reader.onload = () => {
    const source = typeof reader.result === 'string' ? reader.result : ''
    if (!source) {
      showToast('导图读取失败', 'warn')
      return
    }

    editorInstance.value?.insertContent(
      `<img src="${source}" alt="${escapeAttribute(file.name)}" />`,
    )
    emit('edited')
    showToast('已插入导图图片', 'success')
  }

  reader.onerror = () => showToast('导图读取失败', 'warn')
  reader.readAsDataURL(file)
}
</script>

<template>
  <div class="wysiwyg">
    <Editor
      :model-value="modelValue"
      :init="init"
      :tinymce-script-src="`${tinymceBase}/tinymce.min.js`"
      license-key="gpl"
      @update:model-value="$emit('update:modelValue', $event)"
    />

    <input
      ref="diagramInput"
      class="wysiwyg__file"
      type="file"
      accept="image/*"
      @change="handleDiagramFile"
    />
  </div>
</template>

<style scoped>
.wysiwyg {
  height: 100%;
  min-height: 480px;
}

.wysiwyg :deep(.tox-tinymce) {
  height: 100% !important;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.wysiwyg :deep(.tox .tox-editor-header) {
  border-bottom: 1px solid var(--line);
  box-shadow: none;
}

.wysiwyg :deep(.tox .tox-toolbar-overlord),
.wysiwyg :deep(.tox .tox-toolbar__primary) {
  background: var(--surface);
}

.wysiwyg :deep(.tox .tox-tbtn) {
  color: var(--muted-strong);
}

.wysiwyg :deep(.tox .tox-tbtn:hover) {
  background: var(--surface-sunken);
  color: var(--ink);
}

.wysiwyg :deep(.tox .tox-tbtn--enabled) {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.wysiwyg :deep(.tox-edit-area__iframe) {
  outline: none;
}

.wysiwyg :deep(.tox-edit-area::before) {
  border: 0;
}

.wysiwyg__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
