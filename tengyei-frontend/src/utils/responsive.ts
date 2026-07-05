import { onUnmounted, ref } from 'vue'

const QUERY = '(max-width: 768px)'

/** 移动端断点(<=768px),响应式 ref */
export function useIsMobile() {
  const mq = window.matchMedia(QUERY)
  const isMobile = ref(mq.matches)
  const onChange = (e: MediaQueryListEvent) => (isMobile.value = e.matches)
  mq.addEventListener('change', onChange)
  onUnmounted(() => mq.removeEventListener('change', onChange))
  return isMobile
}
