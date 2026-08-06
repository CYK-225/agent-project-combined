# MySQL 数据库连接问题修复指南

**问题日期**: 2026-06-12  
**错误类型**: MySQL 连接失败 (WinError 10061)  

---

## 问题描述

Python 脚本执行成功（returncode=0），但业务逻辑失败：

```json
{
  "status": "error",
  "message": "实例查询失败，请稍后重试"
}
```

**错误日志**：
```
ERROR:database.db_manager:Failed to initialize connection pool: 
(2003, "Can't connect to MySQL server on 'localhost' 
([WinError 10061] 由于目标计算机积极拒绝，无法连接。)")

ERROR:__main__:实例查询失败: 
(2003, "Can't connect to MySQL server on 'localhost' 
([WinError 10061] 由于目标计算机积极拒绝，无法连接。)")
```

---

## 根本原因

### 1. 数据库配置错误
Python 脚本尝试连接 `localhost` 的 MySQL，但：
- ❌ MySQL 服务未启动
- ❌ 应该使用 PostgreSQL（项目配置的数据库）
- ❌ 缺少正确的数据库连接配置

### 2. 可能的配置位置
根据项目架构，数据库配置可能在：
- `.env` 文件
- `config.py` 或 `settings.py`
- 环境变量
- 脚本硬编码

---

## 解决方案

### 方案 1：检查并修正数据库配置（推荐）

#### 步骤 1：查找数据库配置文件

在 skills 仓库中搜索数据库配置：

```bash
# 进入 skills 仓库目录
cd D:\公司\agnet-skill1\skills

# 搜索数据库配置
findstr /s /i "DB_HOST DATABASE_URL MYSQL_HOST" *.py *.env *.cfg

# 或者搜索 pymysql 连接代码
findstr /s /i "pymysql.connect mysql.connector" *.py
```

#### 步骤 2：确认正确的数据库类型

根据项目记忆，项目使用的是 **PostgreSQL**，不是 MySQL：
- Host: `8.163.67.126`
- Port: `5432`
- Database: `postgres`
- Schema: `agent_test`

但 Python 脚本使用的是 `pymysql`（MySQL 驱动），这可能是一个错误。

#### 步骤 3：修改配置

**如果应该使用 PostgreSQL**：
1. 将 `pymysql` 改为 `psycopg2` 或 `asyncpg`
2. 更新连接字符串为 PostgreSQL 格式

**如果确实需要使用 MySQL**：
1. 确保 MySQL 服务已启动
2. 配置正确的 host、port、user、password

### 方案 2：添加环境变量配置

在 skills 仓库根目录创建 `.env` 文件：

```env
# 数据库配置
DB_HOST=8.163.67.126
DB_PORT=5432
DB_NAME=postgres
DB_USER=your_username
DB_PASSWORD=your_password
DB_TYPE=postgresql  # 或 mysql

# 如果使用 MySQL
# DB_HOST=localhost
# DB_PORT=3306
# DB_NAME=your_database
```

然后在 Python 脚本中使用 `python-dotenv` 加载：

```python
from dotenv import load_dotenv
import os

load_dotenv()

DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = int(os.getenv('DB_PORT', '3306'))
# ...
```

### 方案 3：检查 SKILL.md 中的配置说明

查看 `data_search/SKILL.md` 是否有数据库配置说明：

```markdown
## 配置要求

在使用此 Skill 之前，请确保：
1. 已配置数据库连接信息
2. 已设置环境变量 DB_HOST, DB_PORT, etc.
```

如果没有，需要添加配置说明。

---

## 临时解决方案

### 1. 启动本地 MySQL（如果确实需要）

```bash
# Windows 上启动 MySQL 服务
net start MySQL80

# 或者使用 Docker
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0
```

### 2. 修改脚本使用正确的数据库

找到报错的脚本文件（可能是 `skills/data_search/scripts/resolve_entity.py`），检查数据库连接代码：

```python
# 错误的配置
connection = pymysql.connect(
    host='localhost',  # ❌ 应该是实际数据库地址
    port=3306,
    user='root',
    password='...',
    database='...'
)

# 正确的配置（如果使用 PostgreSQL）
import psycopg2
connection = psycopg2.connect(
    host='8.163.67.126',
    port=5432,
    dbname='postgres',
    user='your_user',
    password='your_password',
    options='-c search_path=agent_test'
)
```

---

## 诊断步骤

### 1. 确认数据库类型

检查项目实际使用的数据库：

```bash
# 查看 Java 项目的数据库配置
findstr /s /i "jdbc:postgresql jdbc:mysql" *.yml *.properties

# 或者检查 pom.xml 中的依赖
findstr /s /i "postgresql mysql-connector" pom.xml
```

### 2. 测试数据库连接

```bash
# 测试 PostgreSQL 连接
psql -h 8.163.67.126 -p 5432 -U your_user -d postgres

# 测试 MySQL 连接
mysql -h localhost -P 3306 -u root -p
```

### 3. 检查 Python 脚本

```bash
# 查看 resolve_entity.py 的数据库连接部分
type D:\公司\agnet-skill1\skills\data_search\scripts\resolve_entity.py | findstr /i "connect host port"
```

---

## 长期解决方案

### 1. 统一数据库配置管理

在 skills 仓库中创建统一的配置模块：

```python
# skills/config/database.py
import os
from dotenv import load_dotenv

load_dotenv()

DATABASE_CONFIG = {
    'type': os.getenv('DB_TYPE', 'postgresql'),
    'host': os.getenv('DB_HOST', '8.163.67.126'),
    'port': int(os.getenv('DB_PORT', '5432')),
    'dbname': os.getenv('DB_NAME', 'postgres'),
    'user': os.getenv('DB_USER', ''),
    'password': os.getenv('DB_PASSWORD', ''),
    'schema': os.getenv('DB_SCHEMA', 'agent_test')
}
```

### 2. 在 SKILL.md 中添加配置说明

```markdown
## 环境配置

使用前请配置以下环境变量：

```bash
export DB_HOST=8.163.67.126
export DB_PORT=5432
export DB_NAME=postgres
export DB_USER=your_user
export DB_PASSWORD=your_password
export DB_SCHEMA=agent_test
```

或在仓库根目录创建 `.env` 文件。
```

### 3. 添加连接池健康检查

```python
def check_db_connection():
    """检查数据库连接是否正常"""
    try:
        conn = create_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT 1")
        cursor.close()
        conn.close()
        return True
    except Exception as e:
        log.error(f"Database connection failed: {e}")
        return False
```

---

## 相关文件

- `D:/公司/agnet-skill1/skills/data_search/scripts/resolve_entity.py` - 报错的脚本
- `D:/公司/agnet-skill1/skills/data_search/SKILL.md` - Skill 配置说明
- `D:/公司/agnet-skill1/skills/.env` - 环境变量配置（可能需要创建）
- `D:/公司/agnet-skill1/skills/requirements.txt` - Python 依赖（可能需要添加 psycopg2）

---

## 下一步行动

1. ✅ **立即**：检查 `resolve_entity.py` 中的数据库连接配置
2. ✅ **立即**：确认项目应该使用 MySQL 还是 PostgreSQL
3. ⚠️ **短期**：创建 `.env` 文件配置正确的数据库连接
4. ⚠️ **长期**：统一所有 Skill 的数据库配置管理

---

**创建时间**: 2026-06-12  
**状态**: 🔴 待修复
