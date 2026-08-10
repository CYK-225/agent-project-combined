<template>
  <div class="gui-config-container">
    <div class="workspace-wrapper">
      <!-- 左侧 VNC 工作区 (65%) -->
      <div class="left-panel">
        <FarmVncWorkspace ref="vncWorkspaceRef" :on-refresh="handleRefreshList" />
      </div>
      <!-- 右侧控制台 (35%) -->
      <div class="right-panel">
        <FarmProfilePanel
          ref="profilePanelRef"
          @launch-env="handleLaunchEnv"
          @confirm-login="handleConfirmLogin"
          @destroy-env="handleDestroyEnv"
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
 * 处理拉起环境事件
 * @param {Object} params - { profileName, targetUrl }
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
 * 处理确认登录事件
 */
const handleConfirmLogin = () => {
  if (vncWorkspaceRef.value) {
    vncWorkspaceRef.value.handleConfirmLogin()
  }
}

/**
 * 处理销毁环境事件
 */
const handleDestroyEnv = () => {
  if (vncWorkspaceRef.value) {
    vncWorkspaceRef.value.handleDestroyEnv()
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
