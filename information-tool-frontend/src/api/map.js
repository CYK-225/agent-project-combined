import request from './request';

// 1. 搜索建筑(baidu)
export const searchBuildingsApi = (data) => {
  return request({ url: '/baidu/map/search/buildings', method: 'post', data });
};

// 2. 搜索公司列表(点击地图楼宇图片后触发)
export const searchCompanyListApi = (buildingLocation, buildingUid) => {
  return request({ url: `/baidu/map/search/companyList/${buildingLocation}/${buildingUid}`, method: 'post' });
};

// 3. 获取楼宇基本信息 (业务数据库)
export const getBuildingDetailApi = (uid) => {
  return request({ url: `/baidu/map/${uid}`, method: 'get' });
};

// 4. 获取楼宇下的公司列表 (业务数据库)
export const getCompaniesByBuildingUidApi = (buildingUid) => {
  return request({ url: `/baidu/map/listByBuilding/${buildingUid}`, method: 'get' });
};

// ========== 收藏相关接口 ==========

// 5. 获取用户收藏列表
export const getFavoriteListApi = (userId) => {
  userId = 1;
  return request({ url: `baidu/map/favorite/${userId}`, method: 'get' });
};

// 6. 添加楼宇收藏
export const addFavoriteApi = (userId, buildingUid) => {
  userId = 1;
  return request({ url: `baidu/map/favorite/${userId}/${buildingUid}`, method: 'post' });
};

// 7. 移除楼宇收藏
export const removeFavoriteApi = (userId, buildingUid) => {
  userId = 1;
  return request({ url: `baidu/map/favorite/${userId}/${buildingUid}`, method: 'delete' });
};

// ========== 楼宇深度初始化接口 ==========

// 8. 深度初始化楼宇（用于获取楼宇下所有公司的工商信息）
export const initBuildingApi = (buildingUid, buildingLocation, clientId) => {
  return request({
    url: `/baidu/map/building/init?buildingUid=${buildingUid}&buildingLocation=${buildingLocation}&clientId=${clientId}`,
    method: 'get'
  });
};

// 9. SSE 连接地址（返回完整 URL 字符串，用于 EventSource）
export const getSseConnectUrl = (clientId) => {
  const baseURL = 'http://8.129.128.167:8081/api';
  return `${baseURL}/docker/ens_controller/sse/connect/${clientId}`;
};

/**
 * 下发一键初始化任务 (基础 ENS) - 已改造为 POST
 */
export function initBuildingEnsApi(buildingUid, userId, companyList) {
  return request({
    url: '/baidu/map/building/ensInit',
    method: 'post',
    // POST 请求使用 data 传递，直接传纯数组，不要 join
    data: {
      buildingUid,
      userId,
      companyList 
    }
  });
}

/**
 * 下发深度初始化任务 (AI + ENS) - 已改造为 POST
 */
export function initBuildingDeepApi(buildingUid, userId, companyList) {
  return request({
    url: '/baidu/map/building/init',
    method: 'post',
    data: {
      buildingUid,
      userId,
      companyList
    }
  });
}