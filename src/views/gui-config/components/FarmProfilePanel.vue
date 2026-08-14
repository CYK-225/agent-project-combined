<template>
  <div class="farm-profile-panel">
    <!-- 视图 A: 配置列表层 -->
    <div v-if="currentView === 'list'" class="profile-list-view">
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索配置名称..."
          clearable
          style="width: 100%"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="guiConfigStore.isVncActive"
          @click="openCreateDialog"
        >
          新建养号
        </el-button>
      </div>

      <div v-loading="loading" class="profile-cards">
        <el-card
          v-for="profile in filteredProfiles"
          :key="profile.profileName"
          class="profile-card"
          shadow="hover"
          @click="handleSelectProfile(profile)"
        >
          <div class="card-content">
            <div class="card-top">
              <div class="profile-name">{{ profile.profileName }}</div>
              <el-button
                type="primary"
                size="small"
                plain
                :icon="EditPen"
                @click.stop="handleMarkProfile(profile)"
              >
                标记
              </el-button>
            </div>
            <div class="profile-summary">{{ profile.summary || '暂无描述' }}</div>
          </div>
        </el-card>
        <el-empty v-if="!loading && filteredProfiles.length === 0" description="暂无配置数据，点击右上角新建养号" />
      </div>
    </div>

    <!-- 视图 B: 详情与网址层 -->
    <div v-else class="profile-detail-view">
      <div class="detail-header">
        <el-button :icon="ArrowLeft" @click="handleBackToList">返回列表</el-button>
        <div class="detail-title">{{ currentProfile?.profileName }}</div>
      </div>

      <div class="detail-actions">
        <el-button
          type="primary"
          :icon="Promotion"
          :disabled="guiConfigStore.isVncActive"
          @click="handleUpdateProfile"
        >
          更新配置
        </el-button>
      </div>

      <div class="website-list">
        <div
          v-for="website in currentProfile?.websites || []"
          :key="website.id"
          class="website-item"
        >
          <div class="website-info">
            <div class="website-url">{{ website.url }}</div>
            <el-tag :type="website.status === 1 ? 'success' : 'danger'" size="small">
              {{ website.status === 1 ? '🟢 已登录' : '🔴 未登录' }}
            </el-tag>
          </div>
        </div>
        <el-empty v-if="!currentProfile?.websites?.length" description="该配置暂无网址，可在列表中点击「标记」" />
      </div>
    </div>

    <!-- 新建养号会话对话框 -->
    <el-dialog v-model="createDialogVisible" title="新建养号配置" width="420px">
      <el-form label-width="80px">
        <el-form-item label="配置名称" required>
          <el-input
            v-model="createForm.profileName"
            placeholder="如: user_002"
            maxlength="50"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSubmit">新建并拉起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, ArrowLeft, EditPen, Promotion } from '@element-plus/icons-vue'
import { useGuiConfigStore } from '@/store/guiConfigStore'
import { getFarmProfiles, markFarmProfile } from '@/api/guiConfig'

const emit = defineEmits(['launch-env'])

const guiConfigStore = useGuiConfigStore()

// 视图状态
const currentView = ref('list') // 'list' | 'detail'
const loading = ref(false)

// 数据
const profiles = ref([])
const searchKeyword = ref('')
const currentProfile = ref(null)

// 新建养号表单
const createDialogVisible = ref(false)
const createForm = ref({ profileName: '' })

// 过滤后的配置列表
const filteredProfiles = computed(() => {
  if (!searchKeyword.value) {
    return profiles.value
  }
  const keyword = searchKeyword.value.toLowerCase()
  return profiles.value.filter(profile =>
    profile.profileName?.toLowerCase().includes(keyword) ||
    profile.summary?.toLowerCase().includes(keyword)
  )
})

// 获取配置列表（farm/profiles 统一响应结构 { code, message, data }）
const fetchProfiles = async () => {
  loading.value = true
  try {
    const res = await getFarmProfiles()
    if (res?.code === 200) {
      profiles.value = Array.isArray(res.data) ? res.data : []
    } else {
      ElMessage.error(res?.message || '获取配置列表失败')
      profiles.value = []
    }
    // 详情视图下同步刷新当前配置引用，保证标记/保存后详情即时更新
    if (currentProfile.value) {
      currentProfile.value = profiles.value.find(p => p.profileName === currentProfile.value.profileName) || null
    }
  } catch (error) {
    console.error('获取配置列表失败:', error)
    ElMessage.error('获取配置列表失败')
    profiles.value = []
  } finally {
    loading.value = false
  }
}

// 选择配置
const handleSelectProfile = (profile) => {
  currentProfile.value = profile
  currentView.value = 'detail'
}

// 返回列表
const handleBackToList = () => {
  currentView.value = 'list'
  currentProfile.value = null
}

// 更新已有配置（同名 profileName 复用宿主机目录，create 即为更新）
const handleUpdateProfile = () => {
  if (!currentProfile.value || guiConfigStore.isVncActive) return

  ElMessageBox.confirm(
    `将以「${currentProfile.value.profileName}」创建更新会话：复用该配置目录，系统自动打开百度，VNC 中操作实时落盘。\n确认继续？`,
    '更新配置',
    {
      confirmButtonText: '确认更新',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    emit('launch-env', { profileName: currentProfile.value.profileName })
  }).catch(() => {
    // 用户取消操作，静默处理
  })
}

// ========== 新建养号（废弃"选网址→拉起→确认登录"旧流程，直接创建会话） ==========

const openCreateDialog = () => {
  createForm.value = { profileName: '' }
  createDialogVisible.value = true
}

const handleCreateSubmit = () => {
  const profileName = createForm.value.profileName.trim()
  if (!profileName) {
    ElMessage.warning('请输入配置名称')
    return
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(profileName)) {
    ElMessage.warning('配置名称只能包含字母、数字、下划线和中划线')
    return
  }
  if (profiles.value.some(p => p.profileName === profileName)) {
    ElMessage.warning('该配置名称已存在，如需更新请进入配置详情点击「更新配置」')
    return
  }

  // VNC 端口由后端自动分配（端口资源有限），前端不传
  createDialogVisible.value = false
  emit('launch-env', { profileName })
}

// ========== 配置标记（独立入口，与养号会话无关联） ==========

/**
 * 打开标记网址输入框
 * @param {string} profileName - 配置名
 */
const promptMark = (profileName) => {
  ElMessageBox.prompt(
    '请输入该配置已登录的网址 (如: https://www.zhipin.com/)',
    `标记配置: ${profileName}`,
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^https?:\/\/.+/,
      inputErrorMessage: '请输入以 http:// 或 https:// 开头的合法网址'
    }
  ).then(async ({ value }) => {
    try {
      const res = await markFarmProfile({
        profileName,
        websiteUrl: value
      })
      if (res?.code === 200) {
        ElMessage.success(res.data?.message || '配置标记成功')
        await fetchProfiles()
      } else {
        ElMessage.error(res?.message || '配置标记失败')
      }
    } catch (error) {
      console.error('配置标记失败:', error)
      ElMessage.error(`配置标记失败: ${error.message || '未知错误'}`)
    }
  }).catch(() => {
    // 用户取消操作，静默处理
  })
}

const handleMarkProfile = (profile) => {
  promptMark(profile.profileName)
}

/**
 * 保存成功后引导标记已登录网址（由 VNC 工作区保存成功后触发）
 * @param {string} profileName - 配置名
 */
const openMarkDialog = (profileName) => {
  ElMessageBox.confirm(
    '配置已保存。是否标记本次会话中已登录的网址？\n（未标记的网址不会被任务系统视为已登录可用）',
    '标记已登录网址',
    {
      confirmButtonText: '去标记',
      cancelButtonText: '暂不',
      type: 'info'
    }
  ).then(() => {
    promptMark(profileName)
  }).catch(() => {
    // 用户暂不标记，静默处理
  })
}

// 刷新列表（供父组件调用）
const refreshList = () => {
  fetchProfiles()
}

// 暴露方法给父组件
defineExpose({
  refreshList,
  openMarkDialog
})

onMounted(() => {
  fetchProfiles()
})
</script>

<style scoped>
.farm-profile-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  border-radius: 8px;
  overflow: hidden;
}

/* 视图 A: 配置列表层 */
.profile-list-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.header-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.profile-cards {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.profile-card {
  cursor: pointer;
  transition: all 0.3s;
}

.profile-card:hover {
  transform: translateY(-2px);
}

.card-content {
  padding: 8px 0;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.profile-name {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-summary {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 视图 B: 详情与网址层 */
.profile-detail-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.detail-actions {
  margin-bottom: 16px;
}

.website-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.website-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-radius: 6px;
  transition: all 0.3s;
}

.website-item:hover {
  background-color: #ecf5ff;
}

.website-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.website-url {
  flex: 1;
  font-size: 14px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
