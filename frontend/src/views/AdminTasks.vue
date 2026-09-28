<template>
  <div class="admin-tasks sg-page">
    <div class="sg-head">
      <div class="sg-head-main">
        <span class="sg-head-title">🗂️ 任务大厅管理</span>
        <span class="sg-head-sub">维护专注任务与关联植物</span>
      </div>
      <el-button type="primary" @click="openCreate">＋ 新增任务</el-button>
    </div>

    <el-card shadow="hover" class="sg-card green">
      <el-table :data="tasks" stripe style="width: 100%" v-loading="loading"
        :header-cell-style="{ background: '#eaf5ee', color: '#1b4332', fontWeight: 600 }">
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column label="任务" min-width="160">
          <template #default="{ row }">
            <span class="task-name">{{ row.icon }} {{ row.taskName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="catTagType(row.taskCategory)">{{ row.taskCategory || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defaultMinutes" label="默认时长" width="90" align="center">
          <template #default="{ row }">{{ row.defaultMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column prop="plantName" label="关联植物" width="120">
          <template #default="{ row }">{{ row.plantName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="v => saveRow(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑任务' : '新增任务'" width="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="dialog.form.taskName" maxlength="20" show-word-limit placeholder="如：深度学习" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="dialog.form.category" clearable placeholder="不选则按任务名自动归类" style="width: 220px">
            <el-option v-for="c in CATEGORIES" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="dialog.form.icon" style="width: 100px" placeholder="emoji" />
        </el-form-item>
        <el-form-item label="默认时长">
          <el-input-number v-model="dialog.form.defaultMinutes" :min="1" :max="240" :step="5" />
          <span class="unit">分钟</span>
        </el-form-item>
        <el-form-item label="关联植物">
          <el-select v-model="dialog.form.plantId" clearable filterable placeholder="可空" style="width: 220px">
            <el-option v-for="p in plants" :key="p.id" :value="p.id" :label="`${p.icon} ${p.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="2" maxlength="100" placeholder="任务描述（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const tasks = ref([])
const plants = ref([])
const loading = ref(false)
const dialog = reactive({ visible: false, saving: false, id: null, form: {} })

// 与后端 TaskCategoryUtil.CATEGORIES 保持一致
const CATEGORIES = ['学习', '阅读', '运动', '早起', '冥想', '工作', '生活']

onMounted(async () => {
  await Promise.all([load(), loadPlants()])
})

async function load() {
  loading.value = true
  try {
    const data = await api.get('/api/admin/tasks')
    tasks.value = data.items || []
  } finally {
    loading.value = false
  }
}

async function loadPlants() {
  try {
    const data = await api.get('/api/admin/plants')
    plants.value = data.items || []
  } catch (_) { /* 植物列表加载失败不阻塞页面 */ }
}

function openCreate() {
  dialog.id = null
  dialog.form = { taskName: '', category: '', icon: '✨', defaultMinutes: 25, plantId: null, sortOrder: (tasks.value.length || 0) + 1, description: '' }
  dialog.visible = true
}

function openEdit(row) {
  dialog.id = row.id
  dialog.form = { taskName: row.taskName, category: row.taskCategory || '', icon: row.icon, defaultMinutes: row.defaultMinutes,
    plantId: row.plantId, sortOrder: row.sortOrder, description: row.description || '' }
  dialog.visible = true
}

async function save() {
  if (!dialog.form.taskName?.trim()) { ElMessage.warning('请填写任务名称'); return }
  dialog.saving = true
  try {
    if (dialog.id) {
      await api.put(`/api/admin/tasks/${dialog.id}`, dialog.form)
    } else {
      await api.post('/api/admin/tasks', dialog.form)
    }
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}

/** 行内启用开关变化时静默保存 */
async function saveRow(row) {
  await api.put(`/api/admin/tasks/${row.id}`, { enabled: row.enabled })
  ElMessage.success(`「${row.taskName}」已${row.enabled ? '启用' : '禁用'}`)
}

function remove(row) {
  ElMessageBox.confirm(`确认删除任务「${row.taskName}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await api.delete(`/api/admin/tasks/${row.id}`)
      ElMessage.success('已删除')
      load()
    }).catch(() => {})
}

function catTagType(c) {
  const map = { '学习': 'primary', '阅读': 'success', '运动': 'warning', '早起': 'danger',
    '冥想': 'info', '工作': 'primary', '生活': '' }
  return map[c] || ''
}
</script>

<style scoped>
.task-name { font-weight: 500; color: #1b4332; }
.unit { margin-left: 8px; color: #909399; }
:deep(.el-table) { border-radius: 10px; overflow: hidden; }
</style>
