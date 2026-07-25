# 使用轻量级的 Python 3.10 基础镜像
FROM python:3.10-slim

# 避免 apt 安装时出现交互提示
ENV DEBIAN_FRONTEND=noninteractive

# --- 优化阶段 1: 替换 Debian 12 软件源为阿里云镜像 ---
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# --- 优化阶段 2: 极速安装基础依赖与 Chromium ---
# 直接从阿里源拉取 chromium，彻底抛弃境外 Google 源和 GPG 密钥
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    xauth \
    xclip \
    scrot \
    gnome-screenshot \
    python3-tk \
    python3-dev \
    libpng-dev \
    libjpeg-dev \
    zlib1g-dev \
    chromium \
    chromium-driver \
    ca-certificates \
    fluxbox \
    && ln -s /usr/bin/chromium /usr/bin/google-chrome \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# --- 优化阶段 3: Python 依赖加速 ---
COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 将代码放入容器
COPY utils.py run_gui_owl_1_5_for_pc.py /app/computer_use/

# 设置入口点
ENTRYPOINT ["xvfb-run", "-a", "-s", "-screen 0 1000x1000x24", "python", "/app/computer_use/run_gui_owl_1_5_for_pc.py"]