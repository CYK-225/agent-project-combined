import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // 根据 package.json 里的 --mode 加载对应的 .env 文件
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/agui': {
          // 如果命令里有指定 IP 就用指定的，否则默认兜底为服务器 IP，确保万无一失
          target: env.VITE_PROXY_TARGET || 'http://8.129.128.167:8089',
          changeOrigin: true
        },
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://8.129.128.167:8089',
          changeOrigin: true
        }
      }
    }
  }
})
