<template>
  <div class="garden-page">
    <!-- 标题区 -->
    <div class="page-head">
      <h2 class="page-title">🌿 我的花园</h2>
      <div class="page-meta">
        <span class="chip">📐 {{ gardenSize }}×{{ gardenSize }}</span>
        <span class="chip">🌱 {{ occupiedCount }} 株</span>
        <span class="chip">🎒 {{ pendingCount }} 颗待种</span>
      </div>
    </div>

    <div class="garden-body">
      <!-- 花园网格 -->
      <div class="garden-area">
        <div class="sky-deco"><span>☁️</span><span>☀️</span><span>☁️</span></div>
        <div class="garden-ground">
          <div class="garden-grid" :style="gridStyle">
            <div
              v-for="i in gardenSize"
              :key="'row-' + i"
              class="grid-row"
            >
              <div
                v-for="j in gardenSize"
                :key="i + ',' + j"
                class="grid-cell"
                :class="cellClass(i-1, j-1)"
                @click="onCellClick(i-1, j-1)"
              >
                <!-- 空格子 -->
                <template v-if="!cellAt(i-1, j-1)">
                  <div class="empty-soil">
                    <span class="soil-dot"></span>
                  </div>
                  <div v-if="selectedPlantId" class="plant-hint">点击种植</div>
                </template>

                <!-- 已种植 -->
                <template v-else>
                  <div class="plant-wrap" :class="'stage' + (cellAt(i-1, j-1).stage || 0)">
                    <span class="plant-emoji" :title="cellTitle(i-1, j-1)">
                      {{ cellEmoji(i-1, j-1) }}
                    </span>
                    <span class="sparkle" v-if="cellAt(i-1, j-1).stage >= 1">✨</span>
                  </div>
                  <span class="stage-badge" :class="'stage-' + (cellAt(i-1, j-1).stage || 0)">
                    {{ cellAt(i-1, j-1).stageName || '发芽' }}
                  </span>
                </template>

                <!-- 种植特效 -->
                <div v-if="plantingAt && plantingAt.x===i-1 && plantingAt.y===j-1" class="plant-effect">
                  <span>✨</span><span>💫</span><span>⭐</span>
                </div>
              </div>
            </div>
          </div>
          <div class="soil-bar"></div>
        </div>

        <!-- 图例 -->
        <div class="legend">
          <span class="lg lg-s0"><span class="d">🌱</span>发芽</span>
          <span class="lg lg-s1"><span class="d">🌿</span>成长</span>
          <span class="lg lg-s2"><span class="d">🌸</span>开花</span>
          <span class="tip">💡 空格子：选中种子后点种植；已种格子：选中「同款」种子后点「种植」即可发芽→成长→开花；也可铲除腾出空间</span>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="side-panel">
        <!-- 种子背包 -->
        <div class="panel-card seed-panel">
          <div class="panel-head">
            <span>🎒 待种种子</span>
            <el-tag v-if="pending.length" round size="small" type="success">{{ pendingCount }}</el-tag>
          </div>
          <div v-if="pending.length === 0" class="empty-hint">
            <div class="empty-icon">🌱</div>
            <p>背包空了</p>
            <el-button type="success" @click="$router.push('/tasks')">去完成任务 →</el-button>
          </div>
          <div v-else class="seed-bag">
              <div
                v-for="p in pending"
                :key="p.plantId"
                class="seed-chip"
                :class="{ selected: selectedPlantId === p.plantId, 'match-hint': cellPopup && cellPopup.plant.plantId === p.plantId }"
                @click="selectedPlantId = (selectedPlantId === p.plantId ? null : p.plantId)"
              >
              <span class="seed-emoji">{{ getPlantEmojiByName(p.plantName) }}</span>
              <div class="seed-info">
                <div class="seed-name">{{ p.plantName }}</div>
                <div class="seed-sub">×{{ p.count }}</div>
              </div>
              <span v-if="selectedPlantId === p.plantId" class="check-mark">✓</span>
            </div>
          </div>
        </div>

        <!-- 扩建/统计 -->
        <div class="panel-card stat-panel">
          <div class="panel-head"><span>📊 花园数据</span></div>
          <div class="stat-grid">
            <div class="stat-item">
              <div class="num">{{ gardenSize * gardenSize }}</div>
              <div class="lbl">总格子</div>
            </div>
            <div class="stat-item">
              <div class="num">{{ occupiedCount }}</div>
              <div class="lbl">已种植</div>
            </div>
            <div class="stat-item">
              <div class="num">{{ pendingCount }}</div>
              <div class="lbl">待种植</div>
            </div>
            <div class="stat-item">
              <div class="num">{{ flowerCount }}</div>
              <div class="lbl">🌸 开花</div>
            </div>
          </div>
          <el-button
            v-if="gardenSize < 10"
            type="warning"
            class="expand-btn"
            :disabled="!full"
            @click="expandGarden"
          >
            {{ full ? '🚧 扩建花园' : '把花都种满才能扩建哦' }}
          </el-button>
          <div v-else class="max-tip">🎉 已达最大花园 10×10</div>
        </div>
      </div>
    </div>

    <!-- 已种植格子的操作弹窗：选同款种子后种植成长 / 铲除 -->
    <div v-if="cellPopup" class="cell-modal-mask" @click.self="closePopup">
      <div class="cell-modal">
        <button class="modal-close" @click="closePopup">✕</button>
        <div class="modal-emoji">{{ cellPopup.plant.icon || getPlantEmojiByName(cellPopup.plant.name) }}</div>
        <div class="modal-name">{{ cellPopup.plant.name }}</div>
        <span class="stage-badge" :class="'stage-' + (cellPopup.plant.stage || 0)">
          {{ cellPopup.plant.stageName || '发芽' }}
        </span>

        <div class="modal-progress">
          <div class="prog-label">
            <span>成长进度</span>
            <span class="prog-stage">{{ ['发芽', '成长', '开花'][cellPopup.plant.stage || 0] }}（第{{ (cellPopup.plant.stage || 0) + 1 }}/3 阶段）</span>
          </div>
          <div class="prog-bar">
            <div class="prog-fill" :style="progressStyle(cellPopup.plant)"></div>
          </div>
          <div class="stage-track">
            <span :class="{ on: (cellPopup.plant.stage || 0) >= 0 }">🌱 发芽</span>
            <span :class="{ on: (cellPopup.plant.stage || 0) >= 1 }">🌿 成长</span>
            <span :class="{ on: (cellPopup.plant.stage || 0) >= 2 }">🌸 开花</span>
          </div>
          <div class="prog-hint" v-if="(cellPopup.plant.stage || 0) < 2">
            再在同格种下相同植物：{{ cellPopup.plant.stage >= 1 ? '1 次即可开花' : '1 次→成长，2 次→开花' }}
          </div>
          <div class="prog-hint" v-else>🌸 已盛开，无需再种</div>
        </div>

        <div class="modal-actions">
          <el-button
            type="success"
            :disabled="(cellPopup.plant.stage || 0) >= 2 || !sameSpeciesSelected"
            @click="growCell"
          >
            🌱 种植（成长）
          </el-button>
          <el-button type="danger" plain @click="removeCell">🪓 铲除</el-button>
        </div>
        <div class="modal-tip">
          <template v-if="(cellPopup.plant.stage || 0) >= 2">🌸 这株已经盛开了</template>
          <template v-else-if="!selectedPlantId">请先在右侧背包选中与格内相同的「{{ cellPopup.plant.name }}」种子，再点「种植」</template>
          <template v-else-if="!sameSpeciesSelected">当前选中的「{{ selectedSeedName || '种子' }}」与格内植物不同，请选同款种子</template>
          <template v-else>已选中同款种子「{{ selectedSeedName }}」，点「种植」让它{{ (cellPopup.plant.stage || 0) >= 1 ? '开花' : '成长' }}（第{{ (cellPopup.plant.stage || 0) + 2 }}次种下）</template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const gardenMap = ref({})
const gardenSize = ref(6)
const pending = ref([])
const selectedPlantId = ref(null)
const plantingAt = ref(null)
const cellPopup = ref(null)

const full = computed(() => occupiedCount.value >= gardenSize.value * gardenSize.value)
const occupiedCount = computed(() => Object.keys(gardenMap.value).length)
const pendingCount = computed(() => pending.value.reduce((s, p) => s + p.count, 0))
const flowerCount = computed(() => Object.values(gardenMap.value).filter(p => (p.stage || 0) >= 2).length)
// 弹窗打开且已选中与格内同一种的种子时，才允许「种植（成长）」
const sameSpeciesSelected = computed(() =>
  !!cellPopup.value && !!selectedPlantId.value && selectedPlantId.value === cellPopup.value.plant.plantId
)
const selectedSeedName = computed(() => {
  const c = pending.value.find(p => p.plantId === selectedPlantId.value)
  return c ? c.plantName : ''
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${gardenSize.value}, 58px)`,
  gridTemplateRows: `repeat(${gardenSize.value}, 58px)`,
}))

async function load() {
  const [g, p] = await Promise.all([
    api.get('/api/garden/map').catch(() => ({})),
    api.get('/api/garden/pending').catch(() => ({})),
  ])
  gardenMap.value = g.garden || {}
  gardenSize.value = g.gardenSize || 6
  pending.value = p.pending || []
}

onMounted(load)

function cellAt(x, y) {
  return gardenMap.value[x + ',' + y] || null
}

function cellClass(x, y) {
  const plant = cellAt(x, y)
  if (!plant) return { empty: true, 'can-plant': !!selectedPlantId }
  return { occupied: true, 'stage-0': plant.stage === 0, 'stage-1': plant.stage === 1, 'stage-2': plant.stage >= 2 }
}

function cellEmoji(x, y) {
  const plant = cellAt(x, y)
  if (!plant) return ''
  // 各阶段都展示植物本身的形象，这样发芽时也能看出种的是什么
  return plant.icon || getPlantEmojiByName(plant.name)
}

function cellTitle(x, y) {
  const p = cellAt(x, y)
  if (!p) return ''
  const xp = p.xpMinutes || 0
  const next = p.nextStageMinutes
  const prog = (next && next > 0) ? ` · 成长经验 ${xp}/${next} 分` : ' · 已盛开'
  return `${p.name} · ${p.stageName || '发芽'}${prog}（选中同款种子后可种植成长或铲除）`
}

function getPlantEmojiByName(name) {
  const map = {
    '智慧藤': '📚🌿', '专注花': '🎯🌸', '活力草': '💪🍃',
    '晨光花': '🌅🌻', '宁静叶': '🧘🍀', '星芽草': '✨🌱',
    '灵感菇': '🍄', '韵律兰': '🌷', '逻辑松': '🌲',
    '思绪苇': '🪶', '记忆蕨': '🌾', '安眠星草': '✨',
    '自律棘': '🌵', '联结藤': '💮', '远见葵': '🌞',
    '静心茗': '🍵', '烟火椒': '🌶️', '秩序苔': '🪴',
  }
  return map[name] || '🌻'
}

async function onCellClick(x, y) {
  const plant = cellAt(x, y)
  if (plant) {
    // 已种植：打开操作弹窗（成长 / 铲除），不再直接铲除
    cellPopup.value = { x, y, plant: { ...plant } }
    return
  }
  if (!selectedPlantId.value) {
    ElMessage.warning('🎒 先从右侧背包选一颗种子')
    return
  }
  try {
    plantingAt.value = { x, y }
    const r = await api.post('/api/garden/plant', { plantId: selectedPlantId.value, x, y })
    ElMessage.success(`🌱 种植成功！${r.stageName || ''}`)
    setTimeout(() => plantingAt.value = null, 800)
    load()
    const chip = pending.value.find(p => p.plantId === selectedPlantId.value)
    if (chip && chip.count <= 1) selectedPlantId.value = null
  } catch (_) { plantingAt.value = null }
}

async function expandGarden() {
  try {
    const r = await api.post('/api/garden/expand')
    ElMessage.success(r.msg)
    load()
  } catch (_) { }
}

function closePopup() {
  cellPopup.value = null
}

function progressStyle(plant) {
  // 进度按「阶段」展示（发芽1/3 → 成长2/3 → 开花3/3），保证只前进不倒退，
  // 不再用「分钟/阈值」算百分比（阶段切换时阈值从30跳120会导致进度回退的视觉问题）
  const stage = plant.stage || 0
  const pct = stage === 0 ? 33 : (stage === 1 ? 66 : 100)
  return { width: pct + '%' }
}

function remainMinutes(plant) {
  const xp = plant.xpMinutes || 0
  const next = plant.nextStageMinutes || 0
  return Math.max(0, next - xp)
}

async function growCell() {
  const { x, y, plant } = cellPopup.value
  const plantId = selectedPlantId.value
  if (!plantId || plantId !== plant.plantId) {
    ElMessage.warning(`请先选中与格内相同的「${plant.name}」种子`)
    return
  }
  try {
    const r = await api.post('/api/garden/grow', { x, y, plantId })
    if (r.success) {
      ElMessage.success(`🌱 种植成功！当前：${r.stageName}`)
      await load()
      const updated = cellAt(x, y)
      if (updated) cellPopup.value = { x, y, plant: { ...updated } }
      else closePopup()
      // 该种子用完了就清空选择
      const chip = pending.value.find(p => p.plantId === plantId)
      if (!chip) selectedPlantId.value = null
    } else {
      ElMessage.warning(r.msg)
    }
  } catch (_) { }
}

async function removeCell() {
  const { x, y, plant } = cellPopup.value
  try {
    await ElMessageBox.confirm(`确定铲除「${plant.name}」？`, '提示', { type: 'warning' })
    const r = await api.post('/api/garden/remove', { x, y })
    if (r.success) {
      ElMessage.success('🪓 已铲除，腾出空间种新植物')
      closePopup()
      load()
    } else {
      ElMessage.warning(r.msg)
    }
  } catch (_) { }
}
</script>

<style scoped>
.garden-page { padding: 8px; }

.page-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0; font-size: 22px; color: #1b4332; font-weight: 700;
}
.page-meta { display: flex; gap: 8px; }
.chip {
  padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500;
  background: linear-gradient(135deg, #d8f3dc, #b7e4c7); color: #1b4332;
  box-shadow: 0 2px 6px rgba(27,67,50,.08);
}

.garden-body { display: flex; gap: 20px; }
.garden-area { flex: 1; min-width: 0; }
.side-panel { width: 300px; display: flex; flex-direction: column; gap: 16px; }

/* === 花园主区 === */
.garden-ground {
  background: linear-gradient(180deg, #87CEEB 0%, #98D8AA 55%, #5D8736 100%);
  border-radius: 16px; padding: 28px 28px 0;
  box-shadow: 0 8px 24px rgba(27,67,50,.15), inset 0 -4px 0 rgba(27,67,50,.1);
  position: relative; overflow: hidden;
}
.sky-deco {
  position: absolute; top: 8px; left: 0; right: 0;
  display: flex; justify-content: space-around; font-size: 28px;
  opacity: .7; pointer-events: none;
  animation: skyDrift 20s linear infinite;
}
@keyframes skyDrift {
  0% { transform: translateX(0); } 50% { transform: translateX(10px); } 100% { transform: translateX(0); }
}

.garden-grid {
  display: grid; gap: 6px; justify-content: center;
  padding: 12px;
  background: linear-gradient(180deg, rgba(93,135,54,.35), rgba(61,94,34,.45));
  border-radius: 14px;
  box-shadow: inset 0 2px 8px rgba(0,0,0,.1);
}
.grid-row { display: contents; }

.grid-cell {
  width: 58px; height: 58px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  position: relative; cursor: default;
  transition: transform .2s ease, box-shadow .2s ease;
}
.grid-cell.empty {
  background: linear-gradient(145deg, rgba(139,90,43,.55), rgba(101,67,33,.65));
  border: 2px solid rgba(61,94,34,.3);
  cursor: default;
}
.grid-cell.empty.can-plant {
  cursor: pointer;
  animation: pulseHint 1.6s ease-in-out infinite;
}
@keyframes pulseHint {
  0%,100% { box-shadow: 0 0 0 0 rgba(116,198,157,.0); }
  50% { box-shadow: 0 0 0 6px rgba(116,198,157,.3); }
}
.grid-cell.empty.can-plant:hover {
  transform: scale(1.06);
  background: linear-gradient(145deg, rgba(139,90,43,.7), rgba(101,67,33,.8));
  border-color: #74c69d;
}
.empty-soil .soil-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(61,94,34,.5);
}
.plant-hint {
  position: absolute; bottom: -18px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: #52b788; white-space: nowrap;
  opacity: 0; transition: opacity .2s;
}
.grid-cell.empty.can-plant:hover .plant-hint { opacity: 1; }

.grid-cell.occupied {
  background: linear-gradient(145deg, #6b8e3a, #4a6a2a);
  border: 2px solid rgba(93,135,54,.8);
  cursor: pointer;
}
.grid-cell.occupied:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 18px rgba(0,0,0,.25);
}
.grid-cell.stage-0 {
  background: linear-gradient(145deg, #8B9F5E, #6E8743);
  border-color: #9BBF6A;
}
.grid-cell.stage-1 {
  background: linear-gradient(145deg, #6FA843, #4A7A2A);
  border-color: #74c69d;
  box-shadow: inset 0 0 8px rgba(116,198,157,.25);
}
.grid-cell.stage-2 {
  background: linear-gradient(145deg, #E8B4C9, #D67EA3);
  border-color: #f9a8d4;
  box-shadow: inset 0 0 12px rgba(244,114,182,.3);
  animation: bloomGlow 2.5s ease-in-out infinite;
}
@keyframes bloomGlow {
  0%,100% { box-shadow: inset 0 0 12px rgba(244,114,182,.3); }
  50% { box-shadow: inset 0 0 20px rgba(244,114,182,.55), 0 0 16px rgba(244,114,182,.3); }
}

.plant-wrap { position: relative; animation: plantPop .4s ease-out; }
@keyframes plantPop {
  0% { transform: scale(0); opacity: 0; }
  60% { transform: scale(1.25); }
  100% { transform: scale(1); opacity: 1; }
}
.plant-emoji {
  font-size: 26px; display: block;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,.35));
  animation: sway 3s ease-in-out infinite;
}
/* 生长阶段差异：发芽小巧 → 成长舒展 → 开花最盛 */
.grid-cell.stage-0 .plant-emoji { font-size: 19px; opacity: .92; }
.grid-cell.stage-1 .plant-emoji { font-size: 25px; }
.grid-cell.stage-2 .plant-emoji { font-size: 30px; }
@keyframes sway { 0%,100% { transform: rotate(-3deg); } 50% { transform: rotate(3deg); } }
.stage-2 .plant-emoji { animation: swayBigger 2s ease-in-out infinite; }
@keyframes swayBigger { 0%,100% { transform: rotate(-5deg) scale(1.1); } 50% { transform: rotate(5deg) scale(1.1); } }
.sparkle {
  position: absolute; top: -6px; right: -6px; font-size: 10px;
  animation: sparkle 1.5s ease-in-out infinite;
}
@keyframes sparkle { 0%,100% { opacity: 0; transform: scale(.5); } 50% { opacity: 1; transform: scale(1.2); } }

.stage-badge {
  position: absolute; top: 2px; right: 3px; font-size: 10px;
  padding: 1px 6px; border-radius: 8px; font-weight: 600;
  backdrop-filter: blur(4px); z-index: 2;
}
.stage-badge.stage-0 { background: rgba(74,222,128,.28); color: #065f46; border: 1px solid rgba(16,185,129,.4); }
.stage-badge.stage-1 { background: rgba(59,130,246,.28); color: #1e40af; border: 1px solid rgba(59,130,246,.4); }
.stage-badge.stage-2 { background: rgba(244,114,182,.3); color: #9d174d; border: 1px solid rgba(244,114,182,.5); }

.plant-effect {
  position: absolute; inset: 0; pointer-events: none; z-index: 10;
  display: flex; align-items: center; justify-content: center;
}
.plant-effect span {
  position: absolute; font-size: 18px;
  animation: burst .8s ease-out forwards;
}
.plant-effect span:nth-child(1) { top: 0; left: 50%; animation-delay: 0s; }
.plant-effect span:nth-child(2) { top: 50%; left: 10%; animation-delay: .1s; }
.plant-effect span:nth-child(3) { top: 50%; right: 10%; animation-delay: .2s; }
@keyframes burst {
  0% { transform: translate(0,0) scale(1); opacity: 1; }
  100% { transform: translate(var(--tx,-20px), -30px) scale(1.6); opacity: 0; }
}

.soil-bar {
  height: 8px; margin-top: 12px;
  background: repeating-linear-gradient(90deg, #3e2723 0 12px, #4e342e 12px 24px);
  border-radius: 0 0 16px 16px;
}

.legend {
  padding: 12px 6px 0;
  display: flex; gap: 10px; flex-wrap: wrap; align-items: center;
  font-size: 12px; color: #4b5563;
}
.lg { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 12px; }
.lg-s0 { background: rgba(74,222,128,.15); color: #065f46; }
.lg-s1 { background: rgba(59,130,246,.15); color: #1e40af; }
.lg-s2 { background: rgba(244,114,182,.18); color: #9d174d; }
.lg .d { font-size: 14px; }
.tip { margin-left: auto; color: #6b7280; font-size: 11px; }

/* === 右侧面板 === */
.panel-card {
  background: #fff; border-radius: 14px; padding: 16px;
  box-shadow: 0 4px 16px rgba(27,67,50,.08);
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 15px; font-weight: 600; color: #1b4332; margin-bottom: 12px;
}

.seed-panel { border-top: 3px solid #52b788; }
.empty-hint { text-align: center; padding: 20px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; }
.empty-hint p { color: #6b7280; font-size: 13px; margin: 0 0 12px; }

.seed-bag { display: flex; flex-direction: column; gap: 8px; }
.seed-chip {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 10px;
  border: 2px solid #e5e7eb; background: #fafafa;
  cursor: pointer; transition: all .18s;
}
.seed-chip:hover { border-color: #74c69d; background: #f1f8f4; transform: translateX(2px); }
.seed-chip.selected { border-color: #2d6a4f; background: #d8f3dc; box-shadow: 0 2px 10px rgba(45,106,79,.15); }
/* 弹窗打开时，高亮右侧背包中与格内同种的种子，提示用户先选它 */
.seed-chip.match-hint {
  border-color: #f59e0b;
  background: #fff7ed;
  box-shadow: 0 0 0 3px rgba(245,158,11,.25);
  animation: matchPulse 1.4s ease-in-out infinite;
}
@keyframes matchPulse {
  0%,100% { box-shadow: 0 0 0 2px rgba(245,158,11,.2); }
  50% { box-shadow: 0 0 0 5px rgba(245,158,11,.4); }
}
.seed-emoji { font-size: 22px; }
.seed-info { flex: 1; }
.seed-name { font-weight: 600; font-size: 14px; color: #1b4332; }
.seed-sub { font-size: 11px; color: #6b7280; }
.check-mark { color: #2d6a4f; font-weight: bold; font-size: 16px; }

.stat-panel { border-top: 3px solid #f59e0b; }
.stat-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 14px;
}
.stat-item {
  text-align: center; padding: 10px 4px;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 10px;
}
.stat-item .num { font-size: 22px; font-weight: 700; color: #92400e; }
.stat-item .lbl { font-size: 11px; color: #78350f; margin-top: 2px; }

.expand-btn { width: 100%; border-radius: 10px; }
.max-tip { text-align: center; padding: 8px; background: #d8f3dc; border-radius: 8px; font-size: 12px; color: #2d6a4f; }

/* 响应式 */
@media (max-width: 900px) {
  .garden-body { flex-direction: column; }
  .side-panel { width: 100%; }
}

/* === 已种植格子操作弹窗 === */
.cell-modal-mask {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(27,67,50,.35);
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(2px);
}
.cell-modal {
  width: 320px; max-width: 90vw;
  background: #fff; border-radius: 18px; padding: 24px 22px 18px;
  box-shadow: 0 16px 48px rgba(27,67,50,.3);
  text-align: center; position: relative;
  animation: modalPop .25s ease-out;
}
@keyframes modalPop {
  0% { transform: scale(.9); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}
.modal-close {
  position: absolute; top: 10px; right: 12px;
  border: none; background: transparent; cursor: pointer;
  font-size: 16px; color: #9ca3af; line-height: 1;
}
.modal-close:hover { color: #4b5563; }
.modal-emoji { font-size: 56px; line-height: 1; filter: drop-shadow(0 3px 6px rgba(0,0,0,.2)); }
.modal-name { font-size: 18px; font-weight: 700; color: #1b4332; margin: 6px 0 4px; }
.cell-modal .stage-badge {
  position: static; display: inline-block; margin-bottom: 14px;
}
.modal-progress { text-align: left; margin-bottom: 16px; }
.prog-label {
  display: flex; justify-content: space-between;
  font-size: 12px; color: #4b5563; margin-bottom: 6px;
}
.prog-bar {
  height: 12px; border-radius: 8px; overflow: hidden;
  background: #e5e7eb;
}
.prog-fill {
  height: 100%; border-radius: 8px;
  background: linear-gradient(90deg, #74c69d, #52b788);
  transition: width .4s ease;
}
.prog-hint { font-size: 11px; color: #6b7280; margin-top: 6px; }
.prog-stage { font-weight: 600; color: #2d6a4f; }
.stage-track {
  display: flex; justify-content: space-between;
  font-size: 11px; color: #b6bcc4; margin-top: 7px;
}
.stage-track span.on { color: #2d6a4f; font-weight: 600; }
.modal-actions { display: flex; gap: 10px; }
.modal-actions .el-button { flex: 1; }
.modal-tip { font-size: 11px; color: #9ca3af; margin-top: 12px; line-height: 1.5; }
</style>
