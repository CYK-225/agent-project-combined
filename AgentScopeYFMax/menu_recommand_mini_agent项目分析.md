# menu_recommand_mini_agent 项目分析报告

**分析日期**: 2026-06-12  
**项目路径**: `D:\公司\uai-project\menu_recommand_mini_agent-main`  

---

## 📋 项目概览

这是一个基于 Flask + MySQL 的菜单推荐智能体系统，包含前后端分离架构。

### 技术栈
- **后端**: Flask 3.0.0 + Python
- **数据库**: MySQL (PyMySQL 1.1.0)
- **前端**: 独立 frontend 目录（可能是 Vue/React）
- **AI**: DeepSeek API
- **认证**: JWT (PyJWT 2.8.0)
- **企业微信**: wecom-aibot-python-sdk

---

## 🔍 关键发现

### 1. 数据库配置结构

#### 配置文件位置
- **项目根目录**: `.env.example`（模板文件）
- **Backend 配置**: `backend/config.py`
- **期望的 .env 路径**: `config/.env`（相对于项目根目录）

#### 配置的三个数据库

```python
# 1. 数据查询统计数据库（主库）
DB_HOST=localhost
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=root
DB_PASSWORD=""

# 2. DataPlatform 数据库（ETL 统计表）
DP_HOST=localhost
DP_PORT=3306
DP_NAME=dp
DP_USER=root
DP_PASSWORD=""

# 3. 对话存储数据库（会话消息）
CONVERSATION_DB_HOST=localhost
CONVERSATION_DB_PORT=3306
CONVERSATION_DB_NAME=conversation_db
CONVERSATION_DB_USER=root
CONVERSATION_DB_PASSWORD=""
```

### 2. 与 skills 仓库的关系

**重要发现**：`D:/公司/agnet-skill1/skills/config.py` 的配置结构与这个项目**完全一致**！

这说明：
- ✅ skills 仓库是从这个项目复制/派生出来的
- ✅ 两个项目使用相同的数据库配置格式
- ✅ skills 仓库应该使用相同的 `.env` 配置

### 3. 数据库类型确认

**明确使用 MySQL**：
```python
import pymysql
from pymysql.cursors import DictCursor
from dbutils.pooled_db import PooledDB
```

**不是 PostgreSQL**！之前的记忆可能有误。

---

## 🔧 配置问题诊断

### 当前问题

skills 仓库报错：
```
Can't connect to MySQL server on 'localhost' (WinError 10061)
```

### 根本原因

1. **`.env` 文件缺失**
   - skills 仓库：`D:/公司/agnet-skill1/skills/config/.env` ❌ 不存在
   - 参考项目：`D:/公司/uai-project/menu_recommand_mini_agent-main/config/.env` ⚠️ 需要检查

2. **使用默认配置**
   ```python
   "DB_HOST": os.getenv("DB_HOST", "localhost"),  # 默认 localhost
   "DB_PORT": int(os.getenv("DB_PORT", 3306)),     # 默认 3306
   ```

3. **MySQL 服务未启动或地址错误**
   - 如果数据库在远程服务器（如 `8.163.67.126`），需要修改配置
   - 如果是本地开发，需要启动 MySQL 服务

---

## 📊 项目结构对比

### menu_recommand_mini_agent-main

```
menu_recommand_mini_agent-main/
├── .env.example              # 配置模板
├── backend/
│   ├── config.py             # 配置加载模块
│   ├── database/
│   │   └── db_manager.py     # 数据库管理（MySQL）
│   ├── app.py                # Flask 应用入口
│   ├── skills/               # Skill 定义
│   ├── shortcuts/            # 快捷命令
│   └── requirements.txt      # Python 依赖
├── frontend/                 # 前端代码
├── scripts/                  # 脚本工具
└── tests/                    # 测试代码
```

### agnet-skill1/skills

```
agnet-skill1/skills/
├── config.py                 # 配置加载模块（相同结构）
├── database/
│   └── db_manager.py         # 数据库管理（相同代码）
├── data_search/              # Skill: 数据查询
├── financial_assistant/      # Skill: 财务助手
├── merchant_info/            # Skill: 商户信息
├── pdf/                      # Skill: PDF 生成
├── ppt/                      # Skill: PPT 生成
└── requirements.txt          # Python 依赖（相同）
```

**结论**：skills 仓库是 menu_recommand_mini_agent 的 Skill 模块化版本。

---

## ✅ 解决方案

### 方案 1：从参考项目复制 .env（推荐）

如果 `menu_recommand_mini_agent-main` 项目有可用的 `.env` 文件：

```bash
# 1. 检查参考项目是否有 .env
dir D:\公司\uai-project\menu_recommand_mini_agent-main\config\.env

# 2. 如果有，复制到 skills 仓库
copy D:\公司\uai-project\menu_recommand_mini_agent-main\config\.env ^
     D:\公司\agnet-skill1\skills\config\.env

# 3. 根据需要修改数据库地址
notepad D:\公司\agnet-skill1\skills\config\.env
```

### 方案 2：手动创建 .env

根据实际数据库情况创建：

#### 选项 A：使用远程数据库（如果项目在服务器上）

```env
# D:/公司/agnet-skill1/skills/config/.env

# 数据查询统计数据库
DB_HOST=8.163.67.126
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=your_username
DB_PASSWORD=your_password

# DataPlatform 数据库
DP_HOST=8.163.67.126
DP_PORT=3306
DP_NAME=dp
DP_USER=your_username
DP_PASSWORD=your_password

# 对话存储数据库
CONVERSATION_DB_HOST=8.163.67.126
CONVERSATION_DB_PORT=3306
CONVERSATION_DB_NAME=conversation_db
CONVERSATION_DB_USER=your_username
CONVERSATION_DB_PASSWORD=your_password

# AI API
AI_API_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-your-api-key
AI_MODEL=deepseek-chat

# Flask
FLASK_HOST=0.0.0.0
FLASK_PORT=5000
FLASK_DEBUG=false

# JWT
JWT_SECRET_KEY=your-secret-key
JWT_ALGORITHM=HS256

# 企业微信（本地调试建议关闭）
WECOM_ENABLED=false
```

#### 选项 B：使用本地 MySQL

```env
# D:/公司/agnet-skill1/skills/config/.env

DB_HOST=localhost
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=root
DB_PASSWORD=your_mysql_root_password

# ... 其他配置类似
```

然后启动本地 MySQL：
```bash
# Windows
net start MySQL80

# 或使用 Docker
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0
```

### 方案 3：检查参考项目的实际配置

```bash
# 查看参考项目是否有 .env 文件
dir D:\公司\uai-project\menu_recommand_mini_agent-main\config\.env

# 如果有，查看内容
type D:\公司\uai-project\menu_recommand_mini_agent-main\config\.env

# 如果没有，查看是否有其他配置文件
dir D:\公司\uai-project\menu_recommand_mini_agent-main\*.env /s
```

---

## 🎯 下一步行动

### 立即执行

1. **检查参考项目是否有可用的 .env**
   ```bash
   dir D:\公司\uai-project\menu_recommand_mini_agent-main\config\.env
   ```

2. **获取数据库凭据**
   - 联系项目管理员
   - 或查看项目的部署文档
   - 或检查服务器的 MySQL 配置

3. **创建 skills 仓库的 .env 文件**
   ```bash
   cd D:\公司\agnet-skill1\skills\config
   notepad .env
   ```

4. **测试连接**
   ```bash
   cd D:\公司\agnet-skill1\skills
   .venv\Scripts\python.exe -c "
   from config import CONFIG
   print('DB_HOST:', CONFIG['DB_HOST'])
   print('DB_PORT:', CONFIG['DB_PORT'])
   print('DB_NAME:', CONFIG['DB_NAME'])
   "
   ```

### 验证步骤

```bash
# 1. 激活虚拟环境
cd D:\公司\agnet-skill1\skills
.venv\Scripts\activate

# 2. 测试数据库连接
.venv\Scripts\python.exe -c "
from database.db_manager import get_db_manager
result = get_db_manager().verify_connection()
print('Connection status:', result)
"

# 3. 运行完整测试
.venv\Scripts\python.exe -m skills.data_search.scripts.resolve_entity --name "小红书" --intent order_summary
```

---

## 📝 相关文档

- [Skills数据库配置指南.md](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/Skills数据库配置指南.md)
- [MySQL数据库连接问题修复指南.md](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/MySQL数据库连接问题修复指南.md)
- [虚拟环境初始化问题修复说明.md](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/虚拟环境初始化问题修复说明.md)

---

## 💡 关键结论

1. ✅ **数据库类型**: MySQL（不是 PostgreSQL）
2. ✅ **配置来源**: 从 `menu_recommand_mini_agent-main` 项目派生
3. ❌ **问题原因**: `.env` 文件缺失，使用默认 `localhost` 配置
4. 🔧 **解决方案**: 创建正确的 `.env` 文件，配置实际的数据库地址和凭据

---

**分析完成时间**: 2026-06-12  
**状态**: 🟡 等待数据库配置
