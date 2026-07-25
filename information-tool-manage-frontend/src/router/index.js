import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/prompts',
    children: [
      {
        path: 'prompts',
        name: 'Prompts',
        component: () => import('@/views/prompts/index.vue'),
        meta: { title: '提示词管理', icon: 'Document' }
      },
      {
        path: 'cookie-pool',
        name: 'CookiePool',
        component: () => import('@/views/cookie-pool/index.vue'),
        meta: { title: 'Cookie 池管理', icon: 'Key' }
      },
      {
        path: 'gui-manage',
        name: 'GuiManage',
        component: () => import('@/views/gui-manage/index.vue'),
        meta: { title: 'GUI可视化管理', icon: 'Odometer' }
      },
      {
        path: 'task-monitor',
        name: 'TaskMonitor',
        component: () => import('@/views/task-monitor/index.vue'),
        meta: { title: '任务监控', icon: 'DataBoard' }
      },
      {
        path: 'gui-config',
        name: 'GuiConfig',
        component: () => import('@/views/gui-config/index.vue'),
        meta: { title: 'GUI配置管理', icon: 'Setting' }
      },
      {
        path: 'v3-task-manage',
        name: 'V3TaskManage',
        component: () => import('@/views/v3-task-manage/index.vue'),
        meta: { title: 'V3自动化任务管理', icon: 'Promotion' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
