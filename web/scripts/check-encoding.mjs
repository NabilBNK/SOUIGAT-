import fs from 'node:fs'
import path from 'node:path'

const ROOT = process.cwd()
const TARGETS = [
  path.join(ROOT, 'index.html'),
  path.join(ROOT, 'src'),
]
const TEXT_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.jsx', '.css', '.html', '.json'])

// Common mojibake signatures when UTF-8 is decoded as Latin-1 / CP1252.
const SUSPICIOUS_TOKENS = ['Ã', 'Â', 'â€', 'ðŸ', '\uFFFD']

function walk(dir, out) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath, out)
    } else if (TEXT_EXTENSIONS.has(path.extname(entry.name))) {
      out.push(fullPath)
    }
  }
}

const files = []
for (const target of TARGETS) {
  if (!fs.existsSync(target)) continue
  const stat = fs.statSync(target)
  if (stat.isDirectory()) walk(target, files)
  else files.push(target)
}

let hasError = false
for (const filePath of files) {
  const content = fs.readFileSync(filePath, 'utf8')
  const hit = SUSPICIOUS_TOKENS.find((token) => content.includes(token))
  if (!hit) continue

  hasError = true
  const rel = path.relative(ROOT, filePath)
  console.error(`[encoding] suspicious mojibake token "${hit}" found in ${rel}`)
}

if (hasError) {
  process.exit(1)
}

console.log('[encoding] OK')
