// 构建后将 frontend/dist 复制到 Spring Boot 静态资源目录，
// 这样 IDEA 后端（localhost:8081）直接就能服务到最新前端，避免“改了代码却看不到变化”。
import { existsSync, mkdirSync, readdirSync, statSync, copyFileSync, rmSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const distDir = join(__dirname, 'dist')
// frontend 在 <root>/frontend，静态目录在 <root>/src/main/resources/static
const staticDir = join(__dirname, '..', 'src', 'main', 'resources', 'static')

if (!existsSync(distDir)) {
  console.error('[copy-to-static] 未找到 dist 目录，请先运行 vite build')
  process.exit(1)
}

// 清空旧构建产物（仅 static 目录内），避免残留旧 hash 文件
if (existsSync(staticDir)) {
  for (const name of readdirSync(staticDir)) {
    const p = join(staticDir, name)
    rmSync(p, { recursive: true, force: true })
  }
} else {
  mkdirSync(staticDir, { recursive: true })
}

function copyRecursively(src, dest) {
  const info = statSync(src)
  if (info.isDirectory()) {
    mkdirSync(dest, { recursive: true })
    for (const name of readdirSync(src)) copyRecursively(join(src, name), join(dest, name))
  } else {
    copyFileSync(src, dest)
  }
}

copyRecursively(distDir, staticDir)
console.log('[copy-to-static] 已同步 dist -> ' + staticDir)
