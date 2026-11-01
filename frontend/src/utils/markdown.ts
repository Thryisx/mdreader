import { marked } from 'marked'
import TurndownService from 'turndown'
import { gfm } from 'turndown-plugin-gfm'

const turndown = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
  emDelimiter: '*',
  strongDelimiter: '**',
  fence: '```',
})

turndown.use(gfm)

export function markdownToHtml(markdown: string): string {
  return marked.parse(markdown, {
    async: false,
    gfm: true,
    breaks: true,
  })
}

export function htmlToMarkdown(html: string): string {
  return turndown.turndown(html)
}

export function countWordsFromHtml(html: string): number {
  if (!html) return 0

  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('pre, code, script, style').forEach((node) => node.remove())

  return countWords(doc.body.textContent ?? '')
}

export function countWords(markdown: string): number {
  const plain = markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/~~~[\s\S]*?~~~/g, ' ')
    .replace(/`[^`\n]*`/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_~`|-]/g, ' ')

  const cjkCount = plain.match(/[\u3400-\u9fff]/g)?.length ?? 0
  const latinCount = plain.match(/[A-Za-z0-9]+/g)?.length ?? 0

  return cjkCount + latinCount
}
