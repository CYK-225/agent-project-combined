/**
 * 百度地图加载器
 * 动态加载百度地图 JavaScript API
 */

const BAIDU_MAP_AK = import.meta.env.VITE_BAIDU_MAP_AK

/**
 * 加载百度地图脚本
 * @returns {Promise} BMapGL 对象
 */
export function loadBaiduMap() {
  return new Promise((resolve, reject) => {
    // 如果已加载，直接返回
    if (window.BMapGL) {
      resolve(window.BMapGL)
      return
    }

    // 如果正在加载，等待加载完成
    if (window._baiduMapLoading) {
      const checkLoaded = setInterval(() => {
        if (window.BMapGL) {
          clearInterval(checkLoaded)
          resolve(window.BMapGL)
        }
      }, 100)
      return
    }

    window._baiduMapLoading = true

    // 创建 script 标签加载地图
    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = `http://api.map.baidu.com/api?type=webgl&v=1.0&ak=${BAIDU_MAP_AK}&callback=_baiduMapCallback`
    script.onerror = () => {
      window._baiduMapLoading = false
      reject(new Error('百度地图加载失败'))
    }

    // 全局回调函数
    window._baiduMapCallback = () => {
      window._baiduMapLoading = false
      resolve(window.BMapGL)
    }

    document.head.appendChild(script)
  })
}

/**
 * 加载百度地图绘图工具库
 * @returns {Promise}
 */
export function loadDrawingManager() {
  return new Promise((resolve, reject) => {
    if (window.BMapGLLib && window.BMapGLLib.DrawingManager) {
      resolve(window.BMapGLLib.DrawingManager)
      return
    }

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = 'http://api.map.baidu.com/library/DrawingManager/1.4/src/DrawingManager_min.js'
    script.onload = () => resolve(window.BMapGLLib.DrawingManager)
    script.onerror = () => reject(new Error('绘图工具库加载失败'))
    document.head.appendChild(script)
  })
}

/**
 * 加载百度地图搜索工具库
 * @returns {Promise}
 */
export function loadSearchLibrary() {
  return new Promise((resolve, reject) => {
    if (window.BMapGL && window.BMapGL.LocalSearch) {
      resolve(window.BMapGL.LocalSearch)
      return
    }

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = 'http://api.map.baidu.com/library/SearchInfoWindow/1.4/src/SearchInfoWindow_min.js'
    script.onload = () => resolve(window.BMapGL.LocalSearch)
    script.onerror = () => reject(new Error('搜索工具库加载失败'))
    document.head.appendChild(script)
  })
}
