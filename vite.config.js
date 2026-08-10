import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  // 根据 package.json 里的 --mode 加载对应的 .env 文件
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    // 1. 解决生产环境打包报错
    build: {
      target: 'esnext'
    },
    // 2. 解决项目自身代码开发环境报错
    esbuild: {
      target: 'esnext'
    },
    // 3. 【重点】解决 node_modules 里的第三方依赖预构建报错
    optimizeDeps: {
      esbuildOptions: {
        target: 'esnext'
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true
        }
      }
    }
  }
})