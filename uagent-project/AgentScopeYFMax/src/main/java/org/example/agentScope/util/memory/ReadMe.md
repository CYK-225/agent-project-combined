

# Memory 模块技术文档

## 1. 模块概述

`Memory` 模块主要负责 AgentScope 框架中记忆组件的配置与构建。该模块基于 Spring 框架开发，提供了对 **AutoContextMemory**（自动上下文管理）和 **ReMeLongTermMemory**（长时记忆）的封装支持。

**核心特性：**

* **配置解耦**：通过 Spring `@Value` 注解将硬编码配置抽离至配置文件（application.properties/yaml）。
* **构建器模式**：提供流式 Builder API，支持在默认配置基础上进行动态覆盖。
* **自动装配**：利用 Spring 容器自动管理配置依赖，简化组件初始化流程。

---

## 2. 核心组件架构

### 2.1 自动上下文内存 (`AutoContextMemory`)

该部分由配置类、工厂类和内部构建器组成，旨在灵活创建具有上下文压缩、Token 限制管理能力的内存对象。

* **`AutoContextDefaultConfig`**:
* **职责**: 作为配置数据对象（DTO），从系统属性中加载以 `AutoContextConfig` 为前缀的全局默认配置。
* **关键属性**: `msgThreshold` (消息阈值), `maxToken` (最大 Token), `tokenRatio` (保留比例) 等。


* **`AutoContextMemoryFactory`**:
* **职责**: 内存对象的生产工厂。它注入了默认配置，并提供 `builder()` 方法供外部使用。


* **`MemoryBuilder` (内部类)**:
* **职责**: 提供链式调用的 API。初始化时会自动填充 `AutoContextDefaultConfig` 中的默认值，允许调用者仅修改特定参数，最后调用 `build()` 生成实例。



### 2.2 ReMe 长时记忆 (`ReMeLongTermMemory`)

* **`ReMeLongTermMemoryBuilder`**:
* **职责**: 封装了 `ReMeLongTermMemory` 的构建过程。
* **配置**: 自动注入 `reme.base-url`，调用者只需提供 `userId` 即可获取实例。



---

## 3. 开发与使用规范

### 3.1 配置文件示例

在使用此模块前，需在 `application.properties` 或 `application.yml` 中配置以下参数：

```properties
# AutoContext 默认配置
AutoContextConfig.msgThreshold=10
AutoContextConfig.maxToken=4096
AutoContextConfig.tokenRatio=0.75
AutoContextConfig.lastKeep=2
AutoContextConfig.largePayloadThreshold=1000
AutoContextConfig.offloadSinglePreview=1
AutoContextConfig.minConsecutiveToolMessages=1
AutoContextConfig.currentRoundCompressionRatio=0.5

# ReMe 服务地址
reme.base-url=http://localhost:8080

```

### 3.2 示例代码

以下展示了如何注入工厂并构建不同类型的内存实例。

```java
import org.example.masfanplus.agentScope.util.memory.AutoContextMemoryFactory;
import org.example.masfanplus.agentScope.util.memory.ReMeLongTermMemoryBuilder;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.reme.ReMeLongTermMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentMemoryService {

    @Autowired
    private AutoContextMemoryFactory autoContextFactory;

    @Autowired
    private ReMeLongTermMemoryBuilder reMeBuilder;

    public void initAgentMemory(String userId) {
        // 1. 创建使用全局默认配置的 AutoContextMemory
        AutoContextMemory defaultMem = autoContextFactory.builder()
                .build();

        // 2. 创建自定义配置的 AutoContextMemory (仅覆盖 maxToken 和 msgThreshold)
        AutoContextMemory customMem = autoContextFactory.builder()
                .maxToken(8192)
                .msgThreshold(20)
                .build();

        // 3. 创建绑定到特定用户的长时记忆
        ReMeLongTermMemory longTermMem = reMeBuilder.build(userId);

        // 后续将 memory 对象注入 Agent...
    }
}

```

---

## 4. API 参考

以下表格列出了核心工厂及构建器的方法说明。

### 4.1 AutoContextMemoryFactory

组件路径: `org.example.masfanplus.Memory.AutoContextMemoryFactory`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `builder()` | `MemoryBuilder` | 获取一个预填充了默认配置的内存构建器。 |

### 4.2 MemoryBuilder

组件路径: `org.example.masfanplus.Memory.AutoContextMemoryFactory.MemoryBuilder`
*(注：所有 Setter 方法均返回 `this` 以支持链式调用)*

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `msgThreshold(int val)` | `MemoryBuilder` | 设置触发压缩的消息数量阈值。 |
| `maxToken(int val)` | `MemoryBuilder` | 设置上下文允许的最大 Token 数。 |
| `tokenRatio(double val)` | `MemoryBuilder` | 设置压缩后的 Token 保留比例。 |
| `lastKeep(int val)` | `MemoryBuilder` | 设置末尾保留的消息数量（不参与压缩）。 |
| `largePayloadThreshold(int val)` | `MemoryBuilder` | 设置大负载消息的阈值。 |
| `offloadSinglePreview(int val)` | `MemoryBuilder` | 设置是否卸载单个预览（0/1标志）。 |
| `minConsecutiveToolMessages(int)` | `MemoryBuilder` | 设置最小连续工具消息数。 |
| `currentRoundCompressionRatio(double)` | `MemoryBuilder` | 设置当前轮次的压缩比例。 |
| `customPrompt(PromptConfig val)` | `MemoryBuilder` | 设置自定义的 Prompt 配置对象。 |
| `build()` | `AutoContextMemory` | **终结方法**。根据当前配置构建并返回内存实例。 |

### 4.3 ReMeLongTermMemoryBuilder

组件路径: `org.example.masfanplus.Memory.ReMeLongTermMemoryBuilder`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `build(String userId)` | `ReMeLongTermMemory` | 根据注入的 `baseUrl` 和传入的 `userId` 构建长时记忆对象。 |