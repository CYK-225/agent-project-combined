<template>
  <div class="search-bar" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!-- 城市选择器 + 搜索框容器 -->
    <div class="search-container">
      <!-- 省市二级联动选择器 -->
      <el-cascader
        v-model="selectedCity"
        :options="cityData"
        :props="{ value: 'value', label: 'label', children: 'children' }"
        placeholder="选择城市"
        size="large"
        filterable
        :show-all-levels="false"
        class="city-selector"
        popper-class="city-cascader-dropdown"
        @change="handleCityChange"
      />
      
      <!-- 地址搜索输入框（带自动完成） -->
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="querySearch"
        placeholder="请输入地址关键词"
        size="large"
        clearable
        class="search-input"
        :trigger-on-focus="false"
        :debounce="300"
        value-key="value"
        @select="handleSelect"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #default="{ item }">
          <div class="suggestion-item">
            <div class="suggestion-title">{{ item.title }}</div>
            <div class="suggestion-address">{{ item.address }}</div>
          </div>
        </template>
        <template #append>
          <el-button type="primary" @click="handleSearch">
            搜索
          </el-button>
        </template>
      </el-autocomplete>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useMapStateStore } from '@/stores'

const props = defineProps({
  sidebarCollapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['search', 'city-change'])

const mapStateStore = useMapStateStore()
const keyword = ref('')

// 从 store 获取城市数据
const cityData = computed(() => mapStateStore.cityData)
const selectedCity = computed({
  get: () => mapStateStore.selectedCity,
  set: (val) => mapStateStore.setCity(val)
})

// 获取地图实例
const mapInstance = computed(() => mapStateStore.mapInstance)

// 城市变更处理
function handleCityChange(value) {
  emit('city-change', value)
}

// 百度地图搜索建议
function querySearch(queryString, cb) {
  if (!queryString || !queryString.trim()) {
    cb([])
    return
  }

  // 确保百度地图 API 已加载
  if (!window.BMapGL || !mapInstance.value) {
    cb([])
    return
  }

  const localSearch = new window.BMapGL.LocalSearch(mapInstance.value, {
    onSearchComplete: (results) => {
      if (!results || results.getNumPois() === 0) {
        cb([])
        return
      }

      const suggestions = []
      const numPois = Math.min(results.getNumPois(), 10) // 最多10条

      for (let i = 0; i < numPois; i++) {
        const poi = results.getPoi(i)
        if (poi) {
          suggestions.push({
            value: `${poi.title} - ${poi.address || '未知地址'}`,
            title: poi.title,
            address: poi.address || '未知地址',
            point: poi.point,
            uid: poi.uid,
            city: poi.city,
            province: poi.province
          })
        }
      }
      cb(suggestions)
    }
  })

  // 执行搜索
  localSearch.search(queryString.trim())
}

// 选中搜索建议
function handleSelect(item) {
  if (item && item.point && mapInstance.value) {
    // 地图平移到选中位置
    const point = new window.BMapGL.Point(item.point.lng, item.point.lat)
    mapInstance.value.centerAndZoom(point, 18)
    
    // 添加标记点
    const marker = new window.BMapGL.Marker(point)
    mapInstance.value.addOverlay(marker)
    
    // 触发搜索事件，让父组件处理
    emit('search', item.title)
  }
}

// 手动搜索
function handleSearch() {
  if (!keyword.value.trim()) return
  emit('search', keyword.value.trim())
}
</script>

<style scoped>
.search-bar {
  position: absolute;
  top: 24px;
  left: 280px;
  z-index: 100;
  transition: left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.search-bar.sidebar-collapsed {
  left: 24px;
}

.search-container {
  display: flex;
  gap: 8px;
  align-items: center;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  padding: 4px;
  transition: all 0.3s ease;
}

.search-container:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

/* 城市选择器样式 */
.city-selector {
  width: 140px;
  flex-shrink: 0;
}

.city-selector :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0 8px;
}

.city-selector :deep(.el-input__inner) {
  font-size: 14px;
  color: #409eff;
  font-weight: 500;
}

/* 搜索输入框样式 */
.search-input {
  width: 320px;
}

.search-input :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 4px 8px;
}

.search-input :deep(.el-input__inner) {
  font-size: 15px;
}

.search-input :deep(.el-input-group__append) {
  border-radius: 0 8px 8px 0;
  overflow: hidden;
  background: transparent;
  box-shadow: none;
}

.search-input :deep(.el-input-group__append .el-button) {
  border-radius: 8px;
  padding: 0 20px;
  font-size: 14px;
  height: 36px;
  margin: 2px;
}

/* 搜索建议项样式 */
.suggestion-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.suggestion-item:last-child {
  border-bottom: none;
}

.suggestion-title {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 4px;
}

.suggestion-address {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style>
/* 下拉菜单全局样式 */
.city-cascader-dropdown .el-cascader-menu {
  min-width: 140px;
}
</style>
