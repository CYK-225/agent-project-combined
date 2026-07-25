import request from './request';

// 1. 批量获取公司工商信息
export const submitBatchScanApi = (data) => {
  return request({
    url: '/baidu/map/building/ensInit',
    method: 'get',
    data 
  });
};

// 2. 单个获取公司工商信息
export const submitSingleScanApi = (clientId, configName, companyName) => {
  return request({
    url: `/docker/ens_controller/scan/single/${clientId}/${configName}/${encodeURIComponent(companyName)}`,
    method: 'post'
  });
};