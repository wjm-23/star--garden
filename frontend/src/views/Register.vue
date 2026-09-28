<template>
  <div class="login-wrap">
    <!-- 星空流星背景 -->
    <div class="bg-layer">
      <canvas ref="starCanvas" class="star-canvas"></canvas>
    </div>

    <!-- 左侧介绍 -->
    <div class="hero-panel">
      <div class="hero-content">
        <div class="hero-logo">🌱</div>
        <h1 class="hero-title">星芽花园</h1>
        <p class="hero-tag">加入我们，让专注开出花朵</p>
        <ul class="hero-features">
          <li><span class="dot"></span>创建你的专属花园</li>
          <li><span class="dot"></span>每一次专注都会长出植物</li>
          <li><span class="dot"></span>和好友一起协作专注</li>
        </ul>
      </div>
    </div>

    <!-- 右侧注册卡片 -->
    <div class="login-card">
      <div class="card-inner">
        <div class="card-brand">
          <span class="mini-logo">✨</span>
          <span class="brand-name">创建我的花园</span>
        </div>

        <el-form :model="form" :rules="rules" ref="ref" size="large">
          <div class="input-wrap">
            <span class="input-icon">👤</span>
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="取一个用户名" />
            </el-form-item>
          </div>
          <div class="input-wrap">
            <span class="input-icon">🔒</span>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="密码 ≥ 6 位" />
            </el-form-item>
          </div>
          <div class="input-wrap">
            <span class="input-icon">📧</span>
            <el-form-item prop="email">
              <el-input v-model="form.email" placeholder="邮箱" />
            </el-form-item>
          </div>
          <div class="input-wrap">
            <span class="input-icon">🌿</span>
            <el-form-item prop="nickname">
              <el-input v-model="form.nickname" placeholder="昵称（选填）" />
            </el-form-item>
          </div>
          <button class="submit-btn" :disabled="loading" @click="submit">
            <span v-if="!loading">🌱 种下第一颗种子</span>
            <span v-else>🌿 正在生长...</span>
          </button>
        </el-form>
        <div class="bottom-link">已有账号？<router-link to="/login">去登录 →</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formRef = ref(null)
const form = reactive({ username: '', password: '', email: '', nickname: '' })
const rules = {
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
  const count = Math.floor((W * H) / 6000); stars = []
  for (let i = 0; i < count; i++) stars.push({
    x: Math.random() * W, y: Math.random() * H, r: Math.random() * 1.4 + 0.3,
    baseAlpha: Math.random() * 0.5 + 0.3, tw: Math.random() * 0.02 + 0.005, phase: Math.random() * Math.PI * 2
  })
}
function spawnMeteor() {
  const startX = Math.random() * W * 0.7 + W * 0.2, startY = -50
  const angle = Math.PI / 4 + (Math.random() * 0.2 - 0.1)
  const len = Math.random() * 180 + 120, speed = Math.random() * 6 + 6
  meteors.push({ x: startX, y: startY, len, speed, angle, life: 1 })
}
function loop() {
  if (!ctx) return
  ctx.clearRect(0, 0, W, H)
  for (const s of stars) {
    s.phase += s.tw
    const a = s.baseAlpha + Math.sin(s.phase) * 0.3
    ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(255,255,255,${Math.max(0.1, a)})`; ctx.fill()
  }
  if (Math.random() < 0.012 && meteors.length < 3) spawnMeteor()
  for (let i = meteors.length - 1; i >= 0; i--) {
    const m = meteors[i]
    m.x += Math.cos(m.angle) * m.speed; m.y += Math.sin(m.angle) * m.speed; m.life -= 0.006
    const tx = m.x - Math.cos(m.angle) * m.len, ty = m.y - Math.sin(m.angle) * m.len
    const g = ctx.createLinearGradient(m.x, m.y, tx, ty)
    g.addColorStop(0, `rgba(255,255,255,${Math.max(0, m.life)})`); g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.strokeStyle = g; ctx.lineWidth = 2
    ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(tx, ty); ctx.stroke()
    if (m.life <= 0 || m.x < -100 || m.y > H + 100) meteors.splice(i, 1)
  }
  rafId = requestAnimationFrame(loop)
}
function initStarfield() {
  const c = starCanvas.value; if (!c) return
  ctx = c.getContext('2d'); resize(); window.addEventListener('resize', resize); loop()
}

onMounted(() => { initStarfield() })
onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
})

async function submit() {
  try {
    await formRef.value.validate()
    loading.value = true
    await auth.register(form)
    ElMessage.success('🌱 种子已种下，专注让它开花！')
    router.replace('/tasks')
  } finally { loading.value = false }
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
  padding: 20px; gap: 56px;
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

.hero-panel { width: 420px; position: relative; z-index: 2; color: #e8f5e9; }
.hero-content { padding: 20px 0; }
.hero-logo {
  font-size: 84px; line-height: 1; margin-bottom: 12px;
  animation: bob 3s ease-in-out infinite;
  filter: drop-shadow(0 12px 32px rgba(74,222,128,.5));
}
@keyframes bob { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }
.hero-title {
  font-size: 52px; margin: 0; color: #e8f5e9; letter-spacing: 3px;
  text-shadow: 0 4px 16px rgba(0,0,0,.5);
}
.hero-tag { font-size: 16px; color: #a7d7b5; margin: 10px 0 28px; letter-spacing: 1px; }
.hero-features { list-style: none; padding: 0; margin: 0; }
.hero-features li {
  padding: 10px 0; font-size: 15px; color: #c8e6c9;
  display: flex; align-items: center; gap: 14px;
}
.hero-features .dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: #52b788; box-shadow: 0 0 16px #52b788, 0 0 32px rgba(82,183,136,.4);
}

.login-card { width: 400px; flex-shrink: 0; position: relative; z-index: 3; }
.card-inner {
  background: rgba(10, 36, 25, .85);
  backdrop-filter: blur(24px);
  border-radius: 24px;
  padding: 40px 32px 28px;
  border: 1px solid rgba(82,183,136,.3);
  box-shadow:
    0 40px 100px rgba(0,0,0,.6),
    0 0 0 1px rgba(74,222,128,.1),
    inset 0 1px 0 rgba(255,255,255,.05);
}
.card-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 24px; }
.mini-logo { font-size: 24px; }
.brand-name { font-size: 20px; font-weight: 600; color: #e8f5e9; letter-spacing: 1px; }

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
  box-shadow: 0 10px 28px rgba(27,94,63,.6), 0 0 20px rgba(82,183,136,.25);
  letter-spacing: 2px;
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 14px 36px rgba(27,94,63,.75), 0 0 28px rgba(82,183,136,.4);
}
.submit-btn:disabled { opacity: .85; cursor: wait; }

.bottom-link {
  margin-top: 16px; text-align: center;
  font-size: 12px; color: #a7d7b5;
}
.bottom-link a { color: #74c69d; text-decoration: none; font-weight: 600; }
.bottom-link a:hover { color: #52b788; text-decoration: underline; }

@media (max-width: 900px) {
  .login-wrap { flex-direction: column; gap: 24px; padding: 40px 20px; }
  .hero-panel { width: 100%; text-align: center; }
  .hero-features { display: none; }
  .login-card { width: 100%; max-width: 400px; }
}
</style>
