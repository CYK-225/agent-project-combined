# 核心组件 API

## 概述

核心抽象层，提供模板方法、组件门面、图池和动作池。

## 类

```
graph.workflow.core
├── AbstractGraphTemplate   ← 模板方法基类
├── GraphComponentFacade    ← 统一组件入口
├── GraphPoolManager        ← 图池（@GraphDefinition 扫描）
├── NodeActionPool          ← 节点池（@NodeAction 扫描）
└── EdgeConditionPool       ← 边池（@EdgeCondition 扫描）
```

## AbstractGraphTemplate

所有图工作流必须继承的模板方法基类。

### 必须实现

```java
protected abstract void buildGraph(GraphBuilder builder) throws GraphStateException;
protected abstract OverAllState initialState();
```

### 可选重写

```java
protected KeyStrategyFactory setupStateKeyFactory();
protected BaseCheckpointSaver setupCheckpointSaver(GraphDefinition def);
protected CompileConfig.Builder setupCompileConfig(GraphDefinition def);
protected void afterGraphCompiled(CompiledGraph graph);
protected void init();
```

### 模板方法（final）

```java
public final CompiledGraph compileGraph(GraphDefinition definition) throws GraphStateException;
public final CompiledGraph compileGraph() throws GraphStateException;
public final StateGraph buildStateGraph(GraphDefinition definition) throws GraphStateException;
public final StateGraph buildStateGraph() throws GraphStateException;
public GraphDefinition getDefinition();
```

### compileGraph() 编排流程

```
1. init()
2. setupStateKeyFactory()          → KeyStrategyFactory
3. setupCheckpointSaver(def)       → BaseCheckpointSaver
4. new StateGraph(name, keyFactory)
5. buildGraph(builder)             ← 子类定义拓扑
6. setupCompileConfig(def)         → CompileConfig
7. 注入 SaverConfig
8. stateGraph.compile(config)      → CompiledGraph
9. afterGraphCompiled(graph)
```

### buildStateGraph() — 子图场景

返回未编译的 `StateGraph`，由父图统一编译。

## GraphComponentFacade

6 个组件的统一入口，通过构造器注入到 `AbstractGraphTemplate` 子类。

```java
components.stateKey()          // StateKeyFactory
components.checkpoint()        // CheckpointFactory
components.nodeActions()       // NodeActionPool
components.edgeConditions()    // EdgeConditionPool
components.pool()              // GraphPoolManager
components.engine()            // GraphEngine
```

## GraphPoolManager

自动发现 `@GraphDefinition` 类，懒加载编译。

### 图获取

```java
CompiledGraph getGraph(String name);
List<CompiledGraph> getGraphsByGroup(String group);
Optional<GraphMetadata> getMetadata(String name);
```

### 高层执行

```java
OverAllState invokeGraph(String name, OverAllState state, String threadId);
OverAllState invokeGraph(String name, OverAllState state, String threadId, String checkPointId);
Flux<?> streamGraph(String name, OverAllState state, String threadId);
```

### 缓存管理

```java
void evictCache(String name);
void evictAllCache();
```

## NodeActionPool / EdgeConditionPool

启动时扫描 `@NodeAction` / `@EdgeCondition` 注解类。

```java
AsyncNodeAction get(String name);   // 获取实例（prototype/singleton）
boolean exists(String name);         // 检查是否注册
String getDescription(String name);  // 获取描述
Map<String, String> listAll();       // 列出全部（name → description）
Set<String> getRegisteredNames();    // 获取名称集合
```
