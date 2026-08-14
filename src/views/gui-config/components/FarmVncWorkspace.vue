<template>
  <div class="farm-vnc-workspace">
    <!-- 空闲态 -->
    <div v-if="!guiConfigStore.isVncActive && !loading" class="idle-state">
      <el-icon :size="80" color="#c0c4cc"><Monitor /></el-icon>
      <p class="idle-text">在右侧输入配置名称并点击"新建养号"，系统将自动拉起浏览器进入百度</p>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" v-loading="true" element-loading-text="正在分配养号容器并自动打开百度..." class="loading-state">
    </div>

    <!-- 工作态 -->
    <div v-else class="working-state">
      <div class="session-bar">
        <div class="session-info">
          <span class="session-profile">{{ guiConfigStore.currentProfile }}</span>
          <el-tag size="small" :type="countdownType">剩余 {{ formatRemain }}</el-tag>
          <el-tag v-if="!guiConfigStore.baiduOpened" size="small" type="warning">百度未自动打开，请手动在浏览器访问</el-tag>
        </div>
        <div class="session-actions">
          <el-button
            type="info"
            size="small"
            plain
            :icon="RefreshRight"
            :loading="openingBrowser"
            :disabled="isActionBusy"
            @click="handleOpenBrowser"
          >
            重新打开浏览器
          </el-button>
          <el-button
            type="success"
            size="small"
            :icon="CircleCheck"
            :loading="saving"
            :disabled="isActionBusy"
            @click="handleSaveProfile"
          >
            保存配置
          </el-button>
          <el-button
            type="danger"
            size="small"
            plain
            :icon="CircleClose"
            :loading="canceling"
            :disabled="isActionBusy"
            @click="handleCancelSession"
          >
            取消
          </el-button>
        </div>
      </div>
      <div class="vnc-container">
        <div ref="vncContainer" class="vnc-screen"></div>
      </div>
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

const emit = defineEmits(['saved'])

import { ref, computed, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, CircleCheck, CircleClose, RefreshRight } from '@element-plus/icons-vue'
import RFB from '@novnc/novnc'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useGuiConfigStore } from '@/store/guiConfigStore'
import { createFarmSession, saveFarmSession, cancelFarmSession, openFarmBrowser, getFarmProfiles, getV3SseUrl } from '@/api/guiConfig'
import { v4 as uuidv4 } from 'uuid'

const guiConfigStore = useGuiConfigStore()

// VNC 相关
const VNC_HOST = '8.163.67.126'
const SESSION_TTL = 60 * 60 * 1000 // 养号会话最长 1 小时，后端到时强制回收

const vncContainer = ref(null)
const loading = ref(false)
let rfb = null

// SSE 相关
let sseAbortController = null

// ========== 操作防重入（请求进行中禁用全部操作按钮，避免重复调用接口） ==========
const saving = ref(false)
const canceling = ref(false)
const openingBrowser = ref(false)
const isActionBusy = computed(() => saving.value || canceling.value || openingBrowser.value)

// ========== 统一错误处理 ==========

/**
 * 处理 farm/* 系列接口的业务错误
 * @param {Object} res - { code, message }，503=容器池已满
 */
const handleFarmError = (res, fallback) => {
  if (res?.code === 503) {
    ElMessage.warning(`容器池已满（${res.message || '代理池已满'}），请稍后重试`)
    return
  }
  ElMessage.error(res?.message || fallback)
}

// ========== 创建养号会话 ==========

/**
 * 创建养号会话并自动拉起 VNC
 * @param {Object} params - { profileName }（VNC 端口由后端自动分配）
 */
const launchEnvironment = async ({ profileName }) => {
  if (loading.value || guiConfigStore.isVncActive) {
    return
  }

  loading.value = true
  try {
    // 1. 生成 clientId 并先建立 SSE（失败不阻塞创建流程）
    const clientId = uuidv4()
    connectSse(clientId)

    // 2. 调用创建养号会话接口（端口由后端自动分配）
    const payload = { profileName, clientId }
    const res = await createFarmSession(payload)

    // 3. 校验统一响应码
    if (!res || res.code !== 200) {
      handleFarmError(res, '创建养号会话失败')
      disconnectSse()
      loading.value = false
      return
    }

    const data = res.data || {}
    if (!data.containerId || !data.vncPort) {
      throw new Error('返回数据缺少必要字段 (containerId/vncPort)')
    }

    // 4. 更新 Store，激活工作态
    guiConfigStore.setVncEnv({
      profile: profileName,
      cid: data.containerId,
      port: data.vncPort,
      tid: data.taskId || '',
      cid2: clientId,
      opened: data.baiduOpened !== false,
      startAt: Date.now()
    })

    loading.value = false
    await nextTick()

    // 5. 使用后端实际分配的端口建立 VNC 连接
    await connectVnc(data.vncPort)

    // 6. 启动 1 小时超时倒计时并提示
    startCountdown()
    ElMessage.success(data.message || '养号环境已就绪，请在 VNC 中进行登录/养号操作')
    if (data.baiduOpened === false) {
      ElMessage.warning('自动打开百度失败，请手动在浏览器访问（容器仍可操作）')
    }
  } catch (error) {
    console.error('创建养号会话失败:', error)
    ElMessage.error(`创建养号会话失败: ${error.message || '未知错误'}`)
    disconnectSse()
    loading.value = false // 出错时也必须关闭 loading
  }
}

// ========== 保存 / 取消 / 重新拉起 ==========

/**
 * 重新拉起浏览器（复用 farm/create 自动进百度链路）
 * 404=会话已结束，提示后清理本地会话
 */
const handleOpenBrowser = async () => {
  const { containerId } = guiConfigStore
  if (!containerId || openingBrowser.value) return

  openingBrowser.value = true
  try {
    const res = await openFarmBrowser({ containerId })
    if (!res || res.code !== 200) {
      if (res?.code === 404) {
        ElMessage.warning(res.message || '养号会话不存在或已结束，请重新创建养号环境')
        await cleanup()
        return
      }
      handleFarmError(res, '重新打开浏览器失败')
      return
    }
    ElMessage.success(res.data?.message || '浏览器已重新拉起')
    if (res.data?.baiduOpened === false) {
      ElMessage.warning('自动打开百度失败，可点击重试或手动在浏览器访问')
    }
  } catch (error) {
    console.error('重新打开浏览器失败:', error)
    ElMessage.error(`重新打开浏览器失败: ${error.message || '未知错误'}`)
  } finally {
    openingBrowser.value = false
  }
}

/**
 * 保存配置（落库 + 断开 VNC，不可逆）
 */
const handleSaveProfile = async () => {
  const { currentProfile, containerId } = guiConfigStore
  if (!containerId || saving.value) return

  try {
    await ElMessageBox.confirm(
      '保存后配置将写入数据库且 VNC 连接会断开，无法再对该会话进行操作。确认保存？',
      '保存配置',
      {
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return // 用户取消
  }

  saving.value = true
  try {
    const res = await saveFarmSession({ profileName: currentProfile, containerId })
    if (res && res.code === 200) {
      ElMessage.success(res.data?.message || '配置已保存')
    } else {
      // 后端"先落库后断连"：请求失败/超时时数据库可能已写入，先查列表确认，不盲目重试
      const saved = await isProfileSaved(currentProfile)
      if (!saved) {
        handleFarmError(res, '保存配置失败，请重试')
        return
      }
      ElMessage.success('配置已保存成功，VNC 连接已断开')
    }
  } catch (error) {
    console.error('保存配置失败:', error)
    // 网络异常时同样先确认是否已落库，避免用户盲目重试造成误解
    const saved = await isProfileSaved(currentProfile)
    if (!saved) {
      ElMessage.error(`保存配置失败: ${error.message || '未知错误'}`)
      return
    }
    ElMessage.success('配置已保存成功，VNC 连接已断开')
  } finally {
    saving.value = false
  }

  // 保存成功后的联动：刷新列表 + 清理本地会话 + 引导标记已登录网址
  if (props.onRefresh) {
    props.onRefresh()
  }
  await cleanup()
  emit('saved', currentProfile)
}

/**
 * 查询配置列表确认配置是否已落库（save 失败/超时后用于判断真实保存结果）
 * @param {string} profileName - 配置名
 * @returns {Promise<boolean>}
 */
const isProfileSaved = async (profileName) => {
  try {
    const res = await getFarmProfiles()
    if (res?.code === 200) {
      return Array.isArray(res.data) && res.data.some(p => p.profileName === profileName)
    }
  } catch (error) {
    console.error('查询配置列表失败:', error)
  }
  return false
}

/**
 * 取消养号会话（仅销毁容器，不落库）
 */
const handleCancelSession = async () => {
  const { containerId } = guiConfigStore
  if (!containerId || canceling.value) return

  try {
    await ElMessageBox.confirm(
      '取消后容器将被销毁，本次养号操作将全部丢失。确认取消？',
      '取消养号会话',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        type: 'warning'
      }
    )
  } catch {
    return // 用户取消
  }

  canceling.value = true
  try {
    const res = await cancelFarmSession({ containerId })
    if (!res || res.code !== 200) {
      handleFarmError(res, '取消养号会话失败')
      return
    }
    ElMessage.success(res.data?.message || '养号会话已取消')

    // 刷新配置列表 + 清理本地会话状态
    if (props.onRefresh) {
      props.onRefresh()
    }
    await cleanup()
  } catch (error) {
    console.error('取消养号会话失败:', error)
    ElMessage.error(`取消养号会话失败: ${error.message || '未知错误'}`)
  } finally {
    canceling.value = false
  }
}

// ========== 1 小时超时倒计时 ==========

const remainSeconds = ref(0)
let countdownTimer = null

const formatRemain = computed(() => {
  const s = remainSeconds.value
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const sec = String(s % 60).padStart(2, '0')
  return `${h}:${m}:${sec}`
})

const countdownType = computed(() => {
  if (remainSeconds.value <= 300) return 'danger'
  if (remainSeconds.value <= 900) return 'warning'
  return 'info'
})

const tick = () => {
  const remain = Math.max(0, Math.floor((guiConfigStore.sessionStartAt + SESSION_TTL - Date.now()) / 1000))
  remainSeconds.value = remain
  if (remain <= 0) {
    stopCountdown()
    ElMessage.warning('养号会话已超过 1 小时，后端将强制回收容器，本次操作可能丢失')
    cleanup()
  }
}

const startCountdown = () => {
  stopCountdown()
  tick()
  countdownTimer = setInterval(tick, 1000)
}

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// ========== SSE 连接 ==========

/**
 * 建立 SSE 连接（复用 /api/hr/v3/sse/{clientId}）
 * 养号场景无 AI 参与，仅监听任务事件做异常提示
 */
const connectSse = (clientId) => {
  disconnectSse()

  sseAbortController = new AbortController()
  const token = localStorage.getItem('token') || ''

  fetchEventSource(getV3SseUrl(clientId), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    signal: sseAbortController.signal,

    onmessage(event) {
      if (event.event !== 'task_update') return
      try {
        const data = JSON.parse(event.data)
        console.log('[SSE] 养号会话事件:', data)
        if (['FAILED', 'TIMEOUT', 'ABORTED'].includes(data.status)) {
          ElMessage.warning(`养号会话异常: ${data.errorMessage || data.message || data.status}`)
        }
      } catch (err) {
        console.error('[SSE] 解析数据失败:', err)
      }
    },

    onerror(err) {
      console.error('[SSE] 连接错误:', err)
      // 主动 abort 时抛出错误停止自动重试
      if (sseAbortController?.signal.aborted) {
        throw err
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      console.error('[SSE] 连接异常:', err)
    }
  })
}

/**
 * 断开 SSE 连接
 */
const disconnectSse = () => {
  if (sseAbortController) {
    sseAbortController.abort()
    sseAbortController = null
  }
}

// ========== VNC 连接 ==========

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

      // 设置 VNC 缩放模式（与 GUI 可视化管理模块保持一致）
      rfb.scaleViewport = true
      rfb.resizeSession = true
    } catch (error) {
      reject(error)
    }
  })
}

/**
 * 清理函数：断开 VNC / SSE、停止倒计时并清空 Store
 * 注意：不调用任何后端接口——养号会话由后端 1 小时强制回收，避免误销毁
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

  // 断开 SSE + 停止倒计时
  disconnectSse()
  stopCountdown()

  // 清空 Store
  guiConfigStore.clearVncEnv()
  loading.value = false
}

/**
 * 生命周期钩子：组件卸载前清理（仅断开本地连接，不销毁容器）
 */
onBeforeUnmount(() => {
  cleanup()
})

// 暴露方法给父组件
defineExpose({
  launchEnvironment
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
  display: flex;
  flex-direction: column;
  position: relative;
}

/* 会话操作栏 */
.session-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background-color: #2b2b2b;
  border-bottom: 1px solid #3a3a3a;
}

.session-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.session-profile {
  font-size: 14px;
  font-weight: 600;
  color: #e5eaf3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* VNC 画面区（样式对齐 gui-manage/VncViewer） */
.vnc-container {
  flex: 1;
  position: relative;
  min-height: 400px;
  background: #1a1a2e;
  overflow: hidden;
}

.vnc-screen {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.vnc-screen :deep(canvas) {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
