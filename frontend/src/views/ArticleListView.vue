<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchPublicFileCategories, fetchPublicFiles } from '@/api/public'
import { ApiError } from '@/api/http'
import { toCurrentUser } from '@/api/user'
import { useCurrentUser } from '@/composables/useCurrentUser'
import LoginDialog from '@/components/LoginDialog.vue'
import TopLeftEntry from '@/components/TopLeftEntry.vue'
import UserBar from '@/components/UserBar.vue'
import type { FileCategoryVO, FileVO, UserVO } from '@/types/api'

/**
 * 公开文章页（2026-10-31 按 drawio 重做）。
 * 布局对应 drawio：
 * - 左侧多功能栏：文章的查询（顶部搜索）＋ 工作台 / 设置入口 ＋ 底部沿用工作台登录框。
 * - 右侧文章页面：左列文章的种类 ＋ 右列文章列表。
 * 复用组件：TopLeftEntry（收起入口）、UserBar（底部用户栏）、LoginDialog（工作台同款登录框）。
 * 种类来自后端 GET /api/files/categories，计数为全量统计；无分类数据时回退到按更新时间分组。
 */

/** 种类标识：all 为全部，其余为真实分类名或 UNCATEGORIZED。 */
type KindId = string

interface KindDef {
  id: KindId
  name: string
  desc: string
}

const UNCATEGORIZED = 'UNCATEGORIZED'

const FALLBACK_KINDS: KindDef[] = [
  { id: 'all', name: '全部文章', desc: '全部公开已发布' },
  { id: 'week', name: '最近 7 天', desc: '一周内有更新' },
  { id: 'month', name: '最近 30 天', desc: '一月内有更新' },
  { id: 'earlier', name: '历史文章', desc: '30 天前更新' },
]

const route = useRoute()
const router = useRouter()

const files = ref<FileVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const errorStatus = ref<number | null>(null)
const loginOpen = ref(false)
const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const searchInput = ref(keyword.value)
const pageNum = ref(1)
const pageSize = 9
const categories = ref<FileCategoryVO[]>([])
const selectedKind = ref<string>(parseKind(route.query.kind))
const collapsed = ref(
  typeof window !== 'undefined' ? window.matchMedia('(max-width: 960px)').matches : false,
)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const { user: currentUser, status: userStatus, setUser, reload: reloadUser } = useCurrentUser()
const isLoggedIn = computed(() => currentUser.value !== null)

/** 后端返回真实分类时按分类展示，否则回退到按更新时间分组。 */
const hasRealCategories = computed(() => categories.value.length > 0)

const kinds = computed<KindDef[]>(() => {
  if (!hasRealCategories.value) {
    return FALLBACK_KINDS
  }
  const items: KindDef[] = [{ id: 'all', name: '全部文章', desc: '全部公开已发布' }]
  for (const item of categories.value) {
    const name = item.category
    items.push({
      id: name ?? UNCATEGORIZED,
      name: name ?? '未分类',
      desc: name ? `${name} 类文章` : '未设置分类',
    })
  }
  return items
})

const kindCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = { all: total.value }
  if (hasRealCategories.value) {
    for (const item of categories.value) {
      counts[item.category ?? UNCATEGORIZED] = item.count
    }
    return counts
  }
  const now = Date.now()
  counts.all = files.value.length
  counts.week = 0
  counts.month = 0
  counts.earlier = 0
  for (const file of files.value) {
    const kind = kindOf(file, now)
    if (kind === 'week') {
      counts.week += 1
      counts.month += 1
    } else if (kind === 'month') {
      counts.month += 1
    } else if (kind === 'earlier') {
      counts.earlier += 1
    }
  }
  return counts
})

function parseKind(value: unknown): string {
  if (typeof value !== 'string' || !value) return 'all'
  return value
}

function kindOf(file: FileVO, now: number): KindId {
  const time = new Date(file.updatedAt).getTime()
  if (Number.isNaN(time)) return 'all'
  const days = (now - time) / 86_400_000
  if (days <= 7) return 'week'
  if (days <= 30) return 'month'
  return 'earlier'
}

/** 当前页按种类做客户端过滤；后端分类可用时由后端按分类筛选。 */
const filteredFiles = computed(() => {
  if (hasRealCategories.value) return files.value
  if (selectedKind.value === 'all') return files.value
  const now = Date.now()
  if (selectedKind.value === 'earlier') {
    return files.value.filter((file) => kindOf(file, now) === 'earlier')
  }
  if (selectedKind.value === 'week') {
    return files.value.filter((file) => {
      const kind = kindOf(file, now)
      return kind === 'week'
    })
  }
  return files.value.filter((file) => {
    const kind = kindOf(file, now)
    return kind === 'week' || kind === 'month'
  })
})

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '空文档'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function load() {
  loading.value = true
  error.value = null
  errorStatus.value = null
  try {
    const page = await fetchPublicFiles({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value || undefined,
      category: categoryQuery(),
    })
    files.value = page.records
    total.value = page.total
    await loadCategories()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '文章列表加载失败'
    errorStatus.value = err instanceof ApiError ? err.status : null
  } finally {
    loading.value = false
  }
}

/** 分类参数：选中真实分类时透传，未分类使用 UNCATEGORIZED 约定值。 */
function categoryQuery(): string | undefined {
  if (!hasRealCategories.value) return undefined
  if (selectedKind.value === 'all') return undefined
  return selectedKind.value
}

/** 加载后端分类及计数；失败时保留已有分类，不阻塞列表展示。 */
async function loadCategories() {
  try {
    categories.value = await fetchPublicFileCategories()
  } catch {
    categories.value = []
  }
}

function handleRetry() {
  if (errorStatus.value === 401) {
    loginOpen.value = true
    return
  }
  void load()
}

function handleLoginSuccess(user: UserVO) {
  loginOpen.value = false
  setUser(toCurrentUser(user))
  void load()
}

function search() {
  keyword.value = searchInput.value.trim()
  pageNum.value = 1
  void router.replace({
    query: { ...route.query, q: keyword.value || undefined, kind: selectedKind.value, page: undefined },
  })
  void load()
}

function clearSearch() {
  searchInput.value = ''
  keyword.value = ''
  pageNum.value = 1
  void router.replace({ query: { kind: selectedKind.value } })
  void load()
}

function selectKind(id: string) {
  // 后端分类可用时按分类重新请求；否则仅做本页客户端过滤。
  selectedKind.value = id
  pageNum.value = 1
  void router.replace({
    query: { ...route.query, kind: id === 'all' ? undefined : id },
  })
  if (hasRealCategories.value) {
    void load()
  }
}

function goPage(next: number) {
  pageNum.value = Math.min(totalPages.value, Math.max(1, next))
  void load()
}

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

watch(
  () => route.query.q,
  (next) => {
    const value = typeof next === 'string' ? next : ''
    if (value === keyword.value) return
    keyword.value = value
    searchInput.value = value
    pageNum.value = 1
    void load()
  },
)

watch(
  () => route.query.kind,
  (next) => {
    selectedKind.value = parseKind(next)
  },
)

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="public" :class="{ 'is-collapsed': collapsed }">
    <TopLeftEntry :collapsed="collapsed" @toggle="toggleSidebar" />

    <!-- 左侧多功能栏 -->
    <aside class="public__sidebar" aria-label="多功能栏">
      <div class="public__sidebar-inner">
        <form class="public__search" role="search" @submit.prevent="search">
          <label class="public__search-field">
            <span class="public__label">文章的查询</span>
            <input
              v-model="searchInput"
              type="search"
              name="q"
              placeholder="输入标题关键词"
              autocomplete="off"
            />
          </label>
          <div class="public__search-actions">
            <button type="submit" class="public__button public__button--primary">搜索</button>
            <button v-if="keyword" type="button" class="public__button" @click="clearSearch">
              清空
            </button>
          </div>
        </form>

        <nav class="public__nav" aria-label="功能入口">
          <RouterLink class="public__nav-item" to="/workspace">
            <span class="public__nav-icon" aria-hidden="true">▤</span>
            <span class="public__nav-text">
              <span class="public__nav-title">工作台</span>
              <span class="public__nav-desc">编辑我的文档</span>
            </span>
          </RouterLink>
          <RouterLink class="public__nav-item" to="/settings">
            <span class="public__nav-icon" aria-hidden="true">⚙</span>
            <span class="public__nav-text">
              <span class="public__nav-title">设置</span>
              <span class="public__nav-desc">背景与排版</span>
            </span>
          </RouterLink>
        </nav>

        <div class="public__sidebar-foot">
          <!-- 沿用工作台的登录框：UserBar 展示态 ＋ LoginDialog 弹窗 -->
          <UserBar
            :user="currentUser"
            :status="userStatus"
            @retry="reloadUser"
            @login="loginOpen = true"
          />
          <p class="public__foot-hint">未登录也可阅读公开文章，登录后可去工作台发布。</p>
        </div>
      </div>
    </aside>

    <!-- 右侧文章页面 -->
    <main class="public__main">
      <header class="public__head">
        <div>
          <p class="public__eyebrow">公开阅读</p>
          <h1 class="public__title">公开文章</h1>
        </div>
        <span class="public__total" role="status">共 {{ total }} 篇</span>
      </header>

      <div class="public__content">
        <section class="public__kinds" aria-label="文章的种类">
          <h2 class="public__panel-title">文章的种类</h2>
          <p class="public__panel-desc">
            {{
              hasRealCategories
                ? '按文章分类筛选，计数为全部公开文章的全量统计。'
                : '暂按更新时间分组，待后端分类数据可用后切换为真实分类。'
            }}
          </p>
          <ul class="public__kind-list">
            <li v-for="kind in kinds" :key="kind.id">
              <button
                type="button"
                class="public__kind"
                :class="{ 'is-active': selectedKind === kind.id }"
                :aria-pressed="selectedKind === kind.id"
                @click="selectKind(kind.id)"
              >
                <span class="public__kind-name">{{ kind.name }}</span>
                <span class="public__kind-desc">{{ kind.desc }}</span>
                <span class="public__kind-count">
                  {{ hasRealCategories ? '共' : '本页' }} {{ kindCounts[kind.id] ?? 0 }} 篇
                </span>
              </button>
            </li>
          </ul>
        </section>

        <section class="public__list-panel" aria-label="文章列表">
          <h2 class="public__panel-title">文章列表</h2>
          <p v-if="keyword" class="public__result" role="status">
            正在按“{{ keyword }}”筛选，共 {{ total }} 篇
          </p>

          <div v-if="loading" class="public__state" role="status">正在加载文章…</div>

          <div v-else-if="error" class="public__state public__state--error" role="alert">
            <p>{{ error }}</p>
            <button
              type="button"
              class="public__button public__button--primary"
              @click="handleRetry"
            >
              {{ errorStatus === 401 ? '去登录' : '重试' }}
            </button>
          </div>

          <div v-else-if="filteredFiles.length === 0" class="public__state">
            <p class="public__empty-title">
              {{ files.length === 0 ? (keyword ? '没有找到匹配的文章' : '暂时没有公开文章') : '当前种类在本页暂无文章' }}
            </p>
            <p class="public__empty-desc">
              {{
                files.length === 0
                  ? '换一个关键词试试，或去工作台发布第一篇公开文章。'
                  : '换一个种类，或翻页查看更多。'
              }}
            </p>
            <RouterLink
              v-if="files.length === 0 && !keyword && isLoggedIn"
              class="public__button public__button--primary public__empty-link"
              to="/workspace"
            >
              去工作台发布
            </RouterLink>
          </div>

          <ul v-else class="public__list">
            <li v-for="file in filteredFiles" :key="file.id" class="article-card">
              <RouterLink class="article-card__link" :to="`/read/${file.id}`">
                <img
                  v-if="file.coverUrl"
                  class="article-card__cover"
                  :src="file.coverUrl"
                  alt=""
                  loading="lazy"
                />
                <span class="article-card__body">
                  <span class="article-card__title">{{ file.title }}</span>
                  <span v-if="file.summary" class="article-card__summary">{{ file.summary }}</span>
                  <span class="article-card__meta">
                    <span v-if="file.authorNickname || file.authorUsername">
                      {{ file.authorNickname || file.authorUsername }}
                    </span>
                    <span v-if="file.authorNickname || file.authorUsername" aria-hidden="true">·</span>
                    <span>{{ formatDate(file.updatedAt) }} 更新</span>
                    <span aria-hidden="true">·</span>
                    <span>{{ formatSize(file.fileSize) }}</span>
                  </span>
                  <span v-if="file.category" class="article-card__tag">{{ file.category }}</span>
                </span>
              </RouterLink>
            </li>
          </ul>

          <nav v-if="!loading && !error && totalPages > 1" class="public__pager" aria-label="分页">
            <button
              type="button"
              class="public__button"
              :disabled="pageNum <= 1"
              @click="goPage(pageNum - 1)"
            >
              上一页
            </button>
            <span class="public__page" role="status">第 {{ pageNum }} / {{ totalPages }} 页</span>
            <button
              type="button"
              class="public__button"
              :disabled="pageNum >= totalPages"
              @click="goPage(pageNum + 1)"
            >
              下一页
            </button>
          </nav>
        </section>
      </div>
    </main>

    <LoginDialog
      :open="loginOpen"
      title="请登录"
      description="沿用工作台登录框。登录后可去工作台发布文章，公开文章无需登录也可阅读。"
      @close="loginOpen = false"
      @success="handleLoginSuccess"
    />
  </div>
</template>

<style scoped>
.public {
  display: flex;
  min-height: 100dvh;
}

.public__sidebar {
  width: 232px;
  flex: none;
  overflow: hidden;
  transition:
    width 0.28s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.2s ease;
}

.public.is-collapsed .public__sidebar {
  width: 0;
  opacity: 0;
  pointer-events: none;
}

.public__sidebar-inner {
  display: flex;
  width: 232px;
  height: 100dvh;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 86%, transparent);
  padding-top: 64px;
  backdrop-filter: blur(16px);
}

.public__search {
  padding: 0 14px 12px;
  border-bottom: 1px solid var(--line);
}

.public__label {
  display: block;
  margin-bottom: 6px;
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.public__search-field input {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: 9px;
  background: var(--surface);
  color: var(--ink);
  font-size: 13px;
}

.public__search-field input:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.public__search-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.public__button {
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 9px;
  background: var(--surface);
  color: var(--ink);
  font-size: 13px;
  cursor: pointer;
}

.public__button:hover:not(:disabled) {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.public__button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.public__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.public__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.public__nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
}

.public__nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: inherit;
  text-decoration: none;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.public__nav-item:hover {
  border-color: var(--accent-line);
  transform: translateY(-1px);
}

.public__nav-item.router-link-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.public__nav-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  background: var(--surface-sunken);
  color: var(--accent-strong);
  font-size: 16px;
}

.public__nav-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.public__nav-title {
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 600;
}

.public__nav-desc {
  color: var(--muted);
  font-size: 11.5px;
}

.public__sidebar-foot {
  margin-top: auto;
  border-top: 1px solid var(--line);
}

.public__foot-hint {
  margin: 0;
  padding: 8px 14px 12px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.public__main {
  min-width: 0;
  flex: 1;
  padding: 18px 24px 72px;
  transition: padding-left 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.public.is-collapsed .public__main {
  padding-left: 84px;
}

.public__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 2px 16px;
  border-bottom: 1px solid var(--line);
}

.public__eyebrow {
  margin: 0 0 6px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.public__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(26px, 3.2vw, 34px);
  font-weight: 600;
}

.public__total {
  color: var(--muted);
  font-size: 12.5px;
}

.public__content {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}

.public__kinds,
.public__list-panel {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  padding: 16px;
}

.public__panel-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
}

.public__panel-desc {
  margin: 6px 0 12px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.6;
}

.public__kind-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.public__kind {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--surface-sunken);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.public__kind:hover {
  border-color: var(--accent-line);
}

.public__kind.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.public__kind-name {
  font-size: 13.5px;
  font-weight: 600;
}

.public__kind-desc {
  color: var(--muted);
  font-size: 11.5px;
}

.public__kind-count {
  margin-top: 4px;
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 11px;
}

.public__result {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}

.public__state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  margin-top: 16px;
  padding: 24px;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  color: var(--muted);
  font-size: 14px;
}

.public__empty-title {
  margin: 0;
  color: var(--muted-strong);
  font-size: 15px;
  font-weight: 600;
}

.public__empty-desc {
  margin: 0;
  font-size: 13px;
}

.public__empty-link {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.public__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.article-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.article-card:hover {
  border-color: var(--accent-line);
  transform: translateY(-1px);
}

.article-card__link {
  display: flex;
  gap: 12px;
  padding: 14px;
  color: inherit;
  text-decoration: none;
}

.article-card__cover {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: 9px;
  object-fit: cover;
}

.article-card__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.article-card__title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15.5px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.article-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
}

.article-card__summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--muted-strong);
  font-size: 12.5px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.article-card__tag {
  align-self: flex-start;
  padding: 2px 8px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 11px;
}

.public__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.public__page {
  color: var(--muted);
  font-size: 12.5px;
}

@media (max-width: 1080px) {
  .public__list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .public__sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 45;
    width: 248px;
    box-shadow: var(--shadow-lg);
    transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .public.is-collapsed .public__sidebar {
    width: 248px;
    opacity: 1;
    transform: translateX(-100%);
  }

  .public__sidebar-inner {
    width: 248px;
    padding-top: 58px;
  }

  .public__main {
    padding: 14px 16px 64px;
  }

  .public.is-collapsed .public__main {
    padding-left: 72px;
  }

  .public__content {
    grid-template-columns: minmax(0, 1fr);
  }

  .public__kind-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .public__kind-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .public__sidebar,
  .public__main {
    transition: none;
  }
}
</style>
