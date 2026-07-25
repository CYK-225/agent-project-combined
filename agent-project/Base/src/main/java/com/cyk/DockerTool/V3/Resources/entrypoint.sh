#!/bin/bash
# V3 代理服务器入口点脚本
# 设置虚拟显示器并启动代理服务器

set -e

AGENT_PORT=${AGENT_PORT:-9301}
VNC_PORT=${VNC_PORT:-9302}
CONTAINER_ID=${CONTAINER_ID:-$(hostname)}
PROFILE_NAME=${PROFILE_NAME:-default}
LOG_LEVEL=${LOG_LEVEL:-INFO}

echo "========================================"
echo "V3 代理服务器启动中"
echo "========================================"
echo "容器ID：${CONTAINER_ID}"
echo "配置文件名称：${PROFILE_NAME}"
echo "代理端口(API)：${AGENT_PORT}"
echo "代理端口(VNC)：${VNC_PORT}"
echo "日志级别：${LOG_LEVEL}"
echo "========================================"

export AGENT_PORT
export VNC_PORT
export CONTAINER_ID
export PROFILE_NAME
export PYTHONUNBUFFERED=1

cleanup() {
    echo "V3 收到关闭信号，正在清理..."
    pkill -9 websockify 2>/dev/null || true
    pkill -9 x11vnc 2>/dev/null || true
    pkill -9 Xvfb 2>/dev/null || true
    pkill -9 fluxbox 2>/dev/null || true
    pkill -9 chrome 2>/dev/null || true
    echo "V3 清理完成"
    exit 0
}

trap cleanup SIGTERM SIGINT SIGQUIT

echo "正在清理现有的X服务器进程..."
pkill -9 Xvfb 2>/dev/null || true
pkill -9 X 2>/dev/null || true
sleep 1

echo "正在显示器 ${DISPLAY} 上启动Xvfb..."
Xvfb ${DISPLAY} -ac -screen 0 1000x1000x24 > /dev/null 2>&1 &
XVFB_PID=$!
sleep 2

if ! kill -0 ${XVFB_PID} 2>/dev/null; then
    echo "错误：启动Xvfb失败"
    exit 1
fi
echo "Xvfb已启动，PID：${XVFB_PID}"

echo "正在启动fluxbox窗口管理器..."
mkdir -p /root/.fluxbox
fluxbox > /dev/null 2>&1 &
FLUXBOX_PID=$!
sleep 1

if ! kill -0 ${FLUXBOX_PID} 2>/dev/null; then
    echo "警告：Fluxbox可能未正确启动"
fi
echo "Fluxbox已启动，PID：${FLUXBOX_PID}"

echo "正在启动内部 x11vnc 服务器，端口 5900..."
x11vnc -display ${DISPLAY} -rfbport 5900 -localhost -forever -shared -nopw -bg -quiet > /dev/null 2>&1
sleep 1

echo "正在启动 Websockify 代理，对接外部端口 ${VNC_PORT}..."
websockify --web=/usr/share/novnc/ ${VNC_PORT} localhost:5900 > /dev/null 2>&1 &
WEBSOCKIFY_PID=$!
sleep 1

if kill -0 ${WEBSOCKIFY_PID} 2>/dev/null; then
    echo "V3 Web VNC 已就绪，PID：${WEBSOCKIFY_PID}，对外端口：${VNC_PORT}"
else
    echo "警告：V3 Web VNC 可能未正确启动"
fi

echo "正在设置Chrome配置文件..."
mkdir -p /app/chrome_profile
chmod -R 777 /app/chrome_profile 2>/dev/null || true

rm -rf /app/chrome_profile/Singleton* 2>/dev/null || true
rm -rf /app/chrome_profile/Crashpad 2>/dev/null || true

mkdir -p /app/anno
chmod -R 777 /app/anno

echo "正在等待显示器就绪..."
sleep 2

export DISPLAY=:99
if ! xdpyinfo > /dev/null 2>&1; then
    echo "错误：显示器未就绪"
    exit 1
fi
echo "显示器已就绪"

echo "正在端口 ${AGENT_PORT} 上启动V3代理服务器..."
echo "========================================"

exec "$@"
