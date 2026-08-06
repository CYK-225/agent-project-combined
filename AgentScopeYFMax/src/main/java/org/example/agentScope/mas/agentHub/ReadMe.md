
---

# AgentHub 工具执行框架技术文档

## 1. 简介

**AgentHub** 是一个基于 Spring 框架的轻量级工具执行门面（Facade）。它旨在为多智能体系统（如 AgentScope）提供统一、线程安全的工具调用能力的集成方案。该框架利用 Java 21+ 的虚拟线程（Virtual Threads）特性，支持高并发的工具链式调用，并通过 Spring 容器实现工具的自动发现与注册。

## 2. 核心架构

系统主要由以下四个核心组件构成：

### 2.1 AgentHub (门面)

作为系统的静态入口，`AgentHub` 封装了复杂的调度逻辑。

* **职责**：提供静态工厂方法 `from()` 构建执行器，管理 `ToolDispatcher` 的静态注入。
* **特性**：
* **线程安全**：每次调用都会创建一个新的 `AgentRunner` 实例，确保请求间的状态隔离。
* **Spring 集成**：虽然提供静态方法，但其底层依赖 Spring 容器注入的调度器，通过 `@PostConstruct` 或 Setter 注入建立连接。



### 2.2 ToolDispatcher (调度中心)

这是框架的“心脏”，负责工具的注册与分发。

* **职责**：维护工具注册表，根据名称分发请求。
* **机制**：
* **自动发现**：利用 Spring 的依赖注入特性，自动收集所有继承自 `BaseTool` 的 Bean。
* **容错分发**：支持两级查找策略——优先匹配显式 `toolName`，若失败则尝试从参数 `input` 中提取 `name` 字段进行回退查找。



### 2.3 BaseTool (工具基类)

定义了所有业务工具必须遵守的契约。

* **职责**：规范工具的命名与执行接口。
* **扩展性**：开发者只需继承此类并添加 `@Component` 注解，即可完成工具的接入。

### 2.4 AgentRunner (执行器)

`AgentHub` 的静态内部类，负责实际的任务执行。

* **并发模型**：采用 `Executors.newVirtualThreadPerTaskExecutor()`，为每个工具调用分配一个虚拟线程，极大降低了 I/O 密集型任务的资源消耗。
* **结果聚合**：利用 `CompletableFuture` 实现多任务并行处理与结果聚合。

---

## 3. 使用规范

### 3.1 环境要求

* **JDK 版本**：必须使用 **JDK 21** 或更高版本（依赖虚拟线程特性）。
* **框架依赖**：必须运行在 Spring Boot 环境中，确保 Spring 容器已完全启动。

### 3.2 开发规范

1. **工具定义**：所有自定义工具必须继承 `BaseTool` 类。
2. **容器托管**：工具类必须使用 `@Component` 或 `@Service` 注解，否则无法被 `ToolDispatcher` 扫描到。
3. **命名唯一性**：`getToolName()` 返回的字符串必须在全局范围内唯一，重复的名称会导致容器启动失败（抛出 `RuntimeException`）。
4. **异常处理**：工具内部应自行处理业务异常，框架层仅做简单的异常消息捕获（返回 `❌ 工具 [...] 失败`）。

---

## 4. API 参考

### 4.1 AgentHub

| 方法签名 | 说明 |
| --- | --- |
| `static AgentRunner from(List<ToolUseBlock> blocks)` | **入口方法**。根据传入的工具调用块列表，创建一个新的执行器实例。如果 Spring 容器未初始化，将抛出异常。 |

### 4.2 AgentRunner

| 方法签名 | 说明 |
| --- | --- |
| `Msg execute()` | **执行方法**。并行执行所有工具调用，等待所有任务完成后，将结果聚合为 `Msg` 对象返回。返回的消息角色为 `MsgRole.TOOL`。 |

### 4.3 BaseTool

| 方法签名 | 说明 |
| --- | --- |
| `abstract String getToolName()` | 返回工具的唯一标识符（注册名）。 |
| `abstract String execute(Map<String, Object> input)` | 执行工具的具体业务逻辑。入参为 KV 格式的参数映射，返回值为字符串格式的执行结果。 |

---

## 5. 接入示例

### 5.1 定义工具

编写一个具体的工具类，继承 `BaseTool` 并注册到 Spring 容器。

```java
@Component
public class WeatherTool extends BaseTool {
    @Override
    public String getToolName() {
        return "weather_query";
    }

    @Override
    public String execute(Map<String, Object> input) {
        // 实际业务逻辑
        return "当前天气查询结果: 晴转多云";
    }
}

```

### 5.2 调用工具

在业务流程中，通过 `AgentHub` 触发工具执行。通常配合 Agent 模型（如 AgentScope）的输出使用。

```java
// 假设 response1 是 Agent 模型返回的消息，其中包含了工具调用请求
if (response1.getGenerateReason() == GenerateReason.TOOL_SUSPENDED) {
    
    // 1. 提取工具调用块
    List<ToolUseBlock> blocks = response1.getContentBlocks(ToolUseBlock.class);
    
    // 2. 构建并执行
    Msg toolResponse = AgentHub.from(blocks).execute();
    
    // 3. 获取聚合结果
    System.out.println(toolResponse.getContent()); 
}

```

## 6. 注意事项

* **初始化时机**：请勿在 Spring 容器启动完成前调用 `AgentHub.from()`，否则会抛出 `IllegalStateException`。
* **参数传递**：`ToolDispatcher` 具有防御性编程设计，若在主名称查找失败时尝试使用参数中的 `name` 字段，会校验该字段类型是否为 `String`。
* **并发限制**：虽然使用了虚拟线程，但请注意底层资源（如数据库连接池、外部 API 速率限制）的并发瓶颈。