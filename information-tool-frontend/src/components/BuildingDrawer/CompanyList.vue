<template>
  <div class="company-list">
    <!-- 表格 -->
    <el-table
      :data="sortedCompanies"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="companyName" label="公司名称" min-width="200" show-overflow-tooltip />

      <el-table-column prop="infoStatus" label="信息状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.infoStatus)">
            {{ row.infoStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="scanStatus" label="采集状态" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-if="scanStatuses[row.companyName]"
            :type="getScanStatusType(scanStatuses[row.companyName].status)"
            :effect="scanStatuses[row.companyName].status === '采集中' ? 'dark' : 'light'"
          >
            <el-icon v-if="scanStatuses[row.companyName].status === '采集中'" class="is-loading">
              <Loading />
            </el-icon>
            {{ scanStatuses[row.companyName].status }}
          </el-tag>
          <span v-else>未开始</span>
          
          <!-- 错误提示 -->
          <el-popover
            v-if="scanStatuses[row.companyName] && scanStatuses[row.companyName].error"
            placement="top"
            :title="'错误信息'"
            :width="300"
            trigger="hover"
          >
            <template #reference>
              <el-button v-if="scanStatuses[row.companyName].status === '失败' || scanStatuses[row.companyName].status === '任务异常'" type="danger" icon="Warning" circle size="small" style="margin-left: 8px;" />
            </template>
            <div>{{ scanStatuses[row.companyName].error }}</div>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            size="small"
            @click="handleEdit(row)"
          >
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看/编辑对话框 -->
    <CompanyEditDialog
      v-model:visible="dialogVisible"
      :company="selectedCompany"
      :building-uid="buildingUid"
      :building-name="buildingName"
      :mode="dialogMode"
      @save="handleSave"
      @confirm-complete="handleConfirmComplete"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { View, Edit } from '@element-plus/icons-vue'
import { Loading } from '@element-plus/icons-vue'
import CompanyEditDialog from './CompanyEditDialog.vue'
import { ElMessage, ElPopover } from 'element-plus'
import { useBuildingStore } from '@/stores'

// 扫描配置名常量（可从环境变量读取）
const SCAN_CONFIG_NAME = import.meta.env.VITE_SCAN_CONFIG_NAME || '13145739225'

const props = defineProps({
  companies: {
    type: Array,
    default: () => []
  },
  buildingUid: {
    type: String,
    required: true
  },
  buildingName: {
    type: String,
    default: ''
  },
  scanStatuses: {
    type: Object,
    default: () => ({})
  }
})

// 🌟 新增：基于多维权重的动态排序列表
const sortedCompanies = computed(() => {
  // 避免直接修改原数组，先浅拷贝
  const list = [...props.companies];
  
  return list.sort((a, b) => {
    // --- 规则 1：采集状态排序 (SSE 实时状态) ---
    const statusA = props.scanStatuses[a.companyName]?.status;
    const statusB = props.scanStatuses[b.companyName]?.status;
    
    // 判定是否为"已完成"或"成功" (权重 1，其余为 0)
    const isCompletedA = (statusA === '已完成' || statusA === '成功') ? 1 : 0;
    const isCompletedB = (statusB === '已完成' || statusB === '成功') ? 1 : 0;

    // 如果采集状态权重不同，权重高的（1）排在前面
    if (isCompletedA !== isCompletedB) {
      return isCompletedB - isCompletedA;
    }

    // --- 规则 2：信息状态排序 (数据库静态状态) ---
    const infoWeight = {
      '已完善': 4,
      '已深度初始化': 3,
      '已初始化': 2,
      '未初始化': 1
    };
    
    // 获取权重，未匹配到的默认为 0
    const weightA = infoWeight[a.infoStatus] || 0;
    const weightB = infoWeight[b.infoStatus] || 0;

    // 权重高的排前面
    return weightB - weightA;
  });
});

const emit = defineEmits(['refresh', 'update-company', 'selection-change'])

const buildingStore = useBuildingStore()
const dialogVisible = ref(false)
const selectedCompany = ref(null)
const dialogMode = ref('view') // 'view' 或 'edit'
const selectedRows = ref([]) // 选中的行

// 监听扫描状态变化，状态达成后通知父组件请求最新全量数据
watch(
  () => props.scanStatuses,
  (newStatuses) => {
    let shouldRefresh = false; // 批处理刷新标记，防止短时间发出大量网络请求
    
    // 遍历所有状态更新
    Object.keys(newStatuses).forEach(companyName => {
      try {
        const statusInfo = newStatuses[companyName];
        if (statusInfo.status === '已完成') {
          // 找到对应的企业
          const companyIndex = props.companies.findIndex(c => c.companyName === companyName);
          if (companyIndex !== -1) {
            const company = props.companies[companyIndex];
            
            // 【核心逻辑】：如果发现本地状态还不是'已完善'，说明是刚收到的完成事件
            if (company.infoStatus !== '已完善') {
              // 1. 乐观更新本地 UI 状态，让表格标签先变绿
              company.infoStatus = '已完善';
              // 2. 标记需要查库刷新宽表数据
              shouldRefresh = true;
            }
          }
        }
      } catch (err) {
        console.error(`[状态处理错误] 处理公司 ${companyName} 时发生异常:`, err);
      }
    });
    
    // 只要有任何一家公司刚完成采集，就通知父组件重新去查全量宽表数据
    if (shouldRefresh) {
      emit('refresh');
    }
  },
  { deep: true }
);

// 获取扫描状态标签类型
function getScanStatusType(status) {
  const typeMap = {
    '采集中': 'primary',   // 对应 started
    '已完成': 'success',   // 对应 completed
    '失败': 'danger',      // 对应 failure
    '任务异常': 'danger',  // 对应 task exception
    '等待下发': 'info',
    '未开始': 'info'
  }
  return typeMap[status] || 'info'
}

// 获取状态标签类型
function getStatusType(status) {
  const typeMap = {
    '未初始化': 'info',
    '已初始化': 'warning',
    '已深度初始化' : 'warning',
    '已完善': 'success'
  }
  return typeMap[status] || 'info'
}

// 选中行变化处理
function handleSelectionChange(selection) {
  selectedRows.value = selection
  emit('selection-change', selection)
}

// 查看公司详情
function handleEdit(company) {
  selectedCompany.value = { ...company }
  dialogMode.value = 'view' // [修改] 默认进入纯展示模式
  dialogVisible.value = true
}

// 保存编辑（不修改状态）
function handleSave(updatedData) {
  emit('update-company', { ...updatedData })
  ElMessage.success('保存成功')
}

// 确认完善（修改状态为已完善）
function handleConfirmComplete(updatedData) {
  const completedCompany = {
    ...updatedData,
    infoStatus: '已完善',
    initProgress: 100,
    initStatusText: '已完成',
    isInitializing: false
  }
  emit('update-company', completedCompany)
  ElMessage.success('确认完善成功')
}
</script>

<style scoped>
.company-list {
  padding: 16px;
}

.el-table {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
</style>
