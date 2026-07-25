# AgentScopeGraphYFMax

> 基于 Spring AI Alibaba Graph 的注解驱动工作流编排框架

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2.2-orange.svg)](https://github.com/spring-ai-alibaba/community)
[![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](LICENSE)

## 简介

AgentScopeGraphYFMax 是一个基于 Spring AI Alibaba Graph 构建的工作流编排框架，提供：

- **注解驱动定义** — `@GraphDefinition` 继承 `@Component`，Spring 自动扫描注册
- **组件池化管理** — `@NodeAction` / `@EdgeCondition` 注册独立节点/边类到 `NodeActionPool` / `EdgeConditionPool`，按名称引用，可复用、可测试
- **模板方法模式** — `AbstractGraphTemplate` 屏蔽底层 StateGraph / CompileConfig 组装复杂度
- **充血模型构建器** — `GraphBuilder` 流式 API，支持 5 种拓扑写法，池感知操作
- **多种工作流模式** — 顺序、条件分支、并行扇出、子图嵌套、多智能体协作、人类反馈
- **检查点持久化** — 内存 / PostgreSQL（MyBatis-Flex）可切换
- **时光旅行** — 状态历史查询、分支恢复、修改历史并重执行
- **轻量动作基类** — `SimpleNodeAction` / `SimpleEdgeAction` 抽象类，无需处理 CompletableFuture

## 环境要求

- Java 21
- Maven 3.8+
- Spring Boot 3.4.x

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>AgentScopeGraphYFMax</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 创建第一个工作流

```java
@GraphDefinition(
    name = "simple-qa",
    group = "examples",
    description = "简单的两节点流水线"
)
public class SimpleQaGraph extends AbstractGraphTemplate {

    public SimpleQaGraph(GraphComponentFacade components) {
        super(components);
    }

    @Override
    protected OverAllState initialState() {
        return new OverAllState();
    }

    @Override
    protected void buildGraph(GraphBuilder builder) throws GraphStateException {
        builder.addNode("analyze", state -> {
                    String input = (String) state.value("input").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("analysis", "已分析: " + input);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge("answer")
                .addNode("answer", state -> {
                    String analysis = (String) state.value("analysis").orElse("");
                    Map<String, Object> update = new HashMap<>();
                    update.put("result", "回答: " + analysis);
                    return CompletableFuture.completedFuture(update);
                })
                .addEdge(StateGraph.END);
    }
}
```

### 使用组件池引用独立节点/边类

```java
// 定义独立节点类
@NodeAction(value = "validate", description = "输入验证节点")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}

// 定义独立边类
@EdgeCondition(value = "type-router", description = "输入类型路由")
public class TypeRouterEdge extends SimpleEdgeAction {
    @Override
    protected String execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        return input.contains("?") ? "question-handler" : "default-handler";
    }
}

// 在 buildGraph() 中通过池按名称引用
@Override
protected void buildGraph(GraphBuilder builder) throws GraphStateException {
    builder.addNode("validate", components.nodeActions())       // 从 NodeActionPool 获取
            .addEdge("dispatch")
            .addNode("dispatch", dispatchAction)
            .addConditionalEdges("type-router", components.edgeConditions())  // 从 EdgeConditionPool 获取
            .route("question-handler", "question-handler")
            .route("default-handler", "default-handler")
            .done()
            .addNode("question-handler", components.nodeActions())
            .addEdge(StateGraph.END)
            .addNode("default-handler", components.nodeActions())
            .addEdge(StateGraph.END);
}
```

### 执行工作流

```java
@Autowired
private GraphPoolManager poolManager;

// 方式一：通过 GraphPoolManager 统一入口（推荐）
OverAllState state = new OverAllState();
state = state.input(Map.of("input", "你好世界"));
OverAllState result = poolManager.invokeGraph("simple-qa", state, "thread-1");

// 方式二：手动获取图后调用 GraphEngine
@Autowired
private GraphEngine engine;

CompiledGraph graph = poolManager.getGraph("simple-qa");
OverAllState result = engine.invoke(graph, state, "thread-1", null);
```

## 核心概念

### 注解

| 注解 | 目标 | 继承 | 说明 |
|------|------|------|------|
| `@GraphDefinition` | 类 | `@Component` | 定义图名称、分组、检查点策略、中断节点等 |
| `@NodeAction` | 类 | `@Component` | 声明独立节点动作类，注册到 `NodeActionPool` |
| `@EdgeCondition` | 类 | `@Component` | 声明独立边条件类，注册到 `EdgeConditionPool` |

### 核心组件

| 组件 | 说明 |
|------|------|
| `AbstractGraphTemplate` | 模板方法基类，子类实现 `buildGraph()` + `initialState()` |
| `GraphBuilder` | 充血模型构建器，提供 `addNode` / `addEdge` / `addConditionalEdges` / `addSubgraphNode` / `addParallelBranches`，支持池感知操作 |
| `GraphComponentFacade` | 聚合 6 个组件：`stateKey()` / `checkpoint()` / `nodeActions()` / `edgeConditions()` / `pool()` / `engine()` |
| `GraphPoolManager` | 自动发现 `@GraphDefinition`，懒加载编译，统一 invoke/stream API |
| `NodeActionPool` | 自动发现 `@NodeAction`，按名称获取节点动作实例（prototype/singleton） |
| `EdgeConditionPool` | 自动发现 `@EdgeCondition`，按名称获取边条件实例（prototype/singleton） |
| `GraphEngine` | 底层执行引擎，支持 invoke / stream / 时光旅行 / 状态更新 |

### 动作基类

| 类 | 说明 |
|------|------|
| `SimpleNodeAction` | 节点动作抽象类，实现 `execute(OverAllState) -> Map<String, Object>` |
| `SimpleEdgeAction` | 边动作抽象类，实现 `execute(OverAllState) -> String` |

### 工厂

| 工厂 | 说明 |
|------|------|
| `StateKeyFactory` | 创建 `KeyStrategyFactory`（默认 ReplaceStrategy，可自定义） |
| `CheckpointFactory` | 按策略名创建 `BaseCheckpointSaver`（memory / postgres） |

### 工作流模式

| 模式 | Pattern 实现 | 说明 |
|------|-------------|------|
| 顺序 | `SequentialGraphPattern` | `node1 -> node2 -> ... -> END` |
| 条件分支 | `ConditionalGraphPattern` | `source -> [condition] -> branchA \| branchB -> END` |
| 并行扇出 | `FanOutGraphPattern` | `fanout -> [branches] -> merge -> END` |
| 多智能体 | `MultiAgentGraphPattern` | `agent1 -> agent2 -> ... -> judge -> [continue \| end]` |

## 配置

### application.yml

```yaml
spring:
  threads:
    virtual:
      enabled: true    # 必须启用，用于并行执行

graph:
  workflow:
    engine:
      default-checkpoint-strategy: memory   # memory / postgres
      parallel-executor-enabled: true       # 使用虚拟线程，无需配置线程池大小
      default-recursion-limit: 25
    scan-packages: org.example               # 可选，@GraphDefinition / @NodeAction / @EdgeCondition 扫描包
```

> **虚拟线程：** 并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），无需配置线程池大小。需在 `application.yml` 中启用 `spring.threads.virtual.enabled: true`。

### @GraphDefinition 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` / `value` | String | 类简单名 | 图唯一名称 |
| `description` | String | "" | 描述 |
| `group` | String | "default" | 分组（用于 `getGraphsByGroup()`） |
| `lazy` | boolean | true | 懒加载（首次调用时编译） |
| `active` | boolean | true | 启用/禁用 |
| `scope` | String | "prototype" | "prototype"（每次新建）/ "singleton"（缓存） |
| `priority` | int | 0 | 组内排序（越小优先级越高） |
| `checkpointStrategy` | String | "memory" | 检查点策略 |
| `recursionLimit` | int | 25 | 递归限制 |
| `interruptBefore` | String[] | {} | 中断节点（人类反馈） |
| `interruptAfter` | String[] | {} | 后置中断节点 |
| `enableStreaming` | boolean | false | 流式执行 |
| `parallelism` | int | 0 | 并行线程数（0=系统默认） |

### @NodeAction 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | 类简单名 | 节点唯一名称 |
| `description` | String | "" | 描述 |
| `scope` | String | "prototype" | "prototype"（每次新建）/ "singleton"（缓存） |

### @EdgeCondition 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | 类简单名 | 边唯一名称 |
| `description` | String | "" | 描述 |
| `scope` | String | "prototype" | "prototype"（每次新建）/ "singleton"（缓存） |

## 示例工作流

| 工作流 | 文件 | 拓扑 |
|--------|------|------|
| SequentialQaGraph | `examples/SequentialQaGraph.java` | `analyze -> answer -> END` |
| ParallelAnalysisGraph | `examples/ParallelAnalysisGraph.java` | `fanout -> [financial, technical, market] -> synthesizer -> END` |
| HumanApprovalGraph | `examples/HumanApprovalGraph.java` | `draft -> [interrupt: humanReview] -> finalize \| re-draft -> END` |
| SubgraphWorkflowGraph | `examples/SubgraphWorkflowGraph.java` | `preprocess -> [subgraph: search -> synthesize] -> postprocess -> END` |
| AnnotatedNodeExample | `examples/AnnotatedNodeExample.java` | `validate -> process -> output -> END`（通过 NodeActionPool 引用） |
| AnnotatedEdgeExample | `examples/AnnotatedEdgeExample.java` | `dispatcher -> [type-router] -> handler -> result -> END`（通过 EdgeConditionPool 引用） |

## 项目结构

```
AgentScopeGraphYFMax/
├── src/main/java/org/example/
│   ├── dal/checkpoint/                   # MyBatis-Flex 检查点持久化
│   │   ├── entity/                       # GraphCheckpointEntity, GraphCheckpointBlobEntity
│   │   └── mapper/                       # GraphCheckpointMapper, GraphCheckpointBlobMapper
│   └── graph/
│       ├── createGraph/
│       │   ├── builder/                  # GraphBuilder, ConditionalEdgeBuilder, RunnableConfigBuilder
│       │   ├── edge/                     # SimpleEdgeAction
│       │   ├── engine/                   # GraphEngine, GraphEngineProperties
│       │   └── node/                     # SimpleNodeAction, StreamingNodeAction
│       ├── workflow/
│       │   ├── annotation/              # @GraphDefinition, @NodeAction, @EdgeCondition
│       │   ├── checkpoint/              # CheckpointFactory, MyBatisFlexCheckpointSaver
│       │   ├── core/                    # AbstractGraphTemplate, GraphComponentFacade, GraphPoolManager, NodeActionPool, EdgeConditionPool
│       │   ├── pattern/                 # GraphPattern + 4 种模式实现
│       │   └── state/                   # StateKeyFactory
│       └── examples/                    # 14 个示例（4 图定义 + 6 独立节点 + 2 独立边 + 2 池引用示例）
├── src/main/resources/
│   └── db/checkpoint-postgresql.sql     # PostgreSQL DDL
├── docs/                                # 文档
│   ├── quick-start.md / usage-guide.md / architecture.md  # 入门文档
│   ├── api-reference.md / best-practices.md / troubleshooting.md  # 运维文档
│   ├── createGraph/builder/             # GraphBuilder API
│   ├── createGraph/node/                # 节点动作 API
│   ├── createGraph/edge/                # 边动作 API
│   ├── createGraph/engine/              # GraphEngine API
│   ├── workflow/annotation/             # 注解 API
│   ├── workflow/core/                   # 核心组件 API
│   ├── workflow/checkpoint/             # 检查点 API
│   ├── workflow/pattern/                # 工作流模式 API
│   ├── workflow/state/                  # 状态键策略 API
│   └── examples/                        # 示例文档
├── pom.xml
└── README.md
```

## 文档

### 入门

| 文档 | 说明 |
|------|------|
| [快速开始](docs/quick-start.md) | 5 分钟上手教程 |
| [使用文档](docs/usage-guide.md) | 11 个功能领域完整指南 |
| [架构设计](docs/architecture.md) | 分层架构 |
| [示例工作流](docs/examples/examples.md) | 14 个示例文件说明 |

### 模块 API

| 文档 | 说明 |
|------|------|
| [注解 API](docs/workflow/annotation/annotation-api.md) | @GraphDefinition / @NodeAction / @EdgeCondition |
| [核心组件 API](docs/workflow/core/core-api.md) | AbstractGraphTemplate / GraphComponentFacade / Pool |
| [图构建器 API](docs/createGraph/builder/builder-api.md) | GraphBuilder / ConditionalEdgeBuilder |
| [节点动作 API](docs/createGraph/node/node-api.md) | SimpleNodeAction / NodeActionPool |
| [边动作 API](docs/createGraph/edge/edge-api.md) | SimpleEdgeAction / EdgeConditionPool |
| [图执行引擎 API](docs/createGraph/engine/engine-api.md) | GraphEngine / 时光旅行 |
| [检查点 API](docs/workflow/checkpoint/checkpoint-api.md) | CheckpointFactory / MyBatisFlexCheckpointSaver |
| [状态键策略 API](docs/workflow/state/state-api.md) | StateKeyFactory |
| [工作流模式 API](docs/workflow/pattern/pattern-api.md) | 4 种拓扑模式 |

### 运维

| 文档 | 说明 |
|------|------|
| [API 总览](docs/api-reference.md) | 全部 API 签名 |
| [最佳实践](docs/best-practices.md) | 生产环境建议 |
| [故障排除](docs/troubleshooting.md) | 常见问题 |

## 许可证

本项目采用 Apache 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

---

**基于 [Spring AI Alibaba Graph](https://github.com/spring-ai-alibaba/community) 构建**
