这份文档旨在为开发者提供 `ToolkitFactory` 组件的技术说明。该组件是基于 AgentScope 框架构建的，用于在 Spring Boot 环境下标准化、流程化地生产与管理智能体工具箱（Toolkit）。

---

# ToolkitFactory 技术参考文档

## 1. 概述

`ToolkitFactory` 是一个服务级组件（Service），封装了 AgentScope `Toolkit` 的实例化逻辑。它引入了 **链式调用（Fluent API）** 模式，旨在解决复杂场景下工具组（Tool Group）配置繁琐、MCP（Model Context Protocol）工具集成不便以及配置冗余的问题。

通过 `ToolkitFactory`，开发者可以快速构建包含本地方法、Schema 定义及远程 MCP 服务的混合工具集，并实现工具的逻辑分组与生命周期管理。

---

## 2. 核心特性

* **配置驱动**：支持通过 `application.yml` 注入全局默认行为（超时、并行执行、元工具开关等）。
* **链式构建器**：内置 `ToolkitChain` 类，支持语义化的工具注册流程。
* **多源集成**：统一了`本地工具`、`ToolSchema` 以及 `McpClientWrapper` 的注册入口。
* **逻辑分组**：原生支持工具分组（Tool Grouping），便于智能体在不同任务上下文切换工具权限。

---

## 3. 配置指南

在 `application.yml` 中，可以定义以下属性来控制工厂的默认行为：

```yaml
agentscope:
  toolkit:
    parallel: true             # 是否允许并行执行工具
    allow-tool-deletion: true  # 是否允许动态移除工具
    timeout-seconds: 300       # 工具执行的超时时间（秒）
    enable-meta-tool: false    # 是否默认注册元工具（MetaTool）

```

---





## 4. 使用规范

### 4.1 基础创建（默认配置）

最简化的创建方式，适用于大多数标准 Agent 场景。

```java
Toolkit toolkit = toolkitFactory.create()
    .addTools(new WeatherService(), new CalculateService())
    .build();

```

### 4.2 进阶组管理

当工具数量较多或需要按权限隔离时，建议使用工具组。

```java
Toolkit toolkit = toolkitFactory.create()
    .createToolGroup("admin", "管理员专用工具", true)
    .addTools("admin", new SystemControlTool())
    .addTools("default", new CommonSearchTool())
    .build();

```

### 4.3 MCP 服务集成

集成远程 MCP 工具时，可以直接传递包装器：

```java
Toolkit toolkit = toolkitFactory.create()
    .addMCPTools(mcpClientWrapper)
    .build();

```

### 4.4 动态修改现有实例

如果需要对已存在的 `Toolkit` 实例进行增量操作，可使用 `modify` 方法：

```java
    toolkitFactory.modify(existingToolkit)
        .removeTools("oldToolName")
        .addSchemaTools(newSchemas)
        .build();

```

---

## 5. API 详细说明

### ToolkitFactory (Factory)

| 方法名 | 说明 |
| --- | --- |
| `create()` | 使用 Spring 配置文件中的默认参数初始化 `ToolkitChain`。 |
| `create(ToolkitConfig)` | 使用自定义配置对象初始化，但保留默认元工具开关设置。 |
| `modify(Toolkit)` | 包装现有的 Toolkit 实例进入链式操作模式。 |

### ToolkitChain (Builder)

| 方法名 | 说明 |
| --- | --- |
| `addTools(Object...)` | 将 POJO 类注册为工具，默认存入 "default" 组。 |
| `addMCPTools(McpClientWrapper...)` | 注册 MCP 协议客户端工具。 |
| `createToolGroup(...)` | 定义一个新的工具逻辑组及其状态。 |
| `registerAll(...)` | 批量操作接口，一次性完成分组创建、本地工具注册与 MCP 工具挂载。 |
| `removeTools(String...)` | 根据工具名称执行物理移除。 |
| `build()` | **终结操作**：返回构建完成的 `Toolkit` 对象。 |

---

## 6. 注意事项

1. **线程安全**：`ToolkitFactory` 是单例 Service，但 `ToolkitChain` 是非线程安全的内部类，建议在方法内部作用域创建并消耗，不要跨线程共享 Chain 实例。
2. **默认组依赖**：`initChain` 方法会自动创建名为 `default` 的工具组。若未指定组名，所有工具将归属于此组。
3. **超时控制**：默认超时受 `executionConfig` 控制。对于耗时较长的 IO 任务（如大数据分析），建议通过 `create(customConfig)` 覆盖默认值。

---
