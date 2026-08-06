package org.example.dbagent.prompt;

import org.example.common.proptcraft.PromptComponent;

/**
 * 代码生成提示词基类
 * 所有框架特定的提示词组件都继承此类
 */
public abstract class CodeGenPrompt extends PromptComponent {

    /**
     * 获取框架名称
     */
    public abstract String getFrameworkName();

    /**
     * 获取框架描述
     */
    public abstract String getFrameworkDescription();

    /**
     * 构建完整的系统提示词
     * 包含：角色定义 + 代码规范 + 框架特定规则
     */
    public String buildFullPrompt() {
        // 1. 通用角色定义
        of(buildRolePrompt());

        // 2. 通用代码规范
        of(buildCommonRules());

        // 3. 框架特定规则
        of(buildFrameworkRules());

        // 4. 文件生成规则
        of(buildFileGenerationRules());

        return render();
    }

    /**
     * 角色定义
     */
    protected String buildRolePrompt() {
        return """
                你是一个专业的Java代码生成助手，专门根据数据库表结构生成DAO层代码。
                当前使用的框架：%s (%s)
                """.formatted(getFrameworkName(), getFrameworkDescription());
    }

    /**
     * 通用代码规范（所有框架共享）
     */
    protected String buildCommonRules() {
        return """
                ## 通用代码规范

                ### 字段命名规则
                - 数据库下划线命名转Java驼峰命名
                - 例：user_name -> userName, created_at -> createdAt

                ### 特殊字段处理
                - created_at/create_time 字段添加自动插入时间
                - updated_at/update_time 字段添加自动插入和更新时间

                ### 数据库类型到Java类型映射
                #### 整数类型
                - BIGINT / BIGSERIAL / INT8 -> Long
                - INT / INTEGER / SERIAL / INT4 -> Integer
                - SMALLINT / INT2 -> Integer
                - TINYINT(1) -> Boolean

                #### 布尔类型
                - BOOL / BOOLEAN / BIT -> Boolean

                #### 浮点类型
                - FLOAT / FLOAT4 / REAL -> Float
                - DOUBLE / FLOAT8 / DOUBLE PRECISION -> Double
                - DECIMAL / NUMERIC -> java.math.BigDecimal

                #### 字符串类型
                - VARCHAR / CHAR / TEXT / JSON / JSONB / UUID / XML -> String

                #### 日期时间类型
                - DATE -> java.time.LocalDate
                - DATETIME / TIMESTAMP / TIMESTAMPTZ -> java.time.LocalDateTime
                - TIME / TIMETZ -> java.time.LocalTime

                #### 二进制类型
                - BLOB / BINARY / VARBINARY / BYTEA -> byte[]

                ### JSONB字段处理
                - JSONB字段使用表结构中提供的目标类（targetClass）作为字段类型
                - 需要配置TypeHandler进行序列化/反序列化
                """;
    }

    /**
     * 框架特定规则（子类实现）
     */
    protected abstract String buildFrameworkRules();

    /**
     * 文件生成规则
     */
    protected String buildFileGenerationRules() {
        return """
                ## file_write工具使用方法
                调用file_write工具，传入以下参数：
                - filePath: 文件完整路径
                - content: 文件内容

                请根据用户提供的表结构信息，生成对应的代码文件并使用file_write工具写入。
                """;
    }
}
