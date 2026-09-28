<template>
  <div class="achievements-page sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">🏅 成就殿堂</span>
        <span class="sg-head-sub">每一次专注与互动，都在点亮你的勋章</span>
      </div>
      <el-tag type="success" effect="light" round>已解锁 {{ unlockedCount }} / {{ ALL_ACHIEVEMENTS.length }}</el-tag>
    </div>

    <div class="progress-wrap">
      <div class="progress-bar"><div class="progress-fill" :style="{ width: pct + '%' }"></div></div>
      <span class="progress-text">{{ pct }}%</span>
    </div>

    <el-card shadow="hover" class="sg-card teal">
      <el-row :gutter="14">
        <el-col v-for="a in ALL_ACHIEVEMENTS" :key="a.type" :xs="24" :sm="12" :md="8">
          <div class="ach-card" :class="{ unlocked: unlocked.has(a.type) }">
            <div class="ach-icon">{{ a.icon }}</div>
            <div class="ach-info">
              <div class="ach-name">{{ a.name }}</div>
              <div class="ach-desc">{{ a.desc }}</div>
            </div>
            <el-tag v-if="unlocked.has(a.type)" size="small" type="success" effect="light" class="ach-tag">已解锁</el-tag>
            <el-tag v-else size="small" type="info" effect="plain" class="ach-tag">未解锁</el-tag>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const ALL_ACHIEVEMENTS = [
  { type: 'FIRST_PLANT',    name: '第一株植物',   desc: '种下第一株植物',               icon: '🌱' },
  { type: 'SEVEN_DAYS',     name: '七日之约',     desc: '连续打卡 7 天',                 icon: '🔥' },
  { type: 'THIRTY_PLANTS',  name: '花海守护者',   desc: '累计种下 30 株植物',            icon: '🌸' },
  { type: 'GREEN_THUMB',    name: '绿手指',       desc: '累计种下 60 株植物',            icon: '🌿' },
  { type: 'FIRST_LIKE',     name: '花园首次点赞', desc: '首次给好友的花园点赞鼓励',      icon: '❤️' },
  { type: 'SOCIAL_BUTTERFLY', name: '社交蝴蝶',  desc: '累计给好友点赞 10 次',          icon: '🦋' },
  { type: 'FIRST_LIKED',    name: '收到第一赞',   desc: '花园首次被好友点赞',            icon: '💌' },
  { type: 'POPULAR_GARDENER', name: '人气园丁',  desc: '累计被好友点赞 20 次',          icon: '🌟' },
]

const achievements = ref([])
const unlocked = ref(new Set())

const unlockedCount = computed(() => unlocked.value.size)
const pct = computed(() => Math.round((unlockedCount.value / ALL_ACHIEVEMENTS.length) * 100))

onMounted(async () => {
  try {
    const data = await api.get('/api/achievements')
    achievements.value = data.achievements || []
    unlocked.value = new Set(achievements.value.map(a => a.achievementType))
  } catch (_) { }
})
</script>

<style scoped>
.progress-wrap { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.progress-bar { flex: 1; height: 10px; border-radius: 10px; background: #e9ecef; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 10px; background: linear-gradient(90deg, #52b788, #2d6a4f); transition: width .6s ease; }
.progress-text { font-size: 13px; font-weight: 700; color: #2d6a4f; min-width: 38px; text-align: right; }

.ach-card {
  display: flex; gap: 12px; padding: 16px; border-radius: 12px;
  border: 1px solid #e9ecef; background: #f8f9fa;
  transition: transform .2s, box-shadow .2s, border-color .2s;
  position: relative; margin-bottom: 14px;
}
.ach-card:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(27,67,50,.12); }
.ach-card.unlocked { border-color: #52b788; background: linear-gradient(135deg, #d8f3dc, #b7e4c7); }
.ach-card.unlocked .ach-icon { filter: drop-shadow(0 4px 10px rgba(45,106,79,.35)); }
.ach-card:not(.unlocked) { opacity: .8; }
.ach-card:not(.unlocked) .ach-icon { filter: grayscale(1) opacity(.5); }
.ach-icon { font-size: 38px; line-height: 1; transition: all .2s; }
.ach-info { flex: 1; min-width: 0; }
.ach-name { font-weight: 700; color: #1b4332; }
.ach-desc { font-size: 12px; color: #6c757d; margin: 4px 0 0; }
.ach-tag { position: absolute; top: 12px; right: 12px; }
</style>
