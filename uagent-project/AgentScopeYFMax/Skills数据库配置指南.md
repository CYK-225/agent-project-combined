# Skills 数据库配置指南

**问题**: Python 脚本连接数据库失败（localhost:3306）  
**日期**: 2026-06-12  

---

## 问题原因

`D:/公司/agnet-skill1/skills/config.py` 中的数据库配置默认值为 `localhost`，但缺少 `.env` 文件覆盖这些默认值。

```python
"DB_HOST": os.getenv("DB_HOST", "localhost"),  # ❌ 默认 localhost
"DB_PORT": int(os.getenv("DB_PORT", 3306)),     # ❌ 默认 MySQL 端口
```

---

## 解决方案

### 步骤 1：创建 .env 文件

在 `D:/公司/agnet-skill1/skills/config/` 目录下创建 `.env` 文件：

```bash
cd D:\公司\agnet-skill1\skills\config
notepad .env
```

### 步骤 2：配置数据库连接

根据项目实际使用的数据库类型，选择以下配置之一：

#### 选项 A：如果项目使用 PostgreSQL（推荐，根据项目记忆）

```env
# =============================================================================
# 数据查询统计数据库配置
# =============================================================================
DB_HOST=8.129.128.167
DB_PORT=5432
DB_NAME=postgres
DB_USER=your_username
DB_PASSWORD=your_password

# =============================================================================
# DataPlatform 数据库配置
# =============================================================================
DP_HOST=8.129.128.167
DP_PORT=5432
DP_NAME=postgres
DP_USER=your_username
DP_PASSWORD=your_password

# =============================================================================
# 对话存储数据库配置
# =============================================================================
CONVERSATION_DB_HOST=8.129.128.167
CONVERSATION_DB_PORT=5432
CONVERSATION_DB_NAME=postgres
CONVERSATION_DB_USER=your_username
CONVERSATION_DB_PASSWORD=your_password
```

**⚠️ 重要**：如果使用 PostgreSQL，还需要修改 Python 代码，将 `pymysql` 改为 `psycopg2`。

#### 选项 B：如果项目确实使用 MySQL

```env
# =============================================================================
# 数据查询统计数据库配置
# =============================================================================
DB_HOST=8.129.128.167
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=root
DB_PASSWORD=your_mysql_password

# =============================================================================
# DataPlatform 数据库配置
# =============================================================================
DP_HOST=8.129.128.167
DP_PORT=3306
DP_NAME=dp
DP_USER=root
DP_PASSWORD=your_mysql_password

# =============================================================================
# 对话存储数据库配置
# =============================================================================
CONVERSATION_DB_HOST=8.129.128.167
CONVERSATION_DB_PORT=3306
CONVERSATION_DB_NAME=conversation_db
CONVERSATION_DB_USER=root
CONVERSATION_DB_PASSWORD=your_mysql_password
```

### 步骤 3：确认数据库类型

检查项目实际使用的数据库：

```bash
# 查看 Java 项目的数据库依赖
cd D:\公司\代码审查专用\开发
findstr /s /i "postgresql mysql" AgentScopeYFMax\pom.xml start\pom.xml
```

如果看到 `postgresql` 依赖，说明应该使用 PostgreSQL。

### 步骤 4：如果需要切换到 PostgreSQL

修改 `D:/公司/agnet-skill1/skills/database/db_manager.py`：

#### 4.1 更新 requirements.txt

```txt
# 删除或注释掉
# pymysql==1.1.0
# dbutils==1.3

# 添加
psycopg2-binary==2.9.9
sqlalchemy==2.0.23
```

#### 4.2 修改 db_manager.py

```python
# 替换导入
# import pymysql
# from pymysql.cursors import DictCursor
# from dbutils.pooled_db import PooledDB

import psycopg2
from psycopg2.extras import RealDictCursor
from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

# 修改连接代码
def _init_connection_pool(self):
    try:
        # PostgreSQL 连接字符串
        connection_string = (
            f"postgresql://{CONFIG['DB_USER']}:{CONFIG['DB_PASSWORD']}"
            f"@{CONFIG['DB_HOST']}:{CONFIG['DB_PORT']}/{CONFIG['DB_NAME']}"
            f"?options=-c%20search_path={CONFIG.get('DB_SCHEMA', 'agent_test')}"
        )
        
        self._engine = create_engine(
            connection_string,
            poolclass=QueuePool,
            pool_size=10,
            max_overflow=5
        )
        
        logger.debug("PostgreSQL connection pool initialized (READ-ONLY mode)")
    except Exception as e:
        logger.error(f"Failed to initialize connection pool: {e}")
        raise

def get_connection(self):
    """Get a database connection from the pool."""
    try:
        conn = self._engine.connect()
        return conn
    except Exception as e:
        logger.error(f"Failed to get database connection: {e}")
        raise
```

---

## 快速测试

创建 `.env` 文件后，测试数据库连接：

```bash
cd D:\公司\agnet-skill1\skills

# 激活虚拟环境
.venv\Scripts\activate

# 测试连接
.venv\Scripts\python.exe -c "
from config import CONFIG
print('DB_HOST:', CONFIG['DB_HOST'])
print('DB_PORT:', CONFIG['DB_PORT'])
print('DB_NAME:', CONFIG['DB_NAME'])
"
```

预期输出：
```
DB_HOST: 8.129.128.167
DB_PORT: 5432
DB_NAME: postgres
```

---

## 常见问题

### Q1: 如何获取数据库密码？

联系项目管理员或查看项目的其他配置文件（如 Java 的 application.yml）。

### Q2: 防火墙阻止连接怎么办？

确保：
1. 数据库服务器允许远程连接
2. 防火墙开放相应端口（5432 或 3306）
3. 数据库用户有远程访问权限

### Q3: 能否同时支持 MySQL 和 PostgreSQL？

可以，使用条件判断：

```python
if CONFIG.get('DB_TYPE') == 'postgresql':
    import psycopg2
    # PostgreSQL 连接逻辑
else:
    import pymysql
    # MySQL 连接逻辑
```

---

## 相关文件

- `D:/公司/agnet-skill1/skills/config/.env` - 需要创建的配置文件
- `D:/公司/agnet-skill1/skills/config.py` - 配置加载模块
- `D:/公司/agnet-skill1/skills/database/db_manager.py` - 数据库管理模块
- `D:/公司/agnet-skill1/skills/requirements.txt` - Python 依赖

---

## 下一步

1. ✅ 确认项目使用的数据库类型（MySQL 还是 PostgreSQL）
2. ✅ 创建 `.env` 文件并配置正确的连接信息
3. ⚠️ 如果需要，修改 Python 代码以支持 PostgreSQL
4. ✅ 重启应用并测试

---

**创建时间**: 2026-06-12  
**状态**: 🔴 待配置
