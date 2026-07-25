<template>
  <div class="map-tools">
    <!-- 圈选工具 -->
    <el-tooltip content="圈选工具" placement="left">
      <el-button
        :type="circleActive ? 'primary' : 'default'"
        :class="{ active: circleActive }"
        circle
        size="large"
        @click="$emit('toggle-circle')"
      >
        <el-icon><CirclePlus /></el-icon>
      </el-button>
    </el-tooltip>
    
    <!-- 清除圈选区域 -->
    <el-tooltip content="清除圈选区域" placement="left">
      <el-button
        circle
        size="large"
        :disabled="!hasCircleSelection"
        @click="$emit('clear-circle')"
      >
        <el-icon><CircleClose /></el-icon>
      </el-button>
    </el-tooltip>
    
    <!-- 清除点位 -->
    <el-tooltip content="清除所有点位" placement="left">
      <el-button
        circle
        size="large"
        :disabled="!hasMarkers"
        @click="$emit('clear-markers')"
      >
        <el-icon><Delete /></el-icon>
      </el-button>
    </el-tooltip>
    
    <!-- 重置视图 -->
    <el-tooltip content="重置视图" placement="left">
      <el-button
        circle
        size="large"
        @click="$emit('reset-view')"
      >
        <el-icon><Aim /></el-icon>
      </el-button>
    </el-tooltip>
    
    <!-- 缩放控制 -->
    <div class="zoom-controls">
      <el-button circle size="small" @click="zoomIn">
        <el-icon><Plus /></el-icon>
      </el-button>
      <el-button circle size="small" @click="zoomOut">
        <el-icon><Minus /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useMapStateStore } from '@/stores'

const props = defineProps({
  circleActive: {
    type: Boolean,
    default: false,
  },
  hasCircleSelection: {
    type: Boolean,
    default: false,
  },
  hasMarkers: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['toggle-circle', 'reset-view', 'clear-circle', 'clear-markers'])
const mapStateStore = useMapStateStore()

function zoomIn() {
  const map = mapStateStore.mapInstance
  if (map) {
    map.zoomIn()
  }
}

function zoomOut() {
  const map = mapStateStore.mapInstance
  if (map) {
    map.zoomOut()
  }
}
</script>

<style scoped>
.map-tools {
  position: absolute;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.map-tools .el-button {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.map-tools .el-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
}

.map-tools .el-button:active:not(:disabled) {
  transform: translateY(0);
}

.map-tools .el-button.active {
  background-color: #409eff;
  color: #fff;
  border-color: #409eff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
}

.zoom-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.zoom-controls .el-button {
  width: 32px;
  height: 32px;
  font-size: 14px;
}
</style>
