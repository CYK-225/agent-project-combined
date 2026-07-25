<template>
  <div class="sidebar-container" :class="{ collapsed }">
    <!-- 侧边栏内容 -->
    <div class="sidebar-content">
      <div class="sidebar-header">
        <el-icon size="24"><OfficeBuilding /></el-icon>
        <span class="title">入驻点位工具</span>
      </div>

      <div class="lists-container">
        <!-- 收藏列表 -->
        <div class="favorites-section" :class="{ 'is-expanded': favoritesExpanded }">
          <div class="section-header" @click="toggleFavorites">
            <el-icon><Star /></el-icon>
            <span>我的收藏</span>
            <el-icon class="expand-icon" :class="{ expanded: favoritesExpanded }">
              <ArrowDown />
            </el-icon>
          </div>

          <el-collapse-transition>
            <div v-show="favoritesExpanded" class="favorites-list scrollable-list">
              <el-empty v-if="favorites.length === 0" description="暂无收藏" :image-size="60" />
              
              <div
                v-for="item in favorites"
                :key="item.uid"
                class="favorite-item"
                @click="handleFavoriteClick(item)"
              >
                <div class="favorite-name">{{ item.name }}</div>
                <div class="favorite-address">{{ item.address }}</div>
                <el-icon class="remove-btn" @click.stop="removeFavorite(item.uid)"><Close /></el-icon>
              </div>
            </div>
          </el-collapse-transition>
        </div>

        <!-- 任务入口卡片 -->
        <div class="task-overview-section">
          <div class="task-entry-card" @click="openTaskDialog('basic')">
            <div class="card-left">
              <el-icon class="icon-basic"><Lightning /></el-icon>
              <span>一键初始化任务</span>
            </div>
            <el-badge :value="basicTasks.length" :hidden="basicTasks.length === 0" class="task-badge" />
          </div>

          <div class="task-entry-card deep" @click="openTaskDialog('deep')">
            <div class="card-left">
              <el-icon class="icon-deep"><Search /></el-icon>
              <span>深度初始化任务</span>
            </div>
            <el-badge :value="deepTasks.length" :hidden="deepTasks.length === 0" type="warning" class="task-badge" />
          </div>
        </div>
      </div>

      <!-- 统计信息 -->
      <div class="stats-section">
        <div class="stat-item">
          <span class="stat-label">楼宇总数</span>
          <span class="stat-value">{{ buildingCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">收藏数量</span>
          <span class="stat-value">{{ favorites.length }}</span>
        </div>
      </div>
    </div>

    <!-- 折叠按钮 - 放在侧边栏右侧 -->
    <div class="toggle-btn" @click="toggleSidebar">
      <el-icon>
        <ArrowLeft v-if="!collapsed" />
        <ArrowRight v-else />
      </el-icon>
    </div>

    <!-- 任务详情弹窗 -->
    <el-dialog
      v-model="taskDialogVisible"
      :title="taskDialogTitle"
      width="400px"
      append-to-body
      destroy-on-close
    >
      <div class="dialog-task-list">
        <el-empty v-if="currentDialogTasks.length === 0" description="暂无进行中的任务" />

        <div
          v-for="task in currentDialogTasks"
          :key="task.uid"
          class="dialog-task-item"
          @click="handleTaskClick(task)"
        >
          <div class="task-header">
            <span class="task-building-name">{{ task.buildingName }}</span>
            <span class="task-progress">{{ getTaskCompletedCount(task) }}/{{ task.total }}</span>
          </div>
          <div class="task-footer">
            <span class="task-time">预计剩余时间: {{ calculateRemainingTime(task) }} 分钟</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFavoriteStore, useBuildingStore, useMapStateStore } from '@/stores'
import { useEnsScanner } from '@/utils/useEnsScanner'
import { Lightning, Search, OfficeBuilding, Star, ArrowDown, ArrowLeft, ArrowRight, Close } from '@element-plus/icons-vue'
import { ElLoading } from 'element-plus'

const favoriteStore = useFavoriteStore()
const buildingStore = useBuildingStore()
const mapStateStore = useMapStateStore()

const { scanStatuses } = useEnsScanner()

const collapsed = ref(false)
const favoritesExpanded = ref(true)

// 暴露 collapsed 状态给父组件
defineExpose({ collapsed })

const favorites = computed(() => favoriteStore.favorites)
const buildingCount = computed(() => buildingStore.buildingCount)
const basicTasks = computed(() => buildingStore.basicTasks)
const deepTasks = computed(() => buildingStore.deepTasks)

// 弹窗相关状态
const taskDialogVisible = ref(false)
const currentTaskType = ref('basic')

const taskDialogTitle = computed(() => currentTaskType.value === 'basic' ? '我的一键初始化任务' : '我的深度初始化任务')
const currentDialogTasks = computed(() => currentTaskType.value === 'basic' ? basicTasks.value : deepTasks.value)

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function toggleFavorites() {
  favoritesExpanded.value = !favoritesExpanded.value
}

function openTaskDialog(type) {
  currentTaskType.value = type
  taskDialogVisible.value = true
}

function getTaskCompletedCount(task) {
  if (!task.companyNames) return 0
  return task.companyNames.filter(name => {
    const s = scanStatuses[name]?.status
    return s === '已完成' || s === '成功' || s === '失败' || s === '任务异常'
  }).length
}

function calculateRemainingTime(task) {
  const completedCount = getTaskCompletedCount(task)
  return buildingStore.getTaskRemainingMinutes(task, completedCount)
}

async function handleFavoriteClick(item) {
  // 1. 地图飞跃到目标位置
  const map = mapStateStore.mapInstance
  if (map) {
    const BMapGL = window.BMapGL
    const point = new BMapGL.Point(item.lng, item.lat)
    map.centerAndZoom(point, 17)
  }

  // 2. 缓存兜底机制：尝试从 poiMap 获取楼宇
  let building = buildingStore.poiMap.get(item.uid)

  if (!building) {
    // 页面刷新后首次点击，poiMap 中无缓存，构造基础楼宇对象强行注入 Store
    building = {
      uid: item.uid,
      name: item.name,
      address: item.address,
      lng: item.lng,
      lat: item.lat,
      status: 'uninitialized',
      companies: [],
    }
    buildingStore.addOrUpdateBuilding(building)
  }

  // 3. 选中楼宇并打开抽屉
  buildingStore.selectBuilding(building)
  mapStateStore.setDrawerVisible(true)

  // 4. 三级优先级加载企业数据（带 loading）
  const loadingInstance = ElLoading.service({ text: '正在加载楼宇数据...', background: 'rgba(0, 0, 0, 0.3)' })
  try {
    await buildingStore.loadBuildingCompaniesWithFallback(
      `${building.lat},${building.lng}`,
      building.uid,
      (text) => loadingInstance.setText(text)
    )
  } catch (error) {
    console.error('收藏项点击后拉取企业列表失败:', error)
  } finally {
    loadingInstance.close()
  }
}

function removeFavorite(uid) {
  favoriteStore.removeFavorite(uid)
}

async function handleTaskClick(task) {
  // 1. 关闭弹窗
  taskDialogVisible.value = false

  // 2. 飞跃地图与处理楼宇数据 (复用 handleFavoriteClick 的逻辑核心)
  const map = mapStateStore.mapInstance
  if (map) {
    const point = new window.BMapGL.Point(task.lng, task.lat)
    map.centerAndZoom(point, 17)
  }

  let building = buildingStore.poiMap.get(task.uid)
  if (!building) {
    building = {
      uid: task.uid,
      name: task.buildingName,
      lng: task.lng,
      lat: task.lat,
      status: 'uninitialized',
      companies: [],
    }
    buildingStore.addOrUpdateBuilding(building)
  }

  buildingStore.selectBuilding(building)
  mapStateStore.setDrawerVisible(true)

  const loadingInstance = ElLoading.service({ text: '正在加载楼宇数据...', background: 'rgba(0, 0, 0, 0.3)' })
  try {
    await buildingStore.loadBuildingCompaniesWithFallback(
      `${building.lat},${building.lng}`,
      building.uid,
      (text) => loadingInstance.setText(text)
    )
  } catch (error) {
    console.error('任务项点击后拉取企业列表失败:', error)
  } finally {
    loadingInstance.close()
  }
}
</script>

<style scoped>
.sidebar-container {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  z-index: 200;
  display: flex;
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-container.collapsed {
  transform: translateX(-260px);
}

.sidebar-content {
  width: 260px;
  height: 100%;
  background: linear-gradient(180deg, #ffffff 0%, #f8f9fa 100%);
  box-shadow: 4px 0 16px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* 折叠按钮 - 放在侧边栏右侧 */
.toggle-btn {
  position: absolute;
  right: -24px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 64px;
  background: #fff;
  border-radius: 0 8px 8px 0;
  box-shadow: 4px 2px 12px rgba(0, 0, 0, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  z-index: 199;
}

.toggle-btn:hover {
  background: #409eff;
  color: #fff;
  width: 28px;
  right: -28px;
}

.sidebar-container.collapsed .toggle-btn {
  right: -32px;
  box-shadow: 2px 2px 8px rgba(0, 0, 0, 0.15);
}

.sidebar-container.collapsed .toggle-btn:hover {
  right: -36px;
}

.sidebar-header {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #e8e8e8;
  gap: 12px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
  flex-shrink: 0;
}

.sidebar-header .el-icon {
  font-size: 24px;
}

.sidebar-header .title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.lists-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.favorites-section {
  /* 移除死板的 flex: 1，使得折叠时能收缩到刚好包裹 Header */
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: flex 0.3s ease; /* 增加过渡让高度变化更平滑 */
}

/* 仅在展开状态下，参与瓜分剩余的垂直高度 */
.favorites-section.is-expanded {
  flex: 1;
  overflow: hidden;
}

.section-header {
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  cursor: pointer;
  gap: 10px;
  color: #303133;
  font-weight: 500;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  transition: all 0.25s ease;
  flex-shrink: 0;
}

.section-header:hover {
  background: #f5f7fa;
  padding-left: 24px;
}

.section-header .el-icon:first-child {
  color: #f7ba2a;
  font-size: 18px;
}

.expand-icon {
  margin-left: auto;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: #909399;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.favorites-list {
  flex: 1;
  overflow-y: auto;
  min-height: 100px;
  padding: 12px;
  background: #fafbfc;
}

.favorite-item {
  position: relative;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.favorite-item:hover {
  background: #fff;
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.favorite-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 6px;
  padding-right: 24px;
  font-size: 14px;
}

.favorite-address {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.remove-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px;
  color: #c0c4cc;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s ease;
  border-radius: 4px;
}

.favorite-item:hover .remove-btn {
  opacity: 1;
}

.remove-btn:hover {
  color: #f56c6c;
  background: #fef0f0;
}

.stats-section {
  padding: 16px 20px;
  border-top: 1px solid #e8e8e8;
  background: #fff;
  flex-shrink: 0;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.stat-item:hover {
  background: #e6f2ff;
}

.stat-item:last-child {
  margin-bottom: 0;
}

.stat-label {
  color: #606266;
  font-size: 13px;
}

.stat-value {
  color: #409eff;
  font-weight: 600;
  font-size: 15px;
}

/* 滚动条样式 */
.favorites-list::-webkit-scrollbar,
.scrollable-list::-webkit-scrollbar {
  width: 4px;
}

.favorites-list::-webkit-scrollbar-track,
.scrollable-list::-webkit-scrollbar-track {
  background: transparent;
}

.favorites-list::-webkit-scrollbar-thumb,
.scrollable-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

.favorites-list::-webkit-scrollbar-thumb:hover,
.scrollable-list::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

/* 侧边栏任务入口卡片区域 */
.task-overview-section {
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.task-entry-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.25s ease;
  border: 1px solid transparent;
}

.task-entry-card:hover {
  background: #ecf5ff;
  border-color: #d9ecff;
}

.task-entry-card.deep:hover {
  background: #fdf6ec;
  border-color: #faecd8;
}

.card-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.icon-basic { color: #409eff; font-size: 16px; }
.icon-deep { color: #e6a23c; font-size: 16px; }

.task-badge :deep(.el-badge__content) {
  transform: translateY(-50%) translateX(100%);
}

/* 弹窗内部列表样式 */
.dialog-task-list {
  max-height: 50vh;
  overflow-y: auto;
  padding: 0 4px;
}

.dialog-task-item {
  padding: 14px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.dialog-task-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
  background: #fff;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.task-building-name {
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.task-progress {
  font-size: 14px;
  color: #409eff;
  font-weight: 500;
}

.task-footer {
  font-size: 13px;
  color: #e6a23c;
}
</style>
