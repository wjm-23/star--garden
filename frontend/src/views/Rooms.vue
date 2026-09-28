<template>
  <div class="rooms-page sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">🤝 协作专注房间</span>
        <span class="sg-head-sub">多人一起专注，互不干扰还能 PK</span>
      </div>
    </div>
    <!-- 房间列表 -->
    <el-card shadow="hover" v-if="!connected" class="sg-card green">
      <template #header>
        <div class="card-header">
          <span>📋 进行中的房间</span>
          <el-button type="primary" @click="showCreate = true">
            <el-icon><Plus /></el-icon> 新建房间
          </el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon title="💡 多人一起专注，互不干扰还能 PK" style="margin-bottom:12px;" />
      <el-row :gutter="12">
        <el-col v-for="room in rooms" :key="room.roomId" :xs="24" :sm="12" :md="8">
          <div class="room-card" @click="joinRoom(room.roomId)">
            <div class="room-name">🏡 {{ room.roomId }}</div>
            <div class="room-meta">
              <el-tag size="small" type="success">👥 {{ room.memberCount }} 人</el-tag>
              <el-tag size="small" effect="plain">点击加入</el-tag>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-if="rooms.length === 0" description="暂无活跃房间，点右上角创建一个吧" />

      <!-- 创建房间弹窗 -->
      <el-dialog v-model="showCreate" title="创建协作房间" width="380px" class="rooms-create-dialog">
        <el-form :model="createForm">
          <el-form-item label="房间名">
            <el-input v-model="createForm.roomId" placeholder="例如 早鸟团" maxlength="12" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showCreate = false">取消</el-button>
          <el-button type="primary" @click="joinRoom(createForm.roomId.trim())" :disabled="!createForm.roomId || !createForm.roomId.trim()">
            创建并进入
          </el-button>
        </template>
      </el-dialog>
    </el-card>

    <!-- 房间内 -->
    <el-card shadow="hover" v-else class="sg-card teal">
      <template #header>
        <div class="card-header">
          <span>🏡 房间 · {{ currentRoomId }}</span>
          <div class="room-actions">
            <el-button v-if="isHost" type="danger" @click="deleteRoom">解散房间</el-button>
            <el-button type="info" plain @click="leaveRoom">离开房间</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <!-- 左侧：成员列表 + 专注控制 -->
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="member-card">
            <div class="sub-header">👥 房间成员（{{ members.length }}）</div>
            <div v-for="m in members" :key="m.name" class="member-row">
              <el-icon :size="16" :color="m.focusing ? '#f77f00' : '#6c757d'">
                <component :is="m.focusing ? 'Timer' : 'User'" />
              </el-icon>
              <span class="m-name">{{ m.name }}</span>
              <el-tag v-if="m.host" size="small" type="warning" effect="plain">👑 房主</el-tag>
              <el-tag v-if="m.focusing" size="small" type="warning" effect="light">专注中</el-tag>
              <el-tag v-else-if="!m.host" size="small" effect="plain">空闲</el-tag>
            </div>
          </el-card>

          <el-card shadow="never" class="focus-card">
            <div class="sub-header">🎯 开始协作专注</div>
            <el-form :inline="false">
              <el-form-item label="专注时长">
                <el-radio-group v-model="focusMinutes">
                  <el-radio-button :value="15">15 分钟</el-radio-button>
                  <el-radio-button :value="25">25 分钟</el-radio-button>
                  <el-radio-button :value="45">45 分钟</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="你的状态">
                <span class="focus-timer" v-if="myFocusing">
                  <el-icon><Timer /></el-icon> 已专注 {{ formatMs(myElapsed) }}
                </span>
                <span v-else class="idle">空闲中</span>
              </el-form-item>
              <div>
                <el-button v-if="!myFocusing" type="primary" @click="startFocus">开始专注</el-button>
                <el-button v-else type="success" @click="endFocus">结束专注并结算</el-button>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <!-- 右侧：PK 榜 + 聊天 -->
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="pk-card">
            <div class="sub-header">🏆 今日专注 PK 榜（协作房间成员）</div>
            <el-table :data="pkBoard" size="small" border stripe>
              <el-table-column type="index" label="#" width="46" align="center" />
              <el-table-column prop="name" label="成员" />
              <el-table-column prop="minutes" label="今日专注 (分钟)" width="140" align="center">
                <template #default="{ row }">
                  <span :class="{ 'pk-first': isFirst(row.name) }">{{ row.minutes }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="chat-card">
            <div class="sub-header">💬 房间聊天</div>
            <div ref="chatBoxRef" class="chat-box">
              <div v-for="(m, idx) in messages" :key="idx" class="chat-msg" :class="'chat-' + m.type">
                <b v-if="m.type === 'system'">【{{ m.msg }}】</b>
                <template v-else-if="m.type === 'chat'">
                  <b>{{ m.who }}：</b>{{ m.msg }}
                </template>
                <template v-else-if="m.type === 'focus-start'">
                  🟢 <b>{{ m.who }}</b> 开始专注 {{ m.minutes }} 分钟
                </template>
                <template v-else-if="m.type === 'focus-end'">
                  🔴 <b>{{ m.who }}</b> 结束专注，本次 {{ m.minutes }} 分钟
                </template>
                <template v-else-if="m.type === 'pk'">
                  🏆 PK 榜已更新
                </template>
              </div>
            </div>
            <div class="chat-input">
              <el-input v-model="chatText" placeholder="说点什么..." @keyup.enter="sendChat" />
              <el-button type="primary" @click="sendChat" :disabled="!chatText.trim()">发送</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const auth = useAuthStore()
const rooms = ref([])
const showCreate = ref(false)
const createForm = reactive({ roomId: '' })

// 房间状态
const connected = ref(false)
const currentRoomId = ref('')
let ws = null
let myFocusing = ref(false)
let focusStartTs = 0
const myElapsed = ref(0)
let focusTimer = null
const messages = ref([])
const members = ref([])
const pkBoard = ref([])
const chatText = ref('')
const chatBoxRef = ref(null)

// 当前登录用户是否为房主（后端成员列表会带 host 标记）
const isHost = computed(() =>
  members.value.some(m => m.uid === auth.user?.id && m.host)
)

async function loadRooms() {
  try {
    const data = await api.get('/api/rooms')
    rooms.value = data.rooms || []
  } catch (_) { rooms.value = [] }
}
onMounted(loadRooms)
onBeforeUnmount(() => { leaveRoom(true) })

function wsUrl(roomId) {
  // dev: ws://localhost:5173/ws/room/xxx → vite proxy
  // prod: ws://<same host>/ws/room/xxx
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.host
  return `${proto}//${host}/ws/room/${encodeURIComponent(roomId)}?uid=${auth.user?.id || 0}&uname=${encodeURIComponent(auth.nickname)}`
}

function joinRoom(roomId) {
  if (!roomId) return
  createForm.roomId = ''
  showCreate.value = false
  currentRoomId.value = roomId
  messages.value = []
  members.value = []
  pkBoard.value = []

  ws = new WebSocket(wsUrl(roomId))
  // 连接真正建立后才切换到"房间内"界面，避免连接失败时假进入
  ws.onopen = () => {
    connected.value = true
    ElMessage.success(`已进入房间「${roomId}」`)
  }
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      if (m.type === 'room-deleted') {
        ElMessage.warning('房间已被房主解散')
        leaveRoom()
        return
      }
      if (m.type === 'error') {
        ElMessage.error(m.msg || '操作失败')
        return
      }
      messages.value.push(m)
      if (m.members) members.value = m.members
      if (m.type === 'pk' && m.board) pkBoard.value = m.board
      nextTick(() => {
        if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
      })
    } catch (e) { /* 忽略非 JSON 消息 */ }
  }
  ws.onerror = () => {
    ElMessage.error(`房间「${roomId}」连接失败，请确认后端服务已启动`)
  }
  ws.onclose = () => {
    const wasIn = connected.value
    connected.value = false
    stopFocusTimer()
    if (wasIn) {
      ElMessage.info('房间连接已断开')
    } else {
      ElMessage.warning(`无法连接房间「${roomId}」：后端未启动或网络异常`)
    }
    loadRooms()
  }
}

function leaveRoom(silent = false) {
  stopFocusTimer()
  connected.value = false
  currentRoomId.value = ''
  if (ws) { try { ws.close() } catch (_) { } ws = null }
  if (!silent) loadRooms()
}

// 房主解散房间：仅创建者（房主）可操作，其他人只能"离开"
function deleteRoom() {
  if (!ws) return
  ElMessageBox.confirm('解散后所有成员将立即退出，确定解散该房间？', '解散房间', {
    type: 'warning', confirmButtonText: '确定解散', cancelButtonText: '取消'
  }).then(() => {
    ws.send(JSON.stringify({ type: 'delete-room' }))
  }).catch(() => { /* 取消 */ })
}

function startFocus() {
  if (!ws) return
  ws.send(JSON.stringify({ type: 'focus-start', minutes: focusMinutes.value }))
  myFocusing.value = true
  focusStartTs = Date.now()
  startFocusTimer()
}

function endFocus() {
  if (!ws) return
  ws.send(JSON.stringify({ type: 'focus-end' }))
  myFocusing.value = false
  stopFocusTimer()
}

let focusMinutes = ref(25)
function startFocusTimer() {
  stopFocusTimer()
  myElapsed.value = 0
  focusTimer = setInterval(() => {
    if (myFocusing.value) myElapsed.value = Date.now() - focusStartTs
  }, 1000)
}
function stopFocusTimer() {
  if (focusTimer) { clearInterval(focusTimer); focusTimer = null }
}
function formatMs(ms) {
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60), ss = s % 60
  return `${String(m).padStart(2,'0')}:${String(ss).padStart(2,'0')}`
}

function sendChat() {
  if (!ws || !chatText.value.trim()) return
  ws.send(JSON.stringify({ type: 'chat', msg: chatText.value.trim() }))
  chatText.value = ''
}

function isFirst(name) {
  return pkBoard.value.length > 0 && pkBoard.value[0].name === name
}
</script>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
.card-header span { font-size:16px; font-weight:600; }
.room-card {
  border:1px solid #e9ecef; border-radius:10px; padding:14px; cursor:pointer;
  transition: all .15s; margin-bottom:12px; background:linear-gradient(135deg,#f8f9fa,#e9f5ec);
}
.room-card:hover { transform:translateY(-2px); box-shadow:0 6px 14px rgba(0,0,0,.08); border-color:#74c69d; }
.room-name { font-weight:700; color:#1b4332; margin-bottom:6px; }
.room-meta { display:flex; gap:8px; }

.sub-header { font-weight:700; color:#1b4332; margin-bottom:10px; font-size:14px; }
.member-row { display:flex; align-items:center; gap:8px; padding:6px 0; border-bottom:1px dashed #e9ecef; }
.member-row:last-child { border:none; }
.m-name { flex:1; }
.focus-timer { display:inline-flex; align-items:center; gap:6px; color:#f77f00; font-weight:700; font-size:14px; }
.idle { color:#6c757d; }

.pk-card, .member-card, .chat-card, .focus-card { margin-bottom:12px; }
.pk-first { font-weight:700; color:#d97706; }

.chat-box { height: 260px; overflow-y:auto; border:1px solid #e9ecef; border-radius:8px; padding:8px; margin-bottom:8px; background:#fafafa; }
.chat-msg { margin-bottom:4px; font-size:13px; line-height:1.5; }
.chat-system { color:#6c757d; font-style:italic; }
.chat-input { display:flex; gap:8px; }
.chat-input .el-input { flex:1; }
.room-actions { display:flex; gap:8px; }
</style>

<!-- 创建房间弹窗是 teleport 到 body 的，scoped 样式无法穿透，故用非 scoped 规则保证输入框文字可见 -->
<style>
.rooms-create-dialog .el-input__inner { color: #1b4332 !important; }
.rooms-create-dialog .el-input__wrapper {
  background-color: #ffffff !important;
  box-shadow: 0 0 0 1px #dcdfe6 inset !important;
}
</style>
