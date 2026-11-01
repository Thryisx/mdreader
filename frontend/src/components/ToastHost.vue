<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toast, hideToast } = useToast()
</script>

<template>
  <div class="toast-host" aria-live="polite" aria-atomic="true">
    <Transition name="toast">
      <button
        v-if="toast"
        :key="toast.id"
        type="button"
        class="toast"
        :class="`toast--${toast.tone}`"
        title="点击关闭提示"
        @click="hideToast"
      >
        <span class="toast__dot" aria-hidden="true" />
        <span class="toast__text">{{ toast.message }}</span>
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  left: 50%;
  bottom: 32px;
  z-index: 60;
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(520px, calc(100vw - 40px));
  padding: 11px 18px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  line-height: 1.4;
  box-shadow: var(--shadow-md);
  cursor: pointer;
  pointer-events: auto;
}

.toast__dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--accent);
}

.toast--success .toast__dot {
  background: var(--ok);
}

.toast--warn .toast__dot {
  background: var(--warn);
}

.toast__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 640px) {
  .toast-host {
    bottom: 20px;
  }
}
</style>
