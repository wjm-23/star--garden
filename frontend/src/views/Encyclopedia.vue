<template>
  <div class="page-wrap">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>📚 植物图鉴</span>
          <el-tag effect="plain">已解锁 {{ unlocked.size }} / {{ plants.length }}（{{ progress }}%）</el-tag>
        </div>
      </template>
      <el-progress :percentage="progress" :stroke-width="12" color="#40916c" style="margin-bottom: 16px;" />
      <el-row :gutter="14">
        <el-col v-for="p in plants" :key="p.id" :xs="12" :sm="8" :md="6" :lg="4">
          <div class="plant-card" :class="{ locked: !unlocked.has(p.id), ['rarity-' + rarityClass(p.rarity)]: true }">
            <div class="plant-icon-wrap">
              <span class="plant-icon">{{ p.icon || '🌿' }}</span>
              <span v-if="unlocked.has(p.id)" class="rarity-glow"></span>
            </div>
            <div class="plant-name">{{ p.name }}</div>
            <div class="plant-meta">
              <el-tag v-if="unlocked.has(p.id)" size="small" round :type="rarityTagType(p.rarity)">{{ p.rarity }}</el-tag>
              <el-tag v-else size="small" round effect="plain" class="locked-tag">🔒 未解锁</el-tag>
            </div>
            <div v-if="unlocked.has(p.id)" class="plant-desc">{{ p.description }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const plants = ref([])
const unlocked = ref(new Set())

const progress = computed(() => plants.value.length === 0 ? 0
  : Math.round(unlocked.value.size * 100 / plants.value.length))

onMounted(async () => {
  try {
    const data = await api.get('/api/encyclopedia')
    plants.value = data.plants || []
    unlocked.value = new Set(data.unlockedIds || [])
  } catch (_) { }
})

function rarityClass(r) {
  switch ((r || '').toUpperCase()) {
    case 'COMMON': return 'common'
    case 'RARE': return 'rare'
    case 'EPIC': return 'epic'
    case 'LEGENDARY': return 'legendary'
    default: return 'common'
  }
}

function rarityTagType(r) {
  switch ((r || '').toUpperCase()) {
    case 'COMMON': return 'info'
    case 'RARE': return 'warning'
    case 'EPIC': return ''
    case 'LEGENDARY': return 'danger'
    default: return 'info'
  }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 16px; font-weight: 600; }

.plant-card {
  border: 1.5px solid #e5e7eb; border-radius: 14px; padding: 16px;
  text-align: center; background: linear-gradient(135deg, #ffffff, #f1f8f4);
  transition: transform .2s, box-shadow .2s;
  margin-bottom: 4px;
}
.plant-card:hover { transform: translateY(-3px); box-shadow: 0 10px 24px rgba(27,67,50,.12); }

.plant-card.rarity-common { border-color: #d1d5db; }
.plant-card.rarity-rare { border-color: #fbbf24; background: linear-gradient(135deg, #fffbeb, #fef3c7); }
.plant-card.rarity-epic { border-color: #a855f7; background: linear-gradient(135deg, #faf5ff, #f3e8ff); }
.plant-card.rarity-legendary { border-color: #ef4444; background: linear-gradient(135deg, #fef2f2, #fee2e2); }

/* 未解锁 = 熄灭：保留植物本来的形象，只做去色压暗，不再是问号黑块 */
.plant-card.locked {
  opacity: .5;
  filter: grayscale(.85) brightness(.98);
  cursor: default;
}
.plant-card.locked:hover { transform: none; box-shadow: none; }
.plant-card.locked .plant-icon { animation: none; }
.plant-card.locked .plant-name { color: #94a3b8; }
.locked-tag { opacity: .85; }

/* 已解锁 = 点亮：图标带一层柔光 */
.plant-card:not(.locked) .plant-icon {
  filter: drop-shadow(0 3px 10px rgba(82,183,136,.45));
}

.plant-icon-wrap { position: relative; display: inline-block; margin-bottom: 6px; }
.plant-icon {
  font-size: 40px; display: block;
  filter: drop-shadow(0 3px 8px rgba(0,0,0,.15));
}
.plant-card.rarity-rare .plant-icon { animation: sway 3s ease-in-out infinite; }
.plant-card.rarity-epic .plant-icon { animation: glow 2s ease-in-out infinite; }
.plant-card.rarity-legendary .plant-icon { animation: glow 1.5s ease-in-out infinite; }
@keyframes sway { 0%,100% { transform: rotate(-3deg); } 50% { transform: rotate(3deg); } }
@keyframes glow { 0%,100% { filter: drop-shadow(0 3px 8px rgba(0,0,0,.15)); } 50% { filter: drop-shadow(0 3px 16px rgba(168,85,247,.5)); } }

.rarity-glow {
  position: absolute; inset: -4px; border-radius: 50%;
  pointer-events: none;
}
.plant-card.rarity-rare .rarity-glow { box-shadow: 0 0 16px rgba(251,191,36,.4); border-radius: 50%; }
.plant-card.rarity-epic .rarity-glow { box-shadow: 0 0 20px rgba(168,85,247,.5); border-radius: 50%; }
.plant-card.rarity-legendary .rarity-glow { box-shadow: 0 0 24px rgba(239,68,68,.55); border-radius: 50%; }

.plant-name { font-weight: 700; margin-bottom: 6px; color: #1b4332; font-size: 15px; }
.plant-meta { margin-bottom: 6px; }
.plant-desc { font-size: 11px; color: #6b7280; line-height: 1.4; }
</style>
