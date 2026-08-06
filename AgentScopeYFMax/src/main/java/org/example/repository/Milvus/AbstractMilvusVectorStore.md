
---

# Milvus 向量存储通用服务层文档

## 1. 概述

本模块旨在为 Milvus 向量数据库提供一套**通用的、基于泛型的**操作抽象层。它封装了底层 `MilvusCrudHelper` 的具体实现细节，通过继承机制为上层业务提供标准化的 CRUD（增删改查）与向量搜索（Search）能力。

核心设计包含两个部分：

1. **数据模型基类 (`BaseMilvusMeta`)**：规范实体定义，提供基于 Fastjson2 的对象到 Milvus 字段映射能力。
2. **存储服务抽象类 (`AbstractMilvusVectorStore`)**：提供泛型化的数据库操作接口。

---

## 2. 核心组件架构

### 2.1 实体基类：BaseMilvusMeta

该类是所有需存储至 Milvus 的业务实体的父类。它负责处理序列化转换及搜索结果的元数据承载。

* **功能定位**：数据传输对象 (DTO) / 实体 (Entity)。
* **关键特性**：
* **纯净的 Fastjson2 实现**：完全移除了 Jackson 依赖，实现了序列化与反序列化工具的统一。内部使用 `JSONObject.from(this)` 将实体转换为 `Map<String, Object>` 以供 Milvus SDK 写入。
* **评分字段**：内置 `score` 字段，专门用于承载向量搜索时的相似度得分。该字段被标记为 `@JSONField(serialize = false)`，确保只在内存中流转，不会被错误地写入数据库。



### 2.2 存储抽象类：AbstractMilvusVectorStore `<T>`

该类是标准的 DAO (Data Access Object) 层抽象，通过泛型 `<T extends BaseMilvusMeta>` 约束操作对象。

* **功能定位**：通用存储库 (Repository)。
* **依赖注入**：
* `MilvusCrudHelper`: 底层 Milvus 操作工具类。
* `collectionName`: 集合名称。
* `databaseName`: 数据库名称。
* `entityType`: 实体的 Class 对象（用于反射与反序列化）。



---

## 3. 开发接入规范

为了确保系统的稳定性和代码的一致性，请遵循以下接入步骤：

### 3.1 定义实体类

业务实体**必须**继承 `BaseMilvusMeta`，并建议使用 Lombok 简化代码。

```java
// 示例
@Data
public class UserFaceFeature extends BaseMilvusMeta {
    // 对应 Milvus 中的字段
    private Long userId;
    
    // 对应向量字段
    private List<Float> faceVector;
    
    // 如需忽略某个字段不存入 Milvus，使用 Fastjson2 注解
    @JSONField(serialize = false)
    private String tempData;
}

```

### 3.2 实现存储层

业务存储服务**必须**继承 `AbstractMilvusVectorStore`，并通过构造函数注入必要的元数据。

```java
// 示例
@Service
public class UserFaceStore extends AbstractMilvusVectorStore<UserFaceFeature> {
    
    public UserFaceStore(MilvusCrudHelper helper) {
        super(helper, "user_face_collection", "default_db", UserFaceFeature.class);
    }
}

```

### 3.3 序列化注意事项

* **统一注解**：所有序列化控制均需使用 Fastjson2 的 `@JSONField`。
* **写入机制**：系统调用 `toMilvusMap()` 时，会自动忽略 `serialize = false` 的字段（如 `score`）。
* **读取机制**：系统查询返回时，会使用 `JSON.to(entityType, ...)` 进行反序列化，请确保实体类具有无参构造函数。

---

## 4. API 接口详解

### 4.1 数据写入与更新 (Insert / Upsert)

| 方法签名 | 描述 | 返回值 | 备注 |
| --- | --- | --- | --- |
| `add(List<T> entities)` | 批量新增数据 | `List<Object>` | 底层调用 `toMilvusMap` 转换，返回插入成功的主键列表。 |
| `upsert(List<T> entities)` | 批量更新或插入 | `List<Object>` | 若主键存在则更新，不存在则插入。返回主键列表。 |

### 4.2 数据删除 (Delete)

| 方法签名 | 描述 | 返回值 | 备注 |
| --- | --- | --- | --- |
| `deleteByIds(List<Object> idList)` | 根据主键列表删除 | `Boolean` | 返回是否全部执行删除（注：Milvus 的 deleteCnt 有时为估算值）。 |
| `deleteByFilter(String filter)` | 根据标量表达式删除 | `Boolean` | 表达式语法需符合 Milvus 规范。 |

### 4.3 标量查询 (Query)

主要用于非向量的属性检索。

| 方法签名 | 参数说明 | 返回值 |
| --- | --- | --- |
| `queryByIds(...)` | `ids`: 主键列表<br>

<br>`outputFields`: 指定返回字段 | `List<T>` |
| `query(...)` | `filter`: 过滤表达式<br>

<br>`limit`: 条数<br>

<br>`offset`: 偏移量 | `List<T>` (支持分页) |
| `queryAll(...)` | `filter`: 过滤表达式（通常传空串或 `""`） | `List<T>` (全量或大批量扫描) |

### 4.4 向量搜索 (Search)

基于向量相似度的检索操作，底层会自动将结果转换为实体对象并注入 `score`。

| 方法签名 | 描述 | 适用场景 |
| --- | --- | --- |
| `hybridSearch(...)` | 标准混合搜索 | 常规的向量+标量过滤搜索。 |
| `hybridSearchDIY(...)` | 自定义混合搜索 | 需要更细粒度控制搜索参数的场景。 |
| `performUnifiedSearch(...)` | 统一搜索接口 | 适配多种搜索策略的统一入口。 |

**逻辑说明**：`AbstractMilvusVectorStore` 默认针对**单条目标向量**的搜索结果进行处理（`resp.getSearchResults().getFirst()`），将返回与该目标向量最相似的实体列表。

---

## 5. 内部实现细节 (Internal Implementation)

### 统一 JSON 处理架构

代码重构后，完全统一了 JSON 处理库，消除了 Jackson 与 Fastjson2 混用带来的维护成本。

* **Map 转换 (To Milvus)**:
* 使用 `JSONObject.from(this)`。
* **优势**：直接利用 Fastjson2 的高性能转换能力，且天然支持 `@JSONField` 注解控制。


* **对象还原 (From Milvus)**:
* 使用 `JSON.to(entityType, r.getEntity())`。
* **优势**：在处理查询结果（QueryResp）和搜索结果（SearchResp）时，保持了反序列化行为的一致性。



### Score 注入机制

在执行向量搜索 (`hybridSearch` 等) 时，Milvus 返回的结果包含两部分：实体数据和相似度分数。
`AbstractMilvusVectorStore` 会自动拦截搜索结果：

1. 将数据部分反序列化为实体对象 `T`。
2. 提取 `SearchResult` 中的 `score`。
3. 调用 `entity.setScore()` 显式注入。

这一过程对上层业务透明，业务方只需直接从返回的 `List<T>` 中读取 `getScore()` 即可。