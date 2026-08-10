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
        <el-button type="primary" :icon="Plus" @click="handleAddProfile">
          新增配置
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
            <div class="profile-name">{{ profile.profileName }}</div>
            <div class="profile-summary">{{ profile.summary || '暂无描述' }}</div>
          </div>
        </el-card>
        <el-empty v-if="!loading && filteredProfiles.length === 0" description="暂无配置数据" />
      </div>
    </div>

    <!-- 视图 B: 详情与网址层 -->
    <div v-else class="profile-detail-view">
      <div class="detail-header">
        <el-button :icon="ArrowLeft" @click="handleBackToList">返回列表</el-button>
        <div class="detail-title">当前环境: {{ currentProfile?.profileName }}</div>
      </div>

      <div class="detail-actions">
        <el-button type="primary" :icon="Plus" @click="handleAddWebsite">
          添加目标网址
        </el-button>
      </div>

      <div class="website-list">
        <div
          v-for="(website, index) in currentProfile?.websites || []"
          :key="index"
          class="website-item"
        >
          <div class="website-info">
            <div class="website-url">{{ website.url }}</div>
            <el-tag :type="website.status === 1 ? 'success' : 'danger'" size="small">
              {{ website.status === 1 ? '🟢 已登录' : '🔴 未登录' }}
            </el-tag>
          </div>
          <el-button
            type="primary"
            size="small"
            :icon="Promotion"
            :disabled="guiConfigStore.isVncActive || !currentProfile"
            @click="handleLaunchEnv(currentProfile.profileName, website.url)"
          >
            拉起环境
          </el-button>
        </div>
        <el-empty v-if="!currentProfile?.websites?.length" description="暂无网址数据" />
      </div>
    </div>

    <!-- 激活环境时的底部操作区 -->
    <div class="active-env-footer" v-if="guiConfigStore.isVncActive">
      <el-button type="success" style="width: 100%; margin-bottom: 12px;" @click="emit('confirm-login')">
        <el-icon><CircleCheck /></el-icon>我已完成登录，保存状态
      </el-button>
      <el-button type="danger" style="width: 100%; margin: 0;" @click="emit('destroy-env')">
        <el-icon><CircleClose /></el-icon>销毁环境/取消
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, ArrowLeft, Promotion, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useGuiConfigStore } from '@/store/guiConfigStore'
import { getFarmProfiles } from '@/api/guiConfig'

const emit = defineEmits(['launch-env', 'confirm-login', 'destroy-env'])

const guiConfigStore = useGuiConfigStore()

// 视图状态
const currentView = ref('list') // 'list' | 'detail'
const loading = ref(false)

// 数据
const profiles = ref([])
const searchKeyword = ref('')
const currentProfile = ref(null)

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

// 获取配置列表
const fetchProfiles = async () => {
  loading.value = true
  try {
    const data = await getFarmProfiles()
    profiles.value = Array.isArray(data) ? data : []
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

// 新增配置（利用前端本地预设 + 闭环持久化机制）
const handleAddProfile = () => {
  ElMessageBox.prompt('请输入新的配置名称 (如: user_002)', '新增配置', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_-]+$/,
    inputErrorMessage: '配置名称只能包含字母、数字、下划线和中划线'
  }).then(({ value }) => {
    // 判重校验
    if (profiles.value.some(p => p.profileName === value)) {
      ElMessage.warning('该配置名称已存在于列表中')
      return
    }
    // 本地预设数据，压入数组首部
    profiles.value.unshift({
      profileName: value,
      summary: '新配置，等待拉起环境并登录',
      websites: []
    })
    ElMessage.success('配置预设成功，请点击卡片进入并添加目标网址')
  }).catch(() => {
    // 用户取消操作，静默处理
  })
}

// 添加目标网址
const handleAddWebsite = () => {
  if (!currentProfile.value) return

  ElMessageBox.prompt('请输入目标网址 (如: https://weibo.com)', '添加网址', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^https?:\/\/.+/,
    inputErrorMessage: '请输入以 http:// 或 https:// 开头的合法网址'
  }).then(({ value }) => {
    // 确保 websites 数组存在
    if (!currentProfile.value.websites) {
      currentProfile.value.websites = []
    }
    // 判重校验
    if (currentProfile.value.websites.some(w => w.url === value)) {
      ElMessage.warning('该网址已存在于当前配置中')
      return
    }
    // 本地预设网址，状态默认为未登录 (0)
    currentProfile.value.websites.unshift({
      url: value,
      status: 0
    })
    ElMessage.success('网址添加成功，请点击拉起环境进行初始化登录')
  }).catch(() => {
    // 用户取消操作，静默处理
  })
}

// 拉起环境
const handleLaunchEnv = (profileName, targetUrl) => {
  emit('launch-env', { profileName, targetUrl })
}

// 刷新列表（供父组件调用）
const refreshList = () => {
  fetchProfiles()
}

// 暴露方法给父组件
defineExpose({
  refreshList
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

.profile-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
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

/* 激活环境时的底部操作区 */
.active-env-footer {
  padding: 16px;
  border-top: 1px solid #ebeef5;
  background-color: #fafafa;
  margin-top: auto; /* 利用 flex 自动推到最底部 */
}
</style>
