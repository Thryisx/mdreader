<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useBackground } from '@/composables/useBackground'
import { useReadingPreferences } from '@/composables/useReading'
import { useToast } from '@/composables/useToast'
import type { BackgroundPreset } from '@/types/workspace'

const router = useRouter()
const { showToast } = useToast()
const { preset, customImage, resolvedTheme, setPreset, setCustomImage, clearCustomImage } = useBackground()
const { preferences, update, reset: resetReading } = useReadingPreferences()

const fileInput = ref<HTMLInputElement | null>(null)
const MAX_IMAGE_SIZE = 2 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const presets: Array<{ value: BackgroundPreset; title: string; description: string }> = [
  { value: 'light', title: '浅色', description: '明亮纸张，适合日间阅读' },
  { value: 'dark', title: '深色', description: '低光环境更柔和' },
  { value: 'system', title: '跟随系统', description: '自动匹配系统外观' },
]

const hasCustomImage = computed(() => Boolean(customImage.value))

const currentLabel = computed(() => {
  if (preset.value === 'custom') return '自定义'
  if (preset.value === 'system') {
    return `跟随系统（${resolvedTheme.value === 'dark' ? '深色' : '浅色'}）`
  }
  return resolvedTheme.value === 'dark' ? '深色' : '浅色'
})

function applyCustomBackground() {
  if (!hasCustomImage.value) return
  setPreset('custom')
  console.info('[设置] 应用已上传的自定义背景')
}

function choosePreset(value: BackgroundPreset) {
  setPreset(value)
  console.info('[设置] 切换背景预设', value)
}

function openFilePicker() {
  fileInput.value?.click()
}

function handleImageInput(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file) return

  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    showToast('请选择 JPG / PNG / WebP 图片', 'warn')
    return
  }

  if (file.size > MAX_IMAGE_SIZE) {
    showToast('图片不能超过 2 MB（上传限制待设计图确认）', 'warn')
    return
  }

  const reader = new FileReader()

  reader.onload = () => {
    const source = typeof reader.result === 'string' ? reader.result : ''
    if (!source) {
      showToast('图片读取失败', 'warn')
      return
    }

    setCustomImage(source)
    showToast('已应用自定义背景', 'success')
    console.info('[设置] 上传自定义背景', { name: file.name, size: file.size })
  }

  reader.onerror = () => showToast('图片读取失败', 'warn')
  reader.readAsDataURL(file)
}

function removeCustomImage() {
  clearCustomImage()
  showToast('已移除自定义背景')
  console.info('[设置] 移除自定义背景')
}

function goBack() {
  const state = window.history.state as { back?: string } | null
  if (state?.back) {
    router.back()
    return
  }

  void router.push('/')
}
</script>

<template>
  <div class="settings">
    <header class="settings__top">
      <button type="button" class="settings__back" @click="goBack">
        <span aria-hidden="true">←</span>
        返回阅读页
      </button>
      <span class="settings__brand">MD Reader</span>
    </header>

    <main class="settings__main">
      <div class="settings__intro">
        <p class="settings__eyebrow">用户设置</p>
        <h1 class="settings__title">背景与外观</h1>
      </div>

      <section class="settings__section" aria-labelledby="preset-title">
        <div class="settings__section-head">
          <h2 id="preset-title" class="settings__section-title">预设背景</h2>
          <span class="settings__current">当前：{{ currentLabel }}</span>
        </div>

        <div class="settings__presets">
          <button
            v-for="option in presets"
            :key="option.value"
            type="button"
            class="preset"
            :class="{ 'is-active': preset === option.value }"
            :aria-pressed="preset === option.value"
            @click="choosePreset(option.value)"
          >
            <span class="preset__swatch" :class="`preset__swatch--${option.value}`" aria-hidden="true">
              <span class="preset__swatch-line" />
              <span class="preset__swatch-line preset__swatch-line--short" />
            </span>
            <span class="preset__text">
              <span class="preset__title">{{ option.title }}</span>
              <span class="preset__desc">{{ option.description }}</span>
            </span>
            <span v-if="preset === option.value" class="preset__check" aria-hidden="true">✓</span>
          </button>
        </div>
      </section>

      <section class="settings__section" aria-labelledby="custom-title">
        <div class="settings__section-head">
          <h2 id="custom-title" class="settings__section-title">自定义背景</h2>
          <span class="settings__current">支持 JPG / PNG / WebP，最大 2 MB</span>
        </div>

        <div class="custom" :class="{ 'is-active': preset === 'custom' }">
          <div class="custom__preview" :style="hasCustomImage ? { backgroundImage: `url(${customImage})` } : undefined">
            <span v-if="!hasCustomImage" class="custom__placeholder">尚未上传图片</span>
          </div>

          <div class="custom__body">
            <p class="custom__desc">
              上传后会叠加一层半透明遮罩，保证正文可读。上传限制、即时生效与持久化策略待设计图确认。
            </p>

            <div class="custom__actions">
              <button type="button" class="custom__button custom__button--primary" @click="openFilePicker">
                {{ hasCustomImage ? '更换图片' : '上传图片' }}
              </button>
              <button
                v-if="hasCustomImage && preset !== 'custom'"
                type="button"
                class="custom__button"
                @click="applyCustomBackground"
              >
                使用此背景
              </button>
              <button
                v-if="hasCustomImage"
                type="button"
                class="custom__button"
                @click="removeCustomImage"
              >
                移除
              </button>
            </div>
          </div>
        </div>

        <input
          ref="fileInput"
          class="custom__file"
          type="file"
          accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          @change="handleImageInput"
        />
      </section>

      <section class="settings__section" aria-labelledby="reading-title">
        <div class="settings__section-head">
          <h2 id="reading-title" class="settings__section-title">阅读排版</h2>
          <span class="settings__current">自动保存，阅读页实时生效</span>
        </div>

        <div class="reading">
          <label class="reading__row">
            <span>正文字号 {{ preferences.fontSize }}px</span>
            <input
              type="range"
              min="14"
              max="22"
              step="1"
              :value="preferences.fontSize"
              @input="update({ fontSize: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <label class="reading__row">
            <span>行高 {{ preferences.lineHeight.toFixed(1) }}</span>
            <input
              type="range"
              min="1.5"
              max="2.4"
              step="0.1"
              :value="preferences.lineHeight"
              @input="update({ lineHeight: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <label class="reading__row">
            <span>内容宽度 {{ preferences.maxWidth }}px</span>
            <input
              type="range"
              min="560"
              max="960"
              step="20"
              :value="preferences.maxWidth"
              @input="update({ maxWidth: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <label class="reading__row">
            <span>代码字号 {{ preferences.codeFontSize }}px</span>
            <input
              type="range"
              min="11"
              max="16"
              step="0.5"
              :value="preferences.codeFontSize"
              @input="update({ codeFontSize: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <div class="reading__actions">
            <button type="button" class="custom__button" @click="resetReading">恢复默认排版</button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.settings {
  min-height: 100dvh;
  overflow-y: auto;
}

.settings__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(920px, 100% - 40px);
  margin: 0 auto;
  padding: 18px 0;
}

.settings__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--muted-strong);
  font-size: 12.5px;
  cursor: pointer;
  backdrop-filter: blur(10px);
}

.settings__back:hover {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.settings__brand {
  color: var(--muted);
  font-family: var(--font-serif);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.settings__main {
  width: min(920px, 100% - 40px);
  margin: 0 auto;
  padding: 18px 0 64px;
}

.settings__intro {
  padding: 22px 0 26px;
  border-bottom: 1px solid var(--line);
}

.settings__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.settings__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(26px, 3.4vw, 36px);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.settings__desc {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13.5px;
  line-height: 1.75;
}

.settings__section {
  padding: 26px 0;
  border-bottom: 1px solid var(--line);
}

.settings__section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.settings__section-title {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.settings__current {
  color: var(--muted);
  font-size: 11px;
}

.settings__presets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.preset {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.preset:hover {
  border-color: var(--accent-line);
}

.preset.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.preset:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.preset__swatch {
  display: flex;
  width: 44px;
  height: 44px;
  flex: none;
  flex-direction: column;
  gap: 5px;
  justify-content: center;
  padding: 0 9px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
}

.preset__swatch--light {
  background: #f6f4ef;
}

.preset__swatch--dark {
  background: #1c1b18;
}

.preset__swatch--system {
  background: linear-gradient(135deg, #f6f4ef 0 50%, #1c1b18 50% 100%);
}

.preset__swatch-line {
  width: 100%;
  height: 3px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.35;
}

.preset__swatch--light .preset__swatch-line {
  color: #211e18;
}

.preset__swatch--dark .preset__swatch-line {
  color: #ece6da;
}

.preset__swatch--system .preset__swatch-line {
  color: #6f6a60;
}

.preset__swatch-line--short {
  width: 60%;
}

.preset__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.preset__title {
  font-size: 13px;
  font-weight: 600;
}

.preset__desc {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
}

.preset__check {
  position: absolute;
  top: 9px;
  right: 10px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
}

.custom {
  display: flex;
  gap: 18px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.custom.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.custom__preview {
  display: grid;
  width: 180px;
  height: 116px;
  flex: none;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 9px;
  background-color: var(--surface-sunken);
  background-position: center;
  background-size: cover;
}

.custom__placeholder {
  color: var(--muted);
  font-size: 11px;
}

.custom__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.custom__desc {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.custom__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reading {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.reading__row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--muted-strong);
  font-size: 12.5px;
  font-weight: 600;
}

.reading__row input {
  width: 100%;
  accent-color: var(--accent);
}

.reading__actions {
  display: flex;
  justify-content: flex-end;
}

.custom__button {
  padding: 7px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12px;
  cursor: pointer;
}

.custom__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.custom__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.custom__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.custom__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.custom__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.settings__note {
  margin: 22px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .settings__presets {
    grid-template-columns: minmax(0, 1fr);
  }

  .custom {
    flex-direction: column;
  }

  .custom__preview {
    width: 100%;
    height: 140px;
  }
}
</style>
