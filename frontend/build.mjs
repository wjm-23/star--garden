// 用 Node 直接调用 Vite 构建，避免命令行 cd/--root 在某些 shell 下不可用的问题。
import { build } from 'vite'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
process.chdir(__dirname) // 让 root = 当前 frontend 目录
await build({ configFile: join(__dirname, 'vite.config.js') })
console.log('[build.mjs] vite build 完成')
