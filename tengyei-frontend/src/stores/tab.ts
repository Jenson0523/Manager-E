import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

const STORAGE_KEY = 'open_tabs'
const HOME: TabItem = { path: '/dashboard', title: '工作台', closable: false }

function load(): TabItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as TabItem[]
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch {
    // ignore corrupt storage
  }
  return [{ ...HOME }]
}

export const useTabStore = defineStore('tab', () => {
  const tabs = ref<TabItem[]>(load())
  const activePath = ref<string>(tabs.value[0]?.path ?? HOME.path)

  function persist() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value))
  }

  function openTab(tab: TabItem) {
    if (!tabs.value.some((t) => t.path === tab.path)) {
      tabs.value.push(tab)
      persist()
    }
    activePath.value = tab.path
  }

  function closeTab(path: string): string {
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx === -1) return activePath.value
    if (!tabs.value[idx].closable) return activePath.value
    tabs.value.splice(idx, 1)
    if (activePath.value === path) {
      const next = tabs.value[idx - 1] ?? tabs.value[idx] ?? tabs.value[0]
      activePath.value = next.path
    }
    persist()
    return activePath.value
  }

  function closeOthers(path: string) {
    tabs.value = tabs.value.filter((t) => t.path === path || !t.closable)
    activePath.value = path
    persist()
  }

  function closeAll(): string {
    tabs.value = tabs.value.filter((t) => !t.closable)
    activePath.value = tabs.value[0]?.path ?? HOME.path
    persist()
    return activePath.value
  }

  function setActive(path: string) {
    activePath.value = path
  }

  function reset() {
    tabs.value = [{ ...HOME }]
    activePath.value = HOME.path
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { tabs, activePath, openTab, closeTab, closeOthers, closeAll, setActive, reset }
})
