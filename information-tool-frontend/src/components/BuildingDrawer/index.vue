<template>
  <el-drawer
    v-model="visible"
    title="楼宇详情"
    :size="800"
    :destroy-on-close="false"
    class="building-drawer"
    @closed="handleClose"
  >
    <template #header v-if="building">
      <div class="drawer-header">
        <div class="header-left">
          <el-button
            :type="isFavorite ? 'warning' : 'default'"
            :icon="isFavorite ? StarFilled : Star"
            circle
            size="small"
            @click="toggleFavorite"
            class="favorite-btn"
          />
          <div class="header-main">
            <h3 class="building-name">{{ building.name }}</h3>
            <p class="building-address">
              <el-icon><Location /></el-icon>
              {{ building.address }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <div v-if="building" class="drawer-content">
      <div class="action-section">
        <el-tooltip
          v-if="!batchInitializing"
          content="仅获取基础工商数据，缺少性别、年龄、薪资等维度分析"
          placement="top"
          effect="dark"
        >
          <el-button
            type="primary"
            size="large"
            :disabled="basicTargetCount === 0"
            @click="handleBatchInitialize"
          >
            <el-icon><Lightning /></el-icon>
            一键初始化 (预计完成时间: {{ buildingStore.getTaskTotalMinutes('basic', basicTargetCount) }} 分钟)
          </el-button>
        </el-tooltip>
        
        <el-button
          v-if="!batchInitializing"
          type="warning"
          size="large"
          :disabled="deepTargetCount === 0"
          :loading="isDeepInitializing"
          @click="handleDeepInitialize"
        >
          <el-icon><Search /></el-icon>
          深度初始化 (预计完成时间: {{ buildingStore.getTaskTotalMinutes('deep', deepTargetCount) }} 分钟)
        </el-button>
        
        <el-button
          v-else
          type="danger"
          size="large"
          @click="handleStopBatch"
        >
          <el-icon><VideoPause /></el-icon>
          停止获取
        </el-button>
        
      </div>

      <div v-if="batchInitializing" class="batch-progress-section">
        <div class="batch-progress-header">
          <div style="display: flex; align-items: center; gap: 16px;">
            <span class="batch-title">批量初始化进度</span>
            <span v-if="completedCount < totalBatchCount" class="batch-remaining-time">
              {{ estimatedRemainingText }}
            </span>
          </div>
          <span class="batch-count">{{ completedCount }}/{{ totalBatchCount }}</span>
        </div>
        <el-progress 
          :percentage="batchProgress" 
          :stroke-width="12"
          status="active"
        />
        <div class="batch-status">
          <el-tag v-if="completedCount < totalBatchCount" type="warning" effect="plain" size="small">
            初始化中...
          </el-tag>
          <el-tag v-else type="success" effect="plain" size="small">
            全部完成
          </el-tag>
        </div>
      </div>

      <div class="companies-section">
        <div class="section-title">
          <span>入驻公司列表</span>
          <el-tag type="info">共 {{ mockCompanies.length }} 家</el-tag>
        </div>

        <CompanyList
          :companies="mockCompanies"
          :building-uid="building.uid"
          :building-name="building.name"
          :scan-statuses="scanStatuses"
          @refresh="refreshBuildingData"
          @update-company="handleUpdateCompany"
          @selection-change="handleSelectionChange"
        />
      </div>
    </div>

    <el-empty v-else description="请选择楼宇查看详情" />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useEnsScanner } from '@/utils/useEnsScanner'
import { useFavoriteStore, useBuildingStore, useMapStateStore } from '@/stores'
import CompanyList from './CompanyList.vue'
import { Star, StarFilled, Location, Lightning, VideoPause, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCompaniesByBuildingUidApi } from '@/api/map'

const favoriteStore = useFavoriteStore()
const buildingStore = useBuildingStore()
const mapStateStore = useMapStateStore()

const visible = ref(false)

// 🌟 修复关键：必须在所有依赖它的 computed 和 watch 之前提前声明
const building = computed(() => buildingStore.selectedBuilding)
const isFavorite = computed(() => favoriteStore.isCurrentFavorite(building.value))

// 动态计算当前所选楼宇的进度参数（全局状态已迁移至 Pinia Store）
const batchInitializing = computed(() => building.value && !!buildingStore.batchTasks[building.value.uid]?.isInitializing);
const totalBatchCount = computed(() => building.value ? (buildingStore.batchTasks[building.value.uid]?.total || 0) : 0);
const batchCompanyNames = computed(() => building.value ? (buildingStore.batchTasks[building.value.uid]?.companyNames || []) : []);

// 独立的深度初始化 SSE 实例与状态
const isDeepInitializing = ref(false);

// 初始化ENS扫描器Hook
const { scanStatuses, startBasicScan, startDeepScan, stopDeepScan } = useEnsScanner()

// 选中的公司列表
const selectedCompanies = ref([]);

// 动态计算真实完成数（读取 SSE 全局单例中的状态，"已完成"、"失败"或"任务异常"皆视为该节点执行完毕）
const completedCount = computed(() => {
  if (batchCompanyNames.value.length === 0) return 0;
  let count = 0;
  batchCompanyNames.value.forEach(name => {
    const sseStatus = scanStatuses[name]?.status;
    if (sseStatus === '已完成' || sseStatus === '失败' || sseStatus === '任务异常') {
      count++;
    }
  });
  return count;
});

// 监听进度，当本批次全部完成时，延迟关闭进度条面板并恢复按钮
watch(completedCount, (newVal) => {
  if (batchInitializing.value && totalBatchCount.value > 0 && newVal === totalBatchCount.value) {
    const currentUid = building.value.uid;
    setTimeout(() => {
      // 仅清理当前楼宇的进度面板状态，不影响其他正在跑的楼宇
      buildingStore.removeBatchTask(currentUid);
      isDeepInitializing.value = false;
      ElMessage.success('批量获取任务已全部执行结束');
    }, 1500);
  }
});

const batchProgress = computed(() => {
  if (totalBatchCount.value === 0) return 0
  return Math.round((completedCount.value / totalBatchCount.value) * 100)
})

// 动态计算预计剩余时间文本
const estimatedRemainingText = computed(() => {
  if (!batchInitializing.value || !building.value) return '';
  const state = buildingStore.batchTasks[building.value.uid];
  if (!state) return '';

  const remainingMinutes = buildingStore.getTaskRemainingMinutes(state, completedCount.value);
  
  if (remainingMinutes <= 0) return '即将完成...';
  return `预计剩余时间: ${remainingMinutes} 分钟`;
});

// 当前楼宇的公司列表
const mockCompanies = ref([])

// 确定当前操作的数据源（如果有勾选则只操作勾选的，否则操作全部）
const currentDataSource = computed(() => 
  selectedCompanies.value.length > 0 ? selectedCompanies.value : mockCompanies.value
);

// 1. 一键初始化可执行数量 (仅限：未初始化)
const basicTargetCount = computed(() => {
  return currentDataSource.value.filter(c => c.infoStatus === '未初始化' && !c.isInitializing).length
});

// 2. 深度初始化可执行数量 (限：未初始化 或 已初始化)
const deepTargetCount = computed(() => {
  return currentDataSource.value.filter(c => 
    (c.infoStatus === '未初始化' || c.infoStatus === '已初始化') && !c.isInitializing
  ).length
});

// 监听选中楼宇变化，自动打开抽屉并加载公司数据
watch(() => buildingStore.selectedBuilding, (val) => {
  visible.value = !!val
  if (val) {
    loadMockCompanies(val)
  }
})

// 加载真实公司数据
function loadMockCompanies(buildingData) {
  if (!buildingData) return;
  const targetCompanies = buildingData.companies || [];
  mockCompanies.value = targetCompanies;
}

function handleClose() {
  buildingStore.clearSelection();
  mockCompanies.value = [];
  selectedCompanies.value = [];
}

function toggleFavorite() {
  if (building.value) {
    favoriteStore.toggleFavorite(building.value, building.value)
  }
}

// 更新楼宇状态
function updateBuildingStatus() {
  const allCompleted = mockCompanies.value.every(c => c.infoStatus === '已完善')
  const allInitializedOrCompleted = mockCompanies.value.every(
    c => c.infoStatus === '已初始化' || c.infoStatus === '已完善'
  )
  
  if (building.value) {
    const newStatus = allCompleted ? 'completed' : (allInitializedOrCompleted ? 'initialized' : building.value.status)
    buildingStore.updateBuildingStatus(building.value.uid, newStatus, mockCompanies.value)
  }
}

// 处理选中变化
function handleSelectionChange(selection) {
  selectedCompanies.value = selection;
}

// 🌟 一键初始化 (基础 ENS)
async function handleBatchInitialize() {
  if (!building.value) return;
  const targetCompanies = currentDataSource.value.filter(c => c.infoStatus === '未初始化');
  
  if (targetCompanies.length === 0) {
    ElMessage.info('所选范围中没有需要一键初始化的公司');
    return;
  }
  
  const companyNames = targetCompanies.map(c => c.companyName);
  buildingStore.setBatchTask(building.value.uid, {
    isInitializing: true,
    companyNames,
    total: companyNames.length,
    taskType: 'basic',
    buildingName: building.value.name,
    lng: building.value.lng,
    lat: building.value.lat,
  });
  
  await startBasicScan(building.value.uid, companyNames, targetCompanies);
}

// 🌟 深度初始化 (AI + ENS)
async function handleDeepInitialize() {
  if (!building.value) return;
  const targetCompanies = currentDataSource.value.filter(c => c.infoStatus === '未初始化' || c.infoStatus === '已初始化');

  if (targetCompanies.length === 0) {
    ElMessage.info('所选范围中没有需要深度初始化的公司');
    return;
  }

  isDeepInitializing.value = true;
  const companyNames = targetCompanies.map(c => c.companyName);
  buildingStore.setBatchTask(building.value.uid, {
    isInitializing: true,
    companyNames,
    total: companyNames.length,
    taskType: 'deep',
    buildingName: building.value.name,
    lng: building.value.lng,
    lat: building.value.lat,
  });

  await startDeepScan(building.value.uid, companyNames, targetCompanies);
}

// 处理单个公司更新
function handleUpdateCompany(updatedCompany) {
  const index = mockCompanies.value.findIndex(c => c.id === updatedCompany.id)
  if (index !== -1) {
    mockCompanies.value[index] = { ...updatedCompany }
    updateBuildingStatus()
  }
}

// 中止批量初始化任务
function handleStopBatch() {
  if (!building.value) return;
  const currentUid = building.value.uid;
  
  // 掐断深度初始化的独立通道
  stopDeepScan();
  isDeepInitializing.value = false;
  
  // 1. 关闭当前楼宇的批量进度条状态（全局 Store）
  buildingStore.removeBatchTask(currentUid);
  
  // 2. 🌟 修复：遍历当前楼宇的公司，清理仍在“采集中”的字典状态
  let stoppedCount = 0;
  mockCompanies.value.forEach(company => {
    const cName = company.companyName;
    // 如果发现该公司在字典中的状态仍为“采集中”，则将其删除，UI 将自动回退为“未开始”
    if (scanStatuses[cName] && scanStatuses[cName].status === '采集中') {
      delete scanStatuses[cName];
      stoppedCount++;
    }
  });
  
  // 3. 给出明确的业务提示
  ElMessage({
    message: `已中止获取，清理了 ${stoppedCount} 个排队任务。部分已在云端执行的任务将在完成后自动回传。`,
    type: 'warning',
    duration: 4000
  });
}

// 接收到子组件的刷新请求后，调用真实接口获取最新大宽表数据
async function refreshBuildingData() {
  if (!building.value) return;
  
  try {
    const dbRes = await getCompaniesByBuildingUidApi(building.value.uid);
    const dbCompanies = Array.isArray(dbRes) ? dbRes : (dbRes?.data || []);
    
    if (dbCompanies.length > 0) {
      const statusMap = { 0: '未初始化', 1: '已初始化', 2: '已深度初始化',3: '已深度初始化' };
      const cleanCompanies = dbCompanies.map(c => ({
        ...c,
        id: c.uid || c.id, 
        infoStatus: statusMap[c.infoStatus] || '未初始化'
      }));
      
      mockCompanies.value = cleanCompanies;
      buildingStore.updateBuildingStatus(building.value.uid, 'initialized', cleanCompanies);
    }
  } catch (error) {
    console.error('重新获取楼宇全量数据失败:', error);
  }
}
</script>

<style scoped>
.building-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
}

.favorite-btn {
  flex-shrink: 0;
  margin-top: 2px;
}

.header-main {
  flex: 1;
}

.building-name {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.building-address {
  margin: 0;
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.drawer-content {
  padding: 16px 0;
}

.action-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 4px;
}

.action-section .el-button {
  flex: 1;
  margin-right: 16px;
}

/* 批量进度区域 */
.batch-progress-section {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border: 1px solid #91d5ff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.batch-progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.batch-title {
  font-weight: 600;
  color: #096dd9;
  font-size: 14px;
}

.batch-count {
  font-size: 13px;
  color: #1890ff;
  font-weight: 500;
}

.batch-status {
  margin-top: 10px;
  text-align: center;
}

.batch-remaining-time {
  font-size: 13px;
  color: #e6a23c;
  font-weight: 500;
}

.companies-section {
  border-top: 1px solid #e4e7ed;
  padding-top: 16px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 500;
  color: #303133;
}
</style>
