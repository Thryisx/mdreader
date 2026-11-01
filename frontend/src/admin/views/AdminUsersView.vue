<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  createAdminUser,
  deleteAdminUser,
  fetchAdminUsers,
  resetAdminUserPassword,
  updateAdminUser,
  updateAdminUserStatus,
} from '@/api/admin'
import AdminDataTable from '@/admin/components/AdminDataTable.vue'
import AdminPagination from '@/admin/components/AdminPagination.vue'
import AdminPasswordDialog from '@/admin/components/AdminPasswordDialog.vue'
import AdminSearchBar from '@/admin/components/AdminSearchBar.vue'
import AdminStatusBadge from '@/admin/components/AdminStatusBadge.vue'
import AdminUserDialog from '@/admin/components/AdminUserDialog.vue'
import { useAdminAuth } from '@/admin/composables/useAdminAuth'
import type { AdminColumn, AdminUserDialogMode, AdminUserFormValue } from '@/admin/types'
import { formatDateTime } from '@/admin/utils/format'
import { useToast } from '@/composables/useToast'
import type { UserRole, UserStatus, UserVO } from '@/types/api'

const columns: AdminColumn<UserVO>[] = [
  { key: 'id', label: 'ID', width: '72px' },
  { key: 'username', label: '用户名' },
  { key: 'email', label: '邮箱' },
  { key: 'role', label: '角色', width: '96px' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'createdAt', label: '创建时间', width: '168px' },
  { key: 'actions', label: '操作', width: '248px' },
]

const { user: adminUser } = useAdminAuth()
const { showToast } = useToast()

const keyword = ref('')
const role = ref<'' | UserRole>('')
const status = ref<'' | `${UserStatus}`>('')
const pageNum = ref(1)
const pageSize = ref(10)
const rows = ref<UserVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(false)
const error = ref('')
const actionId = ref<number | null>(null)

const userDialogOpen = ref(false)
const userDialogMode = ref<AdminUserDialogMode>('create')
const editingUser = ref<UserVO | null>(null)
const userSaving = ref(false)

const passwordDialogOpen = ref(false)
const passwordUser = ref<UserVO | null>(null)
const passwordSaving = ref(false)

const currentUserId = computed(() => adminUser.value?.id ?? null)

let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''

  try {
    const data = await fetchAdminUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      role: role.value || undefined,
      status: status.value === '' ? undefined : (Number(status.value) as UserStatus),
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
    error.value = cause instanceof Error ? cause.message : '用户列表加载失败'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
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

function openCreateDialog() {
  userDialogMode.value = 'create'
  editingUser.value = null
  userDialogOpen.value = true
}

function openEditDialog(row: UserVO) {
  userDialogMode.value = 'edit'
  editingUser.value = row
  userDialogOpen.value = true
}

function closeUserDialog() {
  userDialogOpen.value = false
  editingUser.value = null
}

async function handleUserSave(value: AdminUserFormValue) {
  if (userSaving.value) return

  userSaving.value = true

  try {
    if (userDialogMode.value === 'create') {
      await createAdminUser({
        username: value.username,
        password: value.password,
        nickname: value.nickname || undefined,
        email: value.email || undefined,
        role: value.role,
        status: value.status,
      })
      showToast('用户已创建', 'success')
    } else if (editingUser.value) {
      await updateAdminUser(editingUser.value.id, {
        nickname: value.nickname,
        email: value.email,
        role: value.role,
        status: value.status,
      })
      showToast('用户资料已更新', 'success')
    }

    closeUserDialog()
    await load()
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '用户保存失败', 'warn')
  } finally {
    userSaving.value = false
  }
}

async function toggleStatus(row: UserVO) {
  if (actionId.value !== null || row.id === currentUserId.value) return

  actionId.value = row.id
  const nextStatus: UserStatus = row.status === 1 ? 0 : 1

  try {
    await updateAdminUserStatus(row.id, nextStatus)
    showToast(nextStatus === 1 ? '用户已启用' : '用户已禁用', 'success')
    await load()
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '状态更新失败', 'warn')
  } finally {
    actionId.value = null
  }
}

function openPasswordDialog(row: UserVO) {
  passwordUser.value = row
  passwordDialogOpen.value = true
}

function closePasswordDialog() {
  passwordDialogOpen.value = false
  passwordUser.value = null
}

async function handlePasswordSave(password: string) {
  if (passwordSaving.value || !passwordUser.value) return

  passwordSaving.value = true

  try {
    await resetAdminUserPassword(passwordUser.value.id, password)
    showToast('密码已重置', 'success')
    closePasswordDialog()
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '密码重置失败', 'warn')
  } finally {
    passwordSaving.value = false
  }
}

async function removeUser(row: UserVO) {
  if (actionId.value !== null || row.id === currentUserId.value) return

  const confirmed = window.confirm(`确定删除用户「${row.username}」？此操作不可撤销。`)
  if (!confirmed) return

  actionId.value = row.id

  try {
    await deleteAdminUser(row.id)
    showToast('用户已删除', 'success')
    await load()
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '用户删除失败', 'warn')
  } finally {
    actionId.value = null
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">用户管理</h1>
        <p class="admin-page__desc">
          维护管理员与普通用户账号，支持新建、编辑、启禁用、重置密码和删除。
        </p>
      </div>
      <button type="button" class="admin-users__create" @click="openCreateDialog">
        新增用户
      </button>
    </header>

    <div class="admin-page__toolbar">
      <AdminSearchBar
        v-model="keyword"
        placeholder="按用户名或邮箱查询"
        :loading="loading"
        @submit="applyFilters"
      >
        <template #filters>
          <select v-model="role" aria-label="按角色筛选" @change="applyFilters">
            <option value="">全部角色</option>
            <option value="ADMIN">管理员</option>
            <option value="USER">普通用户</option>
          </select>

          <select v-model="status" aria-label="按状态筛选" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
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
      empty-text="没有匹配的用户"
      @retry="load"
    >
      <template #cell-username="{ value }">
        <span class="admin-users__username">{{ value }}</span>
      </template>

      <template #cell-email="{ value }">
        <span v-if="value">{{ value }}</span>
        <span v-else class="admin-users__empty">未填写</span>
      </template>

      <template #cell-role="{ value }">
        <AdminStatusBadge
          :label="value === 'ADMIN' ? '管理员' : '普通用户'"
          :tone="value === 'ADMIN' ? 'accent' : 'neutral'"
        />
      </template>

      <template #cell-status="{ value }">
        <AdminStatusBadge
          :label="value === 1 ? '启用' : '禁用'"
          :tone="value === 1 ? 'ok' : 'muted'"
        />
      </template>

      <template #cell-createdAt="{ value }">
        {{ formatDateTime(String(value)) }}
      </template>

      <template #cell-actions="{ row }">
        <div class="admin-users__actions">
          <button
            type="button"
            class="admin-users__action"
            :disabled="actionId === row.id"
            @click="openEditDialog(row)"
          >
            编辑
          </button>
          <button
            type="button"
            class="admin-users__action"
            :disabled="actionId !== null || row.id === currentUserId"
            :title="row.id === currentUserId ? '不能禁用当前登录用户' : ''"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </button>
          <button
            type="button"
            class="admin-users__action"
            :disabled="actionId !== null"
            @click="openPasswordDialog(row)"
          >
            重置密码
          </button>
          <button
            type="button"
            class="admin-users__action admin-users__action--danger"
            :disabled="actionId !== null || row.id === currentUserId"
            :title="row.id === currentUserId ? '不能删除当前登录用户' : ''"
            @click="removeUser(row)"
          >
            删除
          </button>
        </div>
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

    <AdminUserDialog
      :open="userDialogOpen"
      :mode="userDialogMode"
      :user="editingUser"
      :saving="userSaving"
      @close="closeUserDialog"
      @save="handleUserSave"
    />

    <AdminPasswordDialog
      :open="passwordDialogOpen"
      :user="passwordUser"
      :saving="passwordSaving"
      @close="closePasswordDialog"
      @save="handlePasswordSave"
    />
  </section>
</template>

<style scoped>
.admin-users__create {
  padding: 8px 16px;
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.admin-users__create:hover {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
}

.admin-users__username {
  color: var(--ink);
  font-weight: 600;
}

.admin-users__empty {
  color: var(--muted);
}

.admin-users__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.admin-users__action {
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted-strong);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
}

.admin-users__action:hover:not(:disabled) {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.admin-users__action--danger {
  color: var(--danger);
}

.admin-users__action--danger:hover:not(:disabled) {
  border-color: var(--danger-line);
  background: var(--danger-soft);
  color: var(--danger);
}

.admin-users__action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
