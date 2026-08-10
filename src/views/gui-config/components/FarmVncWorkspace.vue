<template>
  <div class="farm-vnc-workspace">
    <!-- 空闲态 -->
    <div v-if="!guiConfigStore.isVncActive && !loading" class="idle-state">
      <el-icon :size="80" color="#c0c4cc"><Monitor /></el-icon>
      <p class="idle-text">请在右侧选择配置并拉起目标网站环境</p>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" v-loading="true" element-loading-text="正在分配独立安全容器并初始化浏览器环境..." class="loading-state">
    </div>

    <!-- 工作态 -->
    <div v-else class="working-state">
      <div ref="vncContainer" class="vnc-container"></div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  onRefresh: {
    type: Function,
    default: null
  }
})

import { ref, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import RFB from '@novnc/novnc'
import { useGuiConfigStore } from '@/store/guiConfigStore'
import { setupFarmEnvironment, confirmFarmProfile, stopContainer } from '@/api/guiConfig'
import { v4 as uuidv4 } from 'uuid'

const guiConfigStore = useGuiConfigStore()

// VNC 相关
const VNC_HOST = '8.163.67.126'

const vncContainer = ref(null)
const loading = ref(false)
let rfb = null

/**
 * 拉起 VNC 环境 (V3版本)
 * @param {Object} params - { profileName, targetUrl }
 */
const launchEnvironment = async ({ profileName, targetUrl }) => {
  if (loading.value || guiConfigStore.isVncActive) {
    return
  }

  loading.value = true
  try {
    // 1. 生成clientId
    const clientId = uuidv4()

    // 2. 组装 Payload (V3接口参数，端口由后端自动分配)
    const payload = {
      profileName,
      clientId
    }

    // 3. 调用V3连接接口
    const result = await setupFarmEnvironment(payload)

    // 4. 解析V3返回数据（数据在data字段下）
    const actualContainerId = result.data.containerId
    const actualVncPort = result.data.vncPort
    const taskId = result.data.taskId

    // 5. 更新 Store，激活工作态标志
    guiConfigStore.setVncEnv(
      profileName,
      targetUrl,
      actualContainerId,
      actualVncPort,
      taskId,
      clientId
    )

    loading.value = false
    await nextTick()

    // 6. 使用后端实际分配的真实端口建立 VNC 连接
    await connectVnc(actualVncPort)

    ElMessage.success('环境拉起成功，请完成登录操作')
  } catch (error) {
    console.error('拉起环境失败:', error)
    ElMessage.error(`拉起环境失败: ${error.message || '未知错误'}`)
    loading.value = false // 出错时也必须关闭 loading
  }
}

/**
 * 建立 VNC 连接
 * @param {number} port - VNC 端口
 */
const connectVnc = async (port) => {
  return new Promise((resolve, reject) => {
    try {
      const url = `ws://${VNC_HOST}:${port}/websockify`
      rfb = new RFB(vncContainer.value, url, {
        credentials: { password: '' }
      })

      rfb.addEventListener('connect', () => {
        console.log('[VNC] 连接成功')
        loading.value = false
        resolve()
      })

      rfb.addEventListener('disconnect', () => {
        console.log('[VNC] 连接断开')
      })

      rfb.addEventListener('credentialsrequired', () => {
        console.log('[VNC] 需要凭证')
        rfb.sendCredentials({ password: '' })
      })

      rfb.addEventListener('error', (err) => {
        console.error('[VNC] 连接错误:', err)
        reject(err)
      })

      // 设置 VNC 缩放模式
      rfb.scaleViewport = true
      rfb.resizeSession = false
    } catch (error) {
      reject(error)
    }
  })
}

/**
 * 清理函数：销毁 VNC 实例并清空 Store
 */
const cleanup = async () => {
  // 断开 VNC 连接
  if (rfb) {
    try {
      rfb.disconnect()
    } catch (error) {
      console.error('[VNC] 断开连接时出错:', error)
    }
    rfb = null
  }

  // 清空 Store
  guiConfigStore.clearVncEnv()
  loading.value = false
}

/**
 * 确认登录完成
 */
const handleConfirmLogin = async () => {
  try {
    await ElMessageBox.confirm(
      '确认已完成登录并保存状态吗？',
      '确认操作',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 调用确认接口
    await confirmFarmProfile({
      profileName: guiConfigStore.currentProfile,
      targetUrl: guiConfigStore.currentUrl,
      containerId: guiConfigStore.containerId
    })

    ElMessage.success('登录状态已保存')

    // 通知右侧刷新列表
    if (props.onRefresh) {
      props.onRefresh()
    }

    // 清理环境
    await cleanup()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('确认登录失败:', error)
      ElMessage.error(`确认登录失败: ${error.message || '未知错误'}`)
    }
  }
}

/**
 * 销毁环境/取消
 */
const handleDestroyEnv = async () => {
  try {
    await ElMessageBox.confirm(
      '确认要销毁当前环境吗？未保存的登录状态将丢失。',
      '确认销毁',
      {
        confirmButtonText: '确认销毁',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 调用停止容器接口
    if (guiConfigStore.containerId) {
      await stopContainer(guiConfigStore.containerId)
      ElMessage.success('环境已销毁')
    }

    // 通知右侧刷新列表
    if (props.onRefresh) {
      props.onRefresh()
    }

    // 清理环境
    await cleanup()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('销毁环境失败:', error)
      ElMessage.error(`销毁环境失败: ${error.message || '未知错误'}`)
    }
  }
}

/**
 * 生命周期钩子：组件卸载前清理
 */
onBeforeUnmount(async () => {
  // 如果 Store 中仍有 containerId，静默调用 stopContainer
  if (guiConfigStore.containerId) {
    try {
      await stopContainer(guiConfigStore.containerId)
    } catch (error) {
      console.error('[onBeforeUnmount] 清理容器失败:', error)
    }
  }

  // 清理 VNC 连接
  await cleanup()
})

// 暴露方法给父组件
defineExpose({
  launchEnvironment,
  handleConfirmLogin,
  handleDestroyEnv
})
</script>

<style scoped>
.farm-vnc-workspace {
  width: 100%;
  height: 100%;
  background-color: #000;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

/* 空闲态 */
.idle-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #1a1a1a;
  color: #c0c4cc;
}

.idle-text {
  margin-top: 16px;
  font-size: 16px;
  color: #909399;
}

/* 加载态 */
.loading-state {
  width: 100%;
  height: 100%;
  background-color: #1a1a1a;
}

/* 工作态 */
.working-state {
  width: 100%;
  height: 100%;
  position: relative;
}

.vnc-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #000;
}

.vnc-container :deep(canvas) {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
