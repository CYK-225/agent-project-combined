package org.example.repository.Milvus.utils;//package org.example.aitest.infrastructure.Utils.Milvus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 过滤表达式模板构建器。
 * <p>
 * 该构建器允许通过流畅的链式调用 API 来构建复杂的 Milvus 过滤表达式。
 * 它的核心优势在于将表达式逻辑与实际数据值分离，生成带有占位符（如 {@code {param_age_1}}）的表达式字符串
 * 和一个包含实际值的参数映射（{@code Map<String, Object>}）。
 * <p>
 * 这种模板化机制能显著提升包含大量值（尤其是中日韩字符）或复杂列表的查询性能，
 * 因为 Milvus 服务端可以复用已解析的表达式模板，只需注入新的参数值。
 * <p>
 * 本构建器支持：
 * <ul>
 *   <li>所有基本比较操作符 ({@code ==, !=, >, <, >=, <=})</li>
 *   <li>范围操作符 ({@code IN, LIKE})</li>
 *   <li>空值检查 ({@code IS NULL, IS NOT NULL})</li>
 *   <li>JSON 字段操作符 ({@code JSON_CONTAINS, JSON_CONTAINS_ALL, JSON_CONTAINS_ANY})</li>
 *   <li>ARRAY 字段操作符 ({@code ARRAY_CONTAINS, ARRAY_CONTAINS_ALL, ARRAY_CONTAINS_ANY, ARRAY_LENGTH})</li>
 *   <li>复杂的嵌套逻辑（通过 {@code and}, {@code or}, {@code not} 方法）</li>
 *   <li>Milvus 2.6+ 的 {@code RANDOM_SAMPLE} 操作符</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1: 基础模板
 * MilvusFilterTemplateBuilder.TemplateResult result1 = MilvusFilterTemplateBuilder.create()
 *     .gt("age", 25)
 *     .in("city", Arrays.asList("北京", "上海"))
 *     .build();
 * // 表达式: age > {param_age_1} AND city in {param_city_2}
 * // 参数: {param_age_1: 25, param_city_2: [北京, 上海]}
 *
 * // 示例 2: JSON 字段过滤
 * String modelField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("model");
 * String priceField = new MilvusFilterTemplateBuilder.FieldRef("product").jsonKey("price");
 * MilvusFilterTemplateBuilder.TemplateResult result2 = MilvusFilterTemplateBuilder.create()
 *     .eq(modelField, "JSN-087")
 *     .lt(priceField, 1850)
 *     .build();
 * // 表达式: product["model"] == {param_product_model_1} AND product["price"] < {param_product_price_2}
 *
 * // 示例 3: 复杂嵌套逻辑
 * MilvusFilterTemplateBuilder activeOrNoDesc = MilvusFilterTemplateBuilder.create()
 *     .eq("status", "active")
 *     .or(MilvusFilterTemplateBuilder.create().isNull("description"));
 *
 * MilvusFilterTemplateBuilder.TemplateResult result3 = MilvusFilterTemplateBuilder.create()
 *     .gt("age", 30)
 *     .and(activeOrNoDesc)
 *     .build();
 * // 表达式: age > {param_age_1} AND ((status == {param_status_2}) OR (description IS NULL))
 *
 * // 示例 4: 对子表达式取反 (推荐方式)
 * MilvusFilterTemplateBuilder greenOrCheap = MilvusFilterTemplateBuilder.create()
 *     .eq("color", "green")
 *     .or(MilvusFilterTemplateBuilder.create().lt("price", 10));
 *
 * MilvusFilterTemplateBuilder.TemplateResult result4 = MilvusFilterTemplateBuilder.create()
 *     .not(greenOrCheap)
 *     .build();
 * // 表达式: NOT ((color == {param_color_1}) OR (price < {param_price_2}))
 *
 * // 示例 5: 结合 RANDOM_SAMPLE (Milvus 2.6+)
 * MilvusFilterTemplateBuilder baseFilter = MilvusFilterTemplateBuilder.create()
 *     .eq("category", "electronics")
 *     .gt("price", 100);
 *
 * MilvusFilterTemplateBuilder.TemplateResult result5 = MilvusFilterTemplateBuilder.create()
 *     .and(baseFilter)
 *     .randomSample(0.005) // 采样 0.5%
 *     .build();
 * // 表达式: ((category == {param_category_1}) AND (price > {param_price_2})) AND RANDOM_SAMPLE({param_sampling_factor_3})
 * }</pre>
 */
public class MilvusFilterTemplateBuilder {

    private final Map<String, Object> params = new LinkedHashMap<>();
    private final List<ExpressionNode> expressionNodes = new ArrayList<>();
    private boolean isNegated = false;
    private int placeholderCounter = 0;

    // -------------------------
    // 内部隐式类型转换与校验方法
    // -------------------------

    /**
     * 隐式类型转换与校验机制。
     * Milvus 模板参数仅支持特定的数据类型 (如 Integer, Long, Double, String, Boolean, List)。
     * 此方法会自动将 Float 转换为 Double，将 Short/Byte 转换为 Integer。
     * 遇到不支持的类型会抛出明确的异常，明示问题的出处。
     *
     * @param context 上下文（通常是字段名或操作名，用于报错提示）
     * @param value   传入的原始值
     * @return 转换后符合 Milvus 要求的安全类型
     */
    private Object sanitizeValue(String context, Object value) {
        if (value == null) {
            return null;
        }

        // 1. 处理集合/列表类型 (递归转换内部元素)
        if (value instanceof Collection) {
            List<Object> safeList = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                safeList.add(sanitizeValue(context + " (in Collection)", item));
            }
            return safeList;
        }

        // 2. 原生支持的类型，直接放行
        if (value instanceof String || value instanceof Integer ||
                value instanceof Long || value instanceof Double ||
                value instanceof Boolean) {
            return value;
        }

        // 3. 隐式转换：Milvus 不支持 Float，自动转为 Double
        return switch (value) {
            case Float v -> v.doubleValue();

            // 隐式转换：将短整型和字节型转为标准的 Integer
            case Short i -> i.intValue();
            case Byte b -> b.intValue();
            default -> throw new IllegalArgumentException(String.format(
                    "Milvus 过滤表达式构建失败: 字段或操作 [%s] 传入了不支持的数据类型 [%s]。\n" +
                            "提示: Milvus 模板参数仅支持 String, Integer, Long, Double, Boolean 及其集合。" +
                            "如果您使用了自定义对象或 Date 等复杂类型，请先将其转换为受支持的基础类型。",
                    context, value.getClass().getName()
            ));
        };

        // 4. 不支持的类型，抛出明示异常
    }

    // -------------------------
    // 表达式节点内部类
    // -------------------------

    /**
     * 表达式节点的抽象基类。
     */
    public static abstract class ExpressionNode {
        public abstract String toExpression();
    }

    /**
     * 简单的、不可再分的表达式节点。
     */
    public static class SimpleExpression extends ExpressionNode {
        private final String expression;

        public SimpleExpression(String expression) {
            this.expression = expression;
        }

        @Override
        public String toExpression() {
            return expression;
        }
    }

    /**
     * 由逻辑操作符（AND/OR）连接的复合表达式节点。
     */
    public static class LogicalExpression extends ExpressionNode {
        private final List<ExpressionNode> children;
        private final String operator;

        public LogicalExpression(String operator, List<ExpressionNode> children) {
            this.operator = operator;
            this.children = children;
        }

        @Override
        public String toExpression() {
            if (children.isEmpty()) return "";
            if (children.size() == 1) return children.get(0).toExpression();

            return children.stream()
                    .map(ExpressionNode::toExpression)
                    .map(expr -> "(" + expr + ")")
                    .collect(Collectors.joining(" " + operator + " "));
        }
    }

    // -------------------------
    // 静态工厂方法
    // -------------------------

    /**
     * 创建一个新的 {@code MilvusFilterTemplateBuilder} 实例。
     *
     * @return 新的构建器实例。
     */
    public static MilvusFilterTemplateBuilder create() {
        return new MilvusFilterTemplateBuilder();
    }

    // -------------------------
    // 字段引用
    // -------------------------

    /**
     * 创建一个字段引用，用于构建对 JSON 或数组字段的访问。
     *
     * @param fieldName 字段名称。
     * @return 字段引用对象。
     */
    public FieldRef field(String fieldName) {
        return new FieldRef(fieldName);
    }

    /**
     * 字段引用的辅助类，用于构建复杂的字段路径。
     */
    public static class FieldRef {
        private final String name;

        public FieldRef(String name) {
            this.name = name;
        }

        /**
         * 构建对嵌套 JSON 字段的引用。
         * 例如: {@code field("product").jsonKey("metadata", "price")} 会生成 {@code product["metadata"]["price"]}
         *
         * @param keys JSON 路径中的键。
         * @return 完整的字段引用字符串。
         */
        public String jsonKey(String... keys) {
            StringBuilder path = new StringBuilder(name);
            for (String key : keys) {
                path.append("[\"").append(key).append("\"]");
            }
            return path.toString();
        }

        /**
         * 构建对数组字段特定索引的引用。
         * 例如: {@code field("temps").atIndex(10)} 会生成 {@code temps[10]}
         *
         * @param index 数组索引。
         * @return 完整的字段引用字符串。
         */
        public String atIndex(int index) {
            return name + "[" + index + "]";
        }

        /**
         * 构建对动态 JSON 键的引用。
         *
         * @param key 动态键名。
         * @return 完整的字段引用字符串。
         */
        public String dynamicKey(String key) {
            return name + "[\"" + key + "\"]";
        }

        @Override
        public String toString() {
            return name;
        }
    }

// -------------------------
    // 核心：添加带占位符的条件
    // -------------------------

    /**
     * 核心内部方法：构建表达式并将参数存入参数 Map 中。
     * <p>
     * 该方法会自动生成参数占位符，避免直接拼接值导致的注入风险或格式错误。
     *
     * @param field    字段名称
     * @param operator 操作符 (e.g., "==", ">", "in")
     * @param value    参数值
     * @param isList   是否为列表类型（用于处理 IN 等操作）
     * @return 当前 Builder 对象
     */
    private MilvusFilterTemplateBuilder addCondition(String field, String operator, Object value, boolean isList) {
        String placeholderName = generatePlaceholderName(field);
        params.put(placeholderName, sanitizeValue(field, value));

        String expression = field + " " + operator + " {" + placeholderName + "}";
        expressionNodes.add(new SimpleExpression(expression));
        return this;
    }

    /**
     * 生成唯一的参数占位符名称。
     * <p>
     * 格式通常为: {@code param_字段名_计数器}
     *
     * @param field 字段名称
     * @return 唯一的占位符字符串
     */
    private String generatePlaceholderName(String field) {
        String cleanField = field.replaceAll("[^a-zA-Z0-9_]", "_");
        return "param_" + cleanField + "_" + (++placeholderCounter);
    }

    // -------------------------
    // 比较操作符
    // -------------------------

    /**
     * 添加等于 (Equals) 条件: {@code field == value}
     *
     * @param field 字段名称
     * @param value 目标值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder eq(String field, Object value) {
        return addCondition(field, "==", value, false);
    }

    /**
     * 添加不等于 (Not Equals) 条件: {@code field != value}
     *
     * @param field 字段名称
     * @param value 目标值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder ne(String field, Object value) {
        return addCondition(field, "!=", value, false);
    }

    /**
     * 添加大于 (Greater Than) 条件: {@code field > value}
     *
     * @param field 字段名称
     * @param value 比较值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder gt(String field, Object value) {
        return addCondition(field, ">", value, false);
    }

    /**
     * 添加大于等于 (Greater Than or Equals) 条件: {@code field >= value}
     *
     * @param field 字段名称
     * @param value 比较值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder ge(String field, Object value) {
        return addCondition(field, ">=", value, false);
    }

    /**
     * 添加小于 (Less Than) 条件: {@code field < value}
     *
     * @param field 字段名称
     * @param value 比较值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder lt(String field, Object value) {
        return addCondition(field, "<", value, false);
    }

    /**
     * 添加小于等于 (Less Than or Equals) 条件: {@code field <= value}
     *
     * @param field 字段名称
     * @param value 比较值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder le(String field, Object value) {
        return addCondition(field, "<=", value, false);
    }

    // -------------------------
    // 范围操作符
    // -------------------------

    /**
     * 添加 IN 条件，用于匹配字段值是否在指定集合中。
     * <p>
     * 表达式: {@code field in {value1, value2, ...}}
     *
     * @param field  字段名称
     * @param values 值集合
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder in(String field, Collection<?> values) {
        return addCondition(field, "in", values, true);
    }

    /**
     * 添加 LIKE 模糊匹配条件。
     * <p>
     * 注意：Milvus 的 LIKE 通常支持前缀匹配 (e.g., "prefix%")，具体支持取决于 Milvus 版本。
     *
     * @param field   字段名称
     * @param pattern 匹配模式字符串
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder like(String field, String pattern) {
        return addCondition(field, "LIKE", pattern, false);
    }

    // -------------------------
    // NULL 操作符
    // -------------------------

    /**
     * 添加 IS NULL 条件，检查字段是否为空。
     *
     * @param field 字段名称
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder isNull(String field) {
        expressionNodes.add(new SimpleExpression(field + " IS NULL"));
        return this;
    }

    /**
     * 添加 IS NOT NULL 条件，检查字段是否不为空。
     *
     * @param field 字段名称
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder isNotNull(String field) {
        expressionNodes.add(new SimpleExpression(field + " IS NOT NULL"));
        return this;
    }

    // -------------------------
    // JSON 专用操作符
    // -------------------------

    /**
     * 检查 JSON 字段是否包含特定的元素或结构。
     * <p>
     * 对应表达式: {@code JSON_CONTAINS(field, value)}
     *
     * @param field JSON 字段名称
     * @param value 需要检查包含的值或 JSON 对象
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder jsonContains(String field, Object value) {
        String placeholderName = STR."json_contains_val_\{++placeholderCounter}";
        params.put(placeholderName, sanitizeValue(field, value));
        expressionNodes.add(new SimpleExpression("JSON_CONTAINS(" + field + ", {" + placeholderName + "})"));
        return this;
    }

    /**
     * 检查 JSON 字段中是否存在指定的路径或键。
     * <p>
     * 注意: JSON_EXISTS 并非所有 Milvus 版本都支持，请根据实际情况使用。
     *
     * @param field JSON 字段或路径
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder jsonExists(String field) {
        expressionNodes.add(new SimpleExpression("JSON_EXISTS(" + field + ")"));
        return this;
    }

    /**
     * 检查 JSON 数组字段是否包含集合中的<b>所有</b>元素。
     * <p>
     * 对应表达式: {@code JSON_CONTAINS_ALL(field, values)}
     *
     * @param field  JSON 字段名称
     * @param values 必须包含的所有值的集合
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder jsonContainsAll(String field, Collection<?> values) {
        return addJsonFunction("JSON_CONTAINS_ALL", field, values);
    }

    /**
     * 检查 JSON 数组字段是否包含集合中的<b>任意</b>元素。
     * <p>
     * 对应表达式: {@code JSON_CONTAINS_ANY(field, values)}
     *
     * @param field  JSON 字段名称
     * @param values 包含任意一个即可匹配的值集合
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder jsonContainsAny(String field, Collection<?> values) {
        return addJsonFunction("JSON_CONTAINS_ANY", field, values);
    }

    /**
     * 内部辅助方法：构建 JSON 函数表达式。
     *
     * @param funcName 函数名称 (e.g., JSON_CONTAINS_ALL)
     * @param field    字段名称
     * @param values   集合值
     * @return 当前 Builder 对象
     */
    private MilvusFilterTemplateBuilder addJsonFunction(String funcName, String field, Collection<?> values) {
        String placeholderName = STR."\{funcName.toLowerCase()}_list_\{++placeholderCounter}";
        params.put(placeholderName, sanitizeValue(field, values));
        expressionNodes.add(new SimpleExpression(STR."\{funcName}(\{field}, {\{placeholderName}})"));
        return this;
    }

    // -------------------------
    // ARRAY 专用操作符
    // -------------------------

    /**
     * 检查数组字段是否包含指定的值。
     * <p>
     * 对应表达式: {@code ARRAY_CONTAINS(field, value)}
     *
     * @param field  数组字段名称
     * @param values 需要查找的值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder arrayContains(String field, Object values) {
        String placeholderName = "array_contains_val_" + (++placeholderCounter);
        params.put(placeholderName, sanitizeValue(field, values));
        expressionNodes.add(new SimpleExpression("ARRAY_CONTAINS(" + field + ", {" + placeholderName + "})"));
        return this;
    }

    /**
     * 检查数组字段是否包含集合中的<b>所有</b>元素。
     * <p>
     * 对应表达式: {@code ARRAY_CONTAINS_ALL(field, values)}
     *
     * @param field  数组字段名称
     * @param values 必须包含的所有值的集合
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder arrayContainsAll(String field, Collection<?> values) {
        return addArrayFunction("ARRAY_CONTAINS_ALL", field, values);
    }

    /**
     * 检查数组字段是否包含集合中的<b>任意</b>元素。
     * <p>
     * 对应表达式: {@code ARRAY_CONTAINS_ANY(field, values)}
     *
     * @param field  数组字段名称
     * @param values 包含任意一个即可匹配的值集合
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder arrayContainsAny(String field, Collection<?> values) {
        return addArrayFunction("ARRAY_CONTAINS_ANY", field, values);
    }

    /**
     * 内部辅助方法：构建数组函数表达式。
     *
     * @param funcName 函数名称
     * @param field    字段名称
     * @param values   集合值
     * @return 当前 Builder 对象
     */
    private MilvusFilterTemplateBuilder addArrayFunction(String funcName, String field, Collection<?> values) {
        String placeholderName = STR."\{funcName.toLowerCase()}_list_\{++placeholderCounter}";
        params.put(placeholderName, sanitizeValue(field, values));
        expressionNodes.add(new SimpleExpression(STR."\{funcName}(\{field}, {\{placeholderName}})"));
        return this;
    }

    /**
     * 根据数组长度进行过滤。
     * <p>
     * 示例: {@code arrayLength("tags", ">", 5)} 表示筛选 tags 数组长度大于 5 的记录。
     *
     * @param field    数组字段名称
     * @param operator 比较操作符 (e.g., "==", ">", "<")
     * @param length   目标长度
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder arrayLength(String field, String operator, int length) {
        String placeholderName = STR."array_len_\{++placeholderCounter}";
        params.put(placeholderName, sanitizeValue(field, length));
        expressionNodes.add(new SimpleExpression(STR."ARRAY_LENGTH(\{field}) \{operator} {\{placeholderName}}"));
        return this;
    }

    // -------------------------
    // CAST 函数支持 (非标准 Milvus 操作符，请谨慎使用)
    // -------------------------

    /**
     * 对字段进行类型转换后进行比较。
     * <p>
     * 注意：这是非标准操作，可能影响查询性能，请谨慎使用。
     *
     * @param field    字段名称
     * @param dataType 目标数据类型 (e.g., "int64")
     * @param operator 比较操作符
     * @param value    比较值
     * @return 当前 Builder 对象
     */
    public MilvusFilterTemplateBuilder cast(String field, String dataType, String operator, Object value) {
        String placeholderName = "cast_val_" + (++placeholderCounter);
        params.put(placeholderName, sanitizeValue(field, value));
        expressionNodes.add(new SimpleExpression("CAST(" + field + " AS " + dataType + ") " + operator + " {" + placeholderName + "}"));
        return this;
    }
    // -------------------------
    // 随机抽样 (Milvus 2.6+)
    // -------------------------

    /**
     * 添加随机抽样操作符。根据 Milvus 文档，此操作符必须与其他过滤条件通过 {@code AND} 逻辑结合使用。
     *
     * @param samplingFactor 采样因子，取值范围为 (0, 1)，例如 0.001 代表采样 0.1% 的数据。
     * @return 构建器实例。
     * @throws IllegalArgumentException 如果采样因子不在 (0, 1) 范围内。
     */
    public MilvusFilterTemplateBuilder randomSample(double samplingFactor) {
        if (samplingFactor <= 0 || samplingFactor >= 1) {
            throw new IllegalArgumentException("Sampling factor must be in (0, 1)");
        }
        String placeholderName = "sampling_factor_" + (++placeholderCounter);
        params.put(placeholderName, sanitizeValue("RANDOM_SAMPLE", samplingFactor));
        expressionNodes.add(new SimpleExpression("RANDOM_SAMPLE({" + placeholderName + "})"));
        return this;
    }

    // -------------------------
    // 逻辑操作符
    // -------------------------

    /**
     * 使用 {@code AND} 逻辑连接另一个过滤器构建器。
     * 该方法会将另一个构建器生成的完整表达式作为子句加入当前构建器。
     *
     * @param other 另一个过滤器构建器。
     * @return 当前构建器实例。
     */
    public MilvusFilterTemplateBuilder and(MilvusFilterTemplateBuilder other) {
        if (!other.expressionNodes.isEmpty()) {
            this.params.putAll(other.params);
            this.expressionNodes.add(new LogicalExpression("AND", new ArrayList<>(other.expressionNodes)));
        }
        return this;
    }

    /**
     * 使用 {@code OR} 逻辑连接另一个过滤器构建器。
     * 该方法会将另一个构建器生成的完整表达式作为子句加入当前构建器。
     *
     * @param other 另一个过滤器构建器。
     * @return 当前构建器实例。
     */
    public MilvusFilterTemplateBuilder or(MilvusFilterTemplateBuilder other) {
        if (!other.expressionNodes.isEmpty()) {
            this.params.putAll(other.params);
            this.expressionNodes.add(new LogicalExpression("OR", new ArrayList<>(other.expressionNodes)));
        }
        return this;
    }

    /**
     * 对传入的子表达式应用 {@code NOT} 逻辑。这是处理局部取反的推荐方式。
     *
     * @param sub 需要被取反的子表达式构建器。
     * @return 当前构建器实例。
     */
    public MilvusFilterTemplateBuilder not(MilvusFilterTemplateBuilder sub) {
        if (!sub.expressionNodes.isEmpty()) {
            this.params.putAll(sub.params);
            ExpressionNode notNode = new ExpressionNode() {
                @Override
                public String toExpression() {
                    String subExpr = new LogicalExpression("AND", sub.expressionNodes).toExpression();
                    return "NOT (" + subExpr + ")";
                }
            };
            this.expressionNodes.add(notNode);
        }
        return this;
    }

    /**
     * 对整个当前构建的表达式应用 {@code NOT} 逻辑。
     *
     * @return 当前构建器实例。
     */
    public MilvusFilterTemplateBuilder not() {
        this.isNegated = true;
        return this;
    }

    // -------------------------
// 短语匹配操作符 (Milvus 2.5.17+)
// -------------------------

    /**
     * 添加短语匹配条件，不允许位置偏移（精确匹配）。
     *
     * @param field VARCHAR字段名称
     * @param phrase 要搜索的短语
     * @return 构建器实例
     */
    public MilvusFilterTemplateBuilder phraseMatch(String field, String phrase) {
        String fieldPlaceholder = STR."phrase_field_\{++placeholderCounter}";
        String phrasePlaceholder = STR."phrase_text_\{++placeholderCounter}";

// 使用 sanitizeValue
        params.put(fieldPlaceholder, sanitizeValue(field, field));
        params.put(phrasePlaceholder, sanitizeValue(field, phrase));

        expressionNodes.add(new SimpleExpression(STR."PHRASE_MATCH({\{fieldPlaceholder}}, {\{phrasePlaceholder}})"));
        return this;
    }

    /**
     * 添加带有slop参数的短语匹配条件，允许一定的位置灵活性。
     *
     * @param field VARCHAR字段名称
     * @param phrase 要搜索的短语
     * @param slop 允许的最大位置偏移数
     * @return 构建器实例
     */
    public MilvusFilterTemplateBuilder phraseMatch(String field, String phrase, int slop) {
        String fieldPlaceholder = STR."phrase_field_\{++placeholderCounter}";
        String phrasePlaceholder = STR."phrase_text_\{++placeholderCounter}";
        String slopPlaceholder = STR."phrase_slop_\{++placeholderCounter}";

// 使用 sanitizeValue
        params.put(fieldPlaceholder, sanitizeValue(field, field));
        params.put(phrasePlaceholder, sanitizeValue(field, phrase));
        params.put(slopPlaceholder, sanitizeValue(field, slop));

        expressionNodes.add(new SimpleExpression(STR."PHRASE_MATCH({\{fieldPlaceholder}}, {\{phrasePlaceholder}}, {\{slopPlaceholder}})"));
        return this;
    }


    // -------------------------
    // 构建结果
    // -------------------------

    /**
     * 构建最终的过滤表达式字符串，其中包含占位符。
     *
     * @return 过滤表达式字符串。
     */
    public String buildExpression() {
        if (expressionNodes.isEmpty()) {
            return "";
        }
        String expr = new LogicalExpression("AND", expressionNodes).toExpression();

        if (isNegated) {
            expr = "NOT (" + expr + ")";
        }

        return expr;
    }

    /**
     * 构建参数映射，其中键是表达式中的占位符名称，值是实际的数据。
     *
     * @return 参数映射。
     */
    public Map<String, Object> buildParams() {
        return new LinkedHashMap<>(params);
    }
    // -------------------------
    // 新增：构建无变量的内联表达式 (Inline Expression)
    // -------------------------

    /**
     * 构建不带占位符的完整表达式字符串（值已直接嵌入字符串中）。
     * <p>
     * 注意：使用此方法生成的表达式无法利用 Milvus 的 Plan Cache，
     * 且在拼接字符串时存在微小的性能开销，建议仅在调试或不支持模板的场景下使用。
     *
     * @return 完整的 SQL-like 过滤表达式，例如 "age > 25"
     */
    public String buildInlineExpression() {
        // 1. 获取原始带占位符的模板，例如 "age > {param_1}"
        String template = buildExpression();
        if (template == null || template.isEmpty()) {
            return "";
        }

        // 2. 遍历参数，将占位符替换为实际值的字符串形式
        String inlineExpr = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "\\{" + entry.getKey() + "\\}"; // 正则转义花括号
            String stringValue = formatValueForMilvus(entry.getValue());
            inlineExpr = inlineExpr.replaceAll(placeholder, stringValue);
        }

        return inlineExpr;
    }



    /**
     * 构建结果的封装类，同时包含表达式和参数。
     */
    public static class TemplateResult {
        private final String expression;
        private final Map<String, Object> params;

        public TemplateResult(String expression, Map<String, Object> params) {
            this.expression = expression;
            this.params = params;
        }

        public String getExpression() { return expression; }
        public Map<String, Object> getParams() { return params; }

        /**
         * 重写 toString 方法，使参数输出格式符合 Milvus 文档风格 (使用冒号 ":")。
         *
         * @return 格式化的字符串。
         */
        @Override
        public String toString() {
            if (params.isEmpty()) {
                return "Expression: " + expression + "\nParams: {}";
            }

            StringBuilder paramsStr = new StringBuilder();
            paramsStr.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) {
                    paramsStr.append(", ");
                }
                // 使用冒号 ":" 分隔键和值
                paramsStr.append(entry.getKey()).append(": ").append(entry.getValue());
                first = false;
            }
            paramsStr.append("}");

            return "Expression: " + expression + "\nParams: " + paramsStr.toString();
        }

    }

    /**
     * 构建最终结果。
     *
     * @return 包含表达式和参数的 {@code TemplateResult} 对象。
     */
    public TemplateResult build() {
        return new TemplateResult(buildExpression(), buildParams());
    }

    // -------------------------
    // 便捷的静态方法
    // -------------------------

    /**
     * 创建一个检查字段非空的简单过滤器。
     *
     * @param field 字段名。
     * @return 构建结果。
     */
    public static TemplateResult where(String field) {
        return create().isNotNull(field).build();
    }

    /**
     * 创建一个新的构建器实例的便捷方法。
     *
     * @return 新的构建器实例。
     */
    public static MilvusFilterTemplateBuilder condition() {
        return create();
    }
    /**
     * 辅助方法：将 Java 对象格式化为 Milvus 支持的字面量字符串
     */
    private String formatValueForMilvus(Object value) {
        if (value == null) {
            return "null";
        }
        // 字符串：添加双引号 (Milvus 支持单引号或双引号，双引号更通用)
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        // 集合/数组：递归格式化，生成 [v1, v2]
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).stream()
                    .map(this::formatValueForMilvus)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        // 数字、布尔值：直接转字符串
        return String.valueOf(value);
    }
}
