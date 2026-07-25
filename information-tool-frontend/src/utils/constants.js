/**
 * 常量定义
 */

// 公司状态枚举
export const COMPANY_STATUS = {
  UNINITIALIZED: 'uninitialized', // 🔴 未初始化
  INITIALIZED: 'initialized',     // 🔵 已初始化
  DEEPINITIALIZED: 'deepInitialized', //  已深度初始化
  COMPLETED: 'completed',         // 🟢 已完善
}

// 状态标签映射
export const STATUS_LABELS = {
  [COMPANY_STATUS.UNINITIALIZED]: { text: '未初始化', type: 'danger' },
  [COMPANY_STATUS.INITIALIZED]: { text: '已初始化', type: 'primary' },
  [COMPANY_STATUS.DEEPINITIALIZED]: { text: '已深度初始化', type: 'primary' },
  [COMPANY_STATUS.COMPLETED]: { text: '已完善', type: 'success' },
}

// 状态颜色
export const STATUS_COLORS = {
  [COMPANY_STATUS.UNINITIALIZED]: '#F56C6C',
  [COMPANY_STATUS.INITIALIZED]: '#207bd6',
  [COMPANY_STATUS.DEEPINITIALIZED]: '#1562ae',
  [COMPANY_STATUS.COMPLETED]: '#67C23A',
}

// 默认地图配置
export const MAP_CONFIG = {
  defaultCenter: { lng: 113.270793, lat: 23.135308 }, // 广东省广州市
  defaultZoom: 12,
  minZoom: 5,
  maxZoom: 20,
}

// 圈选默认半径（米）
export const DEFAULT_CIRCLE_RADIUS = 1000

// 搜索半径选项
export const RADIUS_OPTIONS = [
  { label: '500米', value: 500 },
  { label: '1公里', value: 1000 },
  { label: '2公里', value: 2000 },
  { label: '3公里', value: 3000 },
  { label: '5公里', value: 5000 },
]
