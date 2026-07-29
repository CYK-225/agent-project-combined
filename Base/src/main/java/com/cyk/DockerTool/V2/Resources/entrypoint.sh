#!/bin/bash
# V2 代理服务器入口点脚本
# 设置虚拟显示器并启动代理服务器

set -e

# 从环境变量获取配置
AGENT_PORT=${AGENT_PORT:-9001}
VNC_PORT=${VNC_PORT:-9002}
CONTAINER_ID=${CONTAINER_ID:-$(hostname)}
PROFILE_NAME=${PROFILE_NAME:-default}
LOG_LEVEL=${LOG_LEVEL:-INFO}

echo "========================================"
echo "V2 代理服务器启动中"
echo "========================================"
echo "容器ID：${CONTAINER_ID}"
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
export PYTHONUNBUFFERED=1

# 清理函数
cleanup() {
    echo "收到关闭信号，正在清理..."

    # 终止 websockify代理
    pkill -9 websockify 2>/dev/null || true

    # 终止 x11vnc
    pkill -9 x11vnc 2>/dev/null || true

    # 终止 Xvfb
    pkill -9 Xvfb 2>/dev/null || true

    # 终止 fluxbox
    pkill -9 fluxbox 2>/dev/null || true

    # 终止所有剩余的 Chrome 进程
    pkill -9 chrome 2>/dev/null || true

    echo "清理完成"
    exit 0
}

# 注册信号处理器
trap cleanup SIGTERM SIGINT SIGQUIT

# 终止任何现有的X服务器进程
echo "正在清理现有的X服务器进程..."
pkill -9 Xvfb 2>/dev/null || true
pkill -9 X 2>/dev/null || true
sleep 1

# 启动Xvfb（X虚拟帧缓冲区）
echo "正在显示器 ${DISPLAY} 上启动Xvfb..."
Xvfb ${DISPLAY} -ac -screen 0 1000x1000x24 > /dev/null 2>&1 &
XVFB_PID=$!
sleep 2

# 验证Xvfb是否正在运行
if ! kill -0 ${XVFB_PID} 2>/dev/null; then
    echo "错误：启动Xvfb失败"
    exit 1
fi
echo "Xvfb已启动，PID：${XVFB_PID}"

# 设置fluxbox窗口管理器
echo "正在启动fluxbox窗口管理器..."
mkdir -p /root/.fluxbox
fluxbox > /dev/null 2>&1 &
FLUXBOX_PID=$!
sleep 1

# 验证fluxbox是否正在运行
if ! kill -0 ${FLUXBOX_PID} 2>/dev/null; then
    echo "警告：Fluxbox可能未正确启动"
fi
echo "Fluxbox已启动，PID：${FLUXBOX_PID}"

# ==========================================
# 启动内部 VNC 与 Web 代理
# ==========================================

# 1. 启动原生的 x11vnc，监听本地的 5900 端口 (-localhost 参数确保安全)
echo "正在启动内部 x11vnc 服务器，端口 5900..."
x11vnc -display ${DISPLAY} -rfbport 5900 -localhost -forever -shared -nopw -bg -quiet > /dev/null 2>&1
sleep 1

# 2. 启动 Websockify，接管对外暴露的 VNC_PORT (默认9002)，并将流量转发到本地 5900
echo "正在启动 Websockify 代理，对接外部端口 ${VNC_PORT}..."
websockify --web=/usr/share/novnc/ ${VNC_PORT} localhost:5900 > /dev/null 2>&1 &
WEBSOCKIFY_PID=$!
sleep 1

if kill -0 ${WEBSOCKIFY_PID} 2>/dev/null; then
    echo "Web VNC 已就绪，PID：${WEBSOCKIFY_PID}，对外端口：${VNC_PORT}"
else
    echo "警告：Web VNC 可能未正确启动"
fi

# ==========================================

# 设置Chrome配置文件目录
echo "正在设置Chrome配置文件..."
mkdir -p /app/chrome_profile
chmod -R 777 /app/chrome_profile 2>/dev/null || true

# 清理Chrome锁文件
rm -rf /app/chrome_profile/Singleton* 2>/dev/null || true
rm -rf /app/chrome_profile/Crashpad 2>/dev/null || true

# 创建输出目录
mkdir -p /app/anno
chmod -R 777 /app/anno

# 等待显示器就绪
echo "正在等待显示器就绪..."
sleep 2

# 验证显示器
export DISPLAY=:99
if ! xdpyinfo > /dev/null 2>&1; then
    echo "错误：显示器未就绪"
    exit 1
fi
echo "显示器已就绪"

# 启动代理服务器
echo "正在端口 ${AGENT_PORT} 上启动代理服务器..."
echo "========================================"

# 执行主命令（作为参数传递）
exec "$@"