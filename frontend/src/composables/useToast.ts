import { readonly, ref } from 'vue'

export type ToastTone = 'info' | 'success' | 'warn'

export interface ToastState {
  id: number
  message: string
  tone: ToastTone
}

const toast = ref<ToastState | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let seed = 0

function clearTimer() {
  if (timer) {
    clearTimeout(timer)
    timer = undefined
  }
}

export function useToast() {
  function showToast(message: string, tone: ToastTone = 'info', duration = 2400) {
    clearTimer()
    seed += 1
    toast.value = { id: seed, message, tone }
    timer = setTimeout(() => {
      toast.value = null
      timer = undefined
    }, duration)
  }

  function hideToast() {
    clearTimer()
    toast.value = null
  }

  return {
    toast: readonly(toast),
    showToast,
    hideToast,
  }
}
