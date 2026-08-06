package org.example.dbagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.ColumnSchema;
import org.example.dbagent.model.DBAgentConfig;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.model.TypeMappingConfig;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL表结构读取器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaReaderService {

    private final DatabaseConnectionManager connectionManager;
    private final TypeMappingConfig typeMappingConfig;

    /**
     * 获取PostgreSQL schema列表
     */
    public List<String> listSchemas(DBAgentConfig config) throws SQLException {
        List<String> schemas = new ArrayList<>();
        // 构建不含 currentSchema 的URL，连接到 postgres 库获取所有 schema
        String host = config.getMysqlHost() != null ? config.getMysqlHost() : "localhost";
        int port = config.getMysqlPort() != null ? config.getMysqlPort() : 5432;
        String url = String.format("jdbc:postgresql://%s:%d/postgres?connectTimeout=10000&socketTimeout=30000", host, port);

        try (Connection conn = connectionManager.getConnectionByUrl(url, config);
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast') ORDER BY schema_name")) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
        }
        log.info("获取schema列表成功: 共{}个schema", schemas.size());
        return schemas;
    }

    /**
     * 使用前端配置获取所有表名
     * PostgreSQL根据schema过滤表
     */
    public List<String> listTables(DBAgentConfig config) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();

            // PostgreSQL需要根据schema过滤表
            String schemaPattern = null;
            if (isPostgreSQL(config)) {
                schemaPattern = config.getMysqlDatabase(); // PostgreSQL模式下mysqlDatabase存储的是schema
                log.info("PostgreSQL模式，根据schema过滤表: schema={}", schemaPattern);
            }

            try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        log.info("获取表列表成功: 共{}张表", tables.size());
        return tables.stream().sorted().toList();
    }

    /**
     * 判断是否为PostgreSQL数据库
     */
    private boolean isPostgreSQL(DBAgentConfig config) {
        if ("postgresql".equalsIgnoreCase(config.getDbType())) {
            return true;
        }
        // 通过URL判断
        String url = connectionManager.buildUrl(config);
        return url != null && url.startsWith("jdbc:postgresql");
    }

    /**
     * 使用默认配置获取所有表名
     */
    public List<String> listTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        log.info("获取表列表成功: 共{}张表", tables.size());
        return tables.stream().sorted().toList();
    }

    /**
     * 使用前端配置获取表结构
     */
    public TableSchema getTableSchema(DBAgentConfig config, String tableName) throws SQLException {
        try (Connection conn = connectionManager.getConnection(config)) {
            // PostgreSQL需要获取schema信息
            String schema = null;
            if (isPostgreSQL(config)) {
                schema = config.getMysqlDatabase();
            }
            return getTableSchemaFromConnection(conn, tableName, schema);
        }
    }

    /**
     * 使用默认配置获取表结构
     */
    public TableSchema getTableSchema(String tableName) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {
            return getTableSchemaFromConnection(conn, tableName, null);
        }
    }

    /**
     * 从连接中获取表结构
     */
    private TableSchema getTableSchemaFromConnection(Connection conn, String tableName, String schema) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();

        // 获取表注释
        String comment = getTableComment(meta, catalog, schema, tableName);
        // 获取主键
        List<String> pks = getPrimaryKeys(meta, catalog, schema, tableName);
        // 获取列信息
        List<ColumnSchema> cols = getColumns(meta, catalog, schema, tableName, pks);

        TableSchema schemaObj = TableSchema.builder()
                .tableName(tableName)
                .tableComment(comment)
                .columns(cols)
                .primaryKeys(pks)
                .build();

        log.info("获取表结构成功: 表={}, 列数={}", tableName, cols.size());
        return schemaObj;
    }

    /**
     * 获取表注释
     */
    private String getTableComment(DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return rs.getString("REMARKS");
            }
        }
        return null;
    }

    /**
     * 获取主键列
     */
    private List<String> getPrimaryKeys(DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        List<String> pks = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    /**
     * 获取列信息
     */
    private List<ColumnSchema> getColumns(DatabaseMetaData meta, String catalog, String schema, String tableName,
                                          List<String> primaryKeys) throws SQLException {
        List<ColumnSchema> cols = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                boolean nullable = "YES".equals(rs.getString("IS_NULLABLE"));
                boolean autoIncrement = "YES".equals(rs.getString("IS_AUTOINCREMENT"));
                String comment = rs.getString("REMARKS");

                // 获取Java类型
                String javaType = mapToJavaType(dataType, columnSize, decimalDigits);

                // JSONB特殊处理：检查是否有自定义类映射
                String typeHandler = null;
                String targetClass = null;
                if (typeMappingConfig.isJsonType(dataType)) {
                    targetClass = typeMappingConfig.getJsonbTargetClass(tableName, columnName);
                    typeHandler = typeMappingConfig.getTypeHandler(dataType);
                    log.debug("JSONB字段映射: {}.{} -> {} (handler: {})",
                            tableName, columnName, targetClass, typeHandler);
                }

                ColumnSchema col = ColumnSchema.builder()
                        .columnName(columnName)
                        .dataType(dataType)
                        .javaType(javaType)
                        .primaryKey(primaryKeys.contains(columnName))
                        .autoIncrement(autoIncrement)
                        .nullable(nullable)
                        .comment(comment)
                        .maxLength(columnSize)
                        .precision(columnSize)
                        .scale(decimalDigits)
                        .typeHandler(typeHandler)
                        .targetClass(targetClass)
                        .build();

                cols.add(col);
            }
        }
        return cols;
    }

    /**
     * 映射数据库类型到Java类型（支持MySQL和PostgreSQL）
     */
    private String mapToJavaType(String dataType, int precision, int scale) {
        if (dataType == null) {
            return "String";
        }

        String upperType = dataType.toUpperCase();

        // 整数类型（MySQL + PostgreSQL）
        if (upperType.equals("BIGINT") || upperType.equals("BIGSERIAL") || upperType.equals("INT8")) {
            return "Long";
        }
        if (upperType.equals("SERIAL") || upperType.equals("INT4")) {
            return "Integer";
        }
        if (upperType.equals("INT2") || upperType.equals("SMALLINT")) {
            return "Integer";
        }
        if (upperType.equals("INT") || upperType.equals("INTEGER") ||
            upperType.equals("MEDIUMINT") || upperType.equals("TINYINT")) {
            // TINYINT(1) 视为 Boolean
            if (upperType.equals("TINYINT") && precision == 1) {
                return "Boolean";
            }
            return "Integer";
        }

        // 浮点类型（MySQL + PostgreSQL）
        if (upperType.equals("FLOAT") || upperType.equals("FLOAT4") || upperType.equals("REAL")) {
            return "Float";
        }
        if (upperType.equals("DOUBLE") || upperType.equals("FLOAT8") || upperType.equals("DOUBLE PRECISION")) {
            return "Double";
        }
        if (upperType.equals("DECIMAL") || upperType.equals("NUMERIC")) {
            return "java.math.BigDecimal";
        }

        // 布尔类型（PostgreSQL）
        if (upperType.equals("BOOL") || upperType.equals("BOOLEAN") || upperType.equals("BIT")) {
            return "Boolean";
        }

        // 字符串类型（MySQL + PostgreSQL）
        if (upperType.equals("VARCHAR") || upperType.equals("CHAR") ||
            upperType.equals("BPCHAR") || upperType.equals("CHARACTER VARYING") ||
            upperType.equals("TEXT") || upperType.equals("LONGTEXT") ||
            upperType.equals("MEDIUMTEXT") || upperType.equals("TINYTEXT") ||
            upperType.equals("ENUM") || upperType.equals("SET") ||
            upperType.equals("UUID") || upperType.equals("JSON") || upperType.equals("JSONB") ||
            upperType.equals("XML") || upperType.equals("CIDR") || upperType.equals("INET") ||
            upperType.equals("MACADDR") || upperType.equals("MONEY")) {
            return "String";
        }

        // 日期时间类型（MySQL + PostgreSQL）
        if (upperType.equals("DATE")) {
            return "java.time.LocalDate";
        }
        if (upperType.equals("DATETIME") || upperType.equals("TIMESTAMP") ||
            upperType.equals("TIMESTAMPTZ") || upperType.equals("TIMESTAMP WITH TIME ZONE") ||
            upperType.equals("TIMESTAMP WITHOUT TIME ZONE")) {
            return "java.time.LocalDateTime";
        }
        if (upperType.equals("TIME") || upperType.equals("TIMETZ") ||
            upperType.equals("TIME WITH TIME ZONE") || upperType.equals("TIME WITHOUT TIME ZONE")) {
            return "java.time.LocalTime";
        }
        if (upperType.equals("YEAR")) {
            return "Integer";
        }

        // 二进制类型（MySQL + PostgreSQL）
        if (upperType.equals("BLOB") || upperType.equals("LONGBLOB") ||
            upperType.equals("MEDIUMBLOB") || upperType.equals("TINYBLOB") ||
            upperType.equals("BINARY") || upperType.equals("VARBINARY") ||
            upperType.equals("BYTEA")) {
            return "byte[]";
        }

        // 布尔类型
        if (upperType.equals("BIT")) {
            return "Boolean";
        }

        // JSON类型
        if (upperType.equals("JSON")) {
            return "String";
        }

        // 默认返回String
        log.warn("未知的MySQL类型: {}, 默认映射为String", dataType);
        return "String";
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection(DBAgentConfig config) {
        return connectionManager.testConnection(config);
    }

    /**
     * 测试默认配置连接
     */
    public boolean testConnection() {
        return connectionManager.testConnection();
    }
}
