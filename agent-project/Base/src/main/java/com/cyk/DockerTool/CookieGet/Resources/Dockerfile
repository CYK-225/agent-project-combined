FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy

# 切换为 root 用户安装系统依赖
USER root
RUN apt-get update && apt-get install -y \
    xvfb \
    fonts-wqy-zenhei \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制依赖文件并使用清华源加速安装
COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 将 Python 脚本拷贝进容器
COPY cookie_agent.py /app/cookie_agent.py

# 设置启动入口
ENTRYPOINT ["xvfb-run", "-a", "-s", "-screen 0 1280x800x24", "python", "/app/cookie_agent.py"]