<template>
  <div class="report-page">
    <el-card shadow="never" class="nav-bar">
      <div class="nav-bar-inner">
        <el-button type="success" @click="$router.push('/garden')">
          <el-icon><House /></el-icon> 返回花园
        </el-button>
        <el-button @click="$router.push('/annual')">
          <el-icon><TrendCharts /></el-icon> 查看年度报告
        </el-button>
      </div>
    </el-card>
    <el-row :gutter="16">
      <!-- 顶部指标卡 -->
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="metric">
          <div class="m-label">累计专注</div>
          <div class="m-value">{{ totalMinutes }}<span> 分钟</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="metric">
          <div class="m-label">累计专注</div>
          <div class="m-value">{{ totalHours }}<span> 小时</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="metric">
          <div class="m-label">专注天数</div>
          <div class="m-value">{{ activeDays }}<span> 天</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="metric">
          <div class="m-label">今日专注</div>
          <div class="m-value">{{ todayMinutes }}<span> 分钟</span></div>
        </el-card>
      </el-col>

      <!-- 趋势 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header"><span>📈 专注时长趋势（近 30 天）</span></div>
          </template>
          <div ref="trendEl" class="echart"></div>
        </el-card>
      </el-col>
      <!-- 类别饼图 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header"><span>🥧 类别分布</span></div>
          </template>
          <div ref="catEl" class="echart"></div>
        </el-card>
      </el-col>

      <!-- 热力日历 -->
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🔥 专注热力日历（近 90 天）</span>
              <el-tag effect="plain" type="success">颜色越深，当日专注时长越长</el-tag>
            </div>
          </template>
          <div ref="heatEl" class="echart-wide"></div>
        </el-card>
      </el-col>

      <!-- 最佳时段 -->
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>⏰ 最佳专注时段（24 小时分布）</span>
              <el-tag effect="plain" type="warning">你在 {{ bestHourText }} 点前后最活跃</el-tag>
            </div>
          </template>
          <div ref="hourEl" class="echart-wide"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const trendEl = ref(null)
const catEl = ref(null)
const heatEl = ref(null)
const hourEl = ref(null)

let trendChart, catChart, heatChart, hourChart

const totalMinutes = ref(0)
const totalHours = ref('0.0')
const activeDays = ref(0)
const todayMinutes = ref(0)
const bestHourText = ref('—')

onMounted(async () => {
  try {
    // 并行拉取四张图的数据
    const [trend, cat, heat, hour] = await Promise.all([
      api.get('/api/report/trend', { params: { days: 30 } }),
      api.get('/api/report/category'),
      api.get('/api/report/heatmap'),
      api.get('/api/report/best-hours'),
    ])
    await nextTick()
    renderTrend(trend.trend || [])
    renderCategory(cat.items || [])
    renderHeatmap(heat.data || [])
    renderBestHours(hour.items || [])
    // 汇总指标
    const mins = (trend.trend || []).reduce((s, r) => s + r.minutes, 0)
    totalMinutes.value = mins
    totalHours.value = (mins / 60).toFixed(1)
    activeDays.value = (trend.trend || []).filter(r => r.minutes > 0).length
    const today = (trend.trend || []).slice(-1)[0]?.minutes || 0
    todayMinutes.value = today
    const best = (hour.items || []).sort((a, b) => b.minutes - a.minutes)[0]
    if (best && best.minutes > 0) bestHourText.value = String(best.hour)
  } catch (e) {
    console.error(e)
  }
  window.addEventListener('resize', resizeAll)
})

onBeforeUnmount(() => {
  resizeAll
  window.removeEventListener('resize', resizeAll)
})

function resizeAll() {
  trendChart?.resize()
  catChart?.resize()
  heatChart?.resize()
  hourChart?.resize()
}

function renderTrend(trend) {
  trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 16, top: 24, bottom: 36 },
    xAxis: {
      type: 'category', data: trend.map(d => d.date.slice(5)),
      axisLabel: { color: '#6c757d' },
    },
    yAxis: { type: 'value', name: '分钟', axisLabel: { color: '#6c757d' } },
    series: [{
      type: 'bar', data: trend.map(d => d.minutes),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#40916c' }, { offset: 1, color: '#95d5b2' },
        ]),
        borderRadius: [4, 4, 0, 0],
      },
      barWidth: '60%',
    }],
  })
}

function renderCategory(items) {
  catChart = echarts.init(catEl.value)
  catChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 分钟 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#6c757d' } },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: items.map(i => ({ name: i.category, value: i.minutes })),
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
    }],
  })
}

function renderHeatmap(data) {
  heatChart = echarts.init(heatEl.value)
  const max = Math.max(60, ...data.map(d => d[1]))
  heatChart.setOption({
    tooltip: { formatter: (p) => `${p.data[0]}: ${p.data[1]} 分钟` },
    visualMap: {
      min: 0, max: max, calculable: true, orient: 'horizontal', left: 'center', bottom: 0,
      inRange: { color: ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39'] },
      textStyle: { color: '#6c757d' },
    },
    calendar: {
      top: 20, left: 40, right: 40,
      range: data[0]?.[0].slice(0, 4) + '-01-01',
      yearLabel: { show: false },
      itemStyle: { borderWidth: 3, borderColor: '#fff' },
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data }],
  })
}

function renderBestHours(items) {
  hourChart = echarts.init(hourEl.value)
  const maxCount = Math.max(1, ...items.map(i => i.count))
  hourChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0 },
    grid: { left: 40, right: 20, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: items.map(i => i.hour + '时'), axisLabel: { color: '#6c757d' } },
    yAxis: [
      { type: 'value', name: '分钟', axisLabel: { color: '#6c757d' } },
      { type: 'value', name: '次数', min: 0, max: maxCount * 1.5, axisLabel: { color: '#6c757d' } },
    ],
    series: [
      {
        name: '专注分钟', type: 'bar',
        data: items.map(i => i.minutes),
        itemStyle: { color: '#40916c', borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '专注次数', type: 'line', yAxisIndex: 1,
        data: items.map(i => i.count),
        smooth: true, symbolSize: 6,
        itemStyle: { color: '#f77f00' },
      },
    ],
  })
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 16px; font-weight: 600; }
.nav-bar { margin-bottom: 12px; border-radius: 10px; }
.nav-bar-inner { display: flex; gap: 10px; justify-content: flex-end; }
.metric {
  background: linear-gradient(135deg, #f1f8f4, #e9f5ec);
  border-radius: 12px;
}
.metric .m-label { font-size: 12px; color: #6c757d; margin-bottom: 4px; }
.metric .m-value { font-size: 28px; font-weight: 700; color: #1b4332; }
.metric .m-value span { font-size: 14px; color: #6c757d; font-weight: 500; margin-left: 4px; }
.echart { width: 100%; height: 300px; }
.echart-wide { width: 100%; height: 280px; }
</style>
