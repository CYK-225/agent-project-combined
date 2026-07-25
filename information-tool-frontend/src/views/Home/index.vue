<template>
  <div class="home-container">
    <!-- 左侧导航栏 -->
    <Sidebar ref="sidebarRef" />
    
    <!-- 地图容器 -->
    <div class="map-wrapper" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <BaiduMap :sidebar-collapsed="sidebarCollapsed" />
    </div>
    
    <!-- 右侧楼宇详情抽屉 -->
    <BuildingDrawer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaiduMap from '@/components/Map/BaiduMap.vue'
import Sidebar from '@/components/Sidebar/index.vue'
import BuildingDrawer from '@/components/BuildingDrawer/index.vue'
import { useFavoriteStore } from '@/stores'

const favoriteStore = useFavoriteStore()
const sidebarRef = ref(null)

const sidebarCollapsed = computed(() => sidebarRef.value?.collapsed ?? false)

// 页面初始化时加载真实的收藏列表
onMounted(() => {
  favoriteStore.fetchFavorites('user_01')
})
</script>

<style scoped>
.home-container {
  width: 100%;
  height: 100vh;
  display: flex;
  position: relative;
  overflow: hidden;
}

.map-wrapper {
  flex: 1;
  height: 100%;
  margin-left: 260px;
  transition: margin-left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.map-wrapper.sidebar-collapsed {
  margin-left: 0;
}
</style>
