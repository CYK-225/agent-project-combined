import { ref, computed, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { cityData } from '@/utils/cityData'

/**
 * 地图状态管理 - 管理地图基础配置、UI交互状态和空间属性
 */
export const useMapStateStore = defineStore('mapState', () => {
  // ========== State ==========
  
  // 百度地图实例
  const mapInstance = shallowRef(null)  
  
  // 圈选工具状态
  const circleToolActive = ref(false)
  const circleCenter = ref(null)
  const circleRadius = ref(0)
  
  // 城市选择状态（默认北京）
  const selectedCity = ref(['广东省', '广州市'])
  
  // 搜索与抽屉状态
  const currentSearchRadius = ref('50')
  const isDrawerVisible = ref(false)

  // ========== Getters ==========
  
  // 当前城市名称（用于后端接口）
  const currentCityName = computed(() => {
    if (selectedCity.value && selectedCity.value.length >= 2) {
      return selectedCity.value[1] // 返回市级名称
    }
    return '广州市'
  })

  // ========== Actions ==========
  
  /**
   * 设置地图实例
   */
  function setMapInstance(map) {
    mapInstance.value = map
  }

  /**
   * 切换圈选工具状态
   */
  function toggleCircleTool() {
    circleToolActive.value = !circleToolActive.value
    if (!circleToolActive.value) {
      circleCenter.value = null
      circleRadius.value = 0
    }
  }
  
  /**
   * 设置圈选参数
   */
  function setCircleSelection(center, radius) {
    circleCenter.value = center
    circleRadius.value = radius
  }

  /**
   * 设置城市选择
   * @param {Array} cityArray - 城市数组 [省, 市]
   */
  function setCity(cityArray) {
    selectedCity.value = cityArray
  }

  /**
   * 设置抽屉可见性
   */
  function setDrawerVisible(visible) {
    isDrawerVisible.value = visible
  }

  return {
    // State
    mapInstance,
    circleToolActive,
    circleCenter,
    circleRadius,
    selectedCity,
    currentSearchRadius,
    isDrawerVisible,
    cityData,
    // Getters
    currentCityName,
    // Actions
    setMapInstance,
    toggleCircleTool,
    setCircleSelection,
    setCity,
    setDrawerVisible,
  }
})
