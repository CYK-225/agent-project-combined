# Git Skill 仓库集成 — 开发者指南

## 1. 概述

Git Skill 仓库集成让 Agent 能够从远程 Git 仓库动态加载技能包（SKILL.md + scripts + assets），无需修改代码、重启服务。

**核心能力：**

- 📦 **远程 Skill 加载**：自动 clone/pull 仓库，解析所有 SKILL.md
- 🔧 **Tool 渐进式披露**：Skill 激活时才暴露关联 Tool，避免 Prompt 膨胀
- 💻 **codeExecution**：为 Skill 提供 Shell/Read/Write 隔离执行环境
- 🔄 **动态绑定**：运行时通过 REST API 绑定/解绑/刷新，无需重启
- 🏗️ **声明式 + 动态双路径**：注解静态配置 + API 动态覆盖

---

## 2. 快速开始

### 2.1 准备 Skill 仓库

在 Git 仓库中按以下结构组织 Skill：

```
your-skills-repo/
├── skills/
│   ├── data-analysis/
│   │   └── SKILL.md          ← 框架自动识别
│   ├── code-review/
│   │   ├── SKILL.md
│   │   └── scripts/
│   │       └── analyze.sh    ← codeExecution 可执行的脚本
│   └── report-gen/
│       ├── SKILL.md
│       └── assets/
│           └── template.md
```

**关键约定：**
- Skill 目录必须放在 `skills/` 子目录下
- 每个 Skill 必须包含 `SKILL.md` 文件
- 框架会自动检测 `skills/` 目录（GitSkillRepository 内置行为）

### 2.2 不使用 Git Skill（默认）

**什么都不用做。** 没配 `skillRepoUrl` 也没动态绑定，`setupSkills()` 返回 null，Agent 正常构建。

### 2.3 使用 Git Skill（配了就自动生效）

只要配了 `skillRepoUrl`（注解或动态绑定），默认 `setupSkills()` 自动加载：

**加载全部 Skill：**
```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "https://github.com/your-org/agent-skills.git"
)
```

**按正则过滤 Skill：**
```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "https://github.com/your-org/agent-skills.git",
    skillNames = {"data-.*", "report-gen"}   // 正则：data-开头的 + 精确匹配 report-gen
)
```

`skillNames` 每个元素都是**正则表达式**，`String.matches()` 匹配：

| skillNames 值 | 匹配效果 |
|---|---|
| `"data-analysis"` | 精确匹配（正则也能精确） |
| `"data-.*"` | 所有 data- 开头的 Skill |
| `".*security.*"` | 名称含 security 的 Skill |
| 不填 / `{}` | 加载全部 |

完整的 Agent 类：

```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "https://github.com/your-org/agent-skills.git",
    skillNames = {"data-.*"}
)
@Component
public class DataAnalystAgent extends AbstractAgentTemplate {

    public DataAnalystAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return "你是一个数据分析专家，善于使用工具完成分析任务。";
    }

    // 不需要覆盖 setupSkills()！
    // 配了 skillRepoUrl → 自动加载
    // 配了 skillNames → 按正则过滤
}
```

**关键点：**
- 只配 `skillRepoUrl` → 加载仓库中所有 Skill
- 配 `skillNames` → 按正则过滤，只加载匹配的
- 没配 → `setupSkills()` 返回 null，Agent 正常工作
- 想自定义（混合本地 + 远程）→ 覆盖 `setupSkills()`

### 2.4 动态绑定（无需注解）

不用 `@AgentDefinition(skillRepoUrl=...)` 也行，运行时通过 API 绑定：

```bash
curl -X POST http://localhost:8080/api/skill-repo/bind \
  -H "Content-Type: application/json" \
  -d '{"agentName": "DataAnalyst", "repoUrl": "https://github.com/your-org/agent-skills.git"}'
```

`getSkillRepoUrl()` 会自动查注册表，不需要注解。

### 2.5 自定义 setupSkills（高级：混合本地 + 远程）

```java
@Override
protected SkillBox setupSkills() {
    AgentSkill localSkill = BaseSkillBoxFactory.createBaseAgentSkill(
        "local_helper", "本地辅助技能", "当用户需要帮助时提供指导"
    );

    return components.skillBox().create(getToolkit())
            .addGitSkills(getSkillRepoUrl())                       // 远程：加载全部
            // 或按正则过滤：
            // .addGitSkills(getSkillRepoUrl(), true, "data-.*", "report-gen")
            .addOnlySkill(localSkill)                               // 本地 Skill
            .buildSkillBox();
}
```

**`addGitSkills` 方法签名：**

| 方法 | 说明 |
|---|---|
| `.addGitSkills(url)` | 加载全部 Skill，开启代码执行 |
| `.addGitSkills(url, codeExecution)` | 加载全部，可选是否开启代码执行 |
| `.addGitSkills(url, codeExecution, "data-.*", "security")` | 按正则过滤，精确名称也能用 |

---

## 3. REST API 参考

所有接口前缀：`/api/skill-repo`

### 3.1 绑定仓库

```
POST /api/skill-repo/bind
```

```json
{
  "agentName": "DataAnalyst",
  "repoUrl": "https://github.com/your-org/agent-skills.git",
  "skillPatterns": ["data-.*", "security"]
}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `agentName` | ✅ | Agent 名称 |
| `repoUrl` | ✅ | Git 仓库地址 |
| `skillPatterns` | ❌ | 正则过滤数组，不传则加载全部 |

**响应：**
```json
{
  "success": true,
  "agentName": "DataAnalyst",
  "repoUrl": "https://github.com/your-org/agent-skills.git",
  "skillPatterns": "[data-.*, security]"
}
```

> ⚠️ 绑定只影响**后续创建**的 Agent 实例。已缓存的 Agent 不受影响，需调用 `/refresh` 刷新。

### 3.2 解绑仓库

```
DELETE /api/skill-repo/unbind/{agentName}
```

**响应：**
```json
{
  "success": true,
  "agentName": "DataAnalyst"
}
```

### 3.3 查看所有绑定

```
GET /api/skill-repo/list
```

**响应：**
```json
{
  "DataAnalyst": "https://github.com/your-org/agent-skills.git",
  "CodeReviewer": "https://github.com/your-org/review-skills.git"
}
```

### 3.4 刷新仓库 + 重建 Agent

```
POST /api/skill-repo/refresh
```

```json
{
  "agentName": "DataAnalyst",
  "threadId": "thread-abc-123",
  "repoUrl": "https://github.com/your-org/new-skills.git",
  "skillPatterns": ["data-.*", "report-gen"]
}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `agentName` | ✅ | Agent 名称 |
| `threadId` | ❌ | 指定要重建的会话 ID |
| `repoUrl` | ❌ | 新的仓库地址（传了会更新绑定） |
| `skillPatterns` | ❌ | 更新正则过滤（传了会覆盖） |

**内部流程：**
1. 如果传了新 `repoUrl` → 更新 Registry
2. `gitSkillManager` 刷新仓库（pull + 重新解析 Skill）
3. 清除指定 threadId 的会话缓存
4. 用户下次发消息时自动用最新 Skill 重建 Agent

### 3.5 查看仓库中的 Skill 列表

```
GET /api/skill-repo/skills?repoUrl=https://github.com/your-org/agent-skills.git
```

**响应：**
```json
["data-analysis", "code-review", "report-gen"]
```

---

## 4. 架构与数据流

### 4.1 设计原则：配了就生效，没配零干扰

- **没配 `skillRepoUrl`** → `getSkillRepoUrl()` 返回 null → `setupSkills()` 返回 null → Agent 正常构建
- **配了 `skillRepoUrl`**（注解或 bind）→ `getSkillRepoUrl()` 有值 → `setupSkills()` 自动加载 Git Skill
- **想自定义** → 覆盖 `setupSkills()`，显式调用 `.addGitSkills(getSkillRepoUrl())`
- **`getSkillRepoUrl()` 惰性自获取** — buildAgent 不主动介入

### 4.2 skillRepoUrl 的获取逻辑

`getSkillRepoUrl()` 通过注解反射 + Registry 查询，惰性自获取：

```
getSkillRepoUrl()
  ├─ 1. SkillRepoRegistry.get(agentName)        ← 动态值（API 绑定的）
  │     └─ 有值 → 返回
  └─ 2. @AgentDefinition.skillRepoUrl           ← 静态值（注解声明的）
        └─ 兜底返回
```

不依赖外部注入，不修改 buildAgent 流程。

### 4.3 使用 vs 不使用的对比

| 场景 | 代码变化 | 行为 |
|---|---|---|
| 不用 Git Skill | 无 | `getSkillRepoUrl()` 返回 null → `setupSkills()` 返回 null → 零干扰 |
| 注解声明 skillRepoUrl | 加一个属性 | 自动加载远程 Skill，不用覆盖 setupSkills() |
| 动态绑定 | 不改代码，调 API | 同上，getSkillRepoUrl() 自动查 Registry |
| 混合本地 + 远程 | 覆盖 setupSkills() | 显式调用 .addGitSkills() + .addOnlySkill() |

### 4.4 三条获取 Agent 的路径

| 路径 | 入口 | Git Skill 生效？ |
|---|---|---|
| AGUI（前端请求） | `CustomThreadSessionManager` → factory 闭包 → `buildAgentWithSession` | ✅ |
| `getAgent(name)` | `AgentPoolManager.instantiateAgent` → `buildAgent` | ✅ |
| `getAgentWithSession(name, threadId)` | `AgentPoolManager` → `buildAgentWithSession` | ✅ |

所有路径都在 `buildAgent` 入口处解析 `agentName` 和 `cachedSkillRepoUrl`，`setupSkills()` 中 `getSkillRepoUrl()` 自动获取。

### 4.5 组件关系图

```
┌──────────────────────────────────────────────────────────┐
│                   SkillRepoRegistry                       │
│         agentName → repoUrl 内存映射（@Component）         │
└───────────────────────┬──────────────────────────────────┘
                        │ get(agentName)
                        ▼
┌──────────────────────────────────────────────────────────┐
│  AbstractAgentTemplate                                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │ getSkillRepoUrl()                                  │  │
│  │   Registry 动态值 > 注解静态值                       │  │
│  └────────────────────┬───────────────────────────────┘  │
│                       ▼                                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │ setupSkills()                                      │  │
│  │   components.skillBox().create(getToolkit())       │  │
│  │     .addGitSkills(getSkillRepoUrl())               │  │
│  │     .buildSkillBox()                               │  │
│  └────────────────────┬───────────────────────────────┘  │
└───────────────────────┼──────────────────────────────────┘
                        ▼
┌──────────────────────────────────────────────────────────┐
│  BaseSkillBoxFactory.SkillBoxBuilder                     │
│  ┌────────────────────────────────────────────────────┐  │
│  │ addGitSkills(repoUrl)                              │  │
│  │   → GitSkillManager.loadSkills(repoUrl)            │  │
│  │   → GitSkillRepository clone/pull                  │  │
│  │   → 解析 SKILL.md → AgentSkill[]                   │  │
│  │   → skillBox.registerSkill() + Tool 渐进披露        │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ addCodeExecution(localPath)                        │  │
│  │   → skillBox.codeExecution()                       │  │
│  │     .withShell().withRead().withWrite()             │  │
│  │     .enable()                                      │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────┐
│  GitSkillManager  (@Component)                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │ ConcurrentHashMap<repoUrl, GitSkillRepository>     │  │
│  │ ConcurrentHashMap<repoUrl, List<AgentSkill>>       │  │
│  │ ConcurrentHashMap<repoUrl, Path> localPathCache    │  │
│  └────────────────────────────────────────────────────┘  │
│  • 同一 repoUrl 只 clone 一次                            │
│  • 后续自动检查远端 HEAD，有更新才 pull                    │
│  • @PreDestroy 清理临时目录                               │
└──────────────────────────────────────────────────────────┘
```

---

## 5. 文件清单

| 文件 | 类型 | 描述 |
|---|---|---|
| `framework/annotation/AgentDefinition.java` | 修改 | +`skillRepoUrl()` 注解属性 |
| `framework/core/SkillRepoRegistry.java` | 新增 | agentName→repoUrl 内存映射 |
| `framework/core/GitSkillManager.java` | 新增 | GitSkillRepository 生命周期管理 |
| `framework/core/AgentComponentFacade.java` | 修改 | +`skillRepoRegistry` 字段 |
| `util/skill/BaseSkillBoxFactory.java` | 修改 | +`addGitSkills()` +`addCodeExecution()` |
| `framework/core/AbstractAgentTemplate.java` | 修改 | +`getSkillRepoUrl()` 自获取逻辑 |
| `framework/core/AgentPoolManager.java` | 修改 | 静态配置自动写入 Registry |
| `framework/controller/SkillRepoController.java` | 新增 | REST API |

---

## 6. 典型场景

### 场景一：AGUI 前端对话（自动生效）

用户通过前端发起对话 → 框架自动调用 `buildAgentWithSession` → `getSkillRepoUrl()` 获取仓库地址 → 自动加载 Git Skill。

**无需任何额外代码。**

### 场景二：后端服务直接调用

```java
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AgentPoolManager agentPool;

    public String analyze(String data) {
        // getAgent 内部自动加载 Git Skill
        ReActAgent agent = agentPool.getAgent("DataAnalyst");
        return agent.reply(Msg.user(data)).getContent();
    }
}
```

### 场景三：运行时切换 Skill 仓库和过滤规则

```bash
# 1. 绑定仓库 + 指定过滤规则
curl -X POST http://localhost:8080/api/skill-repo/bind \
  -d '{"agentName": "DataAnalyst", "repoUrl": "https://github.com/your-org/v2-skills.git", "skillPatterns": ["data-.*"]}'

# 2. 刷新指定会话（也可同时更新过滤规则）
curl -X POST http://localhost:8080/api/skill-repo/refresh \
  -d '{"agentName": "DataAnalyst", "threadId": "thread-abc-123", "skillPatterns": ["data-.*", "security"]}'

# 用户下次发消息 → Agent 自动使用新仓库 + 新过滤规则
```

### 场景四：多 Agent 共享同一仓库

```java
@AgentDefinition(name = "DataAnalyst", skillRepoUrl = "https://github.com/your-org/shared-skills.git")
public class DataAnalystAgent extends AbstractAgentTemplate { /* ... */ }

@AgentDefinition(name = "ReportGen", skillRepoUrl = "https://github.com/your-org/shared-skills.git")
public class ReportGenAgent extends AbstractAgentTemplate { /* ... */ }
```

`GitSkillManager` 内部按 repoUrl 缓存，**同一仓库只 clone 一次**，多个 Agent 共享。

---

## 7. Git 认证配置

`GitSkillRepository` 不内置认证机制，**依赖系统级 Git 配置**。

### 7.1 公开仓库

无需任何配置，直接使用 HTTPS 地址即可。

### 7.2 HTTPS 私有仓库

**方式一：credential helper（推荐）**

```bash
# 开启凭证缓存
git config --global credential.helper store

# 手动 clone 一次，输入用户名密码后自动缓存
git clone https://github.com/your-org/agent-skills.git
```

之后应用内 `GitSkillRepository` clone/pull 会自动使用缓存的凭证。

**方式二：URL 内嵌 Token**

```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "https://x-token-auth:ghp_xxxx@github.com/your-org/agent-skills.git"
)
```

> ⚠️ Token 会出现在注解和日志中，建议结合 Spring 配置外部化：
> ```java
> @AgentDefinition(skillRepoUrl = "${git.skill.repo-url}")
> ```
> 注意：`@AgentDefinition` 注解属性不支持 `${}` 占位符，需通过动态绑定方式：
> ```bash
> curl -X POST http://localhost:8080/api/skill-repo/bind \
>   -d "{\"agentName\":\"DataAnalyst\",\"repoUrl\":\"${GIT_SKILL_REPO_URL}\"}"
> ```

### 7.3 SSH 私有仓库

```bash
# 生成密钥（如果没有）
ssh-keygen -t ed25519 -C "your-app"

# 添加到 ssh-agent
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519

# 将公钥添加到 Git 平台（GitHub/Bitbucket/GitLab → Settings → SSH Keys）
```

使用 SSH 地址：
```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "git@github.com:your-org/agent-skills.git"
)
```

### 7.4 验证认证是否正确

在应用运行环境中执行：
```bash
git clone https://github.com/your-org/agent-skills.git /tmp/test-clone
```

如果命令行能 clone 成功，`GitSkillRepository` 也能。如果失败，先解决系统 Git 认证问题。

---

## 8. 注意事项

1. **认证依赖系统 Git**：应用运行环境需预配置好 Git 凭证（见第 7 节）
2. **首次 clone 有延迟**：仓库较大时首次 Agent 创建会慢，后续走缓存
3. **刷新不等于热替换**：调用 `/refresh` 后，已存在的 Agent 实例不会变，需要重建（清除会话缓存）
4. **scope=prototype 建议**：使用 Git Skill 的 Agent 建议设为 `prototype`，避免单例缓存导致 Skill 不更新
5. **临时目录自动清理**：`GitSkillManager` 在 `@PreDestroy` 时清理所有 clone 的临时目录

---

## 9. Classpath Skill（本地预打包 Skill）

与 Git Skill 对称的另一种 Skill 来源：从 classpath 资源目录加载预打包的 SKILL.md。

### 9.1 与 Git Skill 的区别

| | Git Skill | Classpath Skill |
|---|---|---|
| 来源 | 远程 Git 仓库（需 clone/pull） | classpath 资源目录（本地，随 JAR 打包） |
| Skill 内容可修改 | ✅ 可修改远端文件 | ❌ 只读，Skill 内容编译时固定 |
| 代码执行（Shell/Read/Write） | ✅ | ✅（开发环境有效，Fat JAR 内部资源无法提供真实路径） |
| 适用场景 | Skill 需要动态更新、外部团队维护 | Skill 稳定不变、随应用一起发布 |

### 9.2 资源目录结构

```
src/main/resources/skills/
├── data-analysis/
│   └── SKILL.md
├── code-review/
│   └── SKILL.md
└── report-gen/
    └── SKILL.md
```

### 9.3 使用方式

**注解声明（推荐）：**

```java
@AgentDefinition(
    name = "DataAnalyst",
    classpathResourcePath = "skills",
    classpathSkillNames = {"data-.*"}  // 正则过滤
)
public class DataAnalystAgent extends AbstractAgentTemplate {
    // setupSkills() 自动加载，无需覆盖
}
```

**链式构建器：**

```java
// 加载全部 + 代码执行
builder.addClasspathSkills("skills")

// 正则过滤
builder.addClasspathSkills("skills", true, "data-.*", "report")

// 不开启代码执行
builder.addClasspathSkills("skills", false)
```

**与 Git Skill 合并使用：**

```java
builder
    .addGitSkills(getSkillRepoUrl(), true)       // 远程 Git
    .addClasspathSkills("skills", true)           // 本地 classpath
    .buildSkillBox();
```

### 9.4 REST API

| 端点 | 方法 | 功能 |
|---|---|---|
| `/api/classpath-skill/skills?resourcePath=skills` | GET | 查看 Skill 名称列表 |
| `/api/classpath-skill/refresh?resourcePath=skills` | POST | 刷新缓存（开发环境热重载用） |

### 9.5 Fat JAR 环境注意

在 Fat JAR 中，classpath 资源位于 JAR 文件内部（`jar:file:xxx.jar!/BOOT-INF/classes!/skills/`），
没有真实的文件系统路径，因此 `codeExecution` 的 `workDir` 无法设置，代码执行能力会自动跳过并打印警告日志。

开发环境（IDE 直接运行）下，资源是真实文件系统路径，代码执行正常工作。

---

## 版本信息

- **版本**：1.0
- **依赖**：agentscope 1.0.10（内置 GitSkillRepository，JGit 6.10.0 已传递依赖）
