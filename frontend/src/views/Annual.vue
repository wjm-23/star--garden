<template>
  <div class="annual-page">
    <el-card shadow="hover" class="hero">
      <div class="hero-inner">
        <div class="hero-title">🏆 {{ annual.year }} 年度专注报告</div>
        <div class="hero-sub">每一份专注，都让你的星芽花园悄悄开花</div>
        <el-button type="success" class="back-btn" @click="$router.push('/garden')">
          <el-icon><House /></el-icon> 返回花园
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">累计专注时长</div>
          <div class="stat-value">{{ annual.totalHours }}<span>小时</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">专注次数</div>
          <div class="stat-value">{{ annual.totalCount }}<span>次</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">活跃天数</div>
          <div class="stat-value">{{ annual.activeDays }}<span>天</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">最专注的一天</div>
          <div class="stat-value small">
            {{ annual.bestDay ? annual.bestDay.date : '—' }}
            <span v-if="annual.bestDay">{{ annual.bestDay.minutes }} 分钟</span>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="hover" class="sg-card green">
          <template #header>
            <span class="card-h">📅 {{ annual.year }} 月度专注趋势</span>
          </template>
          <div ref="monthEl" class="echart"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="hover" class="sg-card teal">
          <template #header>
            <span class="card-h">⭐ 最爱的专注类别</span>
          </template>
          <div ref="catEl" class="echart"></div>
          <div class="favorite">
            今年你最常专注的类别是
            <span class="cat-name">{{ annual.favoriteCategory || '—' }}</span>
            ，共 {{ favMinutes }} 分钟。继续保持，花园因你而繁茂 🌿
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const annual = reactive({
  year: new Date().getFullYear(),
  totalMinutes: 0,
  totalHours: '0.0',
  totalCount: 0,
  activeDays: 0,
  favoriteCategory: '—',
  bestDay: null,
  months: [],
  categories: [],
})
const favMinutes = ref(0)
const monthEl = ref(null)
const catEl = ref(null)
let monthChart, catChart

onMounted(async () => {
  try {
    const data = await api.get('/api/report/annual')
    Object.assign(annual, data)
    annual.totalHours = (annual.totalMinutes / 60).toFixed(1)
    favMinutes.value = (annual.categories && annual.categories.length > 0) ? annual.categories[0].minutes : 0
  } catch (_) { }

  await new Promise(r => setTimeout(r, 50))
  renderMonth()
  renderCategory()
  window.addEventListener('resize', resizeAll)
})
onBeforeUnmount(() => {
  resizeAll()
  window.removeEventListener('resize', resizeAll)
})
function resizeAll() { monthChart?.resize(); catChart?.resize() }

function renderMonth() {
  monthChart = echarts.init(monthEl.value)
  const months = annual.months || []
  monthChart.setOption({
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].axisValue} 月：${p[0].value} 分钟` },
    grid: { left: 40, right: 16, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: months.map(m => m.month + '月'), axisLabel: { color: '#6c757d' } },
    yAxis: { type: 'value', name: '分钟', axisLabel: { color: '#6c757d' } },
    series: [{
      type: 'bar', data: months.map(m => m.minutes), barWidth: '65%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#74c69d' }, { offset: 1, color: '#2d6a4f' },
        ]),
      },
    }],
  })
}

function renderCategory() {
  catChart = echarts.init(catEl.value)
  const data = (annual.categories || []).map(c => ({ name: c.category, value: c.minutes }))
  catChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 分钟 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#6c757d' } },
    series: [{
      type: 'pie', radius: ['35%', '65%'], data,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%' },
    }],
  })
}
</script>

<style scoped>
.hero { border-radius: 12px; margin-bottom: 16px;
  background: linear-gradient(135deg, #1b4332, #2d6a4f, #40916c); border: none;
}
.hero-inner { text-align: center; padding: 12px 0; color: #fff; }
.hero-title { font-size: 28px; font-weight: 700; letter-spacing: 2px; }
.hero-sub { margin-top: 6px; opacity: .85; font-size: 13px; }
.back-btn { margin-top: 12px; background: rgba(255,255,255,.15); border-color: rgba(255,255,255,.4); color: #fff; }
.back-btn:hover { background: rgba(255,255,255,.25); border-color: #fff; }
.stat { border-radius: 12px; background: linear-gradient(135deg, #f8f9fa, #e9f5ec); border-top: 3px solid #52b788; }
.stat-label { font-size: 12px; color: #6c757d; }
.stat-value { font-size: 26px; font-weight: 700; color: #1b4332; margin-top: 4px; }
.stat-value.small { font-size: 16px; }
.stat-value span { font-size: 12px; color: #6c757d; margin-left: 4px; font-weight: 500; }
.card-h { font-size: 16px; font-weight: 600; color: #1b4332; }
.echart { width: 100%; height: 320px; }
.favorite { margin-top: 14px; padding: 10px 12px; background: #d8f3dc; border-radius: 8px; font-size: 13px; color: #1b4332; border-left: 3px solid #52b788; }
.cat-name { font-weight: 700; margin: 0 4px; color: #2d6a4f; }
</style>
