# 通信层与Agent构建核心代码分析

## 1. 通信架构概览

项目采用三层通信架构，实现AI中台与工具平台、GUI容器之间的解耦通信：

```
工具平台 ──HTTP──> AI中台 ──HTTP──> GUI容器
    │                  │                │
    │    回调通知       │   异步回调      │
    └──────────────────┴────────────────┘
```

---

## 2. 核心通信组件

### 2.1 AgentTaskApiClient - 工具平台API客户端
**文件**: `AgentScopeYFMax/src/main/java/org/example/acl/apiClient/AgentTaskApiClient.java`

**设计逻辑**:
- 封装与Agent中台的所有HTTP通信接口
- 供工具平台调用，实现任务创建、恢复、查询等操作
- 使用OkHttp实现同步/异步请求

**核心API**:
```java
// 调用Agent（创建或恢复会话）
public boolean invokeAgent(String agentName, String sessionId, String taskId, 
                           Integer containerPort, String instruction, String callbackUrl)

// 恢复Agent任务（容器执行完成后调用）
public boolean resumeTask(String taskId, String executionResult, Boolean success,
                          String result, String screenshot, String screenshotPath, Integer step)

// 查询任务状态
public TaskStatus getTaskStatus(String taskId)

// 取消任务
public boolean cancelTask(String taskId)
```

### 2.2 GuiApiClient - GUI容器通信客户端
**文件**: `AgentScopeYFMax/src/main/java/org/example/acl/apiClient/GuiApiClient.java`

**设计逻辑**:
- 封装与GUI容器的所有HTTP通信
- 支持桌面GUI自动化操作（鼠标、键盘、应用控制）
- 采用异步发送+回调机制，不阻塞主流程

**核心API**:
```java
// 注册Agent到容器
public boolean registerAgent(AgentTaskEntity taskEntity)

// GUI操作（全部异步）
public void leftClick(int step, int x, int y)
public void rightClick(int step, int x, int y)
public void doubleClick(int step, int x, int y)
public void type(int step, String text)
public void key(int step, String keys)
public void scroll(int step, int pixels)
public void openApp(int step, String appName)
public void mouseMove(int step, int x, int y)
public void drag(int step, int x, int y)
public void wait(int step, int seconds)
public void reset(int step)
```

### 2.3 PhoneCallbackController - 容器回调控制器
**文件**: `AgentScopeYFMax/src/main/java/org/example/acl/callBack/PhoneCallbackController.java`

**设计逻辑**:
- 接收GUI容器推送的工具执行结果和截图
- 纯HTTP层接收，业务逻辑全部委托给AgentTaskExecutorService
- 驱动Agent的挂起-恢复循环

**核心接口**:
```java
@RestController
@RequestMapping("/api/phone")
public class PhoneCallbackController {
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody AgentTaskNotifyDTO payload)
}
```

### 2.4 AgentTaskExecutorService - 任务执行服务（核心）
**文件**: `AgentScopeYFMax/src/main/java/org/example/acl/apiClient/AgentTaskExecutorService.java`

**设计逻辑**:
- 负责Agent调用、工具挂起处理、任务恢复
- 通过AgentSessionCache保证挂起-恢复循环使用同一个Agent实例
- 实现DataModel注入机制，将容器URL传递给工具

**核心流程**:

**invokeAgent流程**:
1. 构造GuiApiClient（containerUrl由工具平台传入）
2. 创建ToolExecutionContext，注入DataModel
3. 创建ScreenshotInjectionHook + callRQHook
4. 从AgentSessionCache获取或创建Agent实例
5. 注册到容器（guiClient.registerAgent）
6. 异步执行Agent（CompletableFuture.runAsync）

**executeAgentAsync流程**:
1. 构造用户消息
2. 调用agent.call(userMsg).block()
3. 检查response.getGenerateReason() == TOOL_SUSPENDED
4. 如果挂起：等待容器回调PhoneCallbackController
5. 如果正常完成：标记任务完成、通知工具平台

**resumeTask流程**:
1. 从AgentSessionCache获取同一个Agent实例（关键！）
2. 更新截图（来自容器回调）
3. 获取toolUseId（由ScreenshotInjectionHook缓存）
4. 从callRQHook补充toolName/toolInput字段
5. 构建ToolResultBlock消息
6. 异步恢复Agent执行（subscribe）

### 2.5 callRQHook - 请求Hook
**文件**: `AgentScopeYFMax/src/main/java/org/example/acl/hook/callRQHook.java`

**设计逻辑**:
- 记录推理过程和工具调用信息
- 管理SessionContext和工具平台通信
- 将toolName/toolInput写入DTO供resumeTask使用

**核心功能**:
```java
// 推理完成：打印完整推理文本
protected void handlePostReasoning(PostReasoningEvent event)

// 工具执行前：记录工具名称和参数
protected void handlePreActing(PreActingEvent event)
```

---

## 3. Agent构建核心组件

### 3.1 AbstractAgentTemplate - Agent抽象模板
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/AbstractAgentTemplate.java`

**设计逻辑**:
- 模板方法模式，统一Agent构建流程
- 支持声明式配置和注解驱动
- 实现组件解耦和模块化组装

**子类必须实现的方法**:
```java
protected abstract String setupSysPrompt();      // 系统提示词
protected abstract Model setupCustomModel();      // 模型配置
```

**可选重写的方法**:
```java
protected Toolkit setupTools()                    // 工具箱
protected SkillBox setupSkills()                  // 技能盒
protected AutoContextMemory setupCustomMemory()   // 记忆系统
protected List<Hook> setupCustomHooks()           // 钩子
protected int maxStep()                           // 最大迭代次数
protected ExecutionConfig setupModelExecutionConfig()  // 模型执行配置
protected ExecutionConfig setupToolExecutionConfig()   // 工具执行配置
protected ToolExecutionContext setupToolExecutionContext() // 工具执行上下文
protected StateModule setupSessionContext()       // 会话上下文
```

**构建流程**:

**buildAgentWithSession流程**:
1. preInitMailbox(definition) - 初始化邮箱
2. buildAgent(definition, context, hooks, override) - 构建Agent
3. loadSessionMemory(agent, threadId) - 加载会话记忆
4. attachSessionHooks(agent, threadId, sessionManager) - 挂载Hook

**buildAgent流程**:
1. init() - 初始化
2. assembleModel(definition) - 组装模型
3. getToolkit() - 组装工具箱
4. setupSkills() - 组装技能盒
5. assemblePlan() - 组装计划本
6. assembleMemory() - 组装记忆
7. assembleLongTermMemory() - 组装长程记忆
8. assembleHooks(definition) - 组装钩子
9. config.mergeOverrides(override) - 合并外部配置
10. ReActAgentFactory.create(config).build() - 最终构建

### 3.2 AgentDefinition - Agent定义注解
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/framework/annotation/AgentDefinition.java`

**设计逻辑**:
- 声明式Agent定义，继承Spring @Component
- 支持分组管理、懒加载、场景预设
- 实现配置与代码分离

**核心属性**:
```java
@AgentDefinition(
    name = "hr-agent",                    // Agent名称
    description = "HR智能体",             // 描述
    group = "hr",                         // 分组
    scope = "prototype",                  // 作用域（prototype/singleton）
    lazy = true,                          // 懒加载
    active = true,                        // 激活状态
    modelProvider = ModelProvider.DASHSCOPE,  // 模型提供者
    modelType = "思考",                   // 模型场景类型
    hooksType = "log",                    // Hook策略类型
    maxIters = 50,                        // 最大迭代次数
    enableMemory = true,                  // 启用记忆
    enablePlan = false,                   // 启用计划
    enableLongTermMemory = false,         // 启用长程记忆
    enablePersistence = true,             // 启用持久化
    enableMail = false,                   // 启用邮件通信
    skillRepoUrl = "",                    // Git Skill仓库地址
    skillNames = {},                      // Skill过滤
    classpathResourcePath = "",           // Classpath Skill路径
    classpathSkillNames = {}              // Classpath Skill过滤
)
```

### 3.3 ReActAgentFactory - Agent工厂
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/mas/reActAgent/ReActAgentFactory.java`

**设计逻辑**:
- 基于配置对象（AgentConfigPo）构建ReActAgent
- 采用延迟构建策略，支持链式调用
- 纯粹构建，不涉及AgentPool管理

**核心API**:
```java
// 工厂入口
public static AgentBuilderWrapper create(AgentConfigPo config)

// 链式配置
AgentBuilderWrapper model(Model model)
AgentBuilderWrapper toolkit(Toolkit toolkit)
AgentBuilderWrapper memory(Memory memory)
AgentBuilderWrapper hooks(List<Hook> hooks)
AgentBuilderWrapper skill(SkillBox skillBox)
AgentBuilderWrapper maxIters(int maxIters)
AgentBuilderWrapper toolExecutionContext(ToolExecutionContext context)

// 构建
public ReActAgent build()
```

### 3.4 AgentPoolManager - Agent池管理器
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/AgentPoolManager.java`

**设计逻辑**:
- 自动发现@AgentDefinition注解类
- 管理Agent元数据和实例缓存
- 支持分组管理、懒加载、prototype/singleton模式

**核心API**:
```java
// 获取Agent（无会话）
public ReActAgent getAgent(String name)
public ReActAgent getAgent(String name, ToolExecutionContext context, 
                           List<Hook> dynamicHooks, AgentConfigPo override)

// 获取带会话的Agent
public ReActAgent getAgentWithSession(String name, String threadId)
public ReActAgent getAgentWithSession(String name, String threadId, 
                                      ToolExecutionContext context, 
                                      List<Hook> dynamicHooks, AgentConfigPo override)

// 获取Agent构建器
public ReActAgentFactory.AgentBuilderWrapper getAgentBuilder(String name, 
                                                             ToolExecutionContext context, 
                                                             List<Hook> dynamicHooks, 
                                                             String skillName)

// 分组操作
public List<ReActAgent> getAgentsByGroup(String group)
public MsgAgentPool buildGroupPool(String group)
```

**自动注册流程**:
```
@PostConstruct init():
1. scanAndRegisterAgents() - 扫描@AgentDefinition注解类
2. autoRegisterThreadAwareFactories() - 注册Thread-Aware Factory

自动注册Thread-Aware Factory:
for (AgentMetadata metadata : metadataRegistry.values()) {
    if (metadata.isEnablePersistence()) {
        // 注册Factory闭包
        customThreadSessionManager.registerThreadAwareFactory(
            agentName,
            threadId -> {
                AbstractAgentTemplate template = applicationContext.getBean(metadata.getTemplateClass());
                return template.buildAgentWithSession(threadId, metadata.getDefinition());
            }
        );
    }
}
```

### 3.5 AgentSessionCache - Agent会话缓存
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/AgentSessionCache.java`

**设计逻辑**:
- 以threadId + agentName为key缓存Agent实例
- 支持惰性过期检查和LRU淘汰
- 保证挂起-恢复循环使用同一个Agent实例（关键！）

**核心API**:
```java
// 获取或创建Agent
public ReActAgent getOrCreate(String threadId, String agentName)
public ReActAgent getOrCreate(String threadId, String agentName, AgentConfigPo override)
public ReActAgent getOrCreate(String threadId, String agentName, 
                              ToolExecutionContext context, List<Hook> hooks, AgentConfigPo override)

// 手动注册
public void put(String threadId, String agentName, ReActAgent agent)

// 获取（不自动创建）
public ReActAgent get(String threadId, String agentName)

// 移除
public ReActAgent remove(String threadId, String agentName)
public void removeByThread(String threadId)
```

**配置项**:
```yaml
agentscope:
  session-cache:
    expire-after-access-minutes: 30   # 最后访问后多久过期
    max-size: 1000                    # 最大缓存条目数
```

### 3.6 AgentConfigPo - Agent配置对象
**文件**: `AgentScopeYFMax/src/main/java/org/example/agentScope/mas/reActAgent/AgentConfigPo.java`

**设计逻辑**:
- 封装创建ReActAgent所需的所有参数
- 将配置数据与构建逻辑解耦
- 支持配置合并（mergeOverrides）

**核心属性**:
```java
// 基础元数据
private String name;
private String description;
private String sysPrompt;

// 核心组件
private Model model;
private Toolkit toolkit;
private Memory memory;
private List<Hook> hooks;
private SkillBox skillBox;

// 执行控制
private Integer maxIters;
private Boolean checkRunning;
private ExecutionConfig modelExecutionConfig;
private ExecutionConfig toolExecutionConfig;
private ToolExecutionContext toolExecutionContext;

// 高级功能
private PlanNotebook planNotebook;
private LongTermMemory longTermMemory;
private LongTermMemoryMode longTermMemoryMode;
private StructuredOutputReminder structuredOutputReminder;
```

---

## 4. 业务Agent实现示例

### 4.1 HRAgent - HR智能体
**文件**: `AgentServiceHR/src/main/java/org/example/agent/HR/HRAgent.java`

**实现模式**:
```java
@AgentDefinition(
    name = "hr-agent",
    description = "HR智能体，负责人力资源相关任务处理",
    group = "hr",
    enableMemory = true,
    maxIters = 50
)
public class HRAgent extends AbstractAgentTemplate {
    
    // 1. 系统提示词
    protected String setupSysPrompt() {
        return SYS_PROMPT;  // 定义HR智能体的角色和能力
    }
    
    // 2. 模型配置
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("视觉模型");
    }
    
    // 3. 工具配置
    protected Toolkit setupTools() {
        return components.toolkit()
            .create(ToolkitConfig.builder()
                .parallel(false)
                .executionConfig(ExecutionConfig.builder()
                    .timeout(Duration.ofSeconds(300))
                    .build())
                .build())
            .addTools(new HrTools())
            .build();
    }
    
    // 4. 结构化输出
    protected StructuredOutputReminder setupStructuredOutputReminder() {
        return StructuredOutputReminder.TOOL_CHOICE;
    }
}
```

### 4.2 MasterSPAgents - 餐饮智能体总控
**文件**: `AgentServiceCar/src/main/java/org/example/agent/Sp/agents/MasterSPAgents.java`

**实现模式**:
```java
@AgentDefinition(
    name = "MasterSPAgents",
    description = "餐饮智能体总控，负责精准识别用户意图并路由至四大核心Skill执行任务",
    hooksType = "sp",
    group = "canche",
    enablePersistence = true   // 启用自动记忆持久化
)
public class MasterSPAgents extends AbstractAgentTemplate {
    // 复杂业务逻辑，包含子Agent调用、Hook管理等
}
```

---

## 5. 关键设计模式总结

### 5.1 挂起-恢复机制
```
Agent调用工具 → ToolSuspendException → 返回TOOL_SUSPENDED
    ↓
Agent实例保留在AgentSessionCache
    ↓
GUI容器执行操作 → 回调PhoneCallbackController
    ↓
从缓存取出同一个Agent实例 → 注入工具结果 → 继续执行
```

### 5.2 DataModel注入机制
```
invokeAgent时：
containerUrl → DataModel → ToolExecutionContext → Agent

Agent调用工具时：
框架自动将DataModel注入到工具方法参数
工具通过model.getUrl()获取容器地址
```

### 5.3 组件门面模式
```java
// AgentComponentFacade聚合所有Agent组件
components.toolkit()      // 工具箱工厂
components.memory()       // 记忆工厂
components.model()        // 模型工厂门面
components.skillBox()     // Skill盒工厂
components.hooks()        // Hook工厂
components.postgresSession()  // 会话持久化
components.mailboxCenter()    // 邮箱中心
components.agentPoolManager() // Agent池管理器
```

### 5.4 模板方法模式
```
AbstractAgentTemplate定义构建骨架
    ↓
子类实现具体业务逻辑（setupSysPrompt、setupTools等）
    ↓
AgentPoolManager自动发现和管理
    ↓
一行注解配置即可接入
```

---

## 6. 核心文件索引

### 通信层
| 文件 | 路径 | 说明 |
|------|------|------|
| AgentTaskApiClient.java | AgentScopeYFMax/src/main/java/org/example/acl/apiClient/ | 工具平台API客户端 |
| GuiApiClient.java | AgentScopeYFMax/src/main/java/org/example/acl/apiClient/ | GUI容器通信客户端 |
| AgentTaskExecutorService.java | AgentScopeYFMax/src/main/java/org/example/acl/apiClient/ | 任务执行服务 |
| PhoneCallbackController.java | AgentScopeYFMax/src/main/java/org/example/acl/callBack/ | 容器回调控制器 |
| callRQHook.java | AgentScopeYFMax/src/main/java/org/example/acl/hook/ | 请求Hook |

### Agent构建
| 文件 | 路径 | 说明 |
|------|------|------|
| AbstractAgentTemplate.java | AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/ | Agent抽象模板 |
| AgentDefinition.java | AgentScopeYFMax/src/main/java/org/example/agentScope/framework/annotation/ | Agent定义注解 |
| ReActAgentFactory.java | AgentScopeYFMax/src/main/java/org/example/agentScope/mas/reActAgent/ | Agent工厂 |
| AgentPoolManager.java | AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/ | Agent池管理器 |
| AgentSessionCache.java | AgentScopeYFMax/src/main/java/org/example/agentScope/framework/core/ | Agent会话缓存 |
| AgentConfigPo.java | AgentScopeYFMax/src/main/java/org/example/agentScope/mas/reActAgent/ | Agent配置对象 |

### 业务示例
| 文件 | 路径 | 说明 |
|------|------|------|
| HRAgent.java | AgentServiceHR/src/main/java/org/example/agent/HR/ | HR智能体 |
| MasterSPAgents.java | AgentServiceCar/src/main/java/org/example/agent/Sp/agents/ | 餐饮智能体总控 |

---

## 7. 项目技术栈

- **Java版本**: Java 21
- **框架**: Spring Boot 3.4.9
- **AI框架**: AgentScope-Java, Spring AI Alibaba GraphAI
- **数据库**: PostgreSQL (MyBatis-Flex)
- **缓存**: JetCache
- **向量数据库**: Milvus
- **HTTP客户端**: OkHttp, RestTemplate
- **监控**: Langfuse SDK
