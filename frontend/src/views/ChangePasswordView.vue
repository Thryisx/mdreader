<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAdminAuth } from '@/admin/composables/useAdminAuth'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { useToast } from '@/composables/useToast'
import { useUserAuth } from '@/composables/useUserAuth'
import type { ChangePasswordPayload } from '@/types/api'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const adminAuth = useAdminAuth()
const userAuth = useUserAuth()

const isAdminScope = computed(() => route.query.scope === 'admin')
const saving = ref(false)

async function handleSave(payload: ChangePasswordPayload) {
  if (saving.value) return

  saving.value = true

  try {
    const auth = isAdminScope.value ? adminAuth : userAuth
    await auth.changePassword(payload)
    showToast('密码已修改，请使用新密码重新登录', 'success')
    await router.replace({ name: isAdminScope.value ? 'admin-login' : 'login' })
  } catch (err) {
    const message = err instanceof Error ? err.message : '密码修改失败，请稍后重试'
    showToast(message, 'warn')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  document.title = '修改密码 · MD Reader'
})

onBeforeUnmount(() => {
  document.title = 'MD Reader'
})
</script>

<template>
  <main class="change-password-page">
    <div class="change-password-page__brand">
      <img class="change-password-page__mark" src="/favicon.svg" alt="" aria-hidden="true" />
      <span>
        <strong>MD Reader</strong>
        <small>{{ isAdminScope ? 'Admin Console' : 'Reader Account' }}</small>
      </span>
    </div>

    <section class="change-password-page__card">
      <p class="change-password-page__eyebrow">安全设置</p>
      <h1 class="change-password-page__title">首次登录需要修改密码</h1>
      <p class="change-password-page__desc">
        当前账号仍在使用初始密码或管理员重置的临时密码。完成修改后才能继续使用系统。
      </p>
      <p class="change-password-page__hint">
        新密码需为 10 位，包含 8 位数字和 2 位英文字母。
      </p>
    </section>

    <ChangePasswordDialog
      :open="true"
      :saving="saving"
      :closable="false"
      @save="handleSave"
    />
  </main>
</template>

<style scoped>
.change-password-page {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px 20px;
  background: var(--bg);
}

.change-password-page__brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: var(--ink);
}

.change-password-page__mark {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.change-password-page__brand span:last-child {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.change-password-page__brand strong {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
}

.change-password-page__brand small {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.change-password-page__card {
  width: min(440px, 100%);
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.change-password-page__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.change-password-page__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
}

.change-password-page__desc {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.change-password-page__hint {
  margin: 14px 0 0;
  padding: 10px 12px;
  border: 1px solid var(--accent-line);
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--muted-strong);
  font-size: 12px;
  line-height: 1.55;
}
</style>
