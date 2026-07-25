package org.example.common.commonUtils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.util.List;
import java.util.Map;

public class JsonToLLMUtils {
    /**
     * 针对 LLM 优化的 Fastjson2 写入特性配置：
     * 1. MapSortField: 让 Map 的 Key 按字母排序。
     * (这对 LLM 很重要：保证每次生成的 JSON 结构顺序一致，提高 Prompt 缓存命中率)
     * 2. WriteEnumsUsingName: 确保枚举输出为名称(String)而不是数字索引，LLM 读不懂数字。
     * 3. (默认特性说明): Fastjson2 默认不启用 ReferenceDetection，这意味着它会自动展开重复引用的对象，
     * 而不会输出 LLM 看不懂的 "$ref": "..."，所以不需要像 v1 那样显式禁用。
     */
    private static final JSONWriter.Feature[] LLM_FEATURES = {
            JSONWriter.Feature.MapSortField,
            JSONWriter.Feature.WriteEnumsUsingName,
            // 如果你的对象里 null 值有业务含义（比如 null 代表“未设置” vs 空字符串代表“空”），
            // 可以解开下面这行的注释，否则为了省 Token 默认不输出 null
            // JSONWriter.Feature.WriteNulls
    };

    /**
     * 指定日期格式：LLM 对 "yyyy-MM-dd HH:mm:ss" 的理解力远强于时间戳
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 反序列化
     */
    public static <T> T fromJson(String jsonStr, Class<T> clazz) {
        // 如果字符串不是以 } 结尾，说明还没生成完
        if (!jsonStr.trim().endsWith("}")) {
            throw new RuntimeException(STR."JSON数据不完整，解析中止！内容长度：\{jsonStr.length()}");
        }
        return JSON.parseObject(jsonStr, clazz,"yyyy-MM-dd HH:mm:ss");
    }
    /**
     * 序列化对象为适合 LLM 阅读的字符串
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "null";
        }
        // 使用 Fastjson2 的 API，同时指定日期格式和特性
        return JSON.toJSONString(object, DATE_FORMAT, LLM_FEATURES);
    }
    public static String toPrettyJson(Object object){
        if (object == null) {
            return "null";
        }
        // 使用 Fastjson2 的 API，同时指定日期格式和特性
        return convertToMarkdown(toJson(object));
    }
    /**
     * 将 JSON 转换为 Markdown 报告文本
     */
    public static String convertToMarkdown(String jsonStr) {
        Object json = JSON.parse(jsonStr);
        StringBuilder sb = new StringBuilder();
        buildMarkdown(json, sb, 1);
        return sb.toString();
    }

    private static void buildMarkdown(Object obj, StringBuilder sb, int level) {
        if (obj == null) return;

        // 【核心修复】：使用 Map<?, ?> 而不是 JSONObject
        // 这样编译器会把 Key 视为 Object，彻底避免 Integer 转 String 的报错
        if (obj instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                // 安全转换 Key，无论它是 Integer 还是 String 都能处理
                String keyStr = String.valueOf(k);

                // 判断 Value 是复杂对象还是简单值
                if (v instanceof Map || v instanceof List) {
                    // --- 复杂结构：使用 Markdown 标题 ---

                    // 限制标题最大层级为 6 (# -> ######)
                    int headerLevel = Math.min(level, 6);
                    String headerPrefix = "#".repeat(headerLevel);

                    // 标题前后加换行，确保 Markdown 解析正确
                    sb.append("\n")
                            .append(headerPrefix).append(" ").append(keyStr)
                            .append("\n\n");

                    // 递归处理子级，层级 + 1
                    buildMarkdown(v, sb, level + 1);
                } else {
                    // --- 简单属性：加粗属性名 + 换行 ---
                    sb.append("**").append(keyStr).append("**: ").append(v).append("\n");
                }
            });

        } else if (obj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map || item instanceof List) {
                    // 如果列表里是对象，直接递归，不加项目符号，靠标题区分
                    buildMarkdown(item, sb, level);
                    // 对象之间加一个空行做分割
                    sb.append("\n");
                } else {
                    // 如果列表里是纯文本，使用列表符号
                    sb.append("- ").append(item).append("\n");
                }
            }
        } else {
            // 纯文本兜底
            sb.append(obj).append("\n");
        }
    }

}
