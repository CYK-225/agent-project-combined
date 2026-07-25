# ==========================================
# 阶段 1: 编译 ENScan_GO (升级为 golang 1.23 解决编译报错)
# ==========================================
FROM golang:1.23 AS build-stage

# 加上 --tags 防止 git describe 找不到版本号报错
RUN git clone --depth 1 --tags https://github.com/wgpsec/ENScan_GO.git
WORKDIR /go/ENScan_GO

ENV GO111MODULE=on \
    GOPROXY=https://goproxy.cn,direct

# 编译参数加入 2>/dev/null || echo "latest" 进行容错处理
RUN CGO_ENABLED=0 go build -trimpath -ldflags=" \
-w -s \
-X 'github.com/wgpsec/ENScan/common.BuiltAt=`date +'%F %T %z'`' \
-X 'github.com/wgpsec/ENScan/common.GoVersion=`go version | sed 's/go version //'`' \
-X 'github.com/wgpsec/ENScan/common.GitAuthor=`git show -s --format='format:%aN <%ae>' HEAD`' \
-X 'github.com/wgpsec/ENScan/common.GitCommit=`git log --pretty=format:"%h" -1`' \
-X 'github.com/wgpsec/ENScan/common.GitTag=`git describe --tags --always 2>/dev/null || echo "latest"`' \
" \
-o /enscan .

# ==========================================
# 阶段 2: Python + FastAPI + ENScan 运行环境
# ==========================================
FROM python:3.10-slim

WORKDIR /app

# 设置时区与 Java 端 TZ=Asia/Shanghai 呼应
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从第一阶段提取编译好的 enscan
COPY --from=build-stage /enscan /app/enscan
RUN chmod +x /app/enscan

# 安装 Python 依赖 (通过国内镜像源加速)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 拷贝核心 Python 代理脚本
COPY agent.py /app/agent.py

# ==========================================
# 配置说明:
#   config.yaml 由 Java 端 ENSAgentProperties 生成，
#   并通过 Docker Volume 挂载到 /app/config.yaml
# ==========================================

# 入口点配置为 python 解释器 (加入 -u 参数，强制实时输出日志，杜绝假死静默)
ENTRYPOINT ["python", "-u", "/app/agent.py"]