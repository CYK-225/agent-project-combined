<!--
  SkillManager - Skill 仓库管理抽屉组件
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { skillRepoApi } from '../../api/skillRepo'

// Props 定义
const props = defineProps<{
  visible: boolean
}>()

// Emits 定义
const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

// ==================== 状态 ====================

// 绑定列表：{ agentName: repoUrl }
const bindings = ref<Record<string, string>>({})
const listLoading = ref(false)

// 绑定表单
const bindForm = ref({
  agentName: '',
  repoUrl: '',
  skillPatterns: ''
})
const bindLoading = ref(false)

// 查看技能列表
const skillsDrawerVisible = ref(false)
const currentSkills = ref<string[]>([])
const currentRepoUrl = ref('')
const skillsLoading = ref(false)

// 刷新中状态
const refreshingAgent = ref('')

// ==================== 方法 ====================

// 加载绑定列表
const loadBindings = async () => {
  listLoading.value = true
  try {
    bindings.value = await skillRepoApi.list()
  } catch (error) {
    console.error('获取绑定列表失败:', error)
    ElMessage.error('获取绑定列表失败')
  } finally {
    listLoading.value = false
  }
}

// 绑定仓库
const handleBind = async () => {
  if (!bindForm.value.agentName.trim() || !bindForm.value.repoUrl.trim()) {
    ElMessage.warning('请填写 Agent 名称和仓库地址')
    return
  }

  bindLoading.value = true
  try {
    const patterns = bindForm.value.skillPatterns
      ? bindForm.value.skillPatterns.split(',').map(s => s.trim()).filter(Boolean)
      : undefined

    await skillRepoApi.bind({
      agentName: bindForm.value.agentName.trim(),
      repoUrl: bindForm.value.repoUrl.trim(),
      skillPatterns: patterns
    })
    ElMessage.success('绑定成功')
    bindForm.value = { agentName: '', repoUrl: '', skillPatterns: '' }
    await loadBindings()
  } catch (error) {
    console.error('绑定失败:', error)
    ElMessage.error('绑定失败')
  } finally {
    bindLoading.value = false
  }
}

// 解绑仓库
const handleUnbind = async (agentName: string) => {
  try {
    await ElMessageBox.confirm(
      `确定要解绑 Agent「${agentName}」的仓库吗？`,
      '解绑确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await skillRepoApi.unbind(agentName)
    ElMessage.success('解绑成功')
    await loadBindings()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('解绑失败:', error)
      ElMessage.error('解绑失败')
    }
  }
}

// 刷新仓库
const handleRefresh = async (agentName: string) => {
  refreshingAgent.value = agentName
  try {
    await skillRepoApi.refresh({ agentName })
    ElMessage.success('刷新成功')
    await loadBindings()
  } catch (error) {
    console.error('刷新失败:', error)
    ElMessage.error('刷新失败')
  } finally {
    refreshingAgent.value = ''
  }
}

// 查看 Skill 列表
const handleViewSkills = async (repoUrl: string) => {
  currentRepoUrl.value = repoUrl
  skillsDrawerVisible.value = true
  skillsLoading.value = true
  currentSkills.value = []
  try {
    currentSkills.value = await skillRepoApi.getSkills(repoUrl)
  } catch (error) {
    console.error('获取 Skill 列表失败:', error)
    ElMessage.error('获取 Skill 列表失败')
  } finally {
    skillsLoading.value = false
  }
}

// 抽屉打开时加载数据
watch(() => props.visible, (val) => {
  if (val) loadBindings()
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="Skill 仓库管理"
    direction="rtl"
    size="420px"
    @close="emit('update:visible', false)"
  >
    <!-- 绑定新仓库 -->
    <div class="bind-section">
      <div class="section-label">绑定新仓库</div>
      <div class="form-item">
        <input
          v-model="bindForm.agentName"
          class="form-input"
          placeholder="Agent 名称"
        />
      </div>
      <div class="form-item">
        <input
          v-model="bindForm.repoUrl"
          class="form-input"
          placeholder="仓库地址 (https://...)"
        />
      </div>
      <div class="form-item">
        <input
          v-model="bindForm.skillPatterns"
          class="form-input"
          placeholder="过滤规则（逗号分隔，留空加载全部）"
        />
      </div>
      <button
        class="bind-btn"
        :disabled="bindLoading"
        @click="handleBind"
      >
        {{ bindLoading ? '绑定中...' : '绑定' }}
      </button>
    </div>

    <!-- 已绑定列表 -->
    <div class="list-section">
      <div class="section-label">已绑定列表</div>

      <div v-if="listLoading" class="loading-text">加载中...</div>

      <div v-else-if="Object.keys(bindings).length === 0" class="empty-text">
        暂无绑定
      </div>

      <div v-else class="binding-list">
        <div
          v-for="(repoUrl, agentName) in bindings"
          :key="agentName"
          class="binding-card"
        >
          <div class="card-header">
            <span class="agent-name">{{ agentName }}</span>
          </div>
          <div class="card-body">
            <div class="repo-url" :title="repoUrl">{{ repoUrl }}</div>
          </div>
          <div class="card-actions">
            <button class="action-btn" @click="handleViewSkills(repoUrl)">查看 Skill</button>
            <button
              class="action-btn"
              :disabled="refreshingAgent === agentName"
              @click="handleRefresh(agentName)"
            >
              {{ refreshingAgent === agentName ? '刷新中...' : '刷新' }}
            </button>
            <button class="action-btn danger" @click="handleUnbind(agentName)">解绑</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Skill 列表抽屉 -->
    <el-drawer
      v-model="skillsDrawerVisible"
      title="Skill 列表"
      direction="rtl"
      size="320px"
      :append-to-body="true"
    >
      <div v-if="skillsLoading" class="loading-text">加载中...</div>
      <div v-else-if="currentSkills.length === 0" class="empty-text">
        该仓库暂无 Skill
      </div>
      <div v-else class="skill-list">
        <div v-for="skill in currentSkills" :key="skill" class="skill-item">
          {{ skill }}
        </div>
      </div>
    </el-drawer>
  </el-drawer>
</template>

<style scoped>
/* 绑定表单区 */
.bind-section {
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  margin-bottom: 20px;
}

.section-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary, #1F1F1F);
  margin-bottom: 12px;
}

.form-item {
  margin-bottom: 10px;
}

.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 8px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-primary, #1F1F1F);
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--accent-color, #667eea);
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.form-input::placeholder {
  color: #999;
}

.bind-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: var(--accent-color, #667eea);
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.bind-btn:hover {
  opacity: 0.9;
}

.bind-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 已绑定列表 */
.list-section {
  flex: 1;
}

.loading-text,
.empty-text {
  text-align: center;
  color: var(--text-secondary, #444746);
  font-size: 0.85rem;
  padding: 24px 0;
}

.binding-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.binding-card {
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 10px;
  padding: 14px;
  background: var(--bg-main, #FFFFFF);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.agent-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary, #1F1F1F);
}

.card-body {
  margin-bottom: 12px;
}

.repo-url {
  font-size: 0.8rem;
  color: var(--text-secondary, #444746);
  word-break: break-all;
  line-height: 1.4;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 5px 12px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 6px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-secondary, #444746);
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: var(--accent-color, #667eea);
  color: var(--accent-color, #667eea);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn.danger {
  color: #e74c3c;
  border-color: #e74c3c;
}

.action-btn.danger:hover {
  background: #fdf2f2;
}

/* Skill 列表 */
.skill-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skill-item {
  padding: 8px 12px;
  background: var(--bg-sidebar, #F0F4F9);
  border-radius: 6px;
  font-size: 0.85rem;
  color: var(--text-primary, #1F1F1F);
  font-family: monospace;
}
</style>
