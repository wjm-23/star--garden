<template>
  <div class="tasks-page">
    <!-- 顶部：推荐位 -->
    <el-card class="rec-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">🧠 为你推荐</span>
          <el-tag type="success" effect="light" round>混合推荐 UserCF + 标签 + 时段</el-tag>
        </div>
      </template>
      <el-row :gutter="14">
        <el-col v-for="(r, idx) in recommendations" :key="idx" :xs="24" :sm="12" :md="6">
          <div class="rec-item" @click="startFocus(r.taskName, r.minutes)">
            <div class="rec-icon">{{ r.icon || '✨' }}</div>
            <div class="rec-info">
              <div class="rec-name">{{ r.taskName }}</div>
              <div class="rec-meta">
                <span class="pill">{{ r.category || '通用' }}</span>
                <span class="pill pill-min">{{ r.minutes }} 分钟</span>
              </div>
              <div class="rec-reason">💡 {{ r.reason }}</div>
            </div>
          </div>
        </el-col>
        <el-col v-if="recLoading" :span="24"><el-skeleton :rows="2" animated /></el-col>
      </el-row>
    </el-card>

    <!-- 专注计时器 -->
    <el-card v-if="focusing" class="timer-card" shadow="always">
      <div class="timer-wrap">
        <div class="timer-info">
          <div class="timer-label">当前专注任务</div>
          <div class="timer-task">{{ focusTaskName }}</div>
          <div class="timer-sub">完成后将获得：{{ focusPlantName }}</div>
        </div>
        <div class="timer-countdown">
          <div class="circle-progress">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="45" fill="none" stroke="#e9ecef" stroke-width="6" />
              <circle cx="50" cy="50" r="45" fill="none" stroke="#40916c" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="dashOffset"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="timer-text">{{ formatTime(remainingSec) }}</div>
          </div>
        </div>
        <div class="timer-actions">
          <el-button size="large" @click="togglePause">
            {{ paused ? '▶ 继续' : '⏸ 暂停' }}
          </el-button>
          <el-button size="large" type="primary" @click="completeFocus" :disabled="remainingSec > focusTotalSec - 5">
            提前完成
          </el-button>
          <el-button size="large" type="danger" plain @click="cancelFocus">放弃本次</el-button>
        </div>
      </div>
    </el-card>

    <!-- 任务大厅网格（按类别分组） -->
    <el-card class="hall-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">🎯 任务大厅 · 共 {{ tasks.length }} 种专注任务</span>
          <div class="cat-tabs">
            <span
              v-for="(cat, idx) in categories"
              :key="idx"
              class="cat-tab"
              :class="{ active: activeCategory === cat }"
              @click="activeCategory = cat"
            >{{ catLabels[cat] || cat }}</span>
          </div>
        </div>
      </template>
      <el-empty v-if="filteredTasks.length === 0 && !loading" description="该类别暂无任务" />
      <div v-else class="task-grid">
        <div
          v-for="t in filteredTasks"
          :key="t.id"
          class="task-card"
          @click="startFocus(t.taskName, t.defaultMinutes)"
        >
          <div class="task-emoji">{{ t.icon || '✨' }}</div>
          <div class="task-body">
            <div class="task-name">{{ t.taskName }}</div>
            <div class="task-desc">{{ t.description || '完成后获得对应植物' }}</div>
            <div class="task-footer">
              <span class="tag-min">{{ t.defaultMinutes || 0 }} 分钟</span>
              <span class="tag-plant">🌱 {{ t.plantName || '神秘植物' }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()

const recommendations = ref([])
const tasks = ref([])
const recLoading = ref(true)
const loading = ref(true)
const activeCategory = ref('全部')

const catLabels = {
  '学习': '📚 学习', '阅读': '📖 阅读', '运动': '🏃 运动',
  '早起': '🌅 早起', '冥想': '🧘 冥想', '工作': '💼 工作',
  '生活': '🏠 生活', '全部': '全部',
}

// 分类由后端任务自身配置（focus_task.category）给出，前端不再按任务名猜测
const CATEGORY_ORDER = ['学习', '阅读', '运动', '早起', '冥想', '工作', '生活']
const categoryOf = (t) => t.category || '生活'

const categories = computed(() => {
  const present = new Set(tasks.value.map(categoryOf))
  return ['全部', ...CATEGORY_ORDER.filter(c => present.has(c))]
})

const filteredTasks = computed(() => {
  if (activeCategory.value === '全部') return tasks.value
  return tasks.value.filter(t => categoryOf(t) === activeCategory.value)
})

// ====== 专注计时器 ======
const focusing = ref(false)
const paused = ref(false)
const focusTaskName = ref('')
const focusPlantName = ref('')
const focusTotalSec = ref(0)
const remainingSec = ref(0)
let tickTimer = null

const circumference = 2 * Math.PI * 45
const dashOffset = computed(() => {
  const ratio = remainingSec.value / Math.max(1, focusTotalSec.value)
  return circumference * (1 - ratio)
})

onMounted(async () => {
  try {
    const data = await api.get('/api/recommendations', { params: { n: 4 } })
    recommendations.value = data.items || []
  } catch (_) { } finally { recLoading.value = false }

  try {
    const data = await api.get('/api/tasks')
    tasks.value = data.tasks || []
  } catch (_) { } finally { loading.value = false }
})

onBeforeUnmount(() => stopTick())

function formatTime(sec) {
  sec = Math.max(0, Math.round(sec))
  const m = Math.floor(sec / 60), s = sec % 60
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

async function startFocus(taskName, minutes) {
  if (focusing.value) {
    ElMessage.warning('当前已有专注在进行中，请先完成或放弃')
    return
  }
  try {
    const data = await api.post('/api/focus/start', { taskName, minutes })
    focusing.value = true
    paused.value = false
    focusTaskName.value = data.taskName
    const rec = recommendations.value.find(r => r.taskName === taskName)
    const hall = tasks.value.find(t => t.taskName === taskName)
    focusPlantName.value = rec?.plantName || hall?.plantName || '神秘植物'
    focusTotalSec.value = (data.minutes || minutes) * 60
    remainingSec.value = focusTotalSec.value
    startTick()
    ElMessage.success('🧘 专注开始！放下其他事，专注当下')
  } catch (_) { }
}

function startTick() {
  stopTick()
  tickTimer = setInterval(() => {
    if (!paused.value && remainingSec.value > 0) {
      remainingSec.value -= 1
      if (remainingSec.value <= 0) completeFocus()
    }
  }, 1000)
}
function stopTick() { if (tickTimer) { clearInterval(tickTimer); tickTimer = null } }

function togglePause() { paused.value = !paused.value }

async function completeFocus() {
  stopTick()
  const usedMinutes = Math.max(1, Math.round((focusTotalSec.value - remainingSec.value) / 60))
  try {
    const data = await api.post('/api/focus/complete', {
      taskName: focusTaskName.value,
      durationMinutes: usedMinutes,
    })
    ElMessageBox.alert(
      `🎉 专注完成！<br><b>任务：</b>${focusTaskName.value}<br><b>时长：</b>${usedMinutes} 分钟<br><b>获得植物：</b>🌱 ${data.plant?.name || focusPlantName.value}`,
      '专注完成',
      { dangerouslyUseHTMLString: true, confirmButtonText: '去花园种它！', showCancelButton: true, cancelButtonText: '继续专注' }
    ).then(() => router.push('/garden')).catch(() => {})
    const r = await api.get('/api/recommendations', { params: { n: 4 } })
    recommendations.value = r.items || []
  } catch (_) { } finally { focusing.value = false; paused.value = false }
}

function cancelFocus() {
  stopTick()
  focusing.value = false
  paused.value = false
  ElMessage.info('本次专注已放弃')
}
</script>

<style scoped>
.tasks-page { display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.card-title { font-size: 16px; font-weight: 600; color: #1b4332; }

.rec-card { border-radius: 14px; border-top: 3px solid #52b788; }
.rec-item {
  display: flex; gap: 12px; padding: 14px; cursor: pointer;
  border: 1px solid #e9ecef; border-radius: 12px; height: 100%;
  transition: transform .18s, box-shadow .18s, border-color .18s;
  background: linear-gradient(135deg, #f8f9fa, #e9f5ec);
}
.rec-item:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(64,145,108,.22); border-color: #74c69d; }
.rec-icon { font-size: 34px; }
.rec-info { flex: 1; min-width: 0; }
.rec-name { font-weight: 700; font-size: 15px; color: #1b4332; }
.rec-meta { display: flex; gap: 6px; margin: 4px 0 6px; flex-wrap: wrap; }
.pill { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: #e0e7ff; color: #4338ca; }
.pill-min { background: #dcfce7; color: #166534; }
.rec-reason { font-size: 12px; color: #2d6a4f; line-height: 1.4; }

.timer-card { border-radius: 14px; border: 2px solid #40916c !important; background: linear-gradient(135deg, #d8f3dc, #b7e4c7); }
.timer-wrap { display: flex; align-items: center; gap: 36px; }
.timer-info .timer-label { font-size: 13px; color: #4b5563; }
.timer-info .timer-task { font-size: 22px; font-weight: 700; color: #1b4332; margin: 4px 0; }
.timer-info .timer-sub { font-size: 12px; color: #4b5563; }
.timer-countdown { width: 160px; height: 160px; }
.circle-progress { position: relative; width: 160px; height: 160px; }
.circle-progress svg { width: 100%; height: 100%; }
.timer-text {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 36px; font-weight: 700; color: #1b4332; font-variant-numeric: tabular-nums;
}
.timer-actions { display: flex; flex-direction: column; gap: 8px; }

.hall-card { border-radius: 14px; border-top: 3px solid #2d6a4f; }
.cat-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.cat-tab {
  padding: 5px 12px; border-radius: 16px; font-size: 12px;
  background: #f1f3f5; color: #495057; cursor: pointer;
  transition: all .15s;
}
.cat-tab:hover { background: #e9f5ec; color: #2d6a4f; }
.cat-tab.active { background: #2d6a4f; color: #fff; }

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px; padding-top: 8px;
}
.task-card {
  border: 1.5px solid #e5e7eb; border-radius: 12px; padding: 14px;
  display: flex; gap: 12px; cursor: pointer;
  transition: transform .18s, box-shadow .18s, border-color .18s;
  background: #fff;
}
.task-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(27,67,50,.12);
  border-color: #74c69d;
}
.task-emoji {
  font-size: 36px; flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,.12));
}
.task-body { flex: 1; min-width: 0; }
.task-name { font-weight: 700; font-size: 15px; color: #1b4332; }
.task-desc { font-size: 12px; color: #6b7280; margin: 4px 0 8px; line-height: 1.4; }
.task-footer { display: flex; gap: 6px; flex-wrap: wrap; }
.tag-min {
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  background: #dcfce7; color: #166534;
}
.tag-plant {
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  background: #fef3c7; color: #92400e;
}

@media (max-width: 768px) {
  .timer-wrap { flex-direction: column; gap: 16px; text-align: center; }
  .timer-actions { flex-direction: row; justify-content: center; flex-wrap: wrap; }
  .task-grid { grid-template-columns: 1fr; }
}
</style>
