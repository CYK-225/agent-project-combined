# MilvusCrudHelper 操作指南与 API 参考

本文档基于提供的源码，对 `MilvusCrudHelper` 工具类进行专业、客观的功能拆解与说明。文档涵盖了使用规范、功能分组清单、API 参数说明以及核心场景的代码示例。

## 1. 简介与使用规范

`MilvusCrudHelper` 是一个高度封装的通用 Milvus 增删改查（CRUD）工具类。

* **数据结构**：摒弃了复杂的实体类绑定，仅支持通过灵活的 `Map<String, Object>` 进行数据传递。
* **设计模式**：采用传统的无链式调用设计，方法职责单一明确。
* **自动向量化**：提供了 `prepareDataWithEmbedding` 辅助方法，能够在入库前自动将文本转换为稠密向量（Dense）或稀疏向量（Sparse）。
* **初始化规范**：该类由 Spring 的 `@Component` 管理，在实例化时，**必须通过构造函数传入已初始化的 `MilvusClientV2` 客户端对象**，否则将抛出 `IllegalArgumentException` 异常。

---

## 2. 功能详细罗列与分组

### 2.1 数据写入模块 (Data Writing)

该模块主要负责向量与标量数据的持久化。

* **批量插入 (`insert`)**：将包含多条数据的 `List<Map<String, Object>>` 结构批量写入指定 Collection。
* **插入或更新 (`upsert`)**：支持单条和批量数据的 Upsert。底层默认开启了 `partialUpdate(true)`，即若主键已存在则进行局部更新，否则直接插入新行。
* **数据预处理 (`prepareDataWithEmbedding`)**：针对单条 Map 数据，读取指定的源文本字段，调用 Embedding 工具生成向量，并将其注入到目标稠密向量字段和稀疏向量字段中。

### 2.2 数据查询模块 (Data Query)

支持多维度的标量与属性查询。

* **条件查询 (`query`)**：支持接收基于 `MilvusFilterTemplateBuilder.TemplateResult` 的模板过滤条件或纯字符串表达式（如 `"id > 10"`），并可自定义返回字段、Limit 限制和 Offset 偏移量。
* **全属性查询 (`queryAll`)**：在过滤的基础上，快捷返回匹配记录的所有字段（即 `outputFields` 设为 `["*"]`）。
* **主键检索 (`queryByIds` / `queryAllByIds`)**：根据提供的主键 ID 列表直接获取数据行实体。
* **计数查询 (`count`)**：执行 `count(*)` 操作，统计符合过滤表达式的文档总数。

### 2.3 高级搜索模块 (Search / Hybrid Search)

该模块集成了向量检索、混合检索以及通过外部 NLP 工具进行的重排。

* **自动混合搜索 (`hybridSearch`)**：自动化程度高。根据查询文本和指定的向量字段列表，自动判断并生成对应的浮点向量（Dense）或稀疏向量（Sparse），随后构建多路搜索请求，并默认使用 `RRFRanker` (k=60) 对多路召回结果进行重排。
* **自定义混合搜索 (`hybridSearchDIY`)**：为高级用户保留的接口，需调用者自行构建并传入 `AnnSearchReq` 搜索请求列表。
* **统一搜索 (`performUnifiedSearch`)**：支持全功能的搜索配置，包括全文检索、单路向量搜索、标量过滤、以及 Milvus 2.4+ 支持的 **分组搜索 (Group By)** 功能。内部会对 IP/COSINE 和其他度量类型的分数格式进行自动修正归一化。
* **极简统一搜索 (`simpleUnifiedSearch`)**：以最少参数快速执行搜索。智能识别 `queryData` 类型，`List<Float>` 视为稠密向量，`SortedMap<Long, Float>` 视为稀疏向量。
* **关键词重排序 (`rerankByKeywordScalar`)**：业务层的重排序逻辑。利用 HanLP 提取查询语句和结果实体中的关键词，计算两者的重叠度得分，并通过给定的权重因子 (`metaWeight`) 将其与向量相似度原始得分进行加权融合。

### 2.4 维护与清理模块 (Deletion & Collection Management)

* **数据删除 (`deleteByIds` / `deleteByFilter`)**：允许通过具体的主键 ID 集合或条件表达式来清除集合内的数据。
* **内存管理 (`loadCollection` / `releaseCollection`)**：提供集合的装载和释放功能，搜索前必须将其 Load 至内存，不使用时可 Release 以节约资源。

---

## 3. API 介绍：核心请求配置说明

以下梳理了代码中两个核心配置类的参数定义。

### 3.1 统一搜索配置 (`UnifiedSearchRequest`)

此类用于封装单路/全功能的查询配置。

| 配置项 | 说明 | 默认值/备注 |
| --- | --- | --- |
| `queryVector` | **【核心】** 查询所用的浮点向量数据。 | - |
| `queryText` | **【核心】** 查询文本，用于触发全文检索。 | - |
| `enableFullTextSearch` | **【开关】** 是否启用基于文本的稀疏/全文检索。 | `false` |
| `enableGrouping` | **【开关】** 是否开启结果聚合/分组 (Group By)。 | `false` |
| `enableFiltering` | **【开关】** 是否应用 `filter` 中的标量过滤表达式。 | `false` |
| `groupByField` | 【分组】分组依据的字段名称，通常为 INT64 或 VARCHAR。 | - |
| `groupSize` | 【分组】每一组内需返回的结果条数。 | 默认为 1 |
| `filter` | 【过滤】标准的 Milvus 标量过滤表达式字符串。 | - |
| `outputFields` | 【返回】需包含在结果实体中的列名列表。 | - |
| `annsField` | **【核心】** 被检索的目标向量字段名。 | 全文检索默认为 `"sparse_vector"`，否则为 `"embedding"` |
| `topK` | **【核心】** 返回相似度最高的前 K 条数据。 | - |
| `metricType` | 【度量】向量距离计算方式 (如 L2, IP, COSINE)。 | 需与建表配置保持一致 |
| `metaWeight` | 【权重】用于触发并配置 `rerankByKeywordScalar` 的权重因子。 | - |
| `fieldName` | 【字段】提供给关键词重排序使用，用于提取实体的标量文本。 | - |

### 3.2 混合搜索配置 (`HybridSearch`)

此类专为多路召回 (Multi-vector search) 与融合而设计。

| 配置项 | 说明 | 默认值/备注 |
| --- | --- | --- |
| `queryText` | **【核心】** 原始查询字符串，内部会将其自动转化为多路所需的向量。 | - |
| `vectorFields` | **【核心】** 需同时参与混合搜索的多个字段名集合。 | - |
| `topK` | **【核心】** 经过 RRF 融合重排后，截取返回的最终记录数。 | - |
| `filter` | 【过滤】应用于各路子查询的全局条件表达式。 | - |
| `searchRequests` | 【参数】自定义搜索时所需的 `AnnSearchReq` 实例列表。 | `hybridSearchDIY` 模式下必填 |
| `metaWeight` | 【重排】关键词标量叠加重排序的得分权重。 | - |
| `fieldName` | 【重排】重排序时，指定对比的文档标量列名。 | - |

---

## 4. 典型场景使用示例代码

下面展示了如何使用 `MilvusCrudHelper` 进行数据的入库、简易搜索与混合搜索：

```java
import org.example.masfanplus.repository.Milvus.core.MilvusCrudHelper;

import java.util.*;

// 假定已经通过 Spring 依赖注入获取到 helper 实例
// MilvusCrudHelper helper = new MilvusCrudHelper(milvusClientV2);

public void showUsage(MilvusCrudHelper helper) {
    String dbName = "default_db";
    String collectionName = "agentic_experience_bank";

    // ==========================================
    // 场景 1: 辅助向量化并插入数据
    // ==========================================
    Map<String, Object> dataRow = new HashMap<>();
    dataRow.put("id", 1001L);
    dataRow.put("content", "如何处理微服务架构中的分布式事务一致性问题。");

    // 工具会根据 "content" 生成稠密和稀疏向量，并放入指定的字段中
    Map<String, Object> readyData = helper.prepareDataWithEmbedding(
            dataRow,
            "content",         // 源文本字段
            "embedding",       // 稠密向量存放字段
            "sparse_vector"    // 稀疏向量存放字段
    );
    helper.insert(collectionName, dbName, Collections.singletonList(readyData));

    // ==========================================
    // 场景 2: 简易向量检索 (以稠密向量为例)
    // ==========================================
    // 假定这是经过 Embedding 模型转化后的 768 维 Float 列表
    List<Float> queryDenseVec = Arrays.asList(0.12f, -0.45f, 0.88f /* ... */);

    var simpleResp = helper.simpleUnifiedSearch(
            collectionName,
            "embedding",         // 指定被检索的向量列
            queryDenseVec,       // 传入查询向量
            "id > 1000",         // 增加标量过滤
            5,                   // Top K
            Arrays.asList("id", "content") // 指定回显字段
    );

    // ==========================================
    // 场景 3: 自动化多路混合搜索 + 关键词标量重排序
    // ==========================================
    MilvusCrudHelper.HybridSearch hybridParams = MilvusCrudHelper.HybridSearch.builder()
            .queryText("分布式事务解决方案")
            .vectorFields(Arrays.asList("embedding", "sparse_vector")) // 多路召回字段
            .topK(10)
            .filter("id > 0")
            .fieldName("content") // 用于提取业务关键词比对重叠度
            .metaWeight(0.2)      // 设定基于关键词命中的得分加成权重为 0.2
            .build();

    // 内部自动执行：文本转两路向量 -> 并发召回 -> RRF (k=60) 融合 -> 提取 HanLP 关键词计算重叠度二次排序
    var hybridResp = helper.hybridSearch(hybridParams, dbName, collectionName);
}

```

---

你需要我进一步为你补充针对 `performUnifiedSearch` 复杂分组查询的代码示例，或者解释其中关于 IP/Cosine 度量分数计算的底层逻辑吗？