package org.example.dbagent.prompt;

/**
 * MyBatis-Flex 框架代码生成提示词
 */
public class MyBatisFlexPrompt extends CodeGenPrompt {

    @Override
    public String getFrameworkName() {
        return "MyBatis-Flex";
    }

    @Override
    public String getFrameworkDescription() {
        return "轻量级、高性能的MyBatis增强框架";
    }

    @Override
    protected String buildFrameworkRules() {
        return """
                ## MyBatis-Flex 代码生成规则

                ### Entity类模板
                ```java
                package {basePackage}.entity;

                import com.mybatisflex.annotation.Column;
                import com.mybatisflex.annotation.Id;
                import com.mybatisflex.annotation.KeyType;
                import com.mybatisflex.annotation.Table;
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;

                /**
                 * {表注释}
                 * 对应数据库表：{表名}
                 */
                @Data
                @Table(value = "{表名}")
                @Builder
                @AllArgsConstructor
                @NoArgsConstructor
                public class {EntityName} {

                    // ========== AI-GENERATED-START: fields ==========
                    @Id(keyType = KeyType.Auto)
                    @Column(value = "id")
                    private Long id;

                    @Column(value = "字段名")
                    private String fieldName;
                    // ========== AI-GENERATED-END: fields ==========

                    // ========== HUMAN-AREA: custom ==========
                    // 在此区域添加自定义字段或方法
                    // ========== HUMAN-AREA-END: custom ==========
                }
                ```

                ### Mapper接口模板
                ```java
                package {basePackage}.mapper;

                import com.mybatisflex.core.BaseMapper;
                import org.apache.ibatis.annotations.Mapper;
                import {basePackage}.entity.{EntityName};

                /**
                 * {EntityName} Mapper
                 *
                 * ========== AI-GENERATED-START: mapper ==========
                 * 由DBAgent自动生成
                 * ========== AI-GENERATED-END: mapper ==========
                 */
                @Mapper
                public interface {EntityName}Mapper extends BaseMapper<{EntityName}> {
                }
                ```

                ### Mapper XML模板
                ```xml
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

                <mapper namespace="{basePackage}.mapper.{EntityName}Mapper">

                    <!-- AI-GENERATED-START: result-map -->
                    <resultMap id="BaseResultMap" type="{basePackage}.entity.{EntityName}">
                        <id column="id" property="id"/>
                        <result column="字段名" property="fieldName"/>
                    </resultMap>
                    <!-- AI-GENERATED-END: result-map -->

                    <!-- AI-GENERATED-START: base-column-list -->
                    <sql id="Base_Column_List">
                        id, 字段名
                    </sql>
                    <!-- AI-GENERATED-END: base-column-list -->

                    <!-- HUMAN-AREA: custom -->
                    <!-- 在此区域添加自定义SQL -->
                    <!-- HUMAN-AREA-END: custom -->

                </mapper>
                ```

                ### Service接口模板
                ```java
                package {basePackage}.service;

                import com.mybatisflex.core.service.IService;
                import {basePackage}.entity.{EntityName};

                /**
                 * {EntityName} Service接口
                 */
                public interface I{EntityName}Service extends IService<{EntityName}> {
                }
                ```

                ### ServiceImpl模板
                ```java
                package {basePackage}.service.impl;

                import com.mybatisflex.spring.service.impl.ServiceImpl;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.stereotype.Service;
                import {basePackage}.entity.{EntityName};
                import {basePackage}.mapper.{EntityName}Mapper;
                import {basePackage}.service.I{EntityName}Service;

                /**
                 * {EntityName} Service实现
                 */
                @Slf4j
                @Service
                public class {EntityName}ServiceImpl extends ServiceImpl<{EntityName}Mapper, {EntityName}> implements I{EntityName}Service {
                }
                ```

                ### 关键注解说明
                - `@Table` - 指定表名
                - `@Column` - 指定列名，支持 typeHandler 属性
                - `@Id` - 主键标识，KeyType.Auto 为自增
                - `BaseMapper` - MyBatis-Flex 的基础 Mapper 接口
                - `IService` / `ServiceImpl` - MyBatis-Flex 的 Service 接口和实现

                ### JSONB字段处理
                JSONB字段在Entity中的写法：
                ```java
                @Column(value = "settings", typeHandler = JsonbTypeHandler.class)
                private UserSettings settings;
                ```

                JSONB字段在Mapper XML中的写法：
                ```xml
                <result column="settings" property="settings" typeHandler="com.example.handler.JsonbTypeHandler"/>
                ```
                """;
    }
}
