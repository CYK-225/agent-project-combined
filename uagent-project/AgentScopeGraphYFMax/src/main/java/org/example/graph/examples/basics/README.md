# basics — 图构建入门

本包介绍图工作流的核心概念和最小可用 API。
如果你是第一次接触这个框架，从这里开始。

---

## 1. 图是什么

图（Graph）是一个**有向拓扑结构**，由三类元素组成：

| 元素 | 说明 | API 对应 |
|------|------|----------|
| 节点（Node） | 执行业务逻辑的最小单元 | `addNode(name, action)` |
| 边（Edge） | 定义节点之间的执行顺序 | `addEdge(source, target)` |
| 状态（State） | 贯穿整个图的全局键值对 | `OverAllState` |

执行模型：从 `START` 开始 → 沿边依次执行节点 → 到达 `END` 结束。
每个节点从 `OverAllState` 读取输入，返回 `Map<String, Object>` 更新状态。

```
START → [节点A] → [节点B] → [节点C] → END
         ↓          ↓          ↓
      更新状态    读取+更新   读取+输出
```

---

## 2. OverAllState — 全局状态

`OverAllState` 是一个线程安全的键值容器，所有节点共享。

```java
// 读取
String input = (String) state.value("input").orElse("");

// 更新（节点返回值）
return Map.of("result", "处理完成", "count", 42);
```

**状态更新策略**由 `KeyStrategyFactory` 控制（详见 [config 包](../config/README.md)）：
- 默认 `ReplaceStrategy`：新值覆盖旧值
- 可选 `AppendStrategy`：值追加到列表（如消息历史）

---

## 3. GraphBuilder 核心 API

`GraphBuilder` 是图构建器，所有 `add*` 方法返回 `this`，支持链式调用。

### 3.1 添加节点

```java
builder.addNode("analyze", state -> {
    String input = (String) state.value("input").orElse("");
    return CompletableFuture.completedFuture(
        Map.of("analysis", "分析结果: " + input)
    );
});
```

第一个参数是节点名称（字符串），第二个是 `AsyncNodeAction`。

### 3.2 添加边

```java
// 方式一：隐式源节点 — 使用上一次 addNode 的节点名
builder.addNode("a", action)
       .addEdge("b")              // a → b

// 方式二：显式指定源和目标
builder.addEdge("a", "b");        // a → b
builder.addEdge("c", StateGraph.END);  // c → END
```

**隐式源节点规则**：单参数 `addEdge(target)` 自动使用上一次 `addNode` 的节点名作为源。
适合顺序链路，减少重复代码。

### 3.3 完整示例

```java
builder.addNode("analyze", actionA)
       .addEdge("answer")              // analyze → answer（隐式）
       .addNode("answer", actionB)
       .addEdge(StateGraph.END);       // answer → END
```

---

## 4. SimpleNodeAction — 节点基类

直接写 `AsyncNodeAction` lambda 需要处理 `CompletableFuture`。
`SimpleNodeAction` 封装了样板代码，子类只需实现 `execute()`：

```java
@NodeAction("validate")
public class ValidateNode extends SimpleNodeAction {
    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String input = (String) state.value("input").orElse("");
        if (input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        return Map.of("validated", true, "cleanInput", input.trim());
    }
}
```

> `@NodeAction` 注解将类注册到全局节点池。
> 详见 [pool 包](../pool/README.md)。

---

## 5. 本包文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `SequentialQaGraph` | 图定义 | 最简单的顺序图：analyze → answer → END |
| `ValidateNode` | @NodeAction("validate") | 输入验证 |
| `ProcessNode` | @NodeAction("process") | 数据处理 |
| `OutputNode` | @NodeAction("output") | 结果输出 |

---

## 下一步

- 了解如何通过池按名称引用节点 → [pool 包](../pool/README.md)
- 了解条件分支和人机交互 → [conditional 包](../conditional/README.md)
- 了解并行扇出 → [parallel 包](../parallel/README.md)
