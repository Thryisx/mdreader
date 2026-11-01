export interface ChapterSummary {
  id: string
  title: string
  summary: string
  updatedAt: string
  xssDemo?: boolean
}

export interface Book {
  id: string
  title: string
  author: string
  description: string
  chapters: ChapterSummary[]
}

export interface Chapter extends ChapterSummary {
  bookId: string
  markdown: string
}
