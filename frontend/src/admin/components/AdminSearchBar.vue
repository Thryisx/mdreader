<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    loading?: boolean
  }>(),
  {
    placeholder: '输入关键词查询',
    loading: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

function handleInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function handleSubmit() {
  if (props.loading) return
  emit('submit')
}
</script>

<template>
  <form class="admin-search" role="search" @submit.prevent="handleSubmit">
    <span class="admin-search__icon" aria-hidden="true">
      <svg viewBox="0 0 20 20">
        <circle cx="8.8" cy="8.8" r="5.3" fill="none" stroke="currentColor" stroke-width="1.5" />
        <path
          d="m12.8 12.8 4 4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
        />
      </svg>
    </span>

    <input
      :value="modelValue"
      class="admin-search__input"
      type="search"
      :placeholder="placeholder"
      :aria-label="placeholder"
      :disabled="loading"
      @input="handleInput"
    />

    <div v-if="$slots.filters" class="admin-search__filters">
      <slot name="filters" />
    </div>

    <button class="admin-search__submit" type="submit" :disabled="loading">
      {{ loading ? '查询中…' : '查询' }}
    </button>
  </form>
</template>

<style scoped>
.admin-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 7px 8px 7px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.admin-search:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent);
}

.admin-search__icon {
  display: grid;
  width: 20px;
  height: 20px;
  flex: none;
  place-items: center;
  color: var(--muted);
}

.admin-search__icon svg {
  width: 18px;
  height: 18px;
}

.admin-search__input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-size: 14px;
}

.admin-search__input::placeholder {
  color: var(--muted);
}

.admin-search__input::-webkit-search-cancel-button {
  cursor: pointer;
}

.admin-search__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.admin-search__filters :deep(select) {
  height: 32px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  background: var(--surface-sunken);
  color: var(--muted-strong);
  font-size: 12.5px;
  cursor: pointer;
}

.admin-search__filters :deep(select:focus) {
  border-color: var(--accent-line);
}

.admin-search__submit {
  height: 34px;
  flex: none;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.admin-search__submit:hover:not(:disabled) {
  background: var(--accent-strong);
}

.admin-search__submit:disabled {
  cursor: progress;
  opacity: 0.7;
}

@media (max-width: 720px) {
  .admin-search {
    flex-wrap: wrap;
    padding: 10px 10px 10px 12px;
  }

  .admin-search__input {
    min-width: calc(100% - 36px);
  }

  .admin-search__filters {
    flex: 1;
    flex-wrap: wrap;
  }

  .admin-search__submit {
    margin-left: auto;
  }
}
</style>
