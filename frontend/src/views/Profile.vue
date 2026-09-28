<template>
  <div class="profile-page sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">👤 个人中心</span>
        <span class="sg-head-sub">管理你的资料，记录你的成长</span>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="avatar-card sg-card teal">
          <div class="avatar-ring">
            <el-avatar :size="84" :style="{ background: '#40916c', fontSize: '34px' }">
              {{ form.nickname ? form.nickname.slice(0,1) : form.username.slice(0,1).toUpperCase() }}
            </el-avatar>
          </div>
          <div class="uname">{{ auth.username }}</div>
          <el-tag :type="auth.role === 'ADMIN' ? 'danger' : 'success'" effect="light" class="role-tag">
            {{ auth.role === 'ADMIN' ? '🌟 管理员' : '🌱 普通用户' }}
          </el-tag>
          <el-descriptions :column="1" border size="small" class="stat-desc">
            <el-descriptions-item label="🌱 已种">{{ auth.totalPlants || 0 }}</el-descriptions-item>
            <el-descriptions-item label="🔥 连续">{{ auth.consecutiveDays || 0 }} 天</el-descriptions-item>
            <el-descriptions-item label="🏡 花园">{{ auth.gardenSize || 6 }}×{{ auth.gardenSize || 6 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="hover" class="sg-card green">
          <template #header>
            <div class="card-header"><span>✏️ 编辑个人资料</span></div>
          </template>
          <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" size="large">
            <el-form-item label="用户名">
              <el-input :model-value="form.username" disabled />
              <div class="form-tip">用户名注册后不可修改</div>
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" maxlength="20" show-word-limit />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
            <el-form-item label="头像 URL">
              <el-input v-model="form.avatarUrl" placeholder="可留空" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="save" :loading="saving">保存修改</el-button>
              <el-button @click="loadFromAuth">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import api from '../api'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const form = reactive({ username: '', nickname: '', email: '', avatarUrl: '' })
const formRef = ref(null)
const saving = ref(false)

const rules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

function loadFromAuth() {
  form.username = auth.username
  form.nickname = auth.nickname || auth.username
  form.email = auth.user?.email || ''
  form.avatarUrl = auth.user?.avatarUrl || ''
}

onMounted(async () => {
  try { await auth.refreshProfile() } catch (_) { }
  loadFromAuth()
})

async function save() {
  try {
    await formRef.value.validate()
    saving.value = true
    const data = await api.post('/api/profile/update', {
      nickname: form.nickname,
      email: form.email,
      avatarUrl: form.avatarUrl,
    })
    auth.user = data.user
    localStorage.setItem('sg_user', JSON.stringify(data.user))
    ElMessage.success('资料已更新')
  } catch (_) { } finally { saving.value = false }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 16px; font-weight: 600; color: #1b4332; }
.avatar-card { text-align: center; padding: 22px 16px; }
.avatar-ring { display: inline-flex; padding: 5px; border-radius: 50%; background: linear-gradient(135deg, #52b788, #2d6a4f); box-shadow: 0 6px 16px rgba(45,106,79,.25); }
.uname { font-size: 18px; font-weight: 700; color: #1b4332; margin: 12px 0 8px; }
.role-tag { margin-bottom: 14px; }
.stat-desc { margin-top: 4px; }
:deep(.stat-desc .el-descriptions__label) { color: #6c757d; background: #f6fbf8; }
.form-tip { font-size: 12px; color: #6c757d; margin-top: 4px; }
</style>
