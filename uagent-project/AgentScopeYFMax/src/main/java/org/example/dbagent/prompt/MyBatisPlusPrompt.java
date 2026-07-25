package org.example.dbagent.prompt;

/**
 * MyBatis-Plus 框架代码生成提示词
 */
public class MyBatisPlusPrompt extends CodeGenPrompt {

    @Override
    public String getFrameworkName() {
        return "MyBatis-Plus";
    }

    @Override
    public String getFrameworkDescription() {
        return "MyBatis的增强工具，只做增强不做改变";
    }

    @Override
    protected String buildFrameworkRules() {
        return """
                ## MyBatis-Plus 代码生成规则

                ### Entity类模板
                ```java
                package {basePackage}.entity;

                import com.baomidou.mybatisplus.annotation.IdType;
                import com.baomidou.mybatisplus.annotation.TableId;
                import com.baomidou.mybatisplus.annotation.TableField;
                import com.baomidou.mybatisplus.annotation.TableName;
                import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;

                /**
                 * {表注释}
                 * 对应数据库表：{表名}
                 */
                @Data
                @TableName(value = "{表名}")
                @Builder
                @AllArgsConstructor
                @NoArgsConstructor
                public class {EntityName} {

                    // ========== AI-GENERATED-START: fields ==========
                    @TableId(value = "id", type = IdType.AUTO)
                    private Long id;

                    @TableField("字段名")
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

                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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

                import com.baomidou.mybatisplus.extension.service.IService;
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

                import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

                ### 关键注解说明（MyBatis-Plus vs MyBatis-Flex 区别）
                | 功能 | MyBatis-Plus | MyBatis-Flex |
                |------|--------------|--------------|
                | 表名 | `@TableName` | `@Table` |
                | 主键 | `@TableId(type=IdType.AUTO)` | `@Id(keyType=KeyType.Auto)` |
                | 列名 | `@TableField` | `@Column` |
                | BaseMapper | `com.baomidou.mybatisplus.core.mapper.BaseMapper` | `com.mybatisflex.core.BaseMapper` |
                | IService | `com.baomidou.mybatisplus.extension.service.IService` | `com.mybatisflex.core.service.IService` |
                | ServiceImpl | `com.baomidou.mybatisplus.extension.service.impl.ServiceImpl` | `com.mybatisflex.spring.service.impl.ServiceImpl` |

                ### JSONB字段处理
                JSONB字段在Entity中的写法：
                ```java
                @TableField(value = "settings", typeHandler = JacksonTypeHandler.class)
                private UserSettings settings;
                ```

                注意：MyBatis-Plus 使用 `JacksonTypeHandler` 处理 JSON 字段，无需额外配置。
                """;
    }
}
