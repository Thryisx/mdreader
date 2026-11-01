import { cp, mkdir, rm } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const source = resolve(root, 'node_modules/tinymce')
const target = resolve(root, 'public/tinymce')

const entries = [
  'tinymce.min.js',
  'tinymce.js',
  'icons',
  'models',
  'plugins',
  'skins',
  'themes',
  'license.md',
]

await rm(target, { recursive: true, force: true })
await mkdir(target, { recursive: true })

for (const entry of entries) {
  await cp(resolve(source, entry), resolve(target, entry), { recursive: true })
}

console.log(`[tinymce] assets copied to ${target}`)
