<template>
  <div class="gui-config-container">
    <div class="workspace-wrapper">
      <!-- 左侧 VNC 工作区 (65%) -->
      <div class="left-panel">
        <FarmVncWorkspace ref="vncWorkspaceRef" :on-refresh="handleRefreshList" @saved="handleSaved" />
      </div>
      <!-- 右侧控制台 (35%) -->
      <div class="right-panel">
        <FarmProfilePanel
          ref="profilePanelRef"
          @launch-env="handleLaunchEnv"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import FarmVncWorkspace from './components/FarmVncWorkspace.vue'
import FarmProfilePanel from './components/FarmProfilePanel.vue'

const vncWorkspaceRef = ref(null)
const profilePanelRef = ref(null)

/**
 * 处理创建养号会话事件
 * @param {Object} params - { profileName }
 */
const handleLaunchEnv = (params) => {
  if (vncWorkspaceRef.value) {
    vncWorkspaceRef.value.launchEnvironment(params)
  }
}

/**
 * 刷新右侧列表
 */
const handleRefreshList = () => {
  if (profilePanelRef.value) {
    profilePanelRef.value.refreshList()
  }
}

/**
 * 保存成功后引导用户标记已登录网址
 * @param {string} profileName - 已保存的配置名
 */
const handleSaved = (profileName) => {
  if (profilePanelRef.value) {
    profilePanelRef.value.openMarkDialog(profileName)
  }
}
</script>

<style scoped>
.gui-config-container {
  width: 100%;
  height: calc(100vh - 120px);
  padding: 16px;
  box-sizing: border-box;
  background-color: #f5f7fa;
}

.workspace-wrapper {
  display: flex;
  width: 100%;
  height: 100%;
  gap: 16px;
}

.left-panel {
  flex: 0 0 65%;
  height: 100%;
}

.right-panel {
  flex: 0 0 35%;
  height: 100%;
  overflow-y: auto;
}
</style>
