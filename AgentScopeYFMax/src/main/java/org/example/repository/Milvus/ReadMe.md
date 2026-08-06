# Milvus 向量存储与高级检索模块开发文档

## 1. 模块概述

本模块基于 Milvus V2 SDK 构建，旨在提供一套完整、高效、可扩展的向量数据库操作层。模块集成了底层 CRUD 操作、复杂表达式构建、自动向量化（稠密与稀疏向量）、混合检索（Hybrid Search）以及基于 HanLP 的关键词重排功能。设计上遵循高内聚低耦合原则，通过面向对象的方式封装了 Milvus 的底层调用。

---

## 2. 核心架构与组件

模块主要由以下核心组件构成：

* **配置层 (`VectorStoreConfig`)**：负责初始化 `MilvusClientV2` 和 `MilvusCrudHelper` 实例。
* **核心操作层 (`MilvusCrudHelper`)**：提供无状态的底层 Milvus 交互 API，支持 Map 传参的增删改查、统一搜索（Unified Search）及混合搜索。
* **抽象存储层 (`AbstractMilvusVectorStore` & `BaseMilvusMeta`)**：为业务实体提供泛型基类支持。`BaseMilvusMeta` 实现了对象到 Milvus 数据的自动映射，`AbstractMilvusVectorStore` 封装了类型安全的通用数据访问方法。
* **查询构建器 (`MilvusFilterTemplateBuilder`)**：提供流式 API 构建 Milvus 标量过滤表达式，自动处理参数化和类型安全转换，提升检索性能并避免语法注入。
* **向量化与 NLP (`EmbeddingUtils`, `BgeM3SparseEmbedder`, `HanLPUtils`)**：
* 支持 DashScope 模型生成稠密向量。
* 基于 ONNX 运行时和 HuggingFace Tokenizer 的 BGE-M3 模型生成稀疏向量。
* 集成 HanLP 1.x 进行分词、关键词提取和重叠度计算。



---

## 3. 使用规范

### 3.1 实体定义规范

1. **继承基类**：所有与 Milvus 交互的实体类必须继承 `BaseMilvusMeta`。这为实体提供了搜索时的 `score` 字段以及 `toMilvusMap()` 自动转换方法。
2. **序列化注解**：使用 Fastjson2 的 `@JSONField` 注解映射 Java 属性与 Milvus Schema 字段名（如 `@JSONField(name = "experience_id")`）。
3. **类型对齐**：Java 数据类型需与 Milvus Schema 严格对齐。例如，Milvus 的 `Array<VarChar>` 对应 Java 的 `List<String>`，`Array<Int32>` 对应 `List<Integer>`。

### 3.2 向量与 Embedding 规范

1. **稠密向量**：默认使用 `DashScopeTextEmbedding` 生成 1024 维的 `float[]` 数组。
2. **稀疏向量**：需定义为 `SortedMap<Long, Float>` 格式，键为 Token ID，值为权重。
3. **BM25 向量**：若集合中配置了 Function 自动生成 BM25 向量，插入实体时对应字段应传入 `null`。

### 3.3 过滤查询规范

强烈建议使用 `MilvusFilterTemplateBuilder` 构建查询条件，避免手动拼接字符串，以支持 Milvus 服务端模板缓存并安全处理特殊字符。

---

## 4. API 介绍

### 4.1 MilvusFilterTemplateBuilder (查询模板构建器)

支持通过链式调用构建参数化查询表达式。

* **基础比较**：`.eq()`, `.ne()`, `.gt()`, `.ge()`, `.lt()`, `.le()`
* **范围与模糊**：`.in()`, `.like()`, `.phraseMatch()`
* **JSON/Array 操作**：`.jsonContains()`, `.arrayContainsAll()`, `.arrayLength()` 等
* **逻辑嵌套**：`.and()`, `.or()`, `.not()`
* **高级特性**：支持 `.randomSample()` 进行数据采样
* **生成结果**：调用 `.build()` 返回 `TemplateResult`，包含 `expression` 字符串和 `params` Map。

### 4.2 AbstractMilvusVectorStore (泛型存储库)

业务层应当继承此抽象类并提供具体的实体类型。

* `add(List<T> entities)`：批量新增数据。
* `upsert(List<T> entities)`：批量插入或更新（主键存在则更新）。
* `deleteByIds(List<Object> idList)`：根据主键列表物理删除。
* `queryByIds(List<Object> ids, List<String> outputFields)`：精确标量查询。
* `query(TemplateResult filter, List<String> outputFields, Long limit, Long offset)`：条件分页查询。
* `hybridSearch(MilvusCrudHelper.HybridSearch hybridSearch)`：执行高级混合搜索，自动反序列化为实体列表并注入相似度分数。

### 4.3 MilvusCrudHelper (底层操作核心)

提供更高阶的搜索与重排能力。

* `simpleUnifiedSearch(...)`：简易统一搜索，支持传入 `List<Float>`（稠密）或 `SortedMap<Long, Float>`（稀疏），自动构建对应的 `FloatVec` 或 `SparseFloatVec` 请求。
* `hybridSearch(...)`：接收查询文本和目标向量字段列表，内部自动调用 `EmbeddingUtils` 分配稠密/稀疏向量，并默认使用 `RRFRanker` (k=60) 进行多路召回融合。
* `rerankByKeywordScalar(...)`：基于 HanLP 提取检索 Query 和目标标量字段的关键词，计算 Jaccard 重叠度，根据传入的 `metaWeight` 对向量距离分数进行混合二次重排。

### 4.4 (辅助构造器)

用于辅助混合搜索中List< AnnSearchReq >的构造。

* `simpleUnifiedSearch(...)`：简易统一搜索，支持传入 `List<Float>`（稠密）或 `SortedMap<Long, Float>`（稀疏），自动构建对应的 `FloatVec` 或 `SparseFloatVec` 请求。
* `hybridSearch(...)`：接收查询文本和目标向量字段列表，内部自动调用 `EmbeddingUtils` 分配稠密/稀疏向量，并默认使用 `RRFRanker` (k=60) 进行多路召回融合。
* `rerankByKeywordScalar(...)`：基于 HanLP 提取检索 Query 和目标标量字段的关键词，计算 Jaccard 重叠度，根据传入的 `metaWeight` 对向量距离分数进行混合二次重排。


---

## 5. 综合示例

### 5.1 构建过滤表达式

```java
// 构建查询: complexity_level < -20 AND cost_estimate > 0.5
MilvusFilterTemplateBuilder.TemplateResult filterResult = MilvusFilterTemplateBuilder.create()
        .lt("complexity_level", -20)
        .gt("cost_estimate", 0.5F)
        .build();

// 结果将生成带占位符的表达式及参数 Map 
System.out.println(filterResult.getExpression()); //

```

### 5.2 混合搜索调用

```java
// 定义混合搜索请求，指定查询文本及涉及的向量字段 (系统会自动区分稠密与稀疏并生成向量)
MilvusCrudHelper.HybridSearch hybridRequest = MilvusCrudHelper.HybridSearch.builder()
        .queryText("可持续发展已成为全球关注的焦点")
        .vectorFields(Arrays.asList("problem_vector", "problem_sparse_vector"))
        .topK(3)
        .build();

// 执行搜索并返回实体对象列表
List<AgenticExperience> results = store.hybridSearch(hybridRequest); //

```