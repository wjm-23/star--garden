<template>
  <div class="admin-users sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">👥 用户管理</span>
        <span class="sg-head-sub">查看与维护用户、角色与状态</span>
      </div>
    </div>
    <el-card shadow="hover" class="sg-card green">
      <template #header>
        <div class="card-header">
          <span>🔍 搜索用户</span>
          <div class="actions">
            <el-input v-model="keyword" placeholder="搜索用户名 / 昵称" clearable style="width: 220px"
              :prefix-icon="'Search'" @keyup.enter="load" @clear="load" />
            <el-button @click="load">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="users" stripe style="width: 100%" v-loading="loading"
        :header-cell-style="{ background: '#eaf5ee', color: '#1b4332', fontWeight: 600 }">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="110" />
        <el-table-column prop="email" label="邮箱" min-width="160">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-select :model-value="row.role" size="small" style="width: 96px" @change="r => changeRole(row, r)">
              <el-option label="管理员" value="ADMIN" />
              <el-option label="普通用户" value="USER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" :disabled="row.id === selfId" @change="v => toggleEnabled(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="连续天数" width="90" align="center">
          <template #default="{ row }">🔥 {{ row.consecutiveDays ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="植物数" width="80" align="center">
          <template #default="{ row }">🌱 {{ row.totalPlants ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain :disabled="row.id === selfId" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const authStore = useAuthStore()
const selfId = authStore.user?.id
const users = ref([])
const keyword = ref('')
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await api.get('/api/admin/users', { params: { keyword: keyword.value || undefined } })
    users.value = data.items || []
  } finally {
    loading.value = false
  }
}

async function toggleEnabled(row, enabled) {
  const data = await api.put(`/api/admin/users/${row.id}/enabled`, { enabled })
  row.enabled = data.user.enabled
  ElMessage.success(enabled ? `已启用 ${row.username}` : `已停用 ${row.username}`)
}

async function changeRole(row, role) {
  const data = await api.put(`/api/admin/users/${row.id}/role`, { role })
  row.role = data.user.role
  ElMessage.success(`${row.username} 角色已调整为 ${role === 'ADMIN' ? '管理员' : '普通用户'}`)
}

function remove(row) {
  ElMessageBox.confirm(
    `删除用户「${row.username}」将同时删除其专注记录、花园、成就与点赞数据，且不可恢复。确认删除？`,
    '危险操作', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  ).then(async () => {
    await api.delete(`/api/admin/users/${row.id}`)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 16px; font-weight: 600; color: #1b4332; }
.actions { display: flex; gap: 8px; }
:deep(.el-table) { border-radius: 10px; overflow: hidden; }
</style>
