<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="not-found">
    <p class="not-found__code">404</p>
    <h1 class="not-found__title">页面不存在</h1>
    <p class="not-found__desc">这个地址没有对应的页面，请从工作台重新进入。</p>
    <RouterLink class="not-found__link" to="/">打开工作台</RouterLink>
  </main>
</template>

<style scoped>
.not-found {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  text-align: center;
}

.not-found__code {
  margin: 0 0 8px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.18em;
}

.not-found__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 600;
}

.not-found__desc {
  max-width: 420px;
  margin: 14px 0 24px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.not-found__link {
  display: inline-flex;
  align-items: center;
  height: 38px;
  padding: 0 18px;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}

.not-found__link:hover {
  background: var(--accent-strong);
}
</style>
