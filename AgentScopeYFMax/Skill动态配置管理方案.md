# Skill 动态配置管理方案

**实现日期**: 2026-06-12  
**功能**: 在 AI 中台创建 Skill 时动态生成 `.env` 配置文件  

---

## 🎯 方案概述

### 问题背景
- Skills 仓库需要数据库连接等敏感配置
- 不能将 `.env` 文件提交到 Git（安全风险）
- 不同环境（开发/测试/生产）需要不同的配置
- 手动配置容易出错且难以维护

### 解决方案
在 `GitSkillManager` 加载 Skill 仓库时，自动从 AI 中台获取配置并生成 `.env` 文件。

---

## 🏗️ 架构设计

### 核心组件

#### 1. SkillEnvConfigManager（配置管理器）
**位置**: `org.example.agentScope.util.skill.SkillEnvConfigManager`

**职责**:
- 管理 Skill 仓库的环境配置
- 支持从 AI 中台获取配置（TODO）
- 提供默认配置模板
- 生成 `.env` 文件

**关键方法**:
```java
// 生成 .env 文件
boolean generateEnvFile(String repoUrl, Path workDir, String envName)

// 更新配置（用于动态刷新）
void updateConfig(String repoUrl, String envName, Map<String, String> config)

// 清除缓存
void clearCache(String repoUrl)
```

#### 2. GitSkillManager（集成点）
**位置**: `org.example.agentScope.mas.skillManager.core.GitSkillManager`

**修改内容**:
- 注入 `SkillEnvConfigManager`
- 在创建 `GitSkillRepository` 后调用 `generateEnvFileForRepo()`
- 自动生成 `config/.env` 文件

**执行时机**:
```
克隆仓库 → 创建 GitSkillRepository → ✅ 生成 .env → 加载 Skills
```

---

## 📝 配置流程

### 1. 默认配置流程（当前实现）

```
用户请求加载 Skill
    ↓
GitSkillManager.getOrCreateRepo()
    ↓
创建 GitSkillRepository（克隆仓库）
    ↓
generateEnvFileForRepo()
    ↓
SkillEnvConfigManager.generateEnvFile()
    ↓
使用默认配置模板生成 config/.env
    ↓
Skills 正常加载并使用配置
```

### 2. AI 中台配置流程（待实现）

```
用户在中台创建 Skill
    ↓
填写数据库配置、API Key 等
    ↓
中台保存配置到数据库
    ↓
用户请求加载 Skill
    ↓
GitSkillManager.getOrCreateRepo()
    ↓
创建 GitSkillRepository
    ↓
generateEnvFileForRepo()
    ↓
SkillEnvConfigManager.fetchFromAIPlatform() ← 从中台获取配置
    ↓
生成 config/.env（使用中台配置）
    ↓
Skills 正常加载并使用配置
```

---

## 🔧 使用方法

### 方式 1：使用默认配置（当前）

系统会自动使用默认配置模板生成 `.env` 文件：

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=root
DB_PASSWORD=
...
```

**适用场景**:
- 本地开发测试
- 使用环境变量覆盖默认值

### 方式 2：通过环境变量覆盖

在启动应用时设置系统属性或环境变量：

```bash
# Windows PowerShell
$env:DB_HOST="8.163.67.126"
$env:DB_PORT="3306"
$env:DB_USER="your_user"
$env:DB_PASSWORD="your_password"
java --enable-preview -jar uagent.jar

# 或使用 JVM 参数
java -DDB_HOST=8.163.67.126 -DDB_USER=your_user --enable-preview -jar uagent.jar
```

配置管理器会自动解析 `${DB_HOST:localhost}` 格式的占位符。

### 方式 3：从 AI 中台获取（待实现）

需要在 `SkillEnvConfigManager.fetchFromAIPlatform()` 中实现实际的 API 调用：

```java
private Map<String, String> fetchFromAIPlatform(String repoUrl, String envName) {
    // TODO: 实现 HTTP 调用
    String apiUrl = "https://ai-platform.example.com/api/skill/config";
    String url = apiUrl + "?repo=" + URLEncoder.encode(repoUrl) + "&env=" + envName;
    
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + getAuthToken())
        .GET()
        .build();
    
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    
    if (response.statusCode() == 200) {
        return parseJsonToMap(response.body());
    }
    
    return null;
}
```

---

## 📂 文件路径说明

### Python config.py 的期望路径

**agnet-skill1/skills/config.py**:
```python
env_path = Path(__file__).parent / "config" / ".env"
# __file__ = skills/config.py
# parent = skills/ (仓库根目录)
# 最终路径 = skills/config/.env ✅
```

**Java 生成的路径**:
```java
Path envPath = workDir.resolve("config").resolve(".env");
// workDir = skills/ (仓库根目录)
// 最终路径 = skills/config/.env ✅ 匹配！
```

### 目录结构

```
skills/                          ← workDir (仓库根目录)
├── config.py                    ← Python 配置加载器
├── config/
│   └── .env                     ← Java 自动生成的配置文件 ✅
├── database/
│   └── db_manager.py            ← 数据库管理器
├── data_search/
│   ├── SKILL.md
│   └── scripts/
└── ...
```

---

## ⚙️ 配置项说明

### 数据库配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| DB_HOST | 数据查询统计数据库主机 | localhost |
| DB_PORT | 数据查询统计数据库端口 | 3306 |
| DB_NAME | 数据查询统计数据库名 | menu_recommand |
| DB_USER | 数据查询统计数据库用户 | root |
| DB_PASSWORD | 数据查询统计数据库密码 | (空) |
| DP_HOST | DataPlatform 数据库主机 | localhost |
| DP_PORT | DataPlatform 数据库端口 | 3306 |
| DP_NAME | DataPlatform 数据库名 | dp |
| DP_USER | DataPlatform 数据库用户 | root |
| DP_PASSWORD | DataPlatform 数据库密码 | (空) |
| CONVERSATION_DB_HOST | 对话存储数据库主机 | localhost |
| CONVERSATION_DB_PORT | 对话存储数据库端口 | 3306 |
| CONVERSATION_DB_NAME | 对话存储数据库名 | conversation_db |
| CONVERSATION_DB_USER | 对话存储数据库用户 | root |
| CONVERSATION_DB_PASSWORD | 对话存储数据库密码 | (空) |

### AI API 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| AI_API_URL | AI API 地址 | https://api.deepseek.com/v1 |
| AI_API_KEY | AI API 密钥 | (空) |
| AI_MODEL | AI 模型名称 | deepseek-chat |

### 其他配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| FLASK_HOST | Flask 监听地址 | 0.0.0.0 |
| FLASK_PORT | Flask 端口 | 5000 |
| FLASK_DEBUG | 调试模式 | false |
| FRONTEND_URL | 前端 URL | http://localhost:5173 |
| JWT_SECRET_KEY | JWT 密钥 | change-this-in-production |
| JWT_ALGORITHM | JWT 算法 | HS256 |
| WECOM_ENABLED | 企业微信开关 | true |

---

## 🔄 多环境支持

### 环境切换

通过系统属性指定环境：

```bash
# 开发环境
java -Dskill.env=dev --enable-preview -jar uagent.jar

# 测试环境
java -Dskill.env=test --enable-preview -jar uagent.jar

# 生产环境
java -Dskill.env=prod --enable-preview -jar uagent.jar
```

### 中台配置示例

在中台中为同一 Skill 配置不同环境的参数：

```json
{
  "repo": "git@example.com:skills/data-search.git",
  "environments": {
    "dev": {
      "DB_HOST": "localhost",
      "DB_PORT": 3306,
      "DB_USER": "dev_user",
      "DB_PASSWORD": "dev_pass"
    },
    "test": {
      "DB_HOST": "test-db.example.com",
      "DB_PORT": 3306,
      "DB_USER": "test_user",
      "DB_PASSWORD": "test_pass"
    },
    "prod": {
      "DB_HOST": "prod-db.example.com",
      "DB_PORT": 3306,
      "DB_USER": "prod_user",
      "DB_PASSWORD": "prod_pass"
    }
  }
}
```

---

## 🛡️ 安全性考虑

### 1. 敏感信息保护

- ✅ `.env` 文件不提交到 Git（已添加到 `.gitignore`）
- ✅ 配置从中台集中管理，避免硬编码
- ✅ 支持加密存储（中台实现）

### 2. 访问控制

- ✅ 中台 API 需要认证（Bearer Token）
- ✅ 按用户/角色限制配置访问权限
- ⚠️ TODO: 实现配置审计日志

### 3. 密钥轮换

- ✅ 通过中台统一更新配置
- ✅ 无需修改代码或重新部署
- ⚠️ TODO: 支持配置版本管理

---

## 🧪 测试验证

### 1. 检查 .env 文件是否生成

```bash
# 查看日志
grep "Generated .env file" app.log

# 检查文件是否存在
dir D:\公司\agnet-skill1\skills\config\.env
```

### 2. 验证配置内容

```bash
type D:\公司\agnet-skill1\skills\config\.env
```

预期输出：
```env
# =============================================================================
# Auto-generated by SkillEnvConfigManager
# DO NOT commit this file to version control!
# Generated at: 2026-06-12T03:34:43.123Z
# =============================================================================

# -----------------------------------------------------------------------------
# 数据查询统计数据库配置（原有配置）
# -----------------------------------------------------------------------------
DB_HOST=localhost
DB_PORT=3306
DB_NAME=menu_recommand
DB_USER=root
DB_PASSWORD=
...
```

### 3. 测试数据库连接

```bash
cd D:\公司\agnet-skill1\skills
.venv\Scripts\python.exe -c "
from database.db_manager import get_db_manager
result = get_db_manager().verify_connection()
print('Connection status:', result)
"
```

---

## 📊 优势总结

### vs 手动配置

| 对比项 | 手动配置 | 动态生成 |
|--------|----------|----------|
| 配置效率 | ❌ 每次克隆后手动创建 | ✅ 自动生成 |
| 错误率 | ❌ 容易遗漏或写错 | ✅ 标准化模板 |
| 多环境支持 | ❌ 需要维护多个文件 | ✅ 一键切换 |
| 安全性 | ❌ 可能误提交到 Git | ✅ 自动忽略 |
| 可维护性 | ❌ 分散在各处 | ✅ 集中管理 |
| 密钥轮换 | ❌ 需要逐个修改 | ✅ 中台统一更新 |

### vs 硬编码

| 对比项 | 硬编码 | 动态生成 |
|--------|--------|----------|
| 安全性 | ❌ 密钥暴露在代码中 | ✅ 外部化管理 |
| 灵活性 | ❌ 修改需重新编译 | ✅ 运行时更新 |
| 多租户 | ❌ 无法支持 | ✅ 每个租户独立配置 |
| 合规性 | ❌ 不符合安全规范 | ✅ 符合最佳实践 |

---

## 🚀 后续优化

### 短期（1-2周）

1. ✅ ~~实现基础配置生成逻辑~~
2. ⚠️ 实现 AI 中台 API 集成
3. ⚠️ 添加配置验证逻辑
4. ⚠️ 完善错误处理和日志

### 中期（1个月）

1. 支持配置版本管理
2. 实现配置回滚机制
3. 添加配置变更审计
4. 支持批量配置更新

### 长期（3个月）

1. 集成密钥管理系统（如 HashiCorp Vault）
2. 支持配置热更新（无需重启）
3. 实现配置依赖分析
4. 提供配置可视化界面

---

## 📚 相关文件

- [SkillEnvConfigManager.java](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/src/main/java/org/example/agentScope/util/skill/SkillEnvConfigManager.java) - 配置管理器
- [GitSkillManager.java](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/src/main/java/org/example/agentScope/mas/skillManager/core/GitSkillManager.java) - 集成点
- [虚拟环境初始化问题修复说明.md](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/虚拟环境初始化问题修复说明.md)
- [Skills数据库配置指南.md](file:///d:/公司/代码审查专用/开发/AgentScopeYFMax/Skills数据库配置指南.md)

---

**实现完成时间**: 2026-06-12  
**状态**: 🟢 基础功能已完成，中台集成待实现
