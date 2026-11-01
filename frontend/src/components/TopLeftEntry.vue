<script setup lang="ts">
defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <div class="entry" :class="{ 'is-collapsed': collapsed }">
    <img class="entry__logo" src="/favicon.svg" alt="" aria-hidden="true" />
    <button
      type="button"
      class="entry__button"
      aria-controls="multifunction-bar"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? '展开多功能栏' : '收起多功能栏'"
      :title="collapsed ? '展开多功能栏' : '收起多功能栏'"
      @click="$emit('toggle')"
    >
      <span class="entry__line" aria-hidden="true" />
      <span class="entry__line" aria-hidden="true" />
      <span class="entry__line" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.entry {
  position: fixed;
  top: 14px;
  left: 16px;
  z-index: 46;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: gap 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.entry.is-collapsed {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.entry__logo {
  display: block;
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.entry__button {
  display: inline-flex;
  width: 38px;
  height: 38px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition:
    border-color 0.18s ease,
    background 0.18s ease;
}

.entry__button:hover {
  border-color: var(--accent-line);
  background: var(--surface);
}

.entry__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.entry__line {
  width: 16px;
  height: 1.5px;
  border-radius: 999px;
  background: var(--ink);
}

@media (max-width: 640px) {
  .entry {
    top: 10px;
    left: 12px;
  }

  .entry__logo {
    display: none;
  }
}
</style>
