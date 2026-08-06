#!/bin/bash
# Weixin 模块入口点脚本
# 初始化 Xvfb、fluxbox、x11vnc 和 websockify，支持微信进程清理
# 用于 WSL Ubuntu 22.04 环境

set -e

# 容器内部固定端口配置
# 外部映射端口由 Java 通过 docker run -p 参数决定
AGENT_PORT=9001  # API 服务端口（固定）
VNC_PORT=9002    # VNC 服务端口（固定）
CONTAINER_ID=${CONTAINER_ID:-$(hostname)}
PROFILE_NAME=${PROFILE_NAME:-default}
USERNAME=${USERNAME:-default}
LOG_LEVEL=${LOG_LEVEL:-INFO}

echo "========================================"
echo "Weixin 模块代理服务器启动中"
echo "========================================"
echo "容器ID：${CONTAINER_ID}"
echo "用户名：${USERNAME}"
echo "配置文件名称：${PROFILE_NAME}"
echo "代理端口(API)：${AGENT_PORT}"
echo "代理端口(VNC)：${VNC_PORT}"
echo "日志级别：${LOG_LEVEL}"
echo "========================================"

# 为Python应用导出环境变量
export AGENT_PORT
export VNC_PORT
export CONTAINER_ID
export PROFILE_NAME
export USERNAME
export PYTHONUNBUFFERED=1

# ==========================================
# 清理函数 - 包含微信进程清理
# ==========================================
cleanup() {
    echo "收到关闭信号，正在清理..."

    # 终止 websockify 代理
    pkill -9 websockify 2>/dev/null || true

    # 终止 x11vnc
    pkill -9 x11vnc 2>/dev/null || true

    # 终止 Xvfb
    pkill -9 Xvfb 2>/dev/null || true

    # 终止 fluxbox
    pkill -9 fluxbox 2>/dev/null || true

    # ==========================================
    # 微信进程清理（关键：防止残留进程）
    # ==========================================
    
    # 终止所有微信相关进程
    echo "正在清理微信进程..."
    pkill -9 wechat 2>/dev/null || true
    pkill -9 WeChat 2>/dev/null || true
    pkill -9 weixin 2>/dev/null || true
    pkill -9 xwechat 2>/dev/null || true
    pkill -9 "wechat-linux" 2>/dev/null || true
    
    # 清理微信锁文件
    rm -rf /root/.xwechat/*.lock 2>/dev/null || true
    rm -rf /root/.xwechat/*.pid 2>/dev/null || true
    
    echo "微信进程清理完成"

    echo "清理完成"
    exit 0
}

# 注册信号处理器
trap cleanup SIGTERM SIGINT SIGQUIT

# ==========================================
# 清理残留进程
# ==========================================
echo "正在清理残留的 X 服务器进程..."
pkill -9 Xvfb 2>/dev/null || true
pkill -9 X 2>/dev/null || true
pkill -9 wechat 2>/dev/null || true
pkill -9 WeChat 2>/dev/null || true
sleep 1

# ==========================================
# 启动 Xvfb（X虚拟帧缓冲区）
# ==========================================
echo "正在显示器 ${DISPLAY} 上启动 Xvfb..."
Xvfb ${DISPLAY} -ac -screen 0 1000x1000x24 > /dev/null 2>&1 &
XVFB_PID=$!
sleep 2

# 验证Xvfb是否正在运行
if ! kill -0 ${XVFB_PID} 2>/dev/null; then
    echo "错误：启动 Xvfb 失败"
    exit 1
fi
echo "Xvfb 已启动，PID：${XVFB_PID}"

# ==========================================
# 启动 fluxbox 窗口管理器
# ==========================================
echo "正在启动 fluxbox 窗口管理器..."
mkdir -p /root/.fluxbox
fluxbox > /dev/null 2>&1 &
FLUXBOX_PID=$!
sleep 1

# 验证 fluxbox 是否正在运行
if ! kill -0 ${FLUXBOX_PID} 2>/dev/null; then
    echo "警告：Fluxbox 可能未正确启动"
fi
echo "Fluxbox 已启动，PID：${FLUXBOX_PID}"

# ==========================================
# 启动内部 VNC 与 Web 代理
# ==========================================

# 1. 启动原生的 x11vnc，监听本地的 5900 端口
echo "正在启动内部 x11vnc 服务器，端口 5900..."
x11vnc -display ${DISPLAY} -rfbport 5900 -localhost -forever -shared -nopw -bg -quiet > /dev/null 2>&1
sleep 1

# 2. 启动 Websockify，接管对外暴露的 VNC_PORT
echo "正在启动 Websockify 代理，对接外部端口 ${VNC_PORT}..."
websockify --web=/usr/share/novnc/ ${VNC_PORT} localhost:5900 > /dev/null 2>&1 &
WEBSOCKIFY_PID=$!
sleep 1

if kill -0 ${WEBSOCKIFY_PID} 2>/dev/null; then
    echo "Web VNC 已就绪，PID：${WEBSOCKIFY_PID}，对外端口：${VNC_PORT}"
    echo "VNC 访问地址：http://localhost:${VNC_PORT}/vnc.html"
else
    echo "警告：Web VNC 可能未正确启动"
fi

# ==========================================
# 设置微信数据目录权限
# ==========================================
echo "正在设置微信数据目录..."
mkdir -p /root/.xwechat
mkdir -p /root/xwechat_files
chmod -R 777 /root/.xwechat 2>/dev/null || true
chmod -R 777 /root/xwechat_files 2>/dev/null || true

# 清理微信锁文件（防止启动冲突）
rm -rf /root/.xwechat/*.lock 2>/dev/null || true
rm -rf /root/.xwechat/*.pid 2>/dev/null || true

# 创建输出目录
mkdir -p /app/anno
chmod -R 777 /app/anno

# ==========================================
# 等待显示器就绪
# ==========================================
echo "正在等待显示器就绪..."
sleep 2

# 验证显示器
export DISPLAY=:99
if ! xdpyinfo > /dev/null 2>&1; then
    echo "错误：显示器未就绪"
    exit 1
fi
echo "显示器已就绪"

# ==========================================
# 启动代理服务器
# ==========================================
echo "正在端口 ${AGENT_PORT} 上启动代理服务器..."
echo "========================================"
echo "微信客户端启动命令：wechat"
echo "如需启动微信，请使用 open app 动作，app_name='wechat'"
echo "========================================"

# 执行主命令（作为参数传递）
exec "$@"
