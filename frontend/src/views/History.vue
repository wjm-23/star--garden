<template>
  <div class="history-page sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">📜 专注历史</span>
        <span class="sg-head-sub">回顾你的每一次专注，看种子如何悄悄发芽开花</span>
      </div>
      <el-tag type="success" effect="light" round>共 {{ records.length }} 条记录</el-tag>
    </div>

    <!-- 统计指标条 -->
    <el-row :gutter="12" class="metrics">
      <el-col :xs="12" :sm="6">
        <div class="sg-metric"><div class="m-label">累计专注</div><div class="m-value">{{ totalMinutes }}<span>分钟</span></div></div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="sg-metric"><div class="m-label">专注次数</div><div class="m-value">{{ records.length }}<span>次</span></div></div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="sg-metric"><div class="m-label">活跃天数</div><div class="m-value">{{ activeDays }}<span>天</span></div></div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="sg-metric"><div class="m-label">平均时长</div><div class="m-value">{{ avgMinutes }}<span>分钟</span></div></div>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="sg-card green">
      <template #header>
        <div class="card-header"><span>近 30 天专注明细</span></div>
      </template>
      <el-empty v-if="records.length === 0" description="暂无专注记录，快去任务大厅开始吧" />
      <el-table v-else :data="records" stripe style="width: 100%" size="default"
        :header-cell-style="{ background: '#eaf5ee', color: '#1b4332', fontWeight: 600 }">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="taskName" label="任务" min-width="140" />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="catTagType(row.taskCategory)">{{ row.taskCategory || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMinutes" label="时长" width="110">
          <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="获得植物" width="140">
          <template #default="{ row }">
            <span v-if="row.plantName">🌱 {{ row.plantName }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="completedTime" label="完成时间" width="180">
          <template #default="{ row }">{{ formatTime(row.completedTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const records = ref([])

const totalMinutes = computed(() => records.value.reduce((s, r) => s + (r.durationMinutes || 0), 0))
const activeDays = computed(() => new Set(records.value.map(r => String(r.completedTime || '').slice(0, 10))).size)
const avgMinutes = computed(() => records.value.length ? Math.round(totalMinutes.value / records.value.length) : 0)

onMounted(async () => {
  try {
    const data = await api.get('/api/history', { params: { days: 30 } })
    records.value = data.items || []
  } catch (_) { }
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
function catTagType(c) {
  const map = { '学习': 'primary', '阅读': 'success', '运动': 'warning', '早起': 'danger',
    '冥想': 'info', '工作': 'primary', '生活': '' }
  return map[c] || ''
}
</script>

<style scoped>
.metrics { margin-bottom: 16px; }
.muted { color: #adb5bd; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 16px; font-weight: 600; color: #1b4332; }
:deep(.el-table) { border-radius: 10px; overflow: hidden; }
</style>
