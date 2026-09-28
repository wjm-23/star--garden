<template>
  <el-container class="layout-root">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="logo">🌱</span>
        <div>
          <div class="title">星芽花园</div>
          <div class="sub">STAR-GARDEN</div>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="side-menu"
        router
        background-color="transparent"
        text-color="#e9ecef"
        active-text-color="#74c69d"
      >
        <el-menu-item index="/tasks"><el-icon><List /></el-icon><span>任务大厅</span></el-menu-item>
        <el-menu-item index="/garden"><el-icon><Cpu /></el-icon><span>我的花园</span></el-menu-item>
        <el-menu-item index="/rooms"><el-icon><ChatDotRound /></el-icon><span>协作房间</span></el-menu-item>
        <el-menu-item index="/friends"><el-icon><UserFilled /></el-icon><span>好友花园</span></el-menu-item>
        <el-menu-item index="/report"><el-icon><DataAnalysis /></el-icon><span>数据报告</span></el-menu-item>
        <el-menu-item index="/annual"><el-icon><TrendCharts /></el-icon><span>年度报告</span></el-menu-item>
        <el-menu-item index="/history"><el-icon><Document /></el-icon><span>专注历史</span></el-menu-item>
        <el-menu-item index="/encyclopedia"><el-icon><Collection /></el-icon><span>植物图鉴</span></el-menu-item>
        <el-menu-item index="/achievements"><el-icon><Medal /></el-icon><span>成就</span></el-menu-item>
        <!-- 管理员专区：仅 ADMIN 角色可见 -->
        <template v-if="authStore.role === 'ADMIN'">
          <div class="menu-group-title">系统管理</div>
          <el-menu-item index="/admin/eval"><el-icon><Odometer /></el-icon><span>算法评估</span></el-menu-item>
          <el-menu-item index="/admin/tasks"><el-icon><Tickets /></el-icon><span>任务管理</span></el-menu-item>
          <el-menu-item index="/admin/users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        </template>
      </el-menu>
      <div class="footer-tag">© 202339170206 魏佳冕</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="stats">
          <el-tag type="success" effect="light" round>🌱 已种 {{ authStore.totalPlants }}</el-tag>
          <el-tag type="warning" effect="light" round>🔥 连续 {{ authStore.consecutiveDays }} 天</el-tag>
          <el-tag type="info" effect="light" round>🏡 花园 {{ authStore.gardenSize }}×{{ authStore.gardenSize }}</el-tag>
        </div>
        <el-dropdown @command="onCommand">
          <span class="user-info">
            <el-avatar :size="32" :style="{ background: '#40916c' }">{{ authStore.nickname.slice(0,1) }}</el-avatar>
            <span class="name">{{ authStore.nickname }}</span>
            <el-icon><CaretBottom /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  // / 或 /tasks 都高亮任务大厅
  const p = route.path
  if (p === '/' || p === '' || p.startsWith('/tasks')) return '/tasks'
  return p
})

onMounted(async () => {
  // 启动时拉一次最新资料，保证顶栏统计准确
  try { await authStore.refreshProfile() } catch (_) { /* 忽略 */ }
})

function onCommand(cmd) {
  if (cmd === 'profile') {
    ElMessageBox.alert(`<b>用户名：</b>${authStore.username}<br><b>昵称：</b>${authStore.nickname}<br><b>角色：</b>${authStore.role}`,
      '个人资料', { dangerouslyUseHTMLString: true })
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
      authStore.logout()
      router.replace('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout-root { height: 100vh; }
.aside {
  background: linear-gradient(180deg, #1b4332 0%, #2d6a4f 100%);
  display: flex; flex-direction: column; padding: 20px 12px 16px;
  overflow-x: hidden;
}
.brand {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 8px 20px; color: #fff;
}
.brand .logo { font-size: 28px; }
.brand .title { font-weight: 700; font-size: 16px; letter-spacing: 1px; }
.brand .sub { font-size: 10px; opacity: .7; letter-spacing: 2px; }
.side-menu { border-right: none; flex: 1; }
.side-menu :deep(.el-menu-item) { border-radius: 8px; margin-bottom: 4px; }
.menu-group-title {
  color: rgba(255,255,255,.45); font-size: 11px; letter-spacing: 1px;
  padding: 14px 12px 6px; border-top: 1px dashed rgba(255,255,255,.15); margin-top: 10px;
}
.footer-tag { color: rgba(255,255,255,.6); font-size: 11px; text-align: center; padding-top: 12px; }
.header {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;
  background: #fff; border-bottom: 1px solid #e9ecef; padding: 0 24px; min-width: 0;
}
.stats { display: flex; gap: 10px; flex-wrap: wrap; }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.user-info .name { font-weight: 600; color: #212529; }
.main { background: #f5f6f8; padding: 24px; overflow-y: auto; min-width: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .18s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== 响应式：窄窗口下侧边栏变成顶部水平菜单 ===== */
@media (max-width: 768px) {
  .layout-root { height: auto; min-height: 100vh; }
  .aside {
    /* Element Plus el-aside width 是内联 style，必须 !important 覆盖 */
    width: 100% !important;
    height: auto;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 12px;
    padding: 14px 12px 10px;
  }
  .brand { padding: 4px 4px 8px; margin-right: auto; }
  .footer-tag { display: none; }
  .side-menu {
    flex: 1 1 100%;
    display: flex; flex-wrap: wrap; gap: 6px;
  }
  .side-menu :deep(.el-menu-item) { margin-bottom: 0; flex: 0 0 auto; }
  .header { padding: 10px 14px; }
  .main { padding: 14px 12px; }
}
</style>
