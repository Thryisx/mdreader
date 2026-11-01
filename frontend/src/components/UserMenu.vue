<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const props = defineProps<{
  open: boolean
  anchor: HTMLElement | null
}>()

const emit = defineEmits<{
  close: []
  logout: []
}>()

const route = useRoute()
const panel = ref<HTMLElement | null>(null)
const position = ref({ left: 12, bottom: 76 })

function updatePosition() {
  if (!props.anchor) return

  const rect = props.anchor.getBoundingClientRect()
  position.value = {
    left: Math.max(12, Math.min(rect.left, window.innerWidth - 236)),
    bottom: Math.max(12, window.innerHeight - rect.top + 8),
  }
}

function handlePointerDown(event: PointerEvent) {
  if (!props.open) return

  const target = event.target as Node | null
  if (!target) return
  if (panel.value?.contains(target)) return
  if (props.anchor?.contains(target)) return

  emit('close')
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') {
    emit('close')
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) updatePosition()
  },
)

watch(
  () => route.fullPath,
  () => {
    if (props.open) emit('close')
  },
)

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
  window.addEventListener('resize', updatePosition)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('keydown', handleKeydown)
})

const panelStyle = computed(() => ({
  left: `${position.value.left}px`,
  bottom: `${position.value.bottom}px`,
}))
</script>

<template>
  <Teleport to="body">
    <Transition name="menu">
      <nav
        v-if="open"
        ref="panel"
        class="user-menu"
        :style="panelStyle"
        aria-label="用户菜单"
      >
        <RouterLink class="user-menu__item" to="/settings" @click="$emit('close')">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path
              d="M3 6h14M3 10h14M3 14h14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
            />
            <circle cx="7" cy="6" r="1.8" fill="var(--surface)" stroke="currentColor" stroke-width="1.4" />
            <circle cx="13" cy="10" r="1.8" fill="var(--surface)" stroke="currentColor" stroke-width="1.4" />
            <circle cx="9" cy="14" r="1.8" fill="var(--surface)" stroke="currentColor" stroke-width="1.4" />
          </svg>
          <span>用户设置</span>
        </RouterLink>

        <RouterLink class="user-menu__item" to="/profile" @click="$emit('close')">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <circle cx="10" cy="7" r="3.1" fill="none" stroke="currentColor" stroke-width="1.4" />
            <path
              d="M4 16.2c.7-2.7 3-4.3 6-4.3s5.3 1.6 6 4.3"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
            />
          </svg>
          <span>个人信息</span>
        </RouterLink>

        <button type="button" class="user-menu__item user-menu__item--danger" @click="$emit('logout')">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path
              d="M8 4H4.5A1.5 1.5 0 0 0 3 5.5v9A1.5 1.5 0 0 0 4.5 16H8m4-3 3-3-3-3m3 3H7"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span>退出登录</span>
        </button>
      </nav>
    </Transition>
  </Teleport>
</template>

<style scoped>
.user-menu {
  position: fixed;
  z-index: 55;
  display: flex;
  width: 224px;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.user-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--ink);
  font-size: 13.5px;
  text-decoration: none;
  transition: background 0.14s ease;
}

.user-menu__item:hover {
  background: var(--surface-sunken);
}

.user-menu__item--danger {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--danger);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.user-menu__item--danger:hover {
  background: var(--danger-soft);
}

.user-menu__item--danger svg {
  color: var(--danger);
}

.user-menu__item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.user-menu__item svg {
  width: 18px;
  height: 18px;
  flex: none;
  color: var(--muted-strong);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
