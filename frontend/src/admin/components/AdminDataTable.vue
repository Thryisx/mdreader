<script setup lang="ts" generic="T">
import type { AdminColumn } from '@/admin/types'

withDefaults(
  defineProps<{
    columns: AdminColumn<T>[]
    rows: T[]
    rowKey: (row: T) => string | number
    loading?: boolean
    error?: string
    emptyText?: string
  }>(),
  {
    loading: false,
    error: '',
    emptyText: '暂无数据',
  },
)

defineEmits<{
  retry: []
}>()

function cellValue(row: T, key: (keyof T & string) | 'actions'): unknown {
  if (key === 'actions') return undefined
  return row[key]
}
</script>

<template>
  <section class="admin-table-card" :aria-busy="loading">
    <div class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th
              v-for="column in columns"
              :key="column.key"
              :style="{ width: column.width, textAlign: column.align ?? 'left' }"
              scope="col"
            >
              {{ column.label }}
            </th>
          </tr>
        </thead>

        <tbody v-if="!loading && !error && rows.length">
          <tr v-for="row in rows" :key="rowKey(row)">
            <td
              v-for="column in columns"
              :key="column.key"
              :style="{ textAlign: column.align ?? 'left' }"
            >
              <slot :name="`cell-${column.key}`" :row="row" :value="cellValue(row, column.key)">
                {{ cellValue(row, column.key) ?? '—' }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="loading" class="admin-table__state" aria-live="polite">
      <span class="admin-table__spinner" aria-hidden="true" />
      <p>正在加载数据…</p>
    </div>

    <div v-else-if="error" class="admin-table__state admin-table__state--error" role="alert">
      <p class="admin-table__state-title">数据加载失败</p>
      <p class="admin-table__state-desc">{{ error }}</p>
      <button type="button" class="admin-table__retry" @click="$emit('retry')">重新加载</button>
    </div>

    <div v-else-if="!rows.length" class="admin-table__state">
      <p class="admin-table__state-title">{{ emptyText }}</p>
      <p class="admin-table__state-desc">请调整查询条件后重试。</p>
    </div>
  </section>
</template>

<style scoped>
.admin-table-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.admin-table-scroll {
  overflow-x: auto;
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.admin-table th {
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface-sunken) 72%, transparent);
  color: var(--muted);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-align: left;
  white-space: nowrap;
}

.admin-table td {
  max-width: 280px;
  padding: 13px 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
  color: var(--muted-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-table tbody tr:last-child td {
  border-bottom: 0;
}

.admin-table tbody tr:hover {
  background: color-mix(in srgb, var(--accent-soft) 44%, transparent);
}

.admin-table__state {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 28px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  text-align: center;
}

.admin-table__state p {
  margin: 0;
}

.admin-table__state-title {
  color: var(--muted-strong);
  font-size: 14px;
  font-weight: 600;
}

.admin-table__state-desc {
  max-width: 420px;
  font-size: 12.5px;
  line-height: 1.6;
}

.admin-table__state--error .admin-table__state-title {
  color: var(--danger);
}

.admin-table__spinner {
  width: 18px;
  height: 18px;
  margin-bottom: 6px;
  border: 2px solid var(--line-strong);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: admin-spin 0.8s linear infinite;
}

.admin-table__retry {
  margin-top: 8px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 7px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 12.5px;
  cursor: pointer;
}

.admin-table__retry:hover {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

@keyframes admin-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .admin-table {
    min-width: 720px;
  }
}
</style>
