# Weixin 模块 - 微信 Linux 客户端 Docker 自动化

## 概述

Weixin 模块是一个独立的 Docker 容器池管理系统，专门用于运行原生 Linux 微信客户端的 GUI 自动化。与现有的 Chrome 实现完全隔离，使用独立的配置、端口范围和 API 路径。

## 核心特性

### 1. 确定性 MAC 地址生成

基于用户名 MD5 哈希生成确定性 MAC 地址，确保：
- 同一用户名总是生成相同的 MAC 地址
- 防止微信风控因 MAC 地址漂移触发
- 保持微信登录状态跨容器重启持久化

```java
// MAC 地址生成示例
String macAddress = poolManager.generateDeterministicMacAddress("user123");
// 输出: 02:a1:b2:c3:d4:e5
```

### 2. 微信数据持久化

通过 Docker 卷挂载持久化微信数据：

| 主机路径 | 容器路径 | 用途 |
|---------|---------|------|
| `{basePath}/{user}/xwechat_data` | `/root/.xwechat` | 微信登录状态 |
| `{basePath}/{user}/xwechat_files` | `/root/xwechat_files` | 聊天文件 |
| `{basePath}/{user}/OutPut` | `/app/anno` | 截图输出 |

### 3. 完全隔离的架构

| 特性 | V2 (Chrome) | Weixin (微信) |
|------|-------------|---------------|
| 端口范围 | 9001-9019 | 9101-9119 |
| 镜像名称 | gui-agent:v2 | wechat-agent:v2 |
| API 路径 | /api/v2/* | /api/wechat/* |
| 配置前缀 | agent.pool | wechat.pool |

## 目录结构

```
Weixin/
├── WeChatDockerPoolManager.java    # 容器池管理器
├── AgentControllerWeixin.java      # REST API 控制器
├── DockerControllerWeixin.java     # 回调处理器
├── config/
│   └── WeChatDockerConfiguration.java  # 配置类
└── Resources/
    ├── Dockerfile.v2               # Docker 镜像定义
    ├── entrypoint.sh               # 容器入口脚本
    ├── utils.py                    # Python 工具函数
    ├── agent_server.py             # Flask 服务器
    └── requirements.txt            # Python 依赖
```

## 快速开始

### 1. 构建 Docker 镜像

```bash
cd src/main/java/org/example/masfanplus/AgentScope/util/DockerTool/Weixin/Resources
docker build -f Dockerfile.v2 -t wechat-agent:v2 .
```

### 2. 配置 application.yml

```yaml
wechat:
  pool:
    max-pool-size: 10
    port-range-start: 9101
    image-name: wechat-agent:v2
    profile-base-path: /usr/local/server/ai/WeChatData/profiles
    idle-timeout-seconds: 600
    memory-limit: 2147483648      # 2GB
    memory-swap: 4294967296       # 4GB
    shm-size: 1073741824          # 1GB
    callback-base-url: http://8.163.67.126:9099
    java-base-url: http://host.docker.internal
    default-api-key: your-api-key
    default-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    default-model: qwen-vl-max
    default-max-steps: 50
```

### 3. 启动服务

```bash
# 确保 Docker 服务运行中
# 启动 Spring Boot 应用
./gradlew bootRun
```

## API 端点

### 任务管理

#### 提交微信任务

```http
POST /api/wechat/agent/task
Content-Type: application/json

{
    "username": "user123",
    "instruction": "发送消息给张三：你好",
    "taskId": "optional-task-id",
    "maxSteps": 30
}
```

响应：
```json
{
    "status": "accepted",
    "task_id": "wechat_task_1234567890_abc12345",
    "app_type": "wechat",
    "username": "user123",
    "container_id": "abc123...",
    "container_short_id": "abc123",
    "api_port": 9101,
    "vnc_port": 9102,
    "mac_address": "02:a1:b2:c3:d4:e5",
    "vnc_url": "http://localhost:9102/vnc.html",
    "message": "微信任务分派成功"
}
```

#### 获取用户容器

```http
GET /api/wechat/agent/user/{username}/container
```

#### 获取 MAC 地址

```http
GET /api/wechat/agent/user/{username}/mac
```

### 池管理

#### 获取池统计

```http
GET /api/wechat/agent/pool/stats
```

#### 获取所有容器

```http
GET /api/wechat/agent/pool/containers
```

#### 释放容器

```http
POST /api/wechat/agent/container/{containerId}/release
```

#### 删除容器

```http
DELETE /api/wechat/agent/container/{containerId}
```

### Docker 回调

#### 任务回调

```http
POST /api/wechat/docker/callback/{containerId}
Content-Type: application/json

{
    "task_id": "task-123",
    "data": {
        "status": "processing",
        "step": 5,
        "action": "left_click",
        "image_url": "step_005.png",
        "result": "左键单击于：[500, 300]"
    }
}
```

#### 获取回调历史

```http
GET /api/wechat/docker/callbacks/{taskId}
```

#### 健康检查

```http
GET /api/wechat/docker/health
```

## 微信操作指南

### 系统提示词

Weixin 模块使用专门的系统提示词，指导 LLM 如何操作微信客户端：

```
* 这是一个运行 **微信 Linux 客户端** 的无头桌面 GUI 环境。
* 微信已经安装在系统中，启动命令是 `wechat`。
* 要启动微信，请使用 `open app` 动作，app_name 设为 `wechat`。
* 微信启动需要约 5 秒，启动后请等待并截图确认。
* 屏幕分辨率为 1000x1000。
```

### 启动微信

```json
{
    "name": "computer_use",
    "arguments": {
        "action": "open app",
        "app_name": "wechat"
    }
}
```

### 常用操作

| 操作 | 动作 | 示例 |
|------|------|------|
| 点击联系人 | left_click | `{"action": "left_click", "coordinate": [200, 300]}` |
| 输入消息 | type | `{"action": "type", "text": "你好"}` |
| 发送消息 | key | `{"action": "key", "keys": ["enter"]}` |
| 滚动聊天记录 | scroll | `{"action": "scroll", "pixels": -300}` |
| 等待响应 | wait | `{"action": "wait", "time": 2}` |

## 容器生命周期

```
CREATING → READY → BUSY → READY → TERMINATING
                ↓
              ERROR
```

### 状态说明

| 状态 | 描述 |
|------|------|
| CREATING | 容器正在创建中 |
| READY | 容器就绪，可接受任务 |
| BUSY | 容器正在执行任务 |
| ERROR | 容器发生错误 |
| TERMINATING | 容器正在终止 |

## 定时任务

### 空闲容器清理

每 5 分钟执行一次，清理空闲超过配置超时时间（默认 600 秒）的容器：

```java
@Scheduled(fixedRate = 300000)
public void cleanupIdleContainers() {
    // 清理空闲容器
}
```

## 故障排查

### 1. 微信启动失败

检查微信是否正确安装：
```bash
docker exec -it <container_id> which wechat
```

### 2. 登录状态丢失

- 检查 `/root/.xwechat` 目录是否正确挂载
- 确认 MAC 地址是否一致
- 查看容器日志：`docker logs <container_id>`

### 3. VNC 无法连接

- 检查端口是否正确映射
- 访问 `http://localhost:{VNC_PORT}/vnc.html`
- 确认 x11vnc 和 websockify 进程正在运行

### 4. 截图失败

```bash
# 进入容器检查
docker exec -it <container_id> bash
# 检查 DISPLAY 环境变量
echo $DISPLAY
# 手动截图测试
scrot /tmp/test.png
```

## 性能优化

### 内存配置

微信客户端需要较多内存，建议配置：

```yaml
wechat:
  pool:
    memory-limit: 2147483648   # 2GB
    memory-swap: 4294967296    # 4GB
    shm-size: 1073741824       # 1GB 共享内存
```

### 池大小

根据服务器资源调整：

```yaml
wechat:
  pool:
    max-pool-size: 10  # 根据内存调整，每个容器约 2GB
```

## 安全注意事项

1. **MAC 地址一致性**：确保同一用户名始终使用相同的 MAC 地址
2. **数据隔离**：每个用户的数据目录相互隔离
3. **网络安全**：VNC 端口仅用于调试，生产环境应限制访问
4. **资源限制**：设置合理的内存和 CPU 限制

## 与 V2 模块的对比

| 特性 | V2 (Chrome) | Weixin (微信) |
|------|-------------|---------------|
| 应用类型 | Chromium 浏览器 | 微信 Linux 客户端 |
| 数据持久化 | Chrome Profile | 微信数据目录 |
| MAC 地址 | 顺序生成 | 确定性哈希生成 |
| 启动时间 | ~10秒 | ~15秒 |
| 内存需求 | ~1GB | ~2GB |
| 空闲超时 | 300秒 | 600秒 |

## 版本历史

- **v1.0.0** - 初始版本
  - 基于 Ubuntu 22.04 的 Docker 镜像
  - 确定性 MAC 地址生成
  - 微信数据持久化
  - 独立的 REST API 端点

## 许可证

内部项目，仅供授权使用。
