<script setup lang="ts">
import type { AdminBadgeTone } from '@/admin/types'

withDefaults(
  defineProps<{
    label: string
    tone?: AdminBadgeTone
  }>(),
  {
    tone: 'neutral',
  },
)
</script>

<template>
  <span class="admin-badge" :class="`admin-badge--${tone}`">{{ label }}</span>
</template>

<style scoped>
.admin-badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 1px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}

.admin-badge--accent {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-badge--ok {
  border-color: var(--ok-line);
  background: var(--ok-soft);
  color: var(--ok);
}

.admin-badge--warn {
  border-color: var(--warn-line);
  background: var(--warn-soft);
  color: var(--warn);
}

.admin-badge--danger {
  border-color: var(--danger-line);
  background: var(--danger-soft);
  color: var(--danger);
}

.admin-badge--muted {
  color: var(--muted);
}
</style>
