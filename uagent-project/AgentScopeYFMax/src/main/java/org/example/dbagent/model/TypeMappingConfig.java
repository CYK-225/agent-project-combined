package org.example.dbagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 类型映射配置
 * 从dbagent-type-mapping.yml读取配置
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "dbagent.type-mapping")
public class TypeMappingConfig {

    /** 基础类型映射: 数据库类型 -> Java类型 */
    private Map<String, String> basicMappings = new HashMap<>();

    /** JSONB字段自定义类映射: 表名.字段名 -> 完整类名 */
    private Map<String, String> jsonbMappings = new HashMap<>();

    /** TypeHandler映射: 数据库类型 -> TypeHandler全类名 */
    private Map<String, String> typeHandlers = new HashMap<>();

    /**
     * 获取Java类型（优先使用基础映射）
     * @param dataType 数据库类型
     * @return Java类型，如果未找到返回String
     */
    public String getJavaType(String dataType) {
        if (dataType == null) return "String";
        return basicMappings.getOrDefault(dataType.toUpperCase(), "String");
    }

    /**
     * 获取JSONB字段的目标类
     * @param tableName 表名
     * @param columnName 字段名
     * @return 目标类名，如果未找到返回默认值
     */
    public String getJsonbTargetClass(String tableName, String columnName) {
        if (tableName == null || columnName == null) {
            return getDefaultJsonbClass();
        }

        // 1. 精确匹配: 表名.字段名
        String exactKey = tableName + "." + columnName;
        if (jsonbMappings.containsKey(exactKey)) {
            return jsonbMappings.get(exactKey);
        }

        // 2. 通配符匹配: *.字段名
        String wildcardFieldKey = "*." + columnName;
        if (jsonbMappings.containsKey(wildcardFieldKey)) {
            return jsonbMappings.get(wildcardFieldKey);
        }

        // 3. 通配符匹配: 表名.*
        String wildcardTableKey = tableName + ".*";
        if (jsonbMappings.containsKey(wildcardTableKey)) {
            return jsonbMappings.get(wildcardTableKey);
        }

        // 4. 全局通配符: *
        return getDefaultJsonbClass();
    }

    /**
     * 获取默认的JSONB映射类
     */
    private String getDefaultJsonbClass() {
        return jsonbMappings.getOrDefault("DEFAULT", "com.fasterxml.jackson.databind.JsonNode");
    }

    /**
     * 获取TypeHandler类名
     * @param dataType 数据库类型（如JSONB、JSON）
     * @return TypeHandler全类名，如果未找到返回null
     */
    public String getTypeHandler(String dataType) {
        if (dataType == null) return null;
        return typeHandlers.get(dataType.toUpperCase());
    }

    /**
     * 判断是否为JSONB/JSON类型
     */
    public boolean isJsonType(String dataType) {
        if (dataType == null) return false;
        String upper = dataType.toUpperCase();
        return upper.equals("JSONB") || upper.equals("JSON");
    }
}
