import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { 
  searchBuildingsApi, 
  searchCompanyListApi, 
  getCompaniesByBuildingUidApi 
} from '@/api/map'
import { useMapStateStore } from './mapState'

/**
 * 数据清洗拦截器：将对象中的 '-' 替换为指定提示语
 * @param {Object} companyObj - 企业对象
 * @returns {Object} 清洗后的企业对象
 */
export function formatCompanyData(companyObj) {
  if (!companyObj) return companyObj;
  const formatted = { ...companyObj };
  
  for (const key in formatted) {
    if (formatted[key] === '-') {
      formatted[key] = '未获取到该数据';
    }
  }
  return formatted;
}

/**
 * 批量清洗公司数据
 * @param {Array} list - 公司列表
 * @returns {Array} 清洗后的列表
 */
export function cleanCompanies(list) {
  const statusMap = { 
    0: '未初始化', 
    1: '已初始化', 
    2: '已深度初始化', 
    3: '已完善' 
  };
  
  return Array.isArray(list) ? list.map(c => {
    // 1. 先进行空数据转义（将 '-' 替换为 '未获取到该数据'）
    const formattedC = formatCompanyData(c);
    // 2. 再追加前端需要的状态映射
    return {
      ...formattedC,
      id: formattedC.uid || formattedC.id,
      infoStatus: statusMap[formattedC.infoStatus] || '未初始化'
    };
  }) : [];
}

/**
 * 楼宇数据管理 - 管理核心业务实体（楼宇和企业数据）
 * 负责发起查库和爬虫的 API 请求
 */
export const useBuildingStore = defineStore('building', () => {
  const mapState = useMapStateStore() // 跨 Store 依赖

  // ========== State ==========
  
  // POI状态机：使用 Map 数据结构确保去重，key 为楼宇 uid
  const poiMap = ref(new Map())
  
  // 当前选中的楼宇
  const selectedBuilding = ref(null)

  // 加载状态
  const initializing = ref(false)
  const loadingBuildings = ref(new Set())

  // 批量初始化任务映射表：key 为楼宇 uid，支持全局追踪
  const batchTasks = ref({})

  // ========== Getters ==========
  
  // 获取所有楼宇列表（从 Map 转换为数组）
  const buildings = computed(() => Array.from(poiMap.value.values()))
  
  // 楼宇数量
  const buildingCount = computed(() => poiMap.value.size)
  
  // 获取指定楼宇的加载状态
  const isBuildingLoading = (uid) => loadingBuildings.value.has(uid)

  // 一键初始化任务列表
  const basicTasks = computed(() =>
    Object.values(batchTasks.value).filter(t => t.taskType === 'basic')
  )

  // 深度初始化任务列表
  const deepTasks = computed(() =>
    Object.values(batchTasks.value).filter(t => t.taskType === 'deep')
  )

  // ========== Time Calculation (时间预估统一定义) ==========

  // 核心配置：各类型任务的单家企业耗时（分钟）
  const TASK_TIME_CONFIG = {
    basic: 5, // 一键初始化 3分钟/家
    deep: 10   // 深度初始化 8分钟/家
  }

  /**
   * 获取某类任务的总预估耗时
   * @param {string} taskType - 任务类型 ('basic' | 'deep')
   * @param {number} totalCount - 目标总数
   * @returns {number} 预估总分钟数
   */
  function getTaskTotalMinutes(taskType, totalCount) {
    const timePerTask = TASK_TIME_CONFIG[taskType] || 0
    return totalCount * timePerTask
  }

  /**
   * 获取具体执行中任务的预计剩余时间
   * @param {Object} task - 任务对象 (batchTasks 中的某一项)
   * @param {number} completedCount - 已完成的数量
   * @returns {number} 预估剩余分钟数
   */
  function getTaskRemainingMinutes(task, completedCount) {
    if (!task) return 0
    const remaining = task.total - completedCount
    if (remaining <= 0) return 0
    
    const timePerTask = TASK_TIME_CONFIG[task.taskType] || 0
    return remaining * timePerTask
  }

  // ========== Actions ==========
  
  /**
   * 添加/更新楼宇（自动去重）
   * @param {Object} building - 楼宇数据对象
   */
  function addOrUpdateBuilding(building) {
    if (!building || !building.uid) {
      console.warn('Invalid building data:', building)
      return
    }
    
    // 如果已存在，合并数据；否则新增
    const existing = poiMap.value.get(building.uid)
    if (existing) {
      poiMap.value.set(building.uid, { ...existing, ...building })
    } else {
      poiMap.value.set(building.uid, {
        ...building,
        // 默认状态：未初始化
        status: building.status || 'uninitialized', // uninitialized | initialized | completed
        companies: building.companies || [],
        initializedAt: null,
        completedAt: null,
      })
    }
  }
  
  /**
   * 批量添加楼宇
   * @param {Array} buildings - 楼宇数据数组
   */
  function batchAddBuildings(buildings) {
    if (!Array.isArray(buildings)) return
    buildings.forEach(building => addOrUpdateBuilding(building))
  }
  
  /**
   * 移除楼宇
   * @param {string} uid - 楼宇唯一标识
   */
  function removeBuilding(uid) {
    poiMap.value.delete(uid)
  }
  
  /**
   * 清空所有楼宇
   */
  function clearBuildings() {
    poiMap.value.clear()
  }
  
  /**
   * 设置当前选中楼宇
   * @param {Object} building - 楼宇数据
   */
  function selectBuilding(building) {
    selectedBuilding.value = building
  }
  
  /**
   * 清除选中状态
   */
  function clearSelection() {
    selectedBuilding.value = null
  }
  
  /**
   * 更新楼宇初始化状态
   * @param {string} uid - 楼宇uid
   * @param {string} status - 新状态
   * @param {Array} companies - 公司列表（初始化时）
   */
  function updateBuildingStatus(uid, status, companies = null) {
    const building = poiMap.value.get(uid)
    if (building) {
      building.status = status
      if (status === 'initialized') {
        building.initializedAt = Date.now()
        if (companies) building.companies = companies
      } else if (status === 'completed') {
        building.completedAt = Date.now()
      }
      poiMap.value.set(uid, { ...building })
    }
  }
  
  /**
   * 更新公司数据
   * @param {string} buildingUid - 楼宇uid
   * @param {string} companyId - 公司id
   * @param {Object} data - 更新数据
   */
  function updateCompany(buildingUid, companyId, data) {
    const building = poiMap.value.get(buildingUid)
    if (building && building.companies) {
      const companyIndex = building.companies.findIndex(c => (c.uid || c.id) === companyId)
      if (companyIndex > -1) {
        building.companies[companyIndex] = {
          ...building.companies[companyIndex],
          ...data,
          updatedAt: Date.now(),
        }
        poiMap.value.set(buildingUid, { ...building })
      }
    }
  }
  
  /**
   * 设置楼宇加载状态
   * @param {string} uid - 楼宇uid
   * @param {boolean} loading - 加载状态
   */
  function setBuildingLoading(uid, loading) {
    if (loading) {
      loadingBuildings.value.add(uid)
    } else {
      loadingBuildings.value.delete(uid)
    }
  }
  
  /**
   * 设置全局初始化状态
   */
  function setInitializing(value) {
    initializing.value = value
  }

  /**
   * 设置/更新批量任务
   * @param {string} uid - 楼宇唯一标识
   * @param {Object} taskInfo - 任务信息
   */
  function setBatchTask(uid, taskInfo) {
    batchTasks.value[uid] = { ...taskInfo, uid }
  }

  /**
   * 移除指定批量任务
   * @param {string} uid - 楼宇唯一标识
   */
  function removeBatchTask(uid) {
    delete batchTasks.value[uid]
  }

  /**
   * 清空所有批量任务
   */
  function clearBatchTasks() {
    batchTasks.value = {}
  }

  /**
   * 获取楼宇列表并复用现有逻辑写入 Map
   * 实现两步逻辑：
   * 第一步：调用接口1获取指定范围内的写字楼列表，写入store并设为"未初始化"
   * 第二步：提取所有楼宇UID，并发调用接口4查询业务数据库，更新状态
   */
  async function fetchBuildings(searchParams) {
    try {
      // res 现在直接就是后端返回的百度搜索结果对象
      const res = await searchBuildingsApi(searchParams)
      
      // 修复判断条件：检查百度搜索结果对象是否有results属性
      if (res && res.results && Array.isArray(res.results)) {
        const buildingsWithStatus = res.results.map(building => ({
          ...building,
          status: 'uninitialized',
          companies: []
        }))
        
        batchAddBuildings(buildingsWithStatus)
        
        const buildingUids = buildingsWithStatus.map(building => building.uid)
        
        // 恢复遍历 UID 并调用 getCompaniesByBuildingUidApi 的逻辑，实现"后台静默查库比对"
        const companyPromises = buildingUids.map(uid =>
          getCompaniesByBuildingUidApi(uid)
            .then(companyRes => ({ uid, data: companyRes, success: true }))
            .catch(err => ({ uid, data: null, success: false, error: err }))
        )
        
        const companyResults = await Promise.all(companyPromises)
        
        companyResults.forEach(result => {
          if (result.success && result.data && result.data.length > 0) {
            // 对返回的公司数据进行清洗（包含 '-' 转义）
            const cleanedCompanies = cleanCompanies(result.data);
            
            // 将楼宇状态更新为 initialized（有数据）
            updateBuildingStatus(result.uid, 'initialized', cleanedCompanies)
          } else {
            // 将楼宇状态更新为 uninitialized（无数据）
            updateBuildingStatus(result.uid, 'uninitialized', [])
          }
        })
      }
    } catch (error) {
      console.error('获取楼宇列表失败:', error)
    }
  }

  /**
   * 三级优先级加载企业数据（收藏点击专用）
   * 第一步：优先使用 Store 缓存中的数据
   * 第二步：Store 无数据则调用 getCompaniesByBuildingUidApi 查数据库
   * 第三步：数据库也无数据则调用 searchCompanyListApi 触发爬虫采集，再拉取结果
   * @param {string} location - 百度地图 location 字符串 "lat,lng"
   * @param {string} uid - 楼宇 uid
   * @param {Function} onLoadingText - 可选回调，用于更新 loading 文案
   */
  async function loadBuildingCompaniesWithFallback(location, uid, onLoadingText) {
    // ===== 第一步：检查 Store 缓存 =====
    const cached = poiMap.value.get(uid)
    if (cached && cached.companies && cached.companies.length > 0) {
      console.log(`[收藏加载] 第一步命中 Store 缓存，共 ${cached.companies.length} 条数据`)
      selectBuilding(cached)
      mapState.isDrawerVisible = true
      return
    }

    // ===== 第二步：查询业务数据库 =====
    if (onLoadingText) onLoadingText('正在检索本地企业数据库...')
    try {
      const dbRes = await getCompaniesByBuildingUidApi(uid)
      const dbCompanies = cleanCompanies(dbRes)
      if (dbCompanies.length > 0) {
        console.log(`[收藏加载] 第二步命中数据库，共 ${dbCompanies.length} 条数据`)
        updateBuildingStatus(uid, 'initialized', dbCompanies)
        const updated = poiMap.value.get(uid)
        selectBuilding(updated)
        mapState.isDrawerVisible = true
        return
      }
    } catch (err) {
      console.warn('[收藏加载] 第二步数据库查询失败，继续降级:', err)
    }

    // ===== 第三步：触发爬虫采集 =====
    if (onLoadingText) onLoadingText('本地无数据，正在触发云端智能采集...')
    await fetchBuildingCompanies(location, uid)
  }

  /**
   * 获取企业列表并复用现有逻辑更新楼宇状态 (加入业务数据融合)
   * 修改入参：移除radius参数，仅保留location和uid
   * 执行顺序逻辑：
   * 1. 调用接口2通知后端爬取该楼宇的数据
   * 2. 严格等待接口2成功返回后，立刻调用接口4从本地数据库拉取最新数据
   * 3. 更新楼宇状态并将数据装载入store
   * 4. 触发侧边抽屉展示
   */
  async function fetchBuildingCompanies(location, uid) {
    console.group(`[Debug: Store] 🏢 开始拉取楼宇企业流转 - UID: ${uid}`);
    console.log('[1. 入参]', { location, uid });
    
    try {
      // 1. 调用接口2通知后端爬虫采集
      console.log('[2. 发起 API 2] searchCompanyListApi...');
      await searchCompanyListApi(location, uid);
      console.log('[3. API 2 完成] 爬虫触发成功');
      
      // 2. 调用接口4拉取最新数据
      console.log('[4. 发起 API 4] getCompaniesByBuildingUidApi...');
      const companyRes = await getCompaniesByBuildingUidApi(uid);
      console.log('[5. API 4 原始返回结果]:', companyRes);
      
      // 3. 数据清洗：将后端返回的数字状态码转换为前端需要的中文字符串，同时将 '-' 替换为 '未获取到该数据'
      const cleanedCompanies = cleanCompanies(companyRes);
      
      console.log('[6. 清洗后的企业数组 (准备入库)]:', cleanedCompanies);
      
      updateBuildingStatus(uid, 'initialized', cleanedCompanies);
      
      // 验证 Store 中是否真的存进去了
      const updatedBuilding = poiMap.value.get(uid);
      console.log('[7. 更新后 Store 中的楼宇对象]:', updatedBuilding);
      
      // 4. 打开侧边抽屉
      if (updatedBuilding) selectBuilding(updatedBuilding);
      mapState.isDrawerVisible = true;
      console.log('[8. 流程结束] 抽屉已触发打开');
      console.groupEnd();
    } catch (error) {
      console.error('[Debug: Store] ❌ 获取企业列表流程失败:', error);
      console.groupEnd();
      
      const b = poiMap.value.get(uid);
      if (b) selectBuilding(b);
      mapState.isDrawerVisible = true;
      throw error;
    }
  }

  return {
    // State
    poiMap,
    selectedBuilding,
    initializing,
    loadingBuildings,
    batchTasks,
    // Getters
    buildings,
    buildingCount,
    isBuildingLoading,
    basicTasks,
    deepTasks,
    // Actions
    addOrUpdateBuilding,
    batchAddBuildings,
    removeBuilding,
    clearBuildings,
    selectBuilding,
    clearSelection,
    updateBuildingStatus,
    updateCompany,
    setBuildingLoading,
    setInitializing,
    setBatchTask,
    removeBatchTask,
    clearBatchTasks,
    fetchBuildings,
    fetchBuildingCompanies,
    loadBuildingCompaniesWithFallback,
    // 导出清洗函数供外部使用
    cleanCompanies,
    formatCompanyData,
    // 时间计算
    getTaskTotalMinutes,
    getTaskRemainingMinutes,
  }
})
