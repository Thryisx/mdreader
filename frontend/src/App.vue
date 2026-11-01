<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView } from 'vue-router'

import ToastHost from '@/components/ToastHost.vue'
import { useBackground } from '@/composables/useBackground'
import { useUserAuth } from '@/composables/useUserAuth'

const { customImage, hasCustomImage } = useBackground()

const backgroundStyle = computed(() =>
  hasCustomImage.value ? { backgroundImage: `url("${customImage.value}")` } : undefined,
)

onMounted(() => {
  // 全局恢复 user 会话：刷新 / 工作台与公开页之间跳转后，只要 localStorage 有 token 就自动登录。
  void useUserAuth().ensureSession()
})
</script>

<template>
  <div
    class="app-bg"
    :class="{ 'has-image': hasCustomImage }"
    :style="backgroundStyle"
    aria-hidden="true"
  />
  <div class="app-content">
    <RouterView />
  </div>
  <ToastHost />
</template>
