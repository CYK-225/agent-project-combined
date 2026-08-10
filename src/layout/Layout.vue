<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarCollapsed ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <span v-if="!sidebarCollapsed">AI 数据管理</span>
        <el-icon v-else><Monitor /></el-icon>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/prompts">
          <el-icon><Document /></el-icon>
          <template #title>提示词管理</template>
        </el-menu-item>
        <el-menu-item index="/cookie-pool">
          <el-icon><Key /></el-icon>
          <template #title>Cookie 池管理</template>
        </el-menu-item>
        <el-menu-item index="/gui-manage">
          <el-icon><Odometer /></el-icon>
          <template #title>GUI可视化管理</template>
        </el-menu-item>
        <el-menu-item index="/task-monitor">
          <el-icon><DataBoard /></el-icon>
          <template #title>任务监控</template>
        </el-menu-item>
        <el-menu-item index="/gui-config">
          <el-icon><Setting /></el-icon>
          <template #title>GUI配置管理</template>
        </el-menu-item>
        <el-menu-item index="/v3-task-manage">
          <el-icon><Promotion /></el-icon>
          <template #title>V3自动化任务管理</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-collapse-btn" @click="toggleSidebar">
        <el-icon>
          <Fold v-if="!sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <breadcrumb />
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              管理员 <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人信息</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, Key, Fold, Expand, ArrowDown, Monitor, Odometer, DataBoard, Setting, Promotion } from '@element-plus/icons-vue'
import { useAppStore, useUserStore } from '@/store'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleLogout = () => {
  userStore.clearToken()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  position: relative;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
}

.sidebar-collapse-btn {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #bfcbd9;
  background-color: #263445;
  transition: all 0.3s;
}

.sidebar-collapse-btn:hover {
  background-color: #1f2d3d;
  color: #409EFF;
}

.sidebar-collapse-btn .el-icon {
  font-size: 18px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
  outline: none; /* 新增：去除元素的默认焦点边框 */
}

/* 新增：强制去除 Element Plus 下拉触发器的 focus 样式 */
:deep(.el-tooltip__trigger:focus-visible),
:deep(.el-tooltip__trigger:focus) {
  outline: none !important;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

:deep(.el-menu) {
  border-right: none;
}

.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
