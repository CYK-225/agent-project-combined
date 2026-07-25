<template>
  <div ref="mapContainer" class="baidu-map-container">
    <!-- 搜索栏 -->
    <SearchBar :sidebar-collapsed="sidebarCollapsed" @search="handleSearch" @city-change="handleCityChange" />
    
    <!-- 地图工具栏 -->
    <MapTools
      :circle-active="circleToolActive"
      :has-circle-selection="!!currentCircleOverlay"
      :has-markers="buildingMarkers.size > 0"
      @toggle-circle="toggleCircleTool"
      @reset-view="resetMapView"
      @clear-circle="clearCircleSelection"
      @clear-markers="clearAllBuildingMarkers"
    />
    
    <!-- 圈选半径输入框 -->
    <CircleRadiusInput
      v-model:visible="showRadiusInput"
      v-model:radius="circleRadius"
      v-model:keywords="circleKeywords"
      :position="radiusInputPosition"
      @confirm="handleCircleConfirm"
      @cancel="handleCircleCancel"
    />
    
    <!-- 地图容器 -->
    <div id="baidu-map" class="map-instance"></div>
    
    <!-- 楼宇悬停信息弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="hoverTooltip.visible"
          class="building-tooltip"
          :style="{ left: hoverTooltip.x + 'px', top: hoverTooltip.y + 'px' }"
          @mouseenter="handleTooltipMouseEnter"
          @mouseleave="handleTooltipMouseLeave"
        >
          <div class="tooltip-header">
            <span class="tooltip-title clickable" @click="handleBuildingClick">{{ hoverTooltip.building?.name }}</span>
            <el-tag :type="getBuildingStatusType(hoverTooltip.building?.status)" size="small">
              {{ getBuildingStatusText(hoverTooltip.building?.status) }}
            </el-tag>
          </div>
          <div class="tooltip-address">{{ hoverTooltip.building?.address || '暂无地址信息' }}</div>
          <div class="tooltip-companies">
            <div class="companies-header">
              <span>入驻公司 ({{ hoverTooltip.companies?.length || 0 }}家)</span>
            </div>
            <!-- 条件分支：根据楼宇状态显示不同内容 -->
            <div v-if="hoverTooltip.building?.status === 'uninitialized'">
              <div class="uninitialized-warning">
                <i class="el-icon-warning" style="color: orange; margin-right: 5px;"></i>
                <span>此楼宇还未采集，点击图标进行采集</span>
              </div>
            </div>
            <div v-else-if="hoverTooltip.companies?.length > 0" class="companies-list-container">
              <div class="companies-list" @wheel.stop>
                <div
                  v-for="company in hoverTooltip.companies"
                  :key="company.id"
                  class="company-item"
                  @click="handleCompanyClick(company)"
                >
                  <span class="company-name-text clickable">{{ company.companyName }}</span>
                  <el-tag :type="getStatusType(company.infoStatus)" size="small">
                    {{ company.infoStatus }}
                  </el-tag>
                </div>
              </div>
            </div>
            <div v-else class="no-companies">暂无公司数据</div>
          </div>
        </div>
      </Transition>
    </Teleport>
    
    <!-- 公司编辑对话框 -->
    <CompanyEditDialog
      v-model:visible="dialogVisible"
      :company="selectedCompany"
      :building-uid="currentBuilding?.uid || ''"
      :building-name="currentBuilding?.name || ''"
      :mode="dialogMode"
      @save="handleSave"
      @confirm-complete="handleConfirmComplete"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, markRaw } from 'vue'
import { useMapStateStore, useBuildingStore } from '@/stores'
import { loadBaiduMap } from '@/utils/mapLoader'
import { MAP_CONFIG, DEFAULT_CIRCLE_RADIUS } from '@/utils/constants'
import { searchBuildingsApi, getCompaniesByBuildingUidApi } from '@/api/map'
import { ElMessage, ElLoading } from 'element-plus'
import SearchBar from './SearchBar.vue'
import MapTools from './MapTools.vue'
import CircleRadiusInput from './CircleRadiusInput.vue'
import CompanyEditDialog from '../BuildingDrawer/CompanyEditDialog.vue'

const props = defineProps({
  sidebarCollapsed: {
    type: Boolean,
    default: false
  }
})

const mapStateStore = useMapStateStore()
const buildingStore = useBuildingStore()
const mapContainer = ref(null)

// 地图实例
let map = null
let BMapGL = null

// 圈选相关
const circleToolActive = ref(false)
const showRadiusInput = ref(false)
const circleRadius = ref(DEFAULT_CIRCLE_RADIUS)
const circleKeywords = ref('写字楼')
const radiusInputPosition = ref({ x: 0, y: 0 })
let tempCircleCenter = null
let currentCircleOverlay = null
let circleMarkers = []

// 楼宇标记缓存
let buildingMarkers = new Map()

// 悬停提示框
const hoverTooltip = ref({
  visible: false,
  x: 0,
  y: 0,
  building: null,
  companies: []
})
let showTooltipTimer = null
let hideTooltipTimer = null
let currentHoverBuilding = null

// 公司编辑对话框
const dialogVisible = ref(false)
const selectedCompany = ref(null)
const currentBuilding = ref(null)
const dialogMode = ref('view')

/**
 * 获取楼宇状态类型
 */
function getBuildingStatusType(status) {
  const typeMap = {
    'uninitialized': 'info',
    'initialized': 'warning',
    'completed': 'success'
  }
  return typeMap[status] || 'info'
}

/**
 * 获取楼宇状态文本
 */
function getBuildingStatusText(status) {
  const textMap = {
    'uninitialized': '未初始化',
    'initialized': '已初始化',
    'deepInitialized': '已深度初始化',
    'completed': '已完成'
  }
  return textMap[status] || '未知'
}

/**
 * 获取公司状态标签类型
 */
function getStatusType(status) {
  const typeMap = {
    '未初始化': 'info',
    '已初始化': 'warning',
    '已深度初始化' : 'warning',
    '已完善': 'success'
  }
  return typeMap[status] || 'info'
}

/**
 * 显示楼宇悬停提示（带0.5秒延迟）
 */
function showBuildingTooltip(building, pixel) {
  currentHoverBuilding = building
  
  // 清除之前的定时器
  if (showTooltipTimer) {
    clearTimeout(showTooltipTimer)
  }
  if (hideTooltipTimer) {
    clearTimeout(hideTooltipTimer)
    hideTooltipTimer = null
  }
  
  // 0.5秒后显示弹窗
  showTooltipTimer = setTimeout(() => {
    if (currentHoverBuilding === building) {
      // 从 buildingStore 中获取真实的楼宇数据（包含拉取回来的 companies）
      const storeBuilding = buildingStore.poiMap.get(building.uid)
      const companies = storeBuilding?.companies || []
      
      hoverTooltip.value = {
        visible: true,
        x: pixel.x + 20,
        y: pixel.y - 10,
        building,
        companies
      }
    }
  }, 500)
}

/**
 * 隐藏楼宇悬停提示
 */
function hideBuildingTooltip() {
  currentHoverBuilding = null
  
  // 清除显示定时器
  if (showTooltipTimer) {
    clearTimeout(showTooltipTimer)
    showTooltipTimer = null
  }
  
  // 延迟隐藏，给鼠标移入弹窗的时间
  hideTooltipTimer = setTimeout(() => {
    hoverTooltip.value.visible = false
  }, 200)
}

/**
 * 鼠标进入弹窗
 */
function handleTooltipMouseEnter() {
  if (hideTooltipTimer) {
    clearTimeout(hideTooltipTimer)
    hideTooltipTimer = null
  }
}

/**
 * 鼠标离开弹窗
 */
function handleTooltipMouseLeave() {
  hideTooltipTimer = setTimeout(() => {
    hoverTooltip.value.visible = false
  }, 200)
}

/**
 * 🌟 核心提取：处理楼宇被选中时的三级缓存与数据拉取逻辑
 * @param {Object} building 目标楼宇对象
 */
async function processBuildingSelection(building) {
  if (!building) return;
  
  // 【第一级缓存】：优先检查前端 Store 内存
  const storeBuilding = buildingStore.poiMap.get(building.uid);
  if (storeBuilding && (storeBuilding.status === 'initialized' || storeBuilding.status === 'completed')) {
    buildingStore.selectBuilding(storeBuilding);
    mapStateStore.setDrawerVisible(true);
    return;
  }
  
  // 开启全屏沉浸式 Loading
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '正在检索本地企业数据库...',
    background: 'rgba(0, 0, 0, 0.7)',
  });
  
  try {
    // 【第二级缓存】：查询业务数据库 (接口 4)
    const dbRes = await getCompaniesByBuildingUidApi(building.uid);
    const dbCompanies = Array.isArray(dbRes) ? dbRes : (dbRes?.data || []);
    
    if (dbCompanies.length > 0) {
      loadingInstance.setText('数据命中，正在渲染...');
      const statusMap = { 0: '未初始化', 1: '已初始化',2:'已深度初始化', 3: '已完善' };
      const cleanCompanies = dbCompanies.map(c => ({
        ...c,
        id: c.uid || c.id,
        infoStatus: statusMap[c.infoStatus] || '未初始化'
      }));
      
      buildingStore.updateBuildingStatus(building.uid, 'initialized', cleanCompanies);
      const updatedBuilding = buildingStore.poiMap.get(building.uid);
      buildingStore.selectBuilding(updatedBuilding);
      mapStateStore.setDrawerVisible(true);
      
      loadingInstance.close();
      ElMessage.success(`【${building.name}】数据加载成功`);
    } else {
      // 【第三级兜底】：数据库为空，触发爬虫获取
      loadingInstance.setText('本地无数据，正在触发云端智能采集...');
      const locationStr = String(building.lat) + ',' + String(building.lng);
      await buildingStore.fetchBuildingCompanies(locationStr, building.uid, mapStateStore.currentSearchRadius);
      loadingInstance.close();
    }
  } catch (err) {
    loadingInstance.close();
    ElMessage.error('企业数据同步异常，请重试');
    console.error('获取楼宇公司流转失败:', err);
  }
}

/**
 * 点击楼宇名称 - 打开楼宇详情并拉取企业列表
 */
async function handleBuildingClick() {
  // 关闭悬浮弹窗
  hoverTooltip.value.visible = false;
  
  // 触发公共核心逻辑
  const building = hoverTooltip.value.building;
  await processBuildingSelection(building);
}

/**
 * 点击公司名称 - 打开公司编辑弹窗
 */
function handleCompanyClick(company) {
  // 关闭弹窗
  hoverTooltip.value.visible = false
  
  // 存储当前楼宇和公司信息
  currentBuilding.value = hoverTooltip.value.building
  selectedCompany.value = { ...company }
  dialogMode.value = 'view'
  dialogVisible.value = true
}

/**
 * 保存公司编辑
 */
function handleSave(updatedData) {
  // 这里可以添加保存逻辑，比如更新 store 或调用 API
  ElMessage.success('保存成功')
}

/**
 * 确认完善公司信息
 */
function handleConfirmComplete(updatedData) {
  // 这里可以添加确认完善的逻辑
  ElMessage.success('确认完善成功')
}

/**
 * 初始化地图
 */
async function initMap() {
  try {
    BMapGL = await loadBaiduMap()
    
    // 创建地图实例
    map = new BMapGL.Map('baidu-map', {
      enableMapClick: false,
    })
    
    // 设置中心点和缩放级别
    const centerPoint = new BMapGL.Point(
      MAP_CONFIG.defaultCenter.lng,
      MAP_CONFIG.defaultCenter.lat
    )
    map.centerAndZoom(centerPoint, MAP_CONFIG.defaultZoom)
    
    // 启用滚轮缩放
    map.enableScrollWheelZoom(true)
    
    // 添加控件
    map.addControl(new BMapGL.NavigationControl3D())
    map.addControl(new BMapGL.ScaleControl())
    
    // 保存地图实例到 store
    mapStateStore.setMapInstance(markRaw(map))
    // 绑定点击事件（圈选模式）
    map.addEventListener('click', handleMapClick)
    
    console.log('地图初始化成功')
  } catch (error) {
    console.error('地图初始化失败:', error)
  }
}

/**
 * 处理地图点击（圈选模式）
 */
function handleMapClick(e) {
  if (!circleToolActive.value) return
  
  // 获取点击位置的坐标
  const point = e.latlng
  tempCircleCenter = {
    lng: point.lng,
    lat: point.lat,
  }
  
  // 显示半径输入框
  const pixel = map.pointToPixel(point)
  radiusInputPosition.value = {
    x: pixel.x + 20,
    y: pixel.y - 20,
  }
  showRadiusInput.value = true
}

/**
 * 确认圈选
 */
async function handleCircleConfirm() {
  if (!tempCircleCenter || !circleRadius.value) return
  
  // 清除之前的圈选覆盖物
  clearCircleOverlay()
  
  // 绘制圆形覆盖物
  const centerPoint = new BMapGL.Point(tempCircleCenter.lng, tempCircleCenter.lat)
  currentCircleOverlay = new BMapGL.Circle(centerPoint, circleRadius.value, {
    strokeColor: '#409EFF',
    strokeWeight: 2,
    strokeOpacity: 0.8,
    fillColor: '#409EFF',
    fillOpacity: 0.2,
  })
  map.addOverlay(currentCircleOverlay)
  
  // 保存圈选参数到 store
  mapStateStore.setCircleSelection(tempCircleCenter, circleRadius.value)
  
  // 搜索圈选范围内的楼宇
  await searchBuildingsInCircle(tempCircleCenter, circleRadius.value, circleKeywords.value)
  
  // 关闭输入框
  showRadiusInput.value = false
  circleToolActive.value = false
  
  // 恢复鼠标样式
  const mapEl = document.getElementById('baidu-map')
  if (mapEl) {
    mapEl.style.cursor = 'default'
  }
}

/**
 * 取消圈选
 */
function handleCircleCancel() {
  showRadiusInput.value = false
  tempCircleCenter = null
}

/**
 * 搜索圈选范围内的楼宇
 */
async function searchBuildingsInCircle(center, radius, keywords) {
  let loadingInstance = null
  try {
    loadingInstance = ElMessage({
      type: 'info',
      message: '正在搜索楼宇...',
      duration: 0,
      showClose: false
    })
    
    // 1. 严格确保纬度和经度转换为字符串，并使用逗号拼接
    const locationStr = String(center.lat) + ',' + String(center.lng);
    
    // 2. 规范化打印日志（避免 [object Object] 误导）
    console.log("发起搜索，中心点:", locationStr, "半径:", radius, "关键词:", keywords);
    
    // 3. 调用规范化的后端 API 搜索楼宇
    const response = await searchBuildingsApi({
      location: locationStr, // 这里现在是一个绝对干净的 String
      radius: Number(radius), // 确保半径是数字
      keywords: String(keywords)
    });
    
    // 关闭 loading
    if (loadingInstance) {
      loadingInstance.close()
    }
    
    // 解析后端返回的数据
    const results = response.results || []
    
    if (results.length === 0) {
      ElMessage.info('未找到符合条件的楼宇')
      return
    }
    
    // 转换数据格式
    const buildings = results.map((item, index) => ({
      uid: item.uid || `building_${Date.now()}_${index}`,
      name: item.name || '未知楼宇',
      address: item.address || '',
      lng: item.location?.lng || item.lng,
      lat: item.location?.lat || item.lat,
      status: 'uninitialized', // 默认状态
      companyCount: 0,
      // 保留原始数据供后续使用
      rawData: item
    }))
    
    // 渲染楼宇标记
    renderBuildingMarkers(buildings)
    
    // 添加到 store（自动去重）
    buildingStore.batchAddBuildings(buildings)
    
    ElMessage.success(`成功找到 ${buildings.length} 个楼宇`)
    
    // 调整地图视野以显示所有标记
    if (buildings.length > 0) {
      const points = buildings.map(b => new BMapGL.Point(b.lng, b.lat))
      points.push(new BMapGL.Point(center.lng, center.lat))
      map.setViewport(points)
    }
  } catch (error) {
    // 关闭 loading
    if (loadingInstance) {
      loadingInstance.close()
    }
    console.error('搜索楼宇失败:', error)
    ElMessage.error(error.message || '搜索楼宇失败，请稍后重试')
  }
}

/**
 * 渲染楼宇标记
 */
function renderBuildingMarkers(buildings) {
  buildings.forEach(building => {
    // 检查是否已存在（去重）
    if (buildingMarkers.has(building.uid)) {
      return
    }
    
    const point = new BMapGL.Point(building.lng, building.lat)
    
    // 根据状态设置不同颜色
    const statusColors = {
      uninitialized: '#F56C6C',
      initialized: '#409EFF',
      completed: '#67C23A',
    }
    
    // 创建标记
    const marker = new BMapGL.Marker(point, {
      icon: new BMapGL.Icon(
        createMarkerIcon(statusColors[building.status]),
        new BMapGL.Size(24, 24),
        { anchor: new BMapGL.Size(12, 12) }
      ),
    })
    
    // ==========================================
    // 🌟 统一触发：调用提取好的三级缓存核心逻辑
    // ==========================================
    marker.addEventListener('click', async () => {
      await processBuildingSelection(building);
    })
    
    // 添加鼠标悬停事件
    marker.addEventListener('mouseover', (e) => {
      const pixel = map.pointToPixel(point)
      showBuildingTooltip(building, { x: pixel.x, y: pixel.y })
    })
    
    // 添加鼠标移出事件
    marker.addEventListener('mouseout', () => {
      hideBuildingTooltip()
    })
    
    // 添加标签
    const label = new BMapGL.Label(building.name, {
      position: point,
      offset: new BMapGL.Size(0, -30),
    })
    label.setStyle({
      border: 'none',
      background: 'rgba(0,0,0,0.6)',
      color: '#fff',
      padding: '4px 8px',
      borderRadius: '4px',
      fontSize: '12px',
      cursor: 'pointer' // [新增] 提供可点击的鼠标样式
    })

    // [新增] 为 Label 绑定与 Marker 相同的点击事件
    label.addEventListener('click', async () => {
      await processBuildingSelection(building);
    })
    
    map.addOverlay(marker)
    map.addOverlay(label)
    
    buildingMarkers.set(building.uid, { marker, label })
  })
}

/**
 * 创建标记图标（DataURL）
 */
function createMarkerIcon(color) {
  const canvas = document.createElement('canvas')
  canvas.width = 24
  canvas.height = 24
  const ctx = canvas.getContext('2d')
  
  // 绘制圆点
  ctx.beginPath()
  ctx.arc(12, 12, 10, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()
  
  // 绘制边框
  ctx.strokeStyle = '#fff'
  ctx.lineWidth = 2
  ctx.stroke()
  
  return canvas.toDataURL()
}

/**
 * 清除圈选覆盖物
 */
function clearCircleOverlay() {
  if (currentCircleOverlay) {
    map.removeOverlay(currentCircleOverlay)
    currentCircleOverlay = null
  }
}

/**
 * 清除圈选区域（按钮触发）
 */
function clearCircleSelection() {
  // 清除地图上的圈选覆盖物
  clearCircleOverlay()
  // 清除 store 中的圈选状态
  mapStateStore.setCircleSelection(null, 0)
  // 如果正在圈选，取消圈选模式
  if (circleToolActive.value) {
    circleToolActive.value = false
    mapStateStore.toggleCircleTool()
    const mapEl = document.getElementById('baidu-map')
    if (mapEl) {
      mapEl.style.cursor = 'default'
    }
  }
}

/**
 * 清除所有楼宇点位
 */
function clearAllBuildingMarkers() {
  // 清除地图上的标记
  buildingMarkers.forEach(({ marker, label }) => {
    map.removeOverlay(marker)
    map.removeOverlay(label)
  })
  buildingMarkers.clear()
  // 清除 store 中的楼宇数据
  buildingStore.clearBuildings()
}

/**
 * 切换圈选工具
 */
function toggleCircleTool() {
  circleToolActive.value = !circleToolActive.value
  mapStateStore.toggleCircleTool()
  
  // 设置鼠标样式
  const mapEl = document.getElementById('baidu-map')
  if (mapEl) {
    mapEl.style.cursor = circleToolActive.value ? 'crosshair' : 'default'
  }
}

/**
 * 重置地图视图
 */
function resetMapView() {
  const centerPoint = new BMapGL.Point(
    MAP_CONFIG.defaultCenter.lng,
    MAP_CONFIG.defaultCenter.lat
  )
  map.centerAndZoom(centerPoint, MAP_CONFIG.defaultZoom)
}


/**
 * 处理搜索 (已接入后端业务接口)
 */
async function handleSearch(keyword) {
  
}

/**
 * 处理城市切换并自动跳转地图
 */
function handleCityChange(cityArray) {
  if (!map || !cityArray || cityArray.length === 0) return;
  
  // 获取选择器最后一级的名称（例如从 ['广东省', '广州市'] 取出 '广州市'）
  const cityName = cityArray[cityArray.length - 1];
  
  // 调用百度地图内置方法，通过字符串直接平移中心点。12级缩放比较适合俯瞰整个市级行政区
  map.centerAndZoom(cityName, 12);
  
  ElMessage.success(`地图已切换至 ${cityName}`);
}

/**
 * 清除所有标记
 */
function clearAllMarkers() {
  buildingMarkers.forEach(({ marker, label }) => {
    map.removeOverlay(marker)
    map.removeOverlay(label)
  })
  buildingMarkers.clear()
}

onMounted(() => {
  initMap()
})

onUnmounted(() => {
  if (map) {
    map.removeEventListener('click', handleMapClick)
    clearAllMarkers()
    clearCircleOverlay()
  }
  if (showTooltipTimer) {
    clearTimeout(showTooltipTimer)
  }
  if (hideTooltipTimer) {
    clearTimeout(hideTooltipTimer)
  }
})
</script>

<style scoped>
.baidu-map-container {
  position: relative;
  width: 100%;
  height: 100vh;
}

.map-instance {
  width: 100%;
  height: 100%;
}

/* 楼宇悬停提示框 */
.building-tooltip {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 12px 16px;
  min-width: 280px;
  max-width: 350px;
  pointer-events: auto;
}

.tooltip-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}

.tooltip-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tooltip-title.clickable {
  color: #409eff;
  cursor: pointer;
  transition: color 0.2s;
}

.tooltip-title.clickable:hover {
  color: #66b1ff;
  text-decoration: underline;
}

.tooltip-address {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tooltip-companies {
  border-top: 1px solid #e4e7ed;
  padding-top: 10px;
}

.companies-header {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}

.companies-list-container {
  max-height: 150px;
  overflow: hidden;
}

.companies-list {
  max-height: 150px;
  overflow-y: auto;
  padding-right: 4px;
}

.companies-list::-webkit-scrollbar {
  width: 4px;
}

.companies-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

.companies-list::-webkit-scrollbar-track {
  background: transparent;
}

/* 防止滚动循环 */
.companies-list {
  overscroll-behavior: contain;
}

.company-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f0f2f5;
}

.company-item:last-child {
  border-bottom: none;
}

.company-name-text {
  font-size: 13px;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.company-name-text.clickable {
  color: #409eff;
  cursor: pointer;
  transition: color 0.2s;
}

.company-name-text.clickable:hover {
  color: #66b1ff;
  text-decoration: underline;
}

.no-companies {
  font-size: 12px;
  color: #909399;
  text-align: center;
  padding: 10px 0;
}

/* 未初始化警告样式 */
.uninitialized-warning {
  display: flex;
  align-items: center;
  padding: 8px 0;
  color: orange;
  font-size: 13px;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

/* 覆盖百度地图默认样式 */
:deep(.anchorBL) {
  bottom: 20px !important;
  left: 20px !important;
}
</style>
