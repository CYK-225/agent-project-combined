import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例配置
 */

// 强制写死，彻底隔绝本地 .env 文件的错误污染
const baseURL = 'http://8.129.128.167:8081/api'

const service = axios.create({
  baseURL,
  timeout: 30000, // 30秒超时
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 可以在这里添加 token 等认证信息
    // const token = localStorage.getItem('token')
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`
    // }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    
    // 检查是否是百度地图API直接返回的数据（部分接口如果透传了百度的原始status，保留此判断兼容）
    if (res.status !== undefined && res.code === undefined) {
      if (res.status !== 0) {
        ElMessage.error(res.message || '百度地图API请求失败');
        return Promise.reject(new Error(res.message || '百度地图API请求失败'));
      }
      return res;
    }
    
    // ==========================================
    // 标准 ResultData 统一校验逻辑
    // ==========================================
    
    // 根据后端返回的标准 code 判断请求是否成功
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败');
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    
    // 校验通过，直接剥离包装，将核心 data 返回给业务层
    return res.data;
  },
  (error) => {
    console.error('Response error:', error)
    const message = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service