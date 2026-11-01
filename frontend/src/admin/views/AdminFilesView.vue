<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { fetchAdminAllFiles } from '@/api/admin'
import AdminDataTable from '@/admin/components/AdminDataTable.vue'
import AdminPagination from '@/admin/components/AdminPagination.vue'
import AdminSearchBar from '@/admin/components/AdminSearchBar.vue'
import AdminStatusBadge from '@/admin/components/AdminStatusBadge.vue'
import type { AdminColumn } from '@/admin/types'
import { formatDateTime, formatFileSize } from '@/admin/utils/format'
import type { AdminFileVO, FileStatus, FileVisibility } from '@/types/api'

const columns: AdminColumn<AdminFileVO>[] = [
  { key: 'id', label: 'ID', width: '72px' },
  { key: 'title', label: '标题' },
  { key: 'userId', label: '所属用户', width: '96px' },
  { key: 'originalName', label: '原始文件名' },
  { key: 'fileSize', label: '大小', width: '88px' },
  { key: 'visibility', label: '可见性', width: '88px' },
  { key: 'status', label: '状态 / 流向', width: '210px' },
  { key: 'updatedAt', label: '更新时间', width: '168px' },
]

const statusLabels: Record<FileStatus, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
}

const statusTones: Record<FileStatus, 'neutral' | 'warn' | 'ok' | 'danger'> = {
  DRAFT: 'neutral',
  PENDING: 'warn',
  PUBLISHED: 'ok',
  REJECTED: 'danger',
}

const keyword = ref('')
const userId = ref('')
const visibility = ref<'' | FileVisibility>('')
const status = ref<'' | FileStatus>('')
const pageNum = ref(1)
const pageSize = ref(10)
const rows = ref<AdminFileVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(false)
const error = ref('')

let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''

  try {
    const data = await fetchAdminAllFiles({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      userId: userId.value ? Number(userId.value) : undefined,
      visibility: visibility.value || undefined,
      status: status.value || undefined,
    })

    if (seq !== requestSeq) return

    rows.value = data.records
    total.value = data.total
    pages.value = data.pages
    pageNum.value = data.pageNum
  } catch (cause) {
    if (seq !== requestSeq) return

    rows.value = []
    total.value = 0
    pages.value = 0
    error.value = cause instanceof Error ? cause.message : '文件列表加载失败'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function getStatusLabel(value: unknown) {
  return statusLabels[value as FileStatus] ?? String(value)
}

function getStatusTone(value: unknown) {
  return statusTones[value as FileStatus] ?? 'neutral'
}

type FlowState = 'done' | 'pending' | 'rejected' | 'idle'

function getFlowState(value: unknown, index: number): FlowState {
  const status = value as FileStatus
  if (index < 2) return 'done'
  if (status === 'PUBLISHED') return 'done'
  if (status === 'PENDING') return 'pending'
  if (status === 'REJECTED') return 'rejected'
  return 'idle'
}

function applyFilters() {
  pageNum.value = 1
  void load()
}

function changePage(page: number) {
  if (page === pageNum.value) return
  pageNum.value = page
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">文件管理</h1>
        <p class="admin-page__desc">
          查看全量文件元数据与基本文件流向，支持按用户、可见性和状态筛选；管理端不读取用户文件正文。
        </p>
      </div>
    </header>

    <div class="admin-files__flow-legend" aria-label="文件流向说明">
      <span class="admin-files__flow-legend-title">文件流向</span>
      <span>编辑</span>
      <span class="admin-files__flow-arrow" aria-hidden="true">→</span>
      <span>暂时保存</span>
      <span class="admin-files__flow-arrow" aria-hidden="true">→</span>
      <span>保存</span>
      <span class="admin-files__flow-legend-note">删除后不再展示</span>
    </div>

    <div class="admin-page__toolbar">
      <AdminSearchBar
        v-model="keyword"
        placeholder="按文件标题查询"
        :loading="loading"
        @submit="applyFilters"
      >
        <template #filters>
          <input
            v-model="userId"
            class="admin-files__user-filter"
            type="number"
            min="1"
            placeholder="用户 ID"
            aria-label="按用户 ID 筛选"
            @change="applyFilters"
          />

          <select v-model="visibility" aria-label="按可见性筛选" @change="applyFilters">
            <option value="">全部可见性</option>
            <option value="PUBLIC">公开</option>
            <option value="PRIVATE">私有</option>
          </select>

          <select v-model="status" aria-label="按状态筛选" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="PENDING">待审核</option>
            <option value="PUBLISHED">已发布</option>
            <option value="REJECTED">已驳回</option>
          </select>
        </template>
      </AdminSearchBar>
    </div>

    <AdminDataTable
      :columns="columns"
      :rows="rows"
      :row-key="(row) => row.id"
      :loading="loading"
      :error="error"
      empty-text="没有匹配的文件"
      @retry="load"
    >
      <template #cell-title="{ value }">
        <span class="admin-files__title">{{ value }}</span>
      </template>

      <template #cell-originalName="{ value }">
        <span v-if="value">{{ value }}</span>
        <span v-else class="admin-files__empty">未填写</span>
      </template>

      <template #cell-fileSize="{ value }">
        {{ formatFileSize(Number(value)) }}
      </template>

      <template #cell-visibility="{ value }">
        <AdminStatusBadge
          :label="value === 'PUBLIC' ? '公开' : '私有'"
          :tone="value === 'PUBLIC' ? 'accent' : 'muted'"
        />
      </template>

      <template #cell-status="{ value }">
        <div class="admin-files__status">
          <div class="admin-files__flow">
            <span class="admin-files__flow-step" :class="`is-${getFlowState(value, 0)}`">编辑</span>
            <span class="admin-files__flow-arrow" aria-hidden="true">→</span>
            <span class="admin-files__flow-step" :class="`is-${getFlowState(value, 1)}`">
              暂时保存
            </span>
            <span class="admin-files__flow-arrow" aria-hidden="true">→</span>
            <span class="admin-files__flow-step" :class="`is-${getFlowState(value, 2)}`">
              保存
            </span>
          </div>
          <AdminStatusBadge :label="getStatusLabel(value)" :tone="getStatusTone(value)" />
        </div>
      </template>

      <template #cell-updatedAt="{ value }">
        {{ formatDateTime(String(value)) }}
      </template>
    </AdminDataTable>

    <AdminPagination
      v-if="total > 0"
      :page="pageNum"
      :pages="pages"
      :total="total"
      :page-size="pageSize"
      @change="changePage"
    />

  </section>
</template>

<style scoped>
.admin-files__title {
  color: var(--ink);
  font-weight: 600;
}

.admin-files__empty {
  color: var(--muted);
}

.admin-files__flow-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 11.5px;
}

.admin-files__flow-legend-title {
  margin-right: 3px;
  color: var(--muted);
  font-weight: 600;
}

.admin-files__flow-legend-note {
  margin-left: 4px;
  color: var(--muted);
}

.admin-files__flow-arrow {
  color: var(--muted);
}

.admin-files__status {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 5px;
}

.admin-files__flow {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--muted);
  font-size: 10.5px;
  white-space: nowrap;
}

.admin-files__flow-step {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--surface-sunken);
  color: var(--muted);
}

.admin-files__flow-step.is-done {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-files__flow-step.is-pending {
  background: var(--warn-soft);
  color: var(--warn);
}

.admin-files__flow-step.is-rejected {
  background: var(--danger-soft);
  color: var(--danger);
}

.admin-files__user-filter {
  width: 88px;
  height: 32px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  background: var(--surface-sunken);
  color: var(--muted-strong);
  font-size: 12.5px;
}

.admin-files__user-filter:focus {
  border-color: var(--accent-line);
}

.admin-files__action {
  padding: 4px 9px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 11.5px;
  cursor: pointer;
}

.admin-files__action:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}
</style>
