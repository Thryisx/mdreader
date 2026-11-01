import { computed, ref, watch } from 'vue'

import type { BackgroundPreset } from '@/types/workspace'

const STORAGE_KEY = 'md-reader:background'
const preset = ref<BackgroundPreset>('system')
const customImage = ref('')
const systemDark = ref(false)

let initialized = false

const validPresets: BackgroundPreset[] = ['light', 'dark', 'system', 'custom']

function isPreset(value: unknown): value is BackgroundPreset {
  return typeof value === 'string' && validPresets.includes(value as BackgroundPreset)
}

const resolvedTheme = computed<'light' | 'dark'>(() => {
  if (preset.value === 'system') return systemDark.value ? 'dark' : 'light'
  return preset.value === 'dark' ? 'dark' : 'light'
})

const isDark = computed(() => resolvedTheme.value === 'dark')
const hasCustomImage = computed(() => preset.value === 'custom' && Boolean(customImage.value))

function apply() {
  document.documentElement.dataset.theme = resolvedTheme.value
  document.documentElement.dataset.background = preset.value
}

function persist() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ preset: preset.value, customImage: customImage.value }),
    )
  } catch {
    console.warn('[背景] 本地持久化失败，可能是自定义图片超出浏览器存储上限')
  }
}

function ensureInitialized() {
  if (initialized) return
  initialized = true

  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const stored = JSON.parse(raw) as { preset?: unknown; customImage?: unknown }
      if (isPreset(stored.preset)) preset.value = stored.preset
      if (typeof stored.customImage === 'string') customImage.value = stored.customImage
    }
  } catch {
    console.warn('[背景] 读取本地设置失败，使用默认背景')
  }

  if (typeof window.matchMedia === 'function') {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    systemDark.value = query.matches
    query.addEventListener('change', (event) => {
      systemDark.value = event.matches
      apply()
    })
  }

  apply()
}

watch([preset, customImage], () => {
  apply()
  persist()
})

function setPreset(next: BackgroundPreset) {
  preset.value = next
}

function setCustomImage(dataUrl: string) {
  customImage.value = dataUrl
  preset.value = 'custom'
}

function clearCustomImage() {
  customImage.value = ''
  if (preset.value === 'custom') preset.value = 'system'
}

export function useBackground() {
  ensureInitialized()

  return {
    preset,
    customImage,
    resolvedTheme,
    isDark,
    hasCustomImage,
    setPreset,
    setCustomImage,
    clearCustomImage,
  }
}
