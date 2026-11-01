<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchPublicFileWithContent } from '@/api/public'
import type { PublicFileDetail } from '@/api/public'
import PreviewPane from '@/components/PreviewPane.vue'
import { useBackground } from '@/composables/useBackground'
import { extractHeadings, useReadingPreferences } from '@/composables/useReading'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const { isDark } = useBackground()
const { preferences, styleVars, update, reset } = useReadingPreferences()
const { showToast } = useToast()

const article = ref<PublicFileDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const activeHeading = ref('')
const panelOpen = ref(false)
const progress = ref(0)
const contentRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let progressTimer: ReturnType<typeof setTimeout> | undefined

const headings = computed(() => extractHeadings(article.value?.content ?? ''))
const articleId = computed(() => {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : 0
})
const progressKey = computed(() => `md-reader:progress:${articleId.value}`)

function formatDate(value?: string) {
  if (!value) return '未知时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function load() {
  if (!articleId.value) {
    error.value = '文章地址不正确'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    article.value = await fetchPublicFileWithContent(articleId.value)
    document.title = `${article.value.title} · MD Reader`
    await nextTick()
    restoreProgress()
    observeHeadings()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '文章加载失败'
  } finally {
    loading.value = false
  }
}

function applyHeadingIds() {
  // 与工作台共用同一套 MarkdownRenderer：渲染后按标题顺序回填 id，
  // 不再往正文里拼接 `{#id}`，避免该语法被渲染器原样输出。
  const root = contentRef.value
  if (!root) return
  const elements = Array.from(root.querySelectorAll('h1, h2, h3, h4'))
  headings.value.forEach((heading, index) => {
    elements[index]?.setAttribute('id', heading.id)
  })
}

function observeHeadings() {
  applyHeadingIds()
  observer?.disconnect()
  observer = null
  if (!contentRef.value || headings.value.length === 0) return

  const elements = Array.from(contentRef.value.querySelectorAll('[id]')).filter((element) =>
    headings.value.some((heading) => heading.id === element.id),
  )
  if (elements.length === 0) return

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) activeHeading.value = entry.target.id
      }
    },
    { rootMargin: '-96px 0px -70% 0px', threshold: 0 },
  )
  elements.forEach((element) => observer?.observe(element))
  activeHeading.value = (elements[0] as HTMLElement).id
}

function scrollToHeading(id: string) {
  activeHeading.value = id
  const escaped = typeof CSS !== 'undefined' && 'escape' in CSS ? CSS.escape(id) : id
  contentRef.value?.querySelector(`#${escaped}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  if (window.innerWidth < 1080) panelOpen.value = false
}

function handleScroll() {
  if (progressTimer) clearTimeout(progressTimer)
  progressTimer = setTimeout(() => {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight
    progress.value = scrollable > 0 ? Math.min(1, window.scrollY / scrollable) : 0
    try {
      localStorage.setItem(progressKey.value, JSON.stringify({ ratio: progress.value, at: Date.now() }))
    } catch {
      return
    }
  }, 120)
}

function restoreProgress() {
  try {
    const raw = localStorage.getItem(progressKey.value)
    if (!raw) return
    const saved = JSON.parse(raw) as { ratio?: number }
    if (typeof saved.ratio === 'number' && saved.ratio > 0.02 && saved.ratio < 0.98) {
      const scrollable = document.documentElement.scrollHeight - window.innerHeight
      window.scrollTo({ top: scrollable * saved.ratio })
    }
  } catch {
    return
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    showToast('文章链接已复制', 'success')
  } catch {
    showToast('复制失败，请手动复制地址栏', 'warn')
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push('/')
}

watch(articleId, () => {
  window.scrollTo({ top: 0 })
  activeHeading.value = ''
  progress.value = 0
  void load()
})

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  void load()
})

onUpdated(() => {
  // MarkdownRenderer 为异步高亮渲染：每次更新后重新回填标题 id，保证目录可点。
  applyHeadingIds()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
  observer?.disconnect()
  if (progressTimer) clearTimeout(progressTimer)
  document.title = 'MD Reader'
})
</script>

<template>
  <div class="reader">
    <div class="reader__progress" aria-hidden="true">
      <span class="reader__progress-bar" :style="{ transform: `scaleX(${progress})` }" />
    </div>

    <a class="reader__skip" href="#article-content">跳转到正文</a>

    <header class="reader__top">
      <button type="button" class="reader__back" @click="goBack">← 返回</button>
      <RouterLink class="reader__brand" to="/">MD Reader</RouterLink>
      <div class="reader__top-actions">
        <RouterLink class="reader__top-link" to="/workspace">工作台</RouterLink>
        <button
          type="button"
          class="reader__toc-button"
          :aria-expanded="panelOpen"
          aria-controls="reader-toc"
          @click="panelOpen = !panelOpen"
        >
          {{ panelOpen ? '收起目录' : '目录' }}
        </button>
      </div>
    </header>

    <main class="reader__layout">
      <article class="reader__article" :style="styleVars">
        <div v-if="loading" class="reader__state" role="status">正在加载文章…</div>

        <div v-else-if="error" class="reader__state reader__state--error" role="alert">
          <p>{{ error }}</p>
          <button type="button" class="reader__button reader__button--primary" @click="load">
            重试
          </button>
        </div>

        <template v-else-if="article">
          <p class="reader__eyebrow">公开文章</p>
          <h1 class="reader__title">{{ article.title }}</h1>
          <p class="reader__meta">
            <span>更新于 {{ formatDate(article.updatedAt) }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ Math.max(0, article.fileSize) }} 字节</span>
          </p>

          <div class="reader__actions" aria-label="阅读操作">
            <button type="button" class="reader__button" @click="copyLink">复制链接</button>
            <RouterLink class="reader__button reader__button--link" to="/">返回列表</RouterLink>
          </div>

          <div id="article-content" ref="contentRef" class="reader__content">
            <PreviewPane :markdown="article.content" :is-dark="isDark" bare />
          </div>
        </template>
      </article>

      <aside id="reader-toc" class="reader__toc" :class="{ 'is-open': panelOpen }" aria-label="文章目录">
        <div class="reader__toc-head">
          <h2 class="reader__toc-title">目录</h2>
          <button
            type="button"
            class="reader__toc-close"
            @click="panelOpen = false"
          >
            收起
          </button>
        </div>

        <p v-if="headings.length === 0" class="reader__toc-empty">正文暂无可导航的标题。</p>
        <ol v-else class="reader__toc-list">
          <li
            v-for="heading in headings"
            :key="heading.id"
            class="reader__toc-item"
            :class="[
              `is-level-${heading.level}`,
              { 'is-active': activeHeading === heading.id },
            ]"
          >
            <button type="button" class="reader__toc-link" @click="scrollToHeading(heading.id)">
              {{ heading.text }}
            </button>
          </li>
        </ol>

        <section class="reader__prefs" aria-label="排版设置">
          <h2 class="reader__prefs-title">排版</h2>
          <label class="reader__pref">
            <span>字号 {{ preferences.fontSize }}px</span>
            <input
              type="range"
              min="14"
              max="22"
              step="1"
              :value="preferences.fontSize"
              @input="update({ fontSize: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <label class="reader__pref">
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
          <label class="reader__pref">
            <span>宽度 {{ preferences.maxWidth }}px</span>
            <input
              type="range"
              min="560"
              max="960"
              step="20"
              :value="preferences.maxWidth"
              @input="update({ maxWidth: Number(($event.target as HTMLInputElement).value) })"
            />
          </label>
          <button type="button" class="reader__button reader__button--ghost" @click="reset">
            恢复默认排版
          </button>
        </section>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.reader {
  min-height: 100dvh;
}

.reader__progress {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 60;
  height: 3px;
  background: transparent;
}

.reader__progress-bar {
  display: block;
  height: 100%;
  background: var(--accent);
  transform: scaleX(0);
  transform-origin: 0 50%;
}

.reader__skip {
  position: absolute;
  top: -48px;
  left: 16px;
  z-index: 61;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
}

.reader__skip:focus {
  top: 12px;
}

.reader__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: min(1160px, 100% - 40px);
  margin: 0 auto;
  padding: 16px 0;
}

.reader__back,
.reader__toc-button,
.reader__top-link {
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--muted-strong);
  font-size: 12.5px;
  text-decoration: none;
  cursor: pointer;
}

.reader__back:hover,
.reader__toc-button:hover,
.reader__top-link:hover {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.reader__brand {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
}

.reader__top-actions {
  display: flex;
  gap: 8px;
}

.reader__toc-button {
  display: none;
}

.reader__layout {
  display: grid;
  width: min(1160px, 100% - 40px);
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  align-items: start;
  margin: 0 auto;
  padding: 8px 0 80px;
}

.reader__article {
  max-width: var(--read-max-width);
  margin: 0 auto;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  padding: clamp(24px, 4vw, 48px);
}

.reader__eyebrow {
  margin: 0 0 10px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.reader__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(28px, 3.8vw, 40px);
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.reader__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}

.reader__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 18px 0 8px;
}

.reader__button {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.reader__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.reader__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.reader__button--ghost {
  width: 100%;
  justify-content: center;
  height: 34px;
}

.reader__content {
  margin-top: 12px;
  font-size: var(--read-font-size);
  line-height: var(--read-line-height);
}

.reader__content :deep(.preview__body) {
  font-size: inherit;
  line-height: inherit;
}

.reader__state {
  padding: 28px;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  color: var(--muted);
  font-size: 14px;
}

.reader__toc {
  position: sticky;
  top: 76px;
  max-height: calc(100dvh - 112px);
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  padding: 18px;
}

.reader__toc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.reader__toc-title,
.reader__prefs-title {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.reader__toc-close {
  display: none;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.reader__toc-empty {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}

.reader__toc-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.reader__toc-item {
  margin: 2px 0;
}

.reader__toc-link {
  width: 100%;
  padding: 6px 8px;
  overflow: hidden;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 7px 7px 0;
  background: transparent;
  color: var(--muted-strong);
  font-size: 12.5px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.reader__toc-link:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.reader__toc-item.is-active .reader__toc-link {
  border-left-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-weight: 600;
}

.reader__toc-item.is-level-2 .reader__toc-link {
  margin-left: 10px;
}

.reader__toc-item.is-level-3 .reader__toc-link {
  margin-left: 20px;
}

.reader__toc-item.is-level-4 .reader__toc-link {
  margin-left: 30px;
}

.reader__prefs {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.reader__pref {
  display: block;
  margin-top: 12px;
  color: var(--muted-strong);
  font-size: 12px;
}

.reader__pref input {
  width: 100%;
  margin-top: 6px;
  accent-color: var(--accent);
}

.reader__prefs .reader__button {
  margin-top: 12px;
}

@media (max-width: 1080px) {
  .reader__layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .reader__toc-button {
    display: inline-flex;
  }

  .reader__toc {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 55;
    width: min(340px, 88vw);
    max-height: none;
    border-radius: 0;
    transform: translateX(100%);
    transition: transform 0.24s ease;
  }

  .reader__toc.is-open {
    transform: translateX(0);
    box-shadow: var(--shadow-lg);
  }

  .reader__toc-close {
    display: inline-flex;
  }
}

@media (max-width: 640px) {
  .reader__article {
    padding: 22px 18px 30px;
  }

  .reader__top {
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .reader__toc {
    transition: none;
  }
}
</style>
