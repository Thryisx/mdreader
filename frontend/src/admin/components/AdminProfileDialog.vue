<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    username: string
    nickname: string
    email: string
    saving?: boolean
  }>(),
  {
    saving: false,
  },
)

const emit = defineEmits<{
  close: []
  save: [payload: { nickname: string; email: string }]
}>()

const nicknameInput = ref<HTMLInputElement | null>(null)
const nickname = ref('')
const email = ref('')
const error = ref('')

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function resetForm() {
  nickname.value = props.nickname
  email.value = props.email
  error.value = ''
}

function close() {
  emit('close')
}

function submit() {
  if (props.saving) return

  const nextNickname = nickname.value.trim()
  const nextEmail = email.value.trim()

  if (!nextNickname) {
    error.value = '请输入昵称'
    return
  }

  if (nextEmail && !emailPattern.test(nextEmail)) {
    error.value = '请输入有效的邮箱地址'
    return
  }

  error.value = ''
  emit('save', { nickname: nextNickname, email: nextEmail })
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') close()
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''

    if (!open) return

    resetForm()
    void nextTick(() => nicknameInput.value?.focus())
  },
  { immediate: true },
)

onMounted(() => window.addEventListener('keydown', handleKeydown))

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="admin-profile">
      <div v-if="open" class="admin-profile" @click.self="close">
        <section
          class="admin-profile__panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="admin-profile-title"
        >
          <header class="admin-profile__head">
            <h2 id="admin-profile-title" class="admin-profile__title">编辑资料</h2>
            <button
              type="button"
              class="admin-profile__close"
              aria-label="关闭编辑资料"
              :disabled="saving"
              @click="close"
            >
              ×
            </button>
          </header>

          <p class="admin-profile__desc">
            修改当前管理员的展示信息，保存后同步到后端。
          </p>

          <form class="admin-profile__form" @submit.prevent="submit">
            <label class="admin-profile__field">
              <span>账号</span>
              <input :value="username" type="text" disabled />
            </label>

            <label class="admin-profile__field">
              <span>昵称</span>
              <input
                ref="nicknameInput"
                v-model="nickname"
                type="text"
                maxlength="20"
                placeholder="用于头像菜单显示"
                autocomplete="nickname"
                :disabled="saving"
              />
            </label>

            <label class="admin-profile__field">
              <span>邮箱</span>
              <input
                v-model="email"
                type="email"
                maxlength="120"
                placeholder="name@example.com"
                autocomplete="email"
                :disabled="saving"
              />
            </label>

            <p v-if="error" class="admin-profile__error" role="alert">{{ error }}</p>

            <div class="admin-profile__actions">
              <button
                type="button"
                class="admin-profile__button"
                :disabled="saving"
                @click="close"
              >
                取消
              </button>
              <button
                type="submit"
                class="admin-profile__button admin-profile__button--primary"
                :disabled="saving"
              >
                {{ saving ? '保存中…' : '保存资料' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.admin-profile {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(24, 21, 15, 0.4);
  backdrop-filter: blur(3px);
}

.admin-profile__panel {
  width: min(420px, 100%);
  padding: 22px 24px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.admin-profile__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-profile__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
}

.admin-profile__close {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.admin-profile__close:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-profile__close:disabled,
.admin-profile__button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-profile__desc {
  margin: 9px 0 18px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.admin-profile__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.admin-profile__field span {
  color: var(--muted-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.admin-profile__field input {
  width: 100%;
  height: 38px;
  padding: 0 11px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.admin-profile__field input:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-line);
  outline-offset: 1px;
}

.admin-profile__field input:disabled {
  background: var(--surface-sunken);
  color: var(--muted);
  cursor: not-allowed;
}

.admin-profile__error {
  margin: 10px 0 0;
  color: var(--warn);
  font-size: 12px;
}

.admin-profile__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.admin-profile__button {
  padding: 8px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 12.5px;
  cursor: pointer;
}

.admin-profile__button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.admin-profile__button--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.admin-profile__button--primary:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.admin-profile__button:focus-visible,
.admin-profile__close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.admin-profile-enter-active,
.admin-profile-leave-active {
  transition: opacity 0.18s ease;
}

.admin-profile-enter-active .admin-profile__panel,
.admin-profile-leave-active .admin-profile__panel {
  transition: transform 0.18s ease;
}

.admin-profile-enter-from,
.admin-profile-leave-to {
  opacity: 0;
}

.admin-profile-enter-from .admin-profile__panel,
.admin-profile-leave-to .admin-profile__panel {
  transform: translateY(8px) scale(0.98);
}
</style>
