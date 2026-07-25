package org.example.dbagent.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.DBAgentConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/**
 * 数据库连接管理器（支持 MySQL / PostgreSQL）
 * 使用独立JDBC连接，不依赖Spring DataSource
 */
@Slf4j
@Component
public class DatabaseConnectionManager {

    @Value("${dbagent.mysql.url:}")
    private String defaultMysqlUrl;

    @Value("${dbagent.mysql.username:}")
    private String defaultMysqlUsername;

    @Value("${dbagent.mysql.password:}")
    private String defaultMysqlPassword;

    @Value("${dbagent.postgres.url:}")
    private String defaultPgUrl;

    @Value("${dbagent.postgres.username:}")
    private String defaultPgUsername;

    @Value("${dbagent.postgres.password:}")
    private String defaultPgPassword;

    @Value("${dbagent.mysql.connect-timeout:10}")
    private int connectTimeout;

    @Value("${dbagent.mysql.query-timeout:30}")
    private int queryTimeout;

    public Connection getConnection(DBAgentConfig config) throws SQLException {
        String url = buildUrl(config);
        if (url == null || url.isBlank()) {
            throw new SQLException("数据库连接未配置");
        }

        String resolvedType = resolveDbType(config, url);
        String username = resolveUsername(config, resolvedType);
        String password = resolvePassword(config, resolvedType);

        Properties props = new Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", password != null ? password : "");
        props.setProperty("connectTimeout", String.valueOf(connectTimeout * 1000));
        props.setProperty("socketTimeout", String.valueOf(queryTimeout * 1000));

        log.debug("获取数据库连接: type={}, url={}", resolvedType, url);
        return DriverManager.getConnection(url, props);
    }

    /**
     * 使用指定URL和配置中的认证信息获取连接
     */
    public Connection getConnectionByUrl(String url, DBAgentConfig config) throws SQLException {
        String resolvedType = resolveDbType(config, url);
        String username = resolveUsername(config, resolvedType);
        String password = resolvePassword(config, resolvedType);

        Properties props = new Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", password != null ? password : "");
        props.setProperty("connectTimeout", String.valueOf(connectTimeout * 1000));
        props.setProperty("socketTimeout", String.valueOf(queryTimeout * 1000));

        log.debug("获取数据库连接(指定URL): url={}", url);
        return DriverManager.getConnection(url, props);
    }

    public String buildUrl(DBAgentConfig config) {
        String url = resolveUrl(config);
        if (url != null && !url.isBlank()) {
            return url;
        }

        String resolvedType = resolveDbType(config, null);
        String host = config.getMysqlHost() != null ? config.getMysqlHost() : "localhost";
        int port = config.getMysqlPort() != null ? config.getMysqlPort() : defaultPort(resolvedType);
        String database = config.getMysqlDatabase() != null ? config.getMysqlDatabase() : "";

        if ("postgresql".equals(resolvedType)) {
            return String.format("jdbc:postgresql://%s:%d/postgres?currentSchema=%s&connectTimeout=%d&socketTimeout=%d",
                    host, port, database, connectTimeout * 1000, queryTimeout * 1000);
        }

        return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, database);
    }

    public boolean testConnection(DBAgentConfig config) {
        try (Connection conn = getConnection(config)) {
            boolean valid = conn.isValid(5);
            log.info("数据库连接测试: {}", valid ? "成功" : "失败");
            return valid;
        } catch (SQLException e) {
            log.error("数据库连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        return getConnection(buildDefaultConfig());
    }

    public boolean testConnection() {
        return testConnection(buildDefaultConfig());
    }

    private DBAgentConfig buildDefaultConfig() {
        String host = envOr("DBAGENT_MYSQL_HOST", "localhost");
        String port = envOr("DBAGENT_MYSQL_PORT", "3306");
        String database = envOr("DBAGENT_MYSQL_DB", "agent_test");
        String user = envOr("DBAGENT_MYSQL_USER", "postgres");
        String password = envOr("DBAGENT_MYSQL_PASS", "postgresTest@123");

        return DBAgentConfig.builder()
                .dbType(envOr("DBAGENT_DB_TYPE", "mysql"))
                .mysqlHost(host)
                .mysqlPort(Integer.parseInt(port))
                .mysqlDatabase(database)
                .mysqlUser(user)
                .mysqlPassword(password)
                .build();
    }

    private String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String resolveDbType(DBAgentConfig config, String url) {
        String dbType = config.getDbType();
        if (dbType != null && !dbType.isBlank() && !"auto".equalsIgnoreCase(dbType)) {
            return dbType.toLowerCase(Locale.ROOT);
        }

        if (url != null && !url.isBlank()) {
            if (url.startsWith("jdbc:postgresql")) {
                return "postgresql";
            }
            if (url.startsWith("jdbc:mysql")) {
                return "mysql";
            }
        }

        if (defaultPgUrl != null && !defaultPgUrl.isBlank()) {
            return "postgresql";
        }

        return "mysql";
    }

    private String resolveUrl(DBAgentConfig config) {
        if (config.getMysqlHost() != null || config.getMysqlPort() != null || config.getMysqlDatabase() != null) {
            return buildUrlFromInputs(config, resolveDbType(config, null));
        }

        String resolvedType = resolveDbType(config, null);
        if ("postgresql".equals(resolvedType)) {
            return defaultPgUrl;
        }

        return defaultMysqlUrl;
    }

    private String buildUrlFromInputs(DBAgentConfig config, String resolvedType) {
        String host = config.getMysqlHost() != null ? config.getMysqlHost() : "localhost";
        int port = config.getMysqlPort() != null ? config.getMysqlPort() : defaultPort(resolvedType);
        String database = config.getMysqlDatabase() != null ? config.getMysqlDatabase() : "";

        if ("postgresql".equals(resolvedType)) {
            // PostgreSQL: database name is actually the schema, connect to postgres DB
            return String.format("jdbc:postgresql://%s:%d/postgres?currentSchema=%s&connectTimeout=%d&socketTimeout=%d",
                    host, port, database, connectTimeout * 1000, queryTimeout * 1000);
        }

        return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, database);
    }

    private String resolveUsername(DBAgentConfig config, String resolvedType) {
        if (config.getMysqlUser() != null && !config.getMysqlUser().isBlank()) {
            return config.getMysqlUser();
        }

        if ("postgresql".equals(resolvedType)) {
            return defaultPgUsername;
        }

        return defaultMysqlUsername;
    }

    private String resolvePassword(DBAgentConfig config, String resolvedType) {
        if (config.getMysqlPassword() != null && !config.getMysqlPassword().isBlank()) {
            return config.getMysqlPassword();
        }

        if ("postgresql".equals(resolvedType)) {
            return defaultPgPassword;
        }

        return defaultMysqlPassword;
    }

    private int defaultPort(String resolvedType) {
        if ("postgresql".equals(resolvedType)) {
            return 5432;
        }
        return 3306;
    }
}
