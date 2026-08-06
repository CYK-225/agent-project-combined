

# Session 模块技术文档

## 1. 模块概述

`Session` 模块为 AgentScope 应用提供了基于关系型数据库的持久化存储能力。该模块通过实现 `io.agentscope.core.session.Session` 接口，利用 PostgreSQL 数据库和 MyBatis-Flex ORM 框架，实现了智能体（Agent）状态、会话历史及记忆数据的持久化保存与恢复。

**核心特性：**

* **持久化存储**：支持将 AgentScope 的运行时状态（State）序列化为 JSON 格式并存储至 PostgreSQL。
* **列表优化**：针对聊天记录等列表型数据，采用 Hash 校验机制 (`ListHashUtil`) 实现增量更新或全量重写，优化写入性能。
* **类型安全**：基于 MyBatis-Flex 的 `QueryWrapper` 和 `QueryColumn` 构建类型安全的数据库查询。
* **严格校验**：内置 SessionID 和 Key 的格式与长度校验，保障数据安全性。

## 2. 核心组件架构

### 2.1 会话实现类 (`PostgresSession`)

* **类路径**: `org.example.masfanplus.Session.PostgresSession`
* **职责**: 核心业务实现类。负责拦截 AgentScope 的会话操作请求，处理对象序列化/反序列化，并调用 Mapper 层进行数据库交互。
* **关键机制**:
* **单对象存储**: 对应 `item_index = 0`。
* **列表存储**: 对应 `item_index > 0`，配合 `_hash` 后缀键存储列表内容的哈希值，用于变更检测。



### 2.2 数据实体 (`AgentscopeSessionsEntity`)

* **类路径**: `org.example.masfanplus.Session.AgentscopeSessionsEntity`
* **表名**: `agentscope_sessions`
* **职责**: 数据库表结构的直接映射。

| 字段名 | 类型 | 描述 |
| --- | --- | --- |
| `session_id` | String | 会话唯一标识 (联合主键) |
| `state_key` | String | 状态键名，如 `history` (联合主键) |
| `item_index` | Integer | 列表索引，单对象为 0 (联合主键) |
| `state_data` | String | JSON 格式的序列化数据 |
| `created_at` | Date | 创建时间 |
| `updated_at` | Date | 更新时间 |

### 2.3 数据映射层 (`AgentScopeSessionsMapper`)

* **类路径**: `org.example.masfanplus.Session.AgentScopeSessionsMapper`
* **职责**: 继承自 MyBatis-Flex 的 `BaseMapper`，提供基础的 CRUD 操作接口。

---

## 3. 开发与使用规范

### 3.1 依赖注入配置

该模块是一个标准的 Spring `@Component`。在 AgentScope 初始化或配置阶段，应将 `PostgresSession` 注入到运行时环境中。

```java
import org.example.masfanplus.agentScope.util.session.PostgresSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public void configureSession(PostgresSession postgresSession) {
        // 假设 AgentScope 提供了设置全局 Session 的入口
        // AgentScope.setSession(postgresSession);
        System.out.println("PostgresSession has been initialized.");
    }
}

```

### 3.2 ID 与 Key 命名规范

模块内部 (`PostgresSession`) 对标识符进行了严格校验，开发时需注意：

* **非空校验**: Session ID 和 Key 均不能为空。
* **字符限制**: 不允许包含路径分隔符（`/` 或 `\`）。
* **长度限制**: 最大长度限制为 255 字符。

---

## 4. API 方法参考

以下是 `PostgresSession` 类核心方法的详细说明。

以下是修正后的 **API 方法参考** 部分，已去除 `<br>` 标签并统一了表格格式，使其与其他章节保持一致。

---

## 4. API 方法参考

以下是 `PostgresSession` 类核心方法的详细说明。

### 方法参考

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `save(sessionKey, key, value)` | `void` | **保存单个状态对象**。将状态对象序列化为 JSON 并保存（或更新）。内部强制设置 `item_index` 为 0。 |
| `save(sessionKey, key, values)` | `void` | **保存状态列表**。智能保存列表数据（如对话历史）。系统会计算列表 Hash，若与数据库中现有 Hash 不一致且无法增量追加，则触发全量重写；否则执行增量追加。 |
| `get(sessionKey, key, type)` | `Optional<T>` | **获取单个状态对象**。根据 Key 获取并反序列化单个对象。若不存在返回 `Optional.empty()`。 |
| `getList(sessionKey, key, itemType)` | `List<T>` | **获取状态列表**。根据 Key 获取列表所有项，按 `item_index` 升序排列并反序列化为指定类型。 |
| `exists(sessionKey)` | `boolean` | **检查会话存在性**。判断指定 `session_id` 下是否存在任何数据。 |
| `delete(sessionKey)` | `void` | **删除会话**。物理删除指定 `session_id` 关联的所有数据（包括所有 Key 和列表项）。 |
| `listSessionKeys()` | `Set<SessionKey>` | **列出所有会话**。查询数据库中所有去重后的 `session_id`，并封装为 `SessionKey` 集合返回。 |
| `clearAllSessions()` | `int` | **清空所有会话**。删除表中所有数据。返回值表示受影响的行数。 |
| `close()` | `void` | **关闭资源**。当前实现为空，资源生命周期交由 Spring 容器管理。 |