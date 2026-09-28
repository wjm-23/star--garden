<template>
  <div class="admin-eval sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">🧪 推荐算法消融评估</span>
        <span class="sg-head-sub">对比 5 档模型在 Precision@N / Recall@N 上的差异</span>
      </div>
      <div class="actions">
        <el-tag v-if="lastRunAt" type="info" effect="plain" size="small">上次运行：{{ lastRunAt }}</el-tag>
        <el-button type="primary" :loading="loading" @click="run">运行评估</el-button>
      </div>
    </div>

    <el-card shadow="hover" class="sg-card amber">
      <el-alert type="info" :closable="false" show-icon class="proto"
        title="评估协议：留一时间法（按完成时间排序，前 80% 训练 / 后 20% 测试），对比 5 档消融模型在 Precision@N / Recall@N（宏平均）上的差异" />

      <template v-if="rows.length">
        <div ref="chartEl" class="chart"></div>
        <el-table :data="rows" stripe style="width: 100%"
          :row-class-name="rowClass"
          :header-cell-style="{ background: '#eaf5ee', color: '#1b4332', fontWeight: 600 }">
          <el-table-column prop="model" label="模型" width="130">
            <template #default="{ row }">
              <el-tag :type="row.model === 'FULL' ? 'success' : 'info'" effect="dark" size="small">{{ row.model }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" min-width="260" />
          <el-table-column prop="P@3" label="P@3" width="90" align="center" />
          <el-table-column prop="R@3" label="R@3" width="90" align="center" />
          <el-table-column prop="P@5" label="P@5" width="90" align="center" />
          <el-table-column prop="R@5" label="R@5" width="90" align="center" />
          <el-table-column prop="users" label="评估用户数" width="100" align="center" />
        </el-table>
      </template>
      <el-empty v-else description="点击「运行评估」执行消融实验（需已开启模拟数据播种 app.data.seed=true）" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { ElMessage } from 'element-plus'

const DESC = {
  'POPULAR': '仅全局热度（非个性化基线）',
  'CF': '仅 UserCF（余弦相似度）',
  'CF+DECAY': 'UserCF + 时间衰减因子',
  'CF+TAG': 'CF+DECAY + 标签相似度',
  'FULL': '完整混合模型（+时段匹配）',
}

const loading = ref(false)
const rows = ref([])
const lastRunAt = ref('')
const chartEl = ref(null)
let chart = null

async function run() {
  loading.value = true
  try {
    const data = await api.post('/api/admin/eval/run')
    lastRunAt.value = String(data.evaluatedAt || '').replace('T', ' ')
    rows.value = Object.entries(data.results || {}).map(([model, m]) => ({
      model,
      desc: DESC[model] || '',
      'P@3': m['P@3'] ?? '—',
      'R@3': m['R@3'] ?? '—',
      'P@5': m['P@5'] ?? '—',
      'R@5': m['R@5'] ?? '—',
      users: m['评估用户数'] ?? '—',
    }))
    if ((data.results || {})['FULL']?.['评估用户数'] === 0) {
      ElMessage.warning('评估用户数为 0：请确认已开启模拟数据播种（app.data.seed=true）后重启并重试')
    }
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function rowClass({ row }) { return row.model === 'FULL' ? 'full-row' : '' }

function renderChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    title: { text: '消融实验：各模型 Precision@N / Recall@N 对比', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['P@3', 'R@3', 'P@5', 'R@5'], bottom: 0 },
    grid: { left: 48, right: 24, top: 56, bottom: 56 },
    xAxis: { type: 'category', data: rows.value.map(r => r.model) },
    yAxis: { type: 'value', max: v => Math.ceil(v.max * 100) / 100 || 0.1 },
    series: ['P@3', 'R@3', 'P@5', 'R@5'].map(k => ({
      name: k,
      type: 'bar',
      data: rows.value.map(r => (typeof r[k] === 'number' ? r[k] : 0)),
      barMaxWidth: 26,
      label: { show: true, position: 'top', fontSize: 10, formatter: p => (p.value > 0 ? p.value : '') },
    })),
  })
}

function onResize() { chart && chart.resize() }
window.addEventListener('resize', onResize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
  chart = null
})
</script>

<style scoped>
.actions { display: flex; align-items: center; gap: 10px; }
.proto { margin-bottom: 16px; }
.chart { width: 100%; height: 340px; margin-bottom: 8px; }
:deep(.el-table) { border-radius: 10px; overflow: hidden; }
:deep(.full-row) td.el-table__cell { background: #d8f3dc !important; font-weight: 600; }
</style>
