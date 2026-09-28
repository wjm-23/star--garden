<template>
  <div class="friends-page sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">👯 好友花园</span>
        <span class="sg-head-sub">拜访好友的花园，点个赞鼓励 TA</span>
      </div>
    </div>
    <el-card shadow="hover" class="sg-card green">
      <template #header>
        <div class="card-header">
          <span>🔍 搜索好友</span>
          <el-input v-model="keyword" placeholder="输入用户名搜索" style="width:240px;" clearable
            @keyup.enter="searchFriend" @clear="result = null">
            <template #append>
              <el-button @click="searchFriend"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
      </template>

      <el-empty v-if="!result && !searching" description="输入用户名搜索 TA 的花园吧">
        <div style="color:#6c757d;font-size:12px;">可搜索：sim02、sim03、sim10（60 个模拟用户）</div>
      </el-empty>
      <el-skeleton v-else-if="searching" :rows="3" animated />

      <div v-else-if="result">
        <el-alert v-if="result.error" :title="result.error" type="error" show-icon :closable="false" />
        <template v-else>
          <el-row :gutter="16">
            <el-col :xs="24" :md="8">
              <el-card shadow="hover" class="friend-card">
                <div class="friend-avatar">🌿</div>
                <div class="friend-name">{{ result.friend.nickname }}
                  <span class="friend-username">@{{ result.friend.username }}</span>
                </div>
                <el-descriptions :column="1" border size="small" style="margin-top:10px;">
                  <el-descriptions-item label="🌱 已种">{{ result.friend.totalPlants }}</el-descriptions-item>
                  <el-descriptions-item label="🔥 连续">{{ result.friend.consecutiveDays }} 天</el-descriptions-item>
                  <el-descriptions-item label="🏡 花园">{{ result.friend.gardenSize }}×{{ result.friend.gardenSize }}</el-descriptions-item>
                  <el-descriptions-item label="❤️ 获赞">{{ result.likeCount }}</el-descriptions-item>
                </el-descriptions>
                <el-button type="primary" :disabled="result.hasLikedToday" @click="likeFriend" style="width:100%;margin-top:10px;">
                  <el-icon><Star /></el-icon> {{ result.hasLikedToday ? '今天已点赞' : '点个赞鼓励 TA' }}
                </el-button>
              </el-card>
            </el-col>

            <el-col :xs="24" :md="16">
              <el-card shadow="hover">
                <template #header><span>🌿 {{ result.friend.nickname }} 的花园</span></template>
                <div class="mini-garden-wrap">
                  <div class="garden-grid" :style="{ gridTemplateColumns: `repeat(${result.friend.gardenSize}, 1fr)` }">
                    <template v-for="i in result.friend.gardenSize" :key="'r-' + i">
                      <div v-for="j in result.friend.gardenSize" :key="i + ',' + j" class="grid-cell"
                        :class="{ empty: !cellAt(i-1, j-1), occupied: cellAt(i-1, j-1) }"
                        :title="cellTitle(i-1, j-1)">
                        <template v-if="cellAt(i-1, j-1)">
                          <span class="plant-emoji">{{ cellEmoji(i-1, j-1) }}</span>
                          <span class="stage-badge" :class="'stage-' + (cellAt(i-1, j-1).stage || 0)">
                            {{ cellAt(i-1, j-1).stageName || '发芽' }}
                          </span>
                        </template>
                        <span v-else class="empty-seed"></span>
                      </div>
                    </template>
                  </div>
                </div>
                <div class="legend">
                  <span>🌱 发芽</span> · <span>🌿 成长</span> · <span>🌸 开花</span>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const keyword = ref('')
const result = ref(null)
const searching = ref(false)

async function searchFriend() {
  const name = keyword.value.trim()
  if (!name) return
  searching.value = true; result.value = null
  try {
    const data = await api.get('/api/friends/search', { params: { username: name } })
    if (data && data.friend) {
      result.value = data
    } else {
      result.value = { error: data?.msg || '用户不存在' }
    }
  } catch (_) { result.value = { error: '搜索失败' } }
  finally { searching.value = false }
}

async function likeFriend() {
  try {
    const data = await api.post('/api/friends/like', { friendId: result.value.friend.id })
    if (data.success) {
      result.value.hasLikedToday = true
      result.value.likeCount = data.newCount
      ElMessage.success('点赞成功 ❤️')
    }
  } catch (_) { }
}

function cellAt(x, y) { return result.value?.garden?.[x + ',' + y] || null }
function cellEmoji(x, y) {
  const p = cellAt(x, y); if (!p) return ''
  const stage = p.stage || 0
  if (stage >= 2) return getPlantEmojiByName(p.name)
  return ['🌱', '🌿', '🌸'][stage]
}
function cellTitle(x, y) {
  const p = cellAt(x, y); if (!p) return ''
  return p.name + '（' + (p.stageName || '发芽') + '）'
}
function getPlantEmojiByName(name) {
  const map = { '智慧藤':'📚🌿','专注花':'🎯🌸','活力草':'💪🍃','晨光花':'🌅🌻','宁静叶':'🧘🍀','星芽草':'✨🌱','灵感菇':'🍄','韵律兰':'🌷','逻辑松':'🌲','思绪苇':'🪶','记忆蕨':'🌾','安眠星草':'✨','自律棘':'🌵','联结藤':'💮','远见葵':'🌞','静心茗':'🍵','烟火椒':'🌶️','秩序苔':'🪴' }
  return map[name] || '🌻'
}
</script>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
.card-header span { font-size:16px; font-weight:600; color:#1b4332; }
.friend-card { text-align:center; border-radius:12px; }
.friend-avatar {
  font-size:52px; margin-bottom:6px; display:inline-flex; padding:10px;
  border-radius:50%; background:linear-gradient(135deg,#d8f3dc,#b7e4c7);
  box-shadow:0 4px 12px rgba(45,106,79,.18);
}
.friend-name { font-size:18px; font-weight:700; color:#1b4332; }
.friend-username { font-size:12px; color:#6c757d; font-weight:400; margin-left:6px; }

.mini-garden-wrap { padding: 8px; }
.garden-grid { display:grid; gap:4px; background:linear-gradient(135deg,#1b4332,#2d6a4f); padding:10px; border-radius:10px; }
.grid-cell { width:42px; height:42px; border-radius:6px; display:flex; align-items:center; justify-content:center; position:relative; }
.grid-cell.empty { background:rgba(255,255,255,.03); border:1.5px dashed rgba(255,255,255,.18); }
.grid-cell.occupied { background:linear-gradient(145deg,#2d6a4f,#1b4332); border:1px solid rgba(116,198,157,.55); }
.plant-emoji { font-size:20px; filter:drop-shadow(0 2px 4px rgba(0,0,0,.4)); }
.empty-seed { width:12px; height:12px; border-radius:50%; border:1.5px dashed rgba(255,255,255,.15); }
.stage-badge { position:absolute; top:2px; right:2px; font-size:9px; padding:1px 4px; border-radius:6px; font-weight:600; backdrop-filter:blur(3px); }
.stage-0 { background:rgba(74,222,128,.22); color:#86efac; border:1px solid rgba(134,239,172,.45); }
.stage-1 { background:rgba(96,165,250,.22); color:#93c5fd; border:1px solid rgba(147,197,253,.45); }
.stage-2 { background:rgba(244,114,182,.24); color:#f9a8d4; border:1px solid rgba(249,168,212,.5); }
.legend { padding:6px; text-align:center; font-size:12px; color:#6c757d; }
</style>
