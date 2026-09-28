<template>
  <div class="login-wrap">
    <!-- 星空流星背景 -->
    <div class="bg-layer">
      <canvas ref="starCanvas" class="star-canvas"></canvas>
    </div>

    <!-- 一体化认证卡：左侧品牌文字 + 右侧表单 -->
    <div class="auth-card">
      <!-- 左：品牌文字 -->
      <section class="brand-side">
        <div class="brand-mark">
          <span class="mark-halo"></span>
          <span class="mark-ring"></span>
          <svg class="mark-svg" viewBox="0 0 64 64" aria-hidden="true">
            <defs>
              <linearGradient id="sgLeaf" x1="0" y1="0" x2="0.4" y2="1">
                <stop offset="0" stop-color="#c7f9dc" />
                <stop offset="0.5" stop-color="#74e0a4" />
                <stop offset="1" stop-color="#2d6a4f" />
              </linearGradient>
              <linearGradient id="sgStem" x1="0" y1="1" x2="0" y2="0">
                <stop offset="0" stop-color="#52b788" />
                <stop offset="1" stop-color="#b7e4c7" />
              </linearGradient>
            </defs>
            <path d="M32 58 V32" stroke="url(#sgStem)" stroke-width="3.4" stroke-linecap="round" fill="none" />
            <path d="M32 36 C32 22 45 15 55 15 C55 30 44 38 32 36 Z" fill="url(#sgLeaf)" />
            <path d="M32 44 C32 32 21 25 11 25 C11 38 20 46 32 44 Z" fill="url(#sgLeaf)" opacity="0.82" />
            <circle cx="32" cy="12" r="3.2" fill="#eafff3" opacity="0.9" />
          </svg>
        </div>

        <h1 class="brand-title">星芽花园</h1>
        <p class="brand-tag">专注，让每一颗种子开花</p>

        <div class="brand-icons">
          <span>✨</span><span>🌿</span><span>⭐</span><span>🍀</span><span>🌙</span><span>🌸</span>
        </div>

        <ul class="brand-features">
          <li><i>🌱</i>智能推荐，为你匹配专注任务</li>
          <li><i>🪴</i>专注生长，6×6 花园绽放</li>
          <li><i>🤝</i>好友同频，一起种下星光</li>
        </ul>
      </section>

      <!-- 右：登录 / 注册表单 -->
      <section class="form-side">
        <div class="card-brand">
          <span class="brand-name">欢迎回来</span>
          <span class="brand-sub">登录你的星芽花园</span>
        </div>

        <div class="tab-bar">
          <div class="tab" :class="{ active: active==='login' }" @click="active='login'">登录</div>
          <div class="tab" :class="{ active: active==='register' }" @click="active='register'">注册</div>
        </div>

        <!-- 登录 -->
        <div v-show="active==='login'" class="form-area">
          <el-form :model="loginForm" :rules="rules" ref="loginRef" @submit.prevent="handleLogin" size="large">
            <div class="input-wrap">
              <span class="input-icon">👤</span>
              <el-form-item prop="username">
                <el-input v-model="loginForm.username" placeholder="用户名" />
              </el-form-item>
            </div>
            <div class="input-wrap">
              <span class="input-icon">🔒</span>
              <el-form-item prop="password">
                <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" @keyup.enter="handleLogin" />
              </el-form-item>
            </div>
            <button class="submit-btn" :class="{ loading }" :disabled="loading" @click="handleLogin">
              <span v-if="!loading">🚪 进入我的花园</span>
              <span v-else>🌿 正在生长...</span>
            </button>
          </el-form>
          <div class="demo-tip">
            💡 演示账号：<code>sim01</code> / <code>123456</code> · <code>admin</code> / <code>admin123</code>
          </div>
        </div>

        <!-- 注册 -->
        <div v-show="active==='register'" class="form-area">
          <el-form :model="regForm" :rules="rulesReg" ref="regRef" @submit.prevent="handleRegister" size="large">
            <div class="input-wrap">
              <span class="input-icon">👤</span>
              <el-form-item prop="username">
                <el-input v-model="regForm.username" placeholder="取一个用户名" />
              </el-form-item>
            </div>
            <div class="input-wrap">
              <span class="input-icon">🔒</span>
              <el-form-item prop="password">
                <el-input v-model="regForm.password" type="password" show-password placeholder="密码 ≥6 位" />
              </el-form-item>
            </div>
            <div class="input-wrap">
              <span class="input-icon">📧</span>
              <el-form-item prop="email">
                <el-input v-model="regForm.email" placeholder="邮箱" />
              </el-form-item>
            </div>
            <button class="submit-btn" :class="{ loading }" :disabled="loading" @click="handleRegister">
              <span v-if="!loading">🌱 种下第一颗种子</span>
              <span v-else>🌿 正在生长...</span>
            </button>
          </el-form>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const active = ref('login')
const loading = ref(false)
const loginRef = ref(null)
const regRef = ref(null)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', email: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const rulesReg = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

/* === 星空流星背景 === */
const starCanvas = ref(null)
let rafId = null, ctx = null, stars = [], meteors = [], W = 0, H = 0

function resize() {
  const c = starCanvas.value; if (!c) return
  W = c.width = window.innerWidth; H = c.height = window.innerHeight; createStars()
}
function createStars() {
  const count = Math.floor((W * H) / 4200); stars = []
  for (let i = 0; i < count; i++) stars.push({
    x: Math.random() * W, y: Math.random() * H, r: Math.random() * 1.5 + 0.3,
    baseAlpha: Math.random() * 0.5 + 0.35, tw: Math.random() * 0.025 + 0.006, phase: Math.random() * Math.PI * 2
  })
}
function spawnMeteor() {
  // 从上半部分随机位置斜向下划落，同屏可多条
  const startX = Math.random() * W * 1.1 - W * 0.05
  const startY = -60 - Math.random() * 80
  const angle = Math.PI / 4 + (Math.random() * 0.22 - 0.11) // ≈ 45° 斜向下
  const len = Math.random() * 220 + 150
  const speed = Math.random() * 8 + 8
  const width = Math.random() * 1.6 + 1.4
  const tint = Math.random() < 0.35 ? '170,255,205' : '235,245,255' // 部分流星带青绿
  meteors.push({ x: startX, y: startY, len, speed, angle, width, life: 1, tint })
}
function loop() {
  if (!ctx) return
  ctx.clearRect(0, 0, W, H)

  // 星点闪烁
  for (const s of stars) {
    s.phase += s.tw
    const a = s.baseAlpha + Math.sin(s.phase) * 0.35
    ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(255,255,255,${Math.max(0.12, a)})`; ctx.fill()
  }

  // 流星生成：概率提高 + 同屏最多 8 条
  if (meteors.length < 8 && Math.random() < 0.055) spawnMeteor()
  if (meteors.length < 8 && Math.random() < 0.03) spawnMeteor()

  // 流星绘制
  for (let i = meteors.length - 1; i >= 0; i--) {
    const m = meteors[i]
    m.x += Math.cos(m.angle) * m.speed
    m.y += Math.sin(m.angle) * m.speed
    m.life -= 0.0055
    const tx = m.x - Math.cos(m.angle) * m.len
    const ty = m.y - Math.sin(m.angle) * m.len
    const g = ctx.createLinearGradient(m.x, m.y, tx, ty)
    g.addColorStop(0, `rgba(${m.tint},${Math.max(0, m.life)})`)
    g.addColorStop(0.12, `rgba(${m.tint},${Math.max(0, m.life) * 0.75})`)
    g.addColorStop(1, `rgba(${m.tint},0)`)
    ctx.strokeStyle = g
    ctx.lineWidth = m.width
    ctx.lineCap = 'round'
    ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(tx, ty); ctx.stroke()

    // 流星头部光点
    ctx.save()
    ctx.shadowBlur = 14
    ctx.shadowColor = `rgba(${m.tint},${Math.max(0, m.life)})`
    ctx.beginPath(); ctx.arc(m.x, m.y, m.width * 1.5, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(${m.tint},${Math.max(0, m.life) * 0.95})`
    ctx.fill()
    ctx.restore()

    if (m.life <= 0 || m.x < -200 || m.x > W + 200 || m.y > H + 120) meteors.splice(i, 1)
  }
  rafId = requestAnimationFrame(loop)
}
function initStarfield() {
  const c = starCanvas.value; if (!c) return
  ctx = c.getContext('2d'); resize()
  window.addEventListener('resize', resize)
  // 开场先铺几条流星，避免干等
  for (let i = 0; i < 3; i++) spawnMeteor()
  loop()
}

onMounted(() => {
  initStarfield()
})
onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
})

async function handleLogin() {
  try {
    await loginRef.value.validate()
    loading.value = true
    await auth.login(loginForm.username.trim(), loginForm.password)
    ElMessage.success('🌿 欢迎回来，' + auth.nickname)
    router.replace(route.query.redirect || '/tasks')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  try {
    await regRef.value.validate()
    loading.value = true
    await auth.register(regForm)
    ElMessage.success('🌱 种子已种下，专注让它开花！')
    router.replace('/tasks')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  background:
    radial-gradient(ellipse at 20% 10%, rgba(82,183,136,.18) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 90%, rgba(30,70,130,.45) 0%, transparent 50%),
    linear-gradient(135deg, #03060f 0%, #071326 35%, #0a2419 70%, #0f3d2a 100%);
  position: relative; overflow: hidden;
  padding: 24px;
}

/* === 星空流星背景 === */
.bg-layer { position: absolute; inset: 0; pointer-events: none; overflow: hidden; z-index: 0; }
.star-canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.bg-layer::after {
  content: ''; position: absolute; inset: 0;
  background:
    radial-gradient(ellipse at 78% 18%, rgba(120,170,255,.12) 0%, transparent 42%),
    radial-gradient(ellipse at 18% 82%, rgba(82,183,136,.16) 0%, transparent 50%);
}

/* === 一体化认证卡 === */
.auth-card {
  position: relative; z-index: 2;
  display: flex;
  width: min(920px, 100%);
  border-radius: 28px;
  background: rgba(8, 26, 19, .72);
  backdrop-filter: blur(26px);
  -webkit-backdrop-filter: blur(26px);
  border: 1px solid rgba(82,183,136,.28);
  box-shadow:
    0 44px 120px rgba(0,0,0,.66),
    0 0 0 1px rgba(74,222,128,.08),
    inset 0 1px 0 rgba(255,255,255,.06);
  overflow: hidden;
}

/* === 左侧品牌区 === */
.brand-side {
  flex: 1 1 46%;
  padding: 52px 42px;
  display: flex; flex-direction: column; justify-content: center;
  background:
    radial-gradient(circle at 28% 18%, rgba(82,183,136,.25), transparent 60%),
    radial-gradient(circle at 90% 100%, rgba(60,120,200,.18), transparent 55%),
    linear-gradient(160deg, rgba(27,94,63,.58), rgba(9,30,22,.25));
  border-right: 1px solid rgba(82,183,136,.16);
  position: relative;
}
.brand-mark {
  position: relative; width: 96px; height: 96px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 26px;
}
.mark-halo {
  position: absolute; inset: 0; border-radius: 50%;
  background: radial-gradient(circle, rgba(74,222,128,.38) 0%, rgba(82,183,136,.12) 45%, transparent 72%);
  animation: haloPulse 4s ease-in-out infinite;
}
.mark-ring {
  position: absolute; inset: 2px; border-radius: 50%;
  border: 1px dashed rgba(167,215,181,.42);
  animation: spin 20s linear infinite;
}
.mark-svg {
  position: relative; width: 56px; height: 56px;
  animation: bob 3.4s ease-in-out infinite;
  filter: drop-shadow(0 10px 26px rgba(74,222,128,.55));
}
@keyframes bob { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-9px); } }
@keyframes haloPulse { 0%,100% { transform: scale(1); opacity:.65; } 50% { transform: scale(1.12); opacity:1; } }
@keyframes spin { to { transform: rotate(360deg); } }

.brand-title {
  font-family: 'KaiTi', '楷体', 'Songti SC', 'STSong', serif;
  font-size: 54px; margin: 0; font-weight: 700; letter-spacing: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #e8f5e9 42%, #95d5b2 100%);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent; color: transparent;
  filter: drop-shadow(0 6px 26px rgba(82,183,136,.55));
}
.brand-tag {
  font-size: 16px; color: #b7e4c7; margin: 16px 0 24px;
  letter-spacing: 5px; font-weight: 300;
  text-shadow: 0 2px 12px rgba(0,0,0,.45);
}
.brand-icons { display: flex; gap: 16px; font-size: 20px; margin-bottom: 26px; }
.brand-icons span {
  animation: iconFloat 3.2s ease-in-out infinite;
  filter: drop-shadow(0 4px 12px rgba(74,222,128,.45));
}
.brand-icons span:nth-child(2) { animation-delay: .3s; }
.brand-icons span:nth-child(3) { animation-delay: .6s; }
.brand-icons span:nth-child(4) { animation-delay: .9s; }
.brand-icons span:nth-child(5) { animation-delay: 1.2s; }
.brand-icons span:nth-child(6) { animation-delay: 1.5s; }
@keyframes iconFloat { 0%,100% { transform: translateY(0); opacity:.65; } 50% { transform: translateY(-8px); opacity:1; } }

.brand-features { list-style: none; padding: 0; margin: 0; border-top: 1px solid rgba(183,228,199,.14); padding-top: 22px; }
.brand-features li {
  display: flex; align-items: center; gap: 12px;
  padding: 7px 0; font-size: 14px; color: #b7e4c7; letter-spacing: .5px;
}
.brand-features i { font-style: normal; font-size: 17px; }

/* === 右侧表单区 === */
.form-side {
  flex: 1 1 54%;
  padding: 48px 44px;
  display: flex; flex-direction: column; justify-content: center;
}
.card-brand { margin-bottom: 22px; }
.brand-name { display: block; font-size: 24px; font-weight: 700; color: #e8f5e9; letter-spacing: 1px; }
.brand-sub { display: block; margin-top: 5px; font-size: 13px; color: #74c69d; letter-spacing: 1px; }

.tab-bar {
  display: flex; background: rgba(26,70,48,.6); border-radius: 12px; padding: 4px;
  margin-bottom: 24px;
}
.tab {
  flex: 1; text-align: center; padding: 11px 0;
  border-radius: 10px; font-size: 14px; font-weight: 500;
  cursor: pointer; color: #74c69d;
  transition: all .2s;
}
.tab.active {
  background: rgba(82,183,136,.25); color: #e8f5e9;
  box-shadow: 0 0 16px rgba(82,183,136,.3);
}

.input-wrap { position: relative; margin-bottom: 6px; }
.input-icon {
  position: absolute; left: 14px; top: 50%; transform: translateY(-50%);
  font-size: 16px; z-index: 10; pointer-events: none;
}
.input-wrap :deep(.el-input__wrapper) {
  padding-left: 40px; border-radius: 12px;
  background: rgba(26,70,48,.5) !important;
  box-shadow: 0 0 0 1px rgba(82,183,136,.3) inset !important;
}
.input-wrap :deep(.el-input__inner) { color: #e8f5e9 !important; }
.input-wrap :deep(.el-input__inner::placeholder) { color: #74c69d !important; }

.submit-btn {
  width: 100%; padding: 15px; margin-top: 14px;
  border: none; border-radius: 14px;
  background: linear-gradient(135deg, #1b5e3f, #52b788);
  color: #fff; font-size: 16px; font-weight: 600;
  cursor: pointer; transition: all .25s;
  box-shadow:
    0 10px 28px rgba(27,94,63,.6),
    0 0 20px rgba(82,183,136,.25);
  letter-spacing: 2px;
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow:
    0 14px 36px rgba(27,94,63,.75),
    0 0 28px rgba(82,183,136,.4);
}
.submit-btn:active:not(:disabled) { transform: translateY(0); }
.submit-btn.loading { opacity: .85; cursor: wait; }

.demo-tip {
  margin-top: 18px; padding: 12px 14px;
  background: rgba(26,70,48,.5); border-radius: 10px;
  font-size: 12px; color: #a7d7b5; text-align: center;
  border: 1px solid rgba(82,183,136,.2);
}
.demo-tip code {
  background: rgba(82,183,136,.2); padding: 2px 8px; border-radius: 4px;
  font-family: 'SF Mono', Consolas, monospace; color: #e8f5e9;
}

/* 响应式 */
@media (max-width: 820px) {
  .auth-card { flex-direction: column; width: min(420px, 100%); }
  .brand-side {
    flex: none; padding: 40px 32px 28px; text-align: center;
    align-items: center; border-right: none;
    border-bottom: 1px solid rgba(82,183,136,.16);
  }
  .brand-mark { margin-bottom: 18px; }
  .brand-title { font-size: 42px; }
  .brand-icons { justify-content: center; }
  .brand-features { display: none; }
  .form-side { padding: 34px 30px 40px; }
}
</style>
