---

# AgentScope Framework 模块技术文档

## 1. 概述

本模块是 AgentScope 框架的核心声明式扩展层，提供了一套基于注解驱动的智能体定义与管理方案。采用 **模板方法模式** 与 **工厂模式** 相结合的设计，实现配置与运行时逻辑的完全解耦。主要包含四大核心组件：

1. **@AgentDefinition 注解**：声明式 Agent 元信息配置，支持 12+ 配置参数。
2. **AbstractAgentTemplate 模板**：抽象基类，子类只需实现必要方法即可完成 Agent 组装。
3. **AgentPoolManager 管理器**：负责扫描、注册、缓存和按需实例化 Agent。
4. **AgentComponentFacade 门面**：统一管理模型、工具、记忆、计划等组件工厂。

---

## 2. 功能模块详解

### 2.1 AgentDefinition 注解模块

`@AgentDefinition` 是整个声明式框架的核心入口，用于标识一个类为 Agent 模板并声明其元信息与能力配置。

#### 功能罗列

* **声明式定义**：通过注解直接声明 Agent 名称、描述、分组等元信息。
* **模型预设**：支持通过 `modelType` 指定预设场景（思考/工具/聊天），无需手动创建 Model。
* **钩子策略**：支持通过 `hooksType` 指定预设钩子组合（log/Review/debug）。
* **懒加载控制**：默认启用懒加载，首次调用时才实例化底层 Agent。
* **分组管理**：支持按 `group` 批量获取 Agent，便于组建协作团队。

#### 使用规范

1. **继承要求**：被注解的类必须继承 `AbstractAgentTemplate`，否则会被跳过注册。
2. **名称唯一**：Agent 名称在全局必须唯一，建议使用业务含义明确的命名。
3. **激活控制**：`active = false` 的 Agent 不会被注册到池中，可用于临时禁用。
4. **组件注入**：子类需通过构造函数注入 `AgentComponentFacade`。

#### 注解属性参考 (`@AgentDefinition`)

| 属性 | 类型 | 默认值 | 描述 |
| --- | --- | --- | --- |
| `value` / `name` | String | 类名 | Agent 唯一标识名称 |
| `description` | String | "" | Agent 职责描述 |
| `group` | String | "default" | 分组名称，用于批量管理 |
| `lazy` | boolean | true | 是否启用懒加载 |
| `active` | boolean | true | 是否激活（false 则跳过注册） |
| `modelProvider` | Enum | DASHSCOPE | 模型提供者（DASHSCOPE/OLLAMA） |
| `modelType` | String | "" | 预设模型场景（思考/工具/聊天） |
| `hooksType` | String | "" | 预设钩子策略（log/Review/debug） |
| `maxIters` | int | 5 | ReAct 循环最大迭代次数 |
| `enableMemory` | boolean | true | 是否启用短期记忆 |
| `enablePlan` | boolean | false | 是否启用计划能力 |
| `enableLongTermMemory` | boolean | false | 是否启用长程记忆 |
| `enablePersistence` | boolean | false | 是否启用会话持久化 |
| `enableMail` | boolean | false | 是否启用邮件通信能力 |
| `skillRepoUrl` | String | "" | Git Skill 仓库地址 |
| `skillNames` | String[] | {} | Skill 正则过滤（空=加载全部） |
| `checkRunning` | boolean | true | 是否检查运行状态 |
| `priority` | int | 0 | 优先级（数值越小越高） |

#### 代码示例

```java
// 极简形式 - 只需指定名称
@AgentDefinition("SimpleAgent")
public class SimpleAgent extends AbstractAgentTemplate {
    
    public SimpleAgent(AgentComponentFacade components) {
        super(components);
    }
    
    @Override
    protected String setupSysPrompt() {
        return "你是一个简单的助手，负责回答用户的问题。";
    }
}

// 完整形式 - 指定场景配置
@AgentDefinition(
    name = "DataAnalyst",
    group = "finance_team",
    description = "金融数据分析专家",
    modelType = "思考",
    hooksType = "log",
    maxIters = 10,
    enableMemory = true,
    enablePlan = true,
    priority = 1
)
public class DataAnalystAgent extends AbstractAgentTemplate {
    
    public DataAnalystAgent(AgentComponentFacade components) {
        super(components);
    }
    
    @Override
    protected String setupSysPrompt() {
        return "你是一个金融数据分析师，擅长...";
    }
    
    @Override
    protected List<Object> setupTools() {
        return List.of(new SearchTool(), new CalculateTool());
    }
}
```

---

### 2.2 AbstractAgentTemplate 模板模块

`AbstractAgentTemplate` 是所有声明式 Agent 的抽象基类，采用模板方法模式封装 Agent 构建流程。

#### 功能罗列

* **最小化实现**：子类只需实现 `setupSysPrompt()` 一个抽象方法。
* **12 个可覆盖点**：提供模型、工具、记忆、计划、钩子等 12 个配置扩展点。
* **生命周期回调**：支持 `afterAgentBuilt()` 回调进行后置初始化。
* **注解自动注入**：`AgentPoolManager` 会自动注入 `@AgentDefinition` 元数据。

#### 使用规范

1. **构造函数约定**：子类必须提供一个接收 `AgentComponentFacade` 的构造函数。
2. **Spring Bean**：由于 `@AgentDefinition` 继承了 `@Component`，子类会自动成为 Spring Bean。
3. **配置优先级**：子类覆盖的方法优先级高于注解配置。
4. **线程安全**：模板类本身是单例，避免在模板中存储可变状态。

#### 可覆盖方法参考 (`AbstractAgentTemplate`)

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `setupSysPrompt()` | String | **必填**，设置系统提示词 |
| `setupTools()` | List\<Object\> | 设置工具列表，默认空 |
| `setupSkills()` | SkillBox | 设置技能盒，配了 skillRepoUrl 自动加载 Git Skill |
| `getSkillRepoUrl()` | String | 获取 Git 仓库地址（Registry > 注解） |
| `getSkillPatterns()` | String[] | 获取 Skill 正则过滤（Registry > 注解） |
| `setupCustomModel()` | Model | 自定义模型实例，默认 null |
| `setupCustomPlan()` | PlanNotebook | 自定义计划本，默认 null |
| `setupCustomMemory()` | AutoContextMemory | 自定义短期记忆，默认 null |
| `setupLongTermMemory()` | LongTermMemory | 自定义长程记忆，默认 null |
| `setupLongTermMemoryMode()` | LongTermMemoryMode | 长程记忆模式，默认 null |
| `setupModelExecutionConfig()` | ExecutionConfig | 模型执行配置，默认 null |
| `setupToolExecutionConfig()` | ExecutionConfig | 工具执行配置，默认 null |
| `setupToolExecutionContext()` | ToolExecutionContext | 工具执行上下文，默认 null |
| `setupCustomHooks()` | List\<Hook\> | 自定义钩子列表，默认空 |
| `afterAgentBuilt(agent)` | void | 生命周期回调，默认空实现 |

#### 代码示例

```java
@AgentDefinition(name = "CoderAgent", group = "dev_team", modelType = "工具")
public class CoderAgent extends AbstractAgentTemplate {
    
    private final CodeExecutionTool codeTool;
    
    public CoderAgent(AgentComponentFacade components, CodeExecutionTool codeTool) {
        super(components);
        this.codeTool = codeTool;
    }
    
    @Override
    protected String setupSysPrompt() {
        return "你是一个专业的程序员，负责编写高质量的代码。";
    }
    
    @Override
    protected List<Object> setupTools() {
        return List.of(codeTool, new GitTool(), new TestTool());
    }
    
    @Override
    protected Model setupCustomModel() {
        // 使用自定义模型配置（覆盖注解中的 modelType）
        return components.model().dashScope()
            .temperature(0.3)
            .maxTokens(4096)
            .build();
    }
    
    @Override
    protected void afterAgentBuilt(ReActAgent agent) {
        log.info("CoderAgent [{}] initialized successfully", agent.getName());
    }
}
```

---

### 2.3 AgentPoolManager 管理模块

`AgentPoolManager` 是 Agent 池的核心管理器，负责扫描、注册、缓存和按需实例化所有声明式定义的 Agent。

#### 功能罗列

* **自动扫描**：启动时自动扫描所有 `@AgentDefinition` 注解的类并注册元数据。
* **懒加载**：默认只注册元数据，首次调用 `getAgent()` 时才实例化。
* **实例缓存**：实例化后的 Agent 会被缓存，避免重复创建。
* **分组索引**：支持按 `group` 批量获取 Agent 或构建 `MsgAgentPool`。
* **预热机制**：支持 `warmUp()` 预先实例化所有或指定分组的 Agent。

#### 使用规范

1. **依赖注入**：通过 `@Autowired` 注入 `AgentPoolManager` 使用。
2. **名称引用**：所有 API 均通过 Agent 名称（String）引用，名称不存在会抛出异常。
3. **缓存管理**：可使用 `evictCache()` 清除单个缓存或 `evictAllCache()` 清除全部。
4. **强制重建**：`getAgent(name, true)` 可忽略缓存强制重新创建实例。

#### API 参考 (`AgentPoolManager`)

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `getAgent(String name)` | ReActAgent | 获取 Agent 实例（懒加载） |
| `getAgent(String name, boolean forceRebuild)` | ReActAgent | 获取 Agent，可强制重建 |
| `getAgentsByGroup(String group)` | List\<ReActAgent\> | 获取分组内所有 Agent（按优先级排序） |
| `getAgentMapByGroup(String group)` | Map\<String, ReActAgent\> | 获取分组内 Agent 映射 |
| `buildGroupPool(String group)` | MsgAgentPool | 根据分组构建 MsgAgentPool |
| `buildGroupPool(String group, String... names)` | MsgAgentPool | 构建包含指定 Agent 的 Pool |
| `getActiveAgentNames()` | Set\<String\> | 获取所有已激活的 Agent 名称 |
| `getGroupNames()` | Set\<String\> | 获取所有分组名称 |
| `hasAgent(String name)` | boolean | 检查 Agent 是否存在 |
| `isInstantiated(String name)` | boolean | 检查 Agent 是否已实例化 |
| `getMetadata(String name)` | AgentMetadata | 获取 Agent 元数据 |
| `evictCache(String name)` | void | 清除指定 Agent 缓存 |
| `evictAllCache()` | void | 清除所有 Agent 缓存 |
| `warmUp()` | void | 预热所有 Agent |
| `warmUpGroup(String group)` | void | 预热指定分组 Agent |

#### 代码示例

```java
@Service
@RequiredArgsConstructor
public class AgentService {
    
    private final AgentPoolManager agentPool;
    
    // 获取单个 Agent
    public ReActAgent getAnalyst() {
        return agentPool.getAgent("DataAnalyst");
    }
    
    // 获取分组内所有 Agent
    public List<ReActAgent> getFinanceTeam() {
        return agentPool.getAgentsByGroup("finance_team");
    }
    
    // 快速组建 MsgHub 协作
    public void runCollaboration() {
        MsgAgentPool pool = agentPool.buildGroupPool("finance_team");
        
        MsgHub hub = pool.hub("analysis_hub")
            .join("DataAnalyst", "RiskAssessor")
            .announce(Msg.system("开始分析任务"))
            .build();
        
        // 执行协作逻辑...
    }
    
    // 预热指定分组
    @PostConstruct
    public void init() {
        agentPool.warmUpGroup("finance_team");
    }
}
```

---

### 2.4 AgentComponentFacade 门面模块

`AgentComponentFacade` 是组件工厂的统一门面，集中管理模型、工具、记忆、计划、钩子和技能等组件的创建。

#### 功能罗列

* **统一入口**：通过一个 Bean 访问所有组件工厂。
* **流式访问**：使用 `@Accessors(fluent = true)` 支持流畅的链式调用。
* **Spring 集成**：所有工厂均为 Spring Bean，支持依赖注入。
* **组件解耦**：Agent 模板无需直接依赖各个工厂，通过门面统一获取。

#### 使用规范

1. **构造注入**：在 `AbstractAgentTemplate` 子类中通过构造函数注入。
2. **流式调用**：getter 方法无 `get` 前缀，如 `components.model()` 而非 `components.getModel()`。
3. **按需使用**：只需在覆盖的配置方法中按需调用相应工厂。

#### 组件工厂参考 (`AgentComponentFacade`)

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `model()` | ModelFactoryFacade | 模型工厂门面（DashScope/Ollama） |
| `toolkit()` | ToolkitFactory | 工具包工厂 |
| `memory()` | AutoContextMemoryFactory | 短期记忆工厂 |
| `plan()` | PlanNotebookFactory | 计划本工厂 |
| `hooks()` | HookListFactory | 钩子列表工厂 |
| `skillBox()` | BaseSkillBoxFactory | 技能盒工厂 |
| `agentPoolManager()` | AgentPoolManager | Agent 池管理器 |
| `skillRepoRegistry()` | SkillRepoRegistry | Git Skill 仓库注册表 |
| `mailboxCenter()` | MailboxCenter | 邮件中心 |

#### 代码示例

```java
@AgentDefinition(name = "AdvancedAgent", modelType = "")
public class AdvancedAgent extends AbstractAgentTemplate {
    
    public AdvancedAgent(AgentComponentFacade components) {
        super(components);
    }
    
    @Override
    protected String setupSysPrompt() {
        return "你是一个高级智能助手。";
    }
    
    @Override
    protected Model setupCustomModel() {
        // 使用门面访问模型工厂
        return components.model()
            .dashScope()
            .modelType("qwen-max")
            .temperature(0.7)
            .build();
    }
    
    @Override
    protected AutoContextMemory setupCustomMemory() {
        // 使用门面访问记忆工厂
        return components.memory()
            .builder()
            .maxRounds(10)
            .build();
    }
    
    @Override
    protected List<Hook> setupCustomHooks() {
        // 使用门面访问钩子工厂
        return components.hooks()
            .buildHookList("log");
    }
}
```

---

## 3. 快速开始

### 3.1 定义 Agent

```java
@AgentDefinition(
    name = "MyAgent",
    group = "my_team",
    description = "我的第一个智能体",
    modelType = "聊天",
    hooksType = "log"
)
public class MyAgent extends AbstractAgentTemplate {
    
    public MyAgent(AgentComponentFacade components) {
        super(components);
    }
    
    @Override
    protected String setupSysPrompt() {
        return "你是一个友好的助手。";
    }
}
```

### 3.2 使用 Agent

```java
@RestController
@RequiredArgsConstructor
public class ChatController {
    
    private final AgentPoolManager agentPool;
    
    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        ReActAgent agent = agentPool.getAgent("MyAgent");
        Msg response = agent.reply(Msg.user(message));
        return response.getContent();
    }
}
```

---

## 4. 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentPoolManager                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  @PostConstruct: scanAndRegisterAgents()            │   │
│  │  - 扫描 @AgentDefinition 注解                        │   │
│  │  - 注册 AgentMetadata                               │   │
│  │  - 建立分组索引                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                              │                              │
│                              ▼                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  getAgent(name) / getAgentsByGroup(group)           │   │
│  │  - 懒加载实例化                                      │   │
│  │  - 实例缓存                                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  AbstractAgentTemplate                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  buildAgent()                                       │   │
│  │  1. 解析 @AgentDefinition 配置                       │   │
│  │  2. 调用子类 setup* 方法获取组件                      │   │
│  │  3. 通过 AgentComponentFacade 获取工厂               │   │
│  │  4. 组装 ReActAgent                                 │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  AgentComponentFacade                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │  model   │ │ toolkit  │ │  memory  │ │   plan   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐                                  │
│  │  hooks   │ │ skillBox │                                  │
│  └──────────┘ └──────────┘                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 最佳实践

1. **命名规范**：Agent 名称使用 PascalCase，如 `DataAnalyst`、`CodeReviewer`。
2. **分组策略**：按业务域或功能团队划分 group，如 `finance_team`、`dev_team`。
3. **懒加载优先**：除非有特殊需求，保持 `lazy = true` 以优化启动性能。
4. **复用组件**：通过 `AgentComponentFacade` 复用已配置的工厂，避免重复创建。
5. **钩子策略**：生产环境推荐使用 `hooksType = "log"`，开发调试可使用 `"debug"`。

---

## 6. 版本信息

* **版本**：4.0
* **作者**：AgentScope-Team
* **依赖**：Spring Boot 3.x, Lombok, AgentScope Core
