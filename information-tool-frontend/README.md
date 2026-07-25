# 入驻点位数据获取工具

基于 Vue 3 + Element Plus + 百度地图的点位数据可视化管理系统。

## 项目结构

```
poi-data-tool/
├── public/                 # 静态资源
├── src/
│   ├── api/               # API 接口封装
│   │   ├── building.js    # 楼宇相关 API
│   │   ├── request.js     # Axios 请求封装
│   │   └── index.js       # API 导出
│   ├── assets/            # 资源文件
│   ├── components/        # 组件
│   │   ├── Map/           # 地图组件
│   │   │   ├── BaiduMap.vue       # 地图主容器
│   │   │   ├── SearchBar.vue      # 搜索栏
│   │   │   ├── MapTools.vue       # 地图工具
│   │   │   ├── CircleRadiusInput.vue  # 圈选半径输入
│   │   │   └── index.js
│   │   ├── Sidebar/       # 左侧导航栏
│   │   │   └── index.vue  # 收藏夹组件
│   │   └── BuildingDrawer/    # 右侧楼宇详情抽屉
│   │       ├── index.vue      # 抽屉主组件
│   │       ├── CompanyList.vue    # 公司列表
│   │       └── CompanyEditDialog.vue  # 公司编辑模态框
│   ├── router/            # 路由配置
│   │   └── index.js
│   ├── stores/            # Pinia 状态管理
│   │   ├── map.js         # 地图状态
│   │   └── index.js
│   ├── utils/             # 工具函数
│   │   ├── constants.js   # 常量定义
│   │   └── mapLoader.js   # 百度地图加载器
│   ├── views/             # 页面视图
│   │   └── Home/
│   │       └── index.vue
│   ├── App.vue            # 根组件
│   └── main.js            # 入口文件
├── .env                   # 环境变量
├── vite.config.js         # Vite 配置
└── package.json
```

## 技术栈

- **框架**: Vue 3 + Composition API
- **UI组件库**: Element Plus
- **地图SDK**: 百度地图 JavaScript API GL
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP客户端**: Axios
- **构建工具**: Vite

## 功能特性

### 1. 地图驱动设计
- 全屏可交互地图作为底层视图
- 悬浮搜索栏（左上角）
- 右侧悬浮工具栏（圈选、缩放、定位）

### 2. 关键词检索
- 输入区域关键词，地图平滑过渡（FlyTo）定位
- 自动调整层级并居中显示目标区域

### 3. 空间圈选
- 点击圈选工具，鼠标变为十字准星
- 单击地图确立中心原点
- 输入搜索半径，渲染圆形覆盖区
- 搜索范围内楼宇并渲染标记

### 4. POI 去重渲染
- 使用 Map 数据结构建立 POI 状态机
- 基于 uid 强制去重校验
- 避免多次圈选导致的图钉重影

### 5. 楼宇详情抽屉
- 点击楼宇 Marker 滑出右侧抽屉
- 展示楼宇名称、地址、物业信息
- 收藏按钮快捷操作
- 一键批量初始化功能

### 6. 公司列表与状态管理
- 三种业务状态标识：
  - 🔴 未初始化（红色）
  - 🔵 已初始化（蓝色）
  - 🟢 已完善（绿色）
- 逐行异步渲染，实时更新

### 7. 企业档案编辑
- 居中模态框表单设计
- 折叠面板分类（基础工商信息、地推情报、餐饮痛点）
- 保存与确认完善双操作

### 8. 收藏夹功能
- 左侧导航栏展示收藏列表
- 点击收藏项地图飞跃定位
- 自动展开楼宇详情

## 快速开始

### 1. 配置百度地图密钥

编辑 `.env` 文件，填入你的百度地图 API 密钥：

```env
VITE_BAIDU_MAP_AK=your_baidu_map_ak_here
VITE_API_BASE_URL=http://localhost:8080/api
```

> 获取 AK：[百度地图开放平台](https://lbsyun.baidu.com/)

### 2. 安装依赖

```bash
npm install
```

### 3. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 4. 构建生产环境

```bash
npm run build
```

## 关键设计说明

### POI 去重机制
```javascript
// 使用 Map 数据结构确保楼宇去重
const poiMap = ref(new Map())

function addOrUpdateBuilding(building) {
  const existing = poiMap.value.get(building.uid)
  if (existing) {
    // 合并数据
    poiMap.value.set(building.uid, { ...existing, ...building })
  } else {
    // 新增
    poiMap.value.set(building.uid, building)
  }
}
```

### 异步初始化加载
```javascript
// 防止全局阻塞，按钮独立 Loading
async function handleBatchInitialize() {
  mapStore.setInitializing(true)
  // 异步处理，实时更新列表
  // 通过 WebSocket 或轮询获取进度
  mapStore.setInitializing(false)
}
```

## API 接口约定

### 楼宇相关

| 接口 | 方法 | 说明 |
|------|------|------|
| /building/search | GET | 关键词搜索区域 |
| /building/search/circle | POST | 圈选搜索楼宇 |
| /building/:uid | GET | 获取楼宇详情 |
| /building/:uid/companies | GET | 获取公司列表 |
| /building/:uid/initialize | POST | 批量初始化 |
| /building/:uid/progress | GET | 获取初始化进度 |

### 公司相关

| 接口 | 方法 | 说明 |
|------|------|------|
| /company/:id | PUT | 更新公司信息 |
| /company/:id/complete | POST | 确认完善 |

## 浏览器支持

- Chrome 80+
- Firefox 75+
- Safari 13+
- Edge 80+

## License

MIT
