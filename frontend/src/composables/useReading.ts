import { computed, ref, watch } from 'vue'

export interface ReadingPreferences {
  fontSize: number
  lineHeight: number
  maxWidth: number
  codeFontSize: number
}

const STORAGE_KEY = 'md-reader:reading-preferences:v1'
const DEFAULTS: ReadingPreferences = {
  fontSize: 17,
  lineHeight: 1.9,
  maxWidth: 760,
  codeFontSize: 13.5,
}

const MIN_VALUES: ReadingPreferences = {
  fontSize: 14,
  lineHeight: 1.5,
  maxWidth: 560,
  codeFontSize: 11,
}

const MAX_VALUES: ReadingPreferences = {
  fontSize: 22,
  lineHeight: 2.4,
  maxWidth: 960,
  codeFontSize: 16,
}

function clamp(value: number, min: number, max: number) {
  if (Number.isNaN(value)) return min
  return Math.min(max, Math.max(min, value))
}

function normalize(raw: Partial<ReadingPreferences>): ReadingPreferences {
  return {
    fontSize: clamp(raw.fontSize ?? DEFAULTS.fontSize, MIN_VALUES.fontSize, MAX_VALUES.fontSize),
    lineHeight: clamp(raw.lineHeight ?? DEFAULTS.lineHeight, MIN_VALUES.lineHeight, MAX_VALUES.lineHeight),
    maxWidth: clamp(raw.maxWidth ?? DEFAULTS.maxWidth, MIN_VALUES.maxWidth, MAX_VALUES.maxWidth),
    codeFontSize: clamp(
      raw.codeFontSize ?? DEFAULTS.codeFontSize,
      MIN_VALUES.codeFontSize,
      MAX_VALUES.codeFontSize,
    ),
  }
}

function load(): ReadingPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULTS }
    return normalize(JSON.parse(raw) as Partial<ReadingPreferences>)
  } catch {
    return { ...DEFAULTS }
  }
}

const preferences = ref<ReadingPreferences>(load())

watch(
  preferences,
  (next) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } catch {
      return
    }
  },
  { deep: true },
)

export interface HeadingItem {
  id: string
  level: number
  text: string
}

function slugify(text: string, used: Set<string>) {
  const base =
    text
      .toLowerCase()
      .trim()
      .replace(/[^\p{L}\p{N}\s-]/gu, '')
      .replace(/\s+/g, '-')
      .slice(0, 80) || 'section'
  let candidate = base
  let index = 2
  while (used.has(candidate)) {
    candidate = `${base}-${index}`
    index += 1
  }
  used.add(candidate)
  return candidate
}

export function extractHeadings(markdown: string): HeadingItem[] {
  const used = new Set<string>()
  const headings: HeadingItem[] = []
  const fence = /^(```|~~~)/
  let inFence = false

  for (const line of markdown.split('\n')) {
    if (fence.test(line.trim())) {
      inFence = !inFence
      continue
    }
    if (inFence) continue
    const match = /^(#{1,4})\s+(.+?)\s*(?:#+\s*)?$/.exec(line.trim())
    if (!match) continue
    const text = match[2]?.replace(/[*_`~[\]()!]/g, '').trim() ?? ''
    if (!text) continue
    headings.push({ id: slugify(text, used), level: match[1]?.length ?? 1, text })
  }

  return headings
}

export function useReadingPreferences() {
  function update(patch: Partial<ReadingPreferences>) {
    preferences.value = normalize({ ...preferences.value, ...patch })
  }

  function reset() {
    preferences.value = { ...DEFAULTS }
  }

  const styleVars = computed(() => ({
    '--read-font-size': `${preferences.value.fontSize}px`,
    '--read-line-height': String(preferences.value.lineHeight),
    '--read-max-width': `${preferences.value.maxWidth}px`,
    '--read-code-size': `${preferences.value.codeFontSize}px`,
  }))

  return {
    preferences,
    styleVars,
    minValues: MIN_VALUES,
    maxValues: MAX_VALUES,
    defaults: DEFAULTS,
    update,
    reset,
  }
}
