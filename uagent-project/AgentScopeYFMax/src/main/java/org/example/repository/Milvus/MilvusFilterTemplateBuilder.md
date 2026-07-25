
# MilvusFilterTemplateBuilder 技术文档

`MilvusFilterTemplateBuilder` 是一个专为 Milvus 向量数据库设计的过滤表达式模板构建器。它通过流畅的链式调用（Fluent API）来构建复杂的 Milvus 过滤表达式。

其核心优势在于**将表达式逻辑与实际数据值分离**。该构建器会生成带有占位符（如 `{param_age_1}`）的表达式字符串，并同步生成一个包含实际值的参数映射表（`Map<String, Object>`）。这种模板化机制允许 Milvus 服务端复用已解析的表达式模板，只需注入新的参数值即可，从而显著提升包含大量值（特别是中日韩字符）或复杂列表的查询性能。

---

## 1. 使用规范与内部机制

在使用 `MilvusFilterTemplateBuilder` 之前，需要了解其内部的数据处理规范和隐式转换机制：

* **受支持的数据类型**：Milvus 模板参数仅原生支持 `String`、`Integer`、`Long`、`Double`、`Boolean` 以及这些类型的集合（如 `List`）。
* **隐式类型转换**：
* 为了兼容性，构建器会自动将 `Float` 类型转换为 `Double`。
* 会自动将 `Short` 和 `Byte` 类型转换为标准的 `Integer`。


* **安全校验 (`sanitizeValue`)**：当传入不支持的复杂对象（如自定义实体类、`Date` 等）时，构建器会立即抛出 `IllegalArgumentException` 异常，并明示发生错误的字段或操作上下文。**规范要求**在传入前将复杂类型转换为受支持的基础数据类型。
* **实例化规范**：推荐使用静态工厂方法 `MilvusFilterTemplateBuilder.create()` 或便捷方法 `condition()` 来初始化构建器。

---

## 2. API 介绍与功能分组列表

构建器提供了丰富的方法来应对各种 Milvus 查询场景。以下是按功能分组的 API 详细列表：

### 2.1 字段引用 (FieldRef)

专门用于处理复杂的嵌套字段，特别是针对 JSON 和 ARRAY 类型的动态或深层路径引用。

* `field(String fieldName)`：创建一个字段引用对象。
* `.jsonKey(String... keys)`：构建嵌套 JSON 字段引用（例如：`field("product").jsonKey("metadata", "price")` 生成 `product["metadata"]["price"]`）。
* `.atIndex(int index)`：构建数组特定索引引用（例如：`field("temps").atIndex(10)` 生成 `temps[10]`）。
* `.dynamicKey(String key)`：构建对动态 JSON 键的引用。

### 2.2 基础比较操作符

| API 方法 | 说明 | 对应 Milvus 表达式模板 |
| --- | --- | --- |
| `eq(field, value)` | 等于 (Equal) | `field == {placeholder}` |
| `ne(field, value)` | 不等于 (Not Equal) | `field != {placeholder}` |
| `gt(field, value)` | 大于 (Greater Than) | `field > {placeholder}` |
| `ge(field, value)` | 大于等于 (Greater Equal) | `field >= {placeholder}` |
| `lt(field, value)` | 小于 (Less Than) | `field < {placeholder}` |
| `le(field, value)` | 小于等于 (Less Equal) | `field <= {placeholder}` |

### 2.3 范围与空值检查

| API 方法 | 说明 | 对应 Milvus 表达式模板 |
| --- | --- | --- |
| `in(field, values)` | 包含于指定集合 | `field in {placeholder}` |
| `like(field, pattern)` | 字符串模式匹配 | `field LIKE {placeholder}` |
| `isNull(field)` | 检查字段是否为空 | `field IS NULL` |
| `isNotNull(field)` | 检查字段是否非空 | `field IS NOT NULL` |

### 2.4 JSON 字段专用操作

处理 Milvus 中的 JSON 数据列。可配合 `FieldRef` 生成的字符串作为 `field` 参数传入。

| API 方法 | 说明 | 对应 Milvus 表达式模板 |
| --- | --- | --- |
| `jsonContains(field, value)` | 检查 JSON 数组中是否包含指定的**单个值** | `JSON_CONTAINS(field, {placeholder})` |
| `jsonContainsAll(field, values)` | 检查 JSON 数组中是否包含指定的**所有值** | `JSON_CONTAINS_ALL(field, {placeholder})` |
| `jsonContainsAny(field, values)` | 检查 JSON 数组中是否包含指定的**任意一个值** | `JSON_CONTAINS_ANY(field, {placeholder})` |
| `jsonExists(field)` | 检查指定的 JSON 路径（键）是否存在 | `JSON_EXISTS(field)` |

> **注意**：`JSON_EXISTS` 并非所有 Milvus 版本都支持，请根据您当前部署的 Milvus 服务端版本酌情使用。

### 2.5 ARRAY 字段专用操作

处理 Milvus 中的 Array 数据列。

| API 方法 | 说明 | 对应 Milvus 表达式模板 |
| --- | --- | --- |
| `arrayContains(field, value)` | 检查数组中是否包含指定的**单个值** | `ARRAY_CONTAINS(field, {placeholder})` |
| `arrayContainsAll(field, values)` | 检查数组中是否包含指定的**所有值** | `ARRAY_CONTAINS_ALL(field, {placeholder})` |
| `arrayContainsAny(field, values)` | 检查数组中是否包含指定的**任意一个值** | `ARRAY_CONTAINS_ANY(field, {placeholder})` |
| `arrayLength(field, op, length)` | 使用指定操作符匹配数组的长度 | `ARRAY_LENGTH(field) op {placeholder}` |

### 2.6 高级特性与全文检索

| API 方法 | 说明 | 版本要求 / 备注 |
| --- | --- | --- |
| `randomSample(factor)` | 随机抽样，`factor` 需在 `(0, 1)` 之间 | Milvus 2.6+，需配合 `AND` 逻辑结合使用 |
| `phraseMatch(field, phrase)` | 短语精确匹配（无位置偏移） | Milvus 2.5.17+ |
| `phraseMatch(field, phrase, slop)` | 带 `slop`（最大位置偏移数）的短语匹配 | Milvus 2.5.17+ |
| `cast(field, type, op, value)` | 将字段转换为指定类型后进行比较 | 非标准 Milvus 操作符，请谨慎使用 |

### 2.7 逻辑嵌套与结果构建

| API 方法 | 说明                                                                       |
| --- |--------------------------------------------------------------------------|
| `and(builder)` | 使用 `AND` 逻辑将另一个构建器生成的表达式作为子句接入当前上下文                                      |
| `or(builder)` | 使用 `OR` 逻辑将另一个构建器生成的表达式作为子句接入当前上下文                                       |
| `not(builder)` | 对传入的子表达式构建器应用 `NOT` 逻辑（处理局部取反的推荐方式）                                      |
| `not()` | 对整个当前构建的表达式应用 `NOT` 逻辑                                                   |
| `build()` | 生成最终结果，返回包含 `expression` 和 `params` 的 `TemplateResult` 对象                |
| `buildInlineExpression()` | 直接生成包含参数的过滤表达式，不需要传入TemplateResult，直接传入String，<br/>用于适配混合搜索的条件过滤，但建议一般情况下仍然传入TemplateResult  |



---

## 3. 使用示例代码

以下代码展示了在不同业务场景下，如何使用 `MilvusFilterTemplateBuilder` 进行条件组装。

### 示例 1：基础模板与 IN 范围查询

```java
MilvusFilterTemplateBuilder.TemplateResult result1 = MilvusFilterTemplateBuilder.create()
    .gt("age", 25)
    .in("city", Arrays.asList("北京", "上海"))
    .build();

// 表达式输出: age > {param_age_1} AND city in {param_city_2}
// 参数集输出: {param_age_1: 25, param_city_2: [北京, 上海]}

```

### 示例 2：嵌套 JSON 字段过滤

利用 `FieldRef` 辅助类来构建复杂的 JSON 路径引用，并结合基础比较操作符使用。

```java
String modelField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("model");
String priceField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("price");

MilvusFilterTemplateBuilder.TemplateResult result2 = MilvusFilterTemplateBuilder.create()
    .eq(modelField, "JSN-087")
    .lt(priceField, 1850)
    .build();

// 表达式输出: product["model"] == {param_product_model_1} AND product["price"] < {param_product_price_2}

```

### 示例 3：复杂嵌套与 OR 逻辑

可以将多个构建器实例通过逻辑操作符组合，形成带有括号优先级的树状表达式。

```java
MilvusFilterTemplateBuilder activeOrNoDesc = MilvusFilterTemplateBuilder.create()
    .eq("status", "active")
    .or(MilvusFilterTemplateBuilder.create().isNull("description"));

MilvusFilterTemplateBuilder.TemplateResult result3 = MilvusFilterTemplateBuilder.create()
    .gt("age", 30)
    .and(activeOrNoDesc)
    .build();

// 表达式输出: age > {param_age_1} AND ((status == {param_status_2}) OR (description IS NULL))

```

### 示例 4：对子表达式取反（推荐的 NOT 用法）

```java
MilvusFilterTemplateBuilder greenOrCheap = MilvusFilterTemplateBuilder.create()
    .eq("color", "green")
    .or(MilvusFilterTemplateBuilder.create().lt("price", 10));

MilvusFilterTemplateBuilder.TemplateResult result4 = MilvusFilterTemplateBuilder.create()
    .not(greenOrCheap)
    .build();

// 表达式输出: NOT ((color == {param_color_1}) OR (price < {param_price_2}))

```

### 示例 5：结合 Milvus 2.6+ 的随机抽样机制

```java
MilvusFilterTemplateBuilder baseFilter = MilvusFilterTemplateBuilder.create()
    .eq("category", "electronics")
    .gt("price", 100);

MilvusFilterTemplateBuilder.TemplateResult result5 = MilvusFilterTemplateBuilder.create()
    .and(baseFilter)
    .randomSample(0.005) // 采样 0.5% 的数据
    .build();

// 表达式输出: ((category == {param_category_1}) AND (price > {param_price_2})) AND RANDOM_SAMPLE({sampling_factor_3})

```

---

