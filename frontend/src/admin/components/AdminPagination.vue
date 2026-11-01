<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  pages: number
  total: number
  pageSize: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const pageNumbers = computed(() => {
  const total = Math.max(1, props.pages)
  const start = Math.max(1, Math.min(props.page - 2, total - 4))
  const end = Math.min(total, start + 4)
  const numbers: number[] = []

  for (let page = start; page <= end; page += 1) {
    numbers.push(page)
  }

  return numbers
})

function goTo(page: number) {
  if (page < 1 || page > Math.max(1, props.pages) || page === props.page) return
  emit('change', page)
}
</script>

<template>
  <div class="admin-pagination">
    <p class="admin-pagination__summary">
      共 <strong>{{ total }}</strong> 条 · 每页 {{ pageSize }} 条
    </p>

    <div class="admin-pagination__controls">
      <button
        type="button"
        class="admin-pagination__button"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="goTo(page - 1)"
      >
        上一页
      </button>

      <button
        v-for="number in pageNumbers"
        :key="number"
        type="button"
        class="admin-pagination__button admin-pagination__button--number"
        :class="{ 'is-current': number === page }"
        :aria-current="number === page ? 'page' : undefined"
        @click="goTo(number)"
      >
        {{ number }}
      </button>

      <button
        type="button"
        class="admin-pagination__button"
        :disabled="page >= pages"
        aria-label="下一页"
        @click="goTo(page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 16px;
}

.admin-pagination__summary {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
}

.admin-pagination__summary strong {
  color: var(--muted-strong);
  font-weight: 600;
}

.admin-pagination__controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.admin-pagination__button {
  min-width: 34px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface);
  color: var(--muted-strong);
  font-size: 12.5px;
  cursor: pointer;
}

.admin-pagination__button:hover:not(:disabled):not(.is-current) {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.admin-pagination__button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.admin-pagination__button--number {
  padding: 0;
}

.admin-pagination__button.is-current {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

@media (max-width: 640px) {
  .admin-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
