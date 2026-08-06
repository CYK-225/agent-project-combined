package org.example.agentScope.util.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Skill 配置属性类
 * <p>
 * 从 application.yml 或 AI 中台配置的 YAML 文件中读取配置
 * 使用 @ConfigurationProperties 自动绑定
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * skill:
 *   config:
 *     database:
 *       host: localhost
 *       port: 3306
 *       name: menu_recommand
 *       user: root
 *       password: ""
 *     dataplatform:
 *       host: localhost
 *       port: 3306
 *       name: dp
 *       user: root
 *       password: ""
 *     conversation-db:
 *       host: localhost
 *       port: 3306
 *       name: conversation_db
 *       user: root
 *       password: ""
 *     wecom:
 *       enabled: true
 * </pre>
 *
 * @author AgentScope-Team
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "skill.config")
public class SkillConfigProperties {

    /**
     * 数据查询统计数据库配置
     */
    private DatabaseConfig database = new DatabaseConfig();

    /**
     * DataPlatform 数据库配置
     */
    private DatabaseConfig dataplatform = new DatabaseConfig();

    /**
     * 对话存储数据库配置
     */
    private DatabaseConfig conversationDb = new DatabaseConfig();

    /**
     * 企业微信配置
     */
    private WecomConfig wecom = new WecomConfig();

    /**
     * 数据库配置
     */
    @Data
    public static class DatabaseConfig {
        private String host = "localhost";
        private Integer port = 3306;
        private String name = "menu_recommand";
        private String user = "root";
        private String password = "";
    }

    /**
     * 企业微信配置
     */
    @Data
    public static class WecomConfig {
        private Boolean enabled = true;
    }

    /**
     * 将配置转换为扁平的 Map（用于生成 .env 文件）
     *
     * @return 配置映射
     */
    public Map<String, String> toFlatMap() {
        Map<String, String> config = new java.util.HashMap<>();

        // 数据库配置
        config.put("DB_HOST", database.getHost());
        config.put("DB_PORT", String.valueOf(database.getPort()));
        config.put("DB_NAME", database.getName());
        config.put("DB_USER", database.getUser());
        config.put("DB_PASSWORD", database.getPassword());

        // DataPlatform 配置
        config.put("DP_HOST", dataplatform.getHost());
        config.put("DP_PORT", String.valueOf(dataplatform.getPort()));
        config.put("DP_NAME", dataplatform.getName());
        config.put("DP_USER", dataplatform.getUser());
        config.put("DP_PASSWORD", dataplatform.getPassword());

        // 对话存储数据库配置
        config.put("CONVERSATION_DB_HOST", conversationDb.getHost());
        config.put("CONVERSATION_DB_PORT", String.valueOf(conversationDb.getPort()));
        config.put("CONVERSATION_DB_NAME", conversationDb.getName());
        config.put("CONVERSATION_DB_USER", conversationDb.getUser());
        config.put("CONVERSATION_DB_PASSWORD", conversationDb.getPassword());

        // 企业微信配置
        config.put("WECOM_ENABLED", String.valueOf(wecom.getEnabled()));

        return config;
    }

    /**
     * 将配置转换为分层结构（用于生成 config.yaml）
     *
     * @return 分层配置
     */
    public Map<String, Object> toNestedMap() {
        Map<String, Object> root = new java.util.LinkedHashMap<>();

        // 数据库配置
        Map<String, Object> db = new java.util.LinkedHashMap<>();
        db.put("host", database.getHost());
        db.put("port", database.getPort());
        db.put("name", database.getName());
        db.put("user", database.getUser());
        db.put("password", database.getPassword());
        root.put("database", db);

        // DataPlatform 配置
        Map<String, Object> dp = new java.util.LinkedHashMap<>();
        dp.put("host", dataplatform.getHost());
        dp.put("port", dataplatform.getPort());
        dp.put("name", dataplatform.getName());
        dp.put("user", dataplatform.getUser());
        dp.put("password", dataplatform.getPassword());
        root.put("dataplatform", dp);

        // 对话存储数据库配置
        Map<String, Object> convDb = new java.util.LinkedHashMap<>();
        convDb.put("host", conversationDb.getHost());
        convDb.put("port", conversationDb.getPort());
        convDb.put("name", conversationDb.getName());
        convDb.put("user", conversationDb.getUser());
        convDb.put("password", conversationDb.getPassword());
        root.put("conversation_db", convDb);

        // 企业微信配置
        Map<String, Object> wecomMap = new java.util.LinkedHashMap<>();
        wecomMap.put("enabled", wecom.getEnabled());
        root.put("wecom", wecomMap);

        return root;
    }
}
