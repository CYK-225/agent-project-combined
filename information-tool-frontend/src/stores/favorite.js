// import { ref, computed } from 'vue'
// import { defineStore } from 'pinia'
// import { getFavoriteListApi, addFavoriteApi, removeFavoriteApi } from '@/api/map'

// /**
//  * 收藏状态管理 - 管理当前用户的个性化数据（收藏夹）
//  */
// export const useFavoriteStore = defineStore('favorite', () => {
//   // ========== State ==========
  
//   // 收藏列表
//   const favorites = ref([])

//   // ========== Getters ==========
  
//   // 收藏数量
//   const favoriteCount = computed(() => favorites.value.length)
  
//   // 判断是否已收藏指定UID的楼宇
//   const isFavorite = (uid) => favorites.value.some(item => item.uid === uid)
  
//   // 判断当前选中的楼宇是否被收藏（需传入selectedBuilding）
//   const isCurrentFavorite = (selectedBuilding) => {
//     if (!selectedBuilding) return false
//     return favorites.value.some(item => item.uid === selectedBuilding.uid)
//   }

//   // ========== Actions ==========
  
//   /**
//    * 从后端获取收藏列表并解析 location 字段映射为 lng/lat
//    * @param {string} userId - 用户ID
//    */
//   async function fetchFavorites(userId) {
//     try {
//       const res = await getFavoriteListApi(userId)
//       if (Array.isArray(res)) {
//         favorites.value = res.map(item => {
//           // 解析 location 字段："lat,lng" 格式（百度地图标准）→ 独立的 lng 和 lat
//           let lng = item.lng
//           let lat = item.lat
//           if (item.location && typeof item.location === 'string') {
//             const parts = item.location.split(',')
//             if (parts.length === 2) {
//               lat = parseFloat(parts[0])
//               lng = parseFloat(parts[1])
//             }
//           } else if (item.location && typeof item.location === 'object') {
//             // 兼容 location 为对象格式 { lng, lat }
//             lng = item.location.lng
//             lat = item.location.lat
//           }
//           return {
//             uid: item.uid || item.buildingUid,
//             name: item.name || item.buildingName,
//             address: item.address,
//             lng,
//             lat,
//             addedAt: item.addedAt || item.createdAt || Date.now(),
//           }
//         })
//       }
//     } catch (error) {
//       console.error('获取收藏列表失败:', error)
//     }
//   }

//   /**
//    * 添加收藏（先同步后端，成功后更新本地）
//    * @param {Object} building - 楼宇数据
//    */
//   async function addFavorite(building) {
//     if (favorites.value.some(item => item.uid === building.uid)) return
//     try {
//       const userId = 'user_01'
//       await addFavoriteApi(userId, building.uid)
//       favorites.value.push({
//         uid: building.uid,
//         name: building.name,
//         address: building.address,
//         lng: building.lng,
//         lat: building.lat,
//         addedAt: Date.now(),
//       })
//     } catch (error) {
//       console.error('添加收藏失败:', error)
//     }
//   }
  
//   /**
//    * 移除收藏（先同步后端，成功后更新本地）
//    * @param {string} uid - 楼宇uid
//    */
//   async function removeFavorite(uid) {
//     const index = favorites.value.findIndex(item => item.uid === uid)
//     if (index === -1) return
//     try {
//       const userId = 'user_01'
//       await removeFavoriteApi(userId, uid)
//       favorites.value.splice(index, 1)
//     } catch (error) {
//       console.error('移除收藏失败:', error)
//     }
//   }
  
//   /**
//    * 切换收藏状态
//    * @param {Object} building - 楼宇数据
//    * @param {Object} selectedBuilding - 当前选中的楼宇（用于判断是否已收藏）
//    */
//   function toggleFavorite(building, selectedBuilding) {
//     if (isCurrentFavorite(selectedBuilding)) {
//       removeFavorite(building.uid)
//     } else {
//       addFavorite(building)
//     }
//   }

//   return {
//     // State
//     favorites,
//     // Getters
//     favoriteCount,
//     isFavorite,
//     isCurrentFavorite,
//     // Actions
//     fetchFavorites,
//     addFavorite,
//     removeFavorite,
//     toggleFavorite,
//   }
// })



/**
 * 临时方案: 收藏列表存在本地浏览器
 */

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
// 注释掉或删除后端的 API 引入，因为现在改用 localStorage
// import { getFavoriteListApi, addFavoriteApi, removeFavoriteApi } from '@/api/map'

// 定义本地存储的 Key
const LOCAL_STORAGE_KEY = 'inbound_tool_favorites_local'

/**
 * 收藏状态管理 - 本地免登录版（使用 localStorage 存储）
 */
export const useFavoriteStore = defineStore('favorite', () => {
  // ========== State ==========
  
  // 收藏列表
  const favorites = ref([])

  // ========== Getters ==========
  
  // 收藏数量
  const favoriteCount = computed(() => favorites.value.length)
  
  // 判断是否已收藏指定UID的楼宇
  const isFavorite = (uid) => favorites.value.some(item => item.uid === uid)
  
  // 判断当前选中的楼宇是否被收藏（需传入selectedBuilding）
  const isCurrentFavorite = (selectedBuilding) => {
    if (!selectedBuilding) return false
    return favorites.value.some(item => item.uid === selectedBuilding.uid)
  }

  // ========== 私有辅助方法 ==========
  
  // 将当前状态同步到本地浏览器存储
  const saveToLocal = () => {
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(favorites.value))
    } catch (e) {
      console.error('保存收藏到本地失败:', e)
    }
  }

  // ========== Actions ==========
  
  /**
   * 从本地浏览器读取收藏列表
   * @param {string} userId - 保留参数以兼容原有组件调用，但实际不再使用
   */
  async function fetchFavorites(userId) {
    try {
      const localData = localStorage.getItem(LOCAL_STORAGE_KEY)
      if (localData) {
        favorites.value = JSON.parse(localData)
      } else {
        favorites.value = []
      }
    } catch (error) {
      console.error('读取本地收藏列表失败，已重置为空:', error)
      favorites.value = []
    }
  }

  /**
   * 添加收藏并保存到本地
   * @param {Object} building - 楼宇数据
   */
  async function addFavorite(building) {
    // 防重复添加
    if (favorites.value.some(item => item.uid === building.uid)) return
    
    // 直接推入前端状态
    favorites.value.push({
      uid: building.uid,
      name: building.name,
      address: building.address,
      lng: building.lng,
      lat: building.lat,
      addedAt: Date.now(),
    })
    
    // 持久化到 localStorage
    saveToLocal()
  }
  
  /**
   * 移除收藏并同步到本地
   * @param {string} uid - 楼宇uid
   */
  async function removeFavorite(uid) {
    const index = favorites.value.findIndex(item => item.uid === uid)
    if (index === -1) return
    
    // 从前端状态中移除
    favorites.value.splice(index, 1)
    
    // 持久化到 localStorage
    saveToLocal()
  }
  
  /**
   * 切换收藏状态
   * @param {Object} building - 楼宇数据
   * @param {Object} selectedBuilding - 当前选中的楼宇（用于判断是否已收藏）
   */
  function toggleFavorite(building, selectedBuilding) {
    if (isCurrentFavorite(selectedBuilding)) {
      removeFavorite(building.uid)
    } else {
      addFavorite(building)
    }
  }

  return {
    // State
    favorites,
    // Getters
    favoriteCount,
    isFavorite,
    isCurrentFavorite,
    // Actions
    fetchFavorites,
    addFavorite,
    removeFavorite,
    toggleFavorite,
  }
})