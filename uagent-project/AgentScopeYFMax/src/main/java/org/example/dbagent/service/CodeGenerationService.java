package org.example.dbagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.example.dbagent.detector.SchemaDiffDetector;
import org.example.dbagent.merger.MergeResult;
import org.example.dbagent.merger.SmartMerger;
import org.example.dbagent.model.ChangeReport;
import org.example.dbagent.model.ColumnSchema;
import org.example.dbagent.model.DBAgentConfig;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.model.TypeMappingConfig;
import org.example.dbagent.parser.CodeParser;
import org.example.dbagent.parser.ParsedCode;
import org.example.dbagent.prompt.CodeGenPrompt;
import org.example.dbagent.prompt.CodeGenPromptFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 代码生成服务
 * 负责生成Entity、Mapper、Service代码
 * 使用AgentPoolManager调用DBCodeGenAgent来生成代码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final SchemaDiffDetector schemaDiffDetector;
    private final SmartMerger smartMerger;
    private final CodeParser codeParser;
    private final ObjectMapper objectMapper;
    private final AgentPoolManager agentPoolManager;
    private final TypeMappingConfig typeMappingConfig;

    @Value("${dbagent.codegen.base-package:org.example.repository.dal}")
    private String defaultBasePackage;

    @Value("${dbagent.git.local-path:./workspace/dbagent-repo}")
    private String localPath;

    /**
     * 使用前端配置生成或更新代码
     */
    public MergeResult generateOrUpdateCode(DBAgentConfig config, String tableName, TableSchema dbSchema, String mode) {
        String basePackage = config.getBasePackage() != null ? config.getBasePackage() : defaultBasePackage;
        return doGenerateOrUpdateCode(config, basePackage, tableName, dbSchema, mode);
    }

    /**
     * 使用默认配置生成或更新代码
     */
    public MergeResult generateOrUpdateCode(String tableName, TableSchema dbSchema, String mode) {
        return doGenerateOrUpdateCode(null, defaultBasePackage, tableName, dbSchema, mode);
    }

    /**
     * 生成或更新代码的核心逻辑
     */
    private MergeResult doGenerateOrUpdateCode(DBAgentConfig config, String basePackage, String tableName, TableSchema dbSchema, String mode) {
        log.info("开始生成代码: 表={}, 模式={}, 包名={}", tableName, mode, basePackage);

        try {
            if ("auto".equals(mode)) {
                mode = resolveAutoMode(basePackage, tableName);
                log.info("自动判断生成模式: 表={}, 结果={}", tableName, mode);
            }

            if ("incremental".equals(mode)) {
                // 增量更新模式 - 使用AI读取现有代码并修改
                return incrementalUpdateWithAgent(config, tableName, dbSchema, basePackage);
            } else {
                // 全量生成模式 - 使用Agent生成
                return fullGenerateWithAgent(config, basePackage, tableName, dbSchema);
            }
        } catch (Exception e) {
            log.error("代码生成失败", e);
            return MergeResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private String resolveAutoMode(String basePackage, String tableName) {
        String entityName = toEntityName(tableName);
        String entityDir = localPath + "/src/main/java/" + basePackage.replace('.', '/') + "/entity";
        Path entityPath = Paths.get(entityDir, entityName + ".java");

        if (Files.exists(entityPath)) {
            return "incremental";
        }

        return "full";
    }

    /**
     * 使用Agent全量生成代码
     */
    private MergeResult fullGenerateWithAgent(DBAgentConfig config, String basePackage, String tableName, TableSchema dbSchema) {
        log.info("使用Agent生成代码: 表={}, 框架={}", tableName, config.getFramework());

        // 获取框架类型
        String framework = config.getFramework();
        if (framework == null || framework.isBlank()) {
            framework = "mybatis-flex"; // 默认使用 mybatis-flex
        }

        // 构建提示词（包含框架信息）
        String prompt = buildCodeGenPrompt(basePackage, tableName, dbSchema, framework);

        // 调用Agent
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(prompt)
                .build();

        try {
            String response = agentPoolManager.getAgent("DBCodeGen")
                    .call(userMsg)
                    .block()
                    .getTextContent();

            log.info("Agent生成完成: {}", response);

            List<String> changes = new ArrayList<>();
            changes.add("生成Entity: " + toEntityName(tableName));
            changes.add("生成Mapper: " + toEntityName(tableName) + "Mapper");
            changes.add("生成Service: I" + toEntityName(tableName) + "Service");
            changes.add("生成ServiceImpl: " + toEntityName(tableName) + "ServiceImpl");

            return MergeResult.builder()
                    .success(true)
                    .mergedCode(response)
                    .updatedBlocks(List.of("fields", "constants", "methods"))
                    .preservedBlocks(List.of())
                    .changes(changes)
                    .build();

        } catch (Exception e) {
            log.error("Agent调用失败，回退到模板生成", e);
            // 回退到模板生成
            return fullGenerateWithTemplate(basePackage, tableName, dbSchema);
        }
    }

    /**
     * 使用模板全量生成代码（回退方案）
     */
    private MergeResult fullGenerateWithTemplate(String basePackage, String tableName, TableSchema dbSchema) {
        log.info("使用模板生成代码: 表={}", tableName);

        String entityName = toEntityName(tableName);
        String mapperName = entityName + "Mapper";
        String serviceName = entityName + "Service";
        String serviceImplName = entityName + "ServiceImpl";

        String entityDir = localPath + "/src/main/java/" + basePackage.replace('.', '/') + "/entity";
        String mapperDir = localPath + "/src/main/java/" + basePackage.replace('.', '/') + "/mapper";
        String serviceDir = localPath + "/src/main/java/" + basePackage.replace('.', '/') + "/service";
        String serviceImplDir = serviceDir + "/impl";
        String mapperXmlDir = localPath + "/src/main/resources/mapper";

        try {
            createDirectories(entityDir, mapperDir, serviceDir, serviceImplDir, mapperXmlDir);

            List<String> changes = new ArrayList<>();

            // 生成Entity
            String entityCode = generateEntityCode(basePackage, tableName, dbSchema, entityName);
            Path entityPath = Paths.get(entityDir, entityName + ".java");
            Files.writeString(entityPath, entityCode);
            changes.add("生成Entity: " + entityName);

            // 生成Mapper
            String mapperCode = generateMapperCode(basePackage, entityName, mapperName);
            Path mapperPath = Paths.get(mapperDir, mapperName + ".java");
            Files.writeString(mapperPath, mapperCode);
            changes.add("生成Mapper: " + mapperName);

            // 生成Mapper XML（包含TypeHandler配置）
            String mapperXmlCode = generateMapperXml(basePackage, tableName, dbSchema, entityName, mapperName);
            Path mapperXmlPath = Paths.get(mapperXmlDir, mapperName + ".xml");
            Files.writeString(mapperXmlPath, mapperXmlCode);
            changes.add("生成MapperXML: " + mapperName + ".xml");

            // 生成Service接口
            String serviceCode = generateServiceCode(basePackage, entityName, serviceName);
            Path servicePath = Paths.get(serviceDir, "I" + serviceName + ".java");
            Files.writeString(servicePath, serviceCode);
            changes.add("生成Service: I" + serviceName);

            // 生成ServiceImpl
            String serviceImplCode = generateServiceImplCode(basePackage, entityName, mapperName, serviceName, serviceImplName);
            Path serviceImplPath = Paths.get(serviceImplDir, serviceImplName + ".java");
            Files.writeString(serviceImplPath, serviceImplCode);
            changes.add("生成ServiceImpl: " + serviceImplName);

            log.info("模板生成完成: {}", changes);

            return MergeResult.builder()
                    .success(true)
                    .mergedCode(entityCode)
                    .updatedBlocks(List.of("fields", "constants", "methods"))
                    .preservedBlocks(List.of())
                    .changes(changes)
                    .build();

        } catch (Exception e) {
            log.error("模板生成失败", e);
            return MergeResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 构建代码生成提示词
     */
    private String buildCodeGenPrompt(String basePackage, String tableName, TableSchema dbSchema, String framework) {
        StringBuilder sb = new StringBuilder();

        // 获取框架提示词组件
        CodeGenPrompt prompt = CodeGenPromptFactory.getPrompt(framework);
        sb.append("请根据以下表结构信息生成").append(prompt.getFrameworkName()).append("的DAO层代码：\n\n");

        sb.append("## 表信息\n");
        sb.append("- 表名：").append(tableName).append("\n");
        sb.append("- 表注释：").append(dbSchema.getTableComment() != null ? dbSchema.getTableComment() : "无").append("\n");
        sb.append("- 基础包名：").append(basePackage).append("\n");
        sb.append("- 代码目录：").append(localPath).append("\n");
        sb.append("- 框架类型：").append(framework).append("\n\n");

        // 检查是否有JSONB字段需要TypeHandler
        boolean hasJsonb = dbSchema.getColumns().stream()
                .anyMatch(col -> typeMappingConfig.isJsonType(col.getDataType()));

        sb.append("## 列信息\n");
        if (hasJsonb) {
            sb.append("| 列名 | 数据类型 | Java类型 | TypeHandler | 目标类 | 主键 | 自增 | 可空 | 注释 |\n");
            sb.append("|------|----------|----------|-------------|--------|------|------|------|------|\n");
            for (ColumnSchema col : dbSchema.getColumns()) {
                String fieldType = col.getJavaType();
                if (col.getTargetClass() != null && !col.getTargetClass().isEmpty()
                        && typeMappingConfig.isJsonType(col.getDataType())) {
                    fieldType = col.getTargetClass();
                }
                sb.append("| ").append(col.getColumnName())
                        .append(" | ").append(col.getDataType())
                        .append(" | ").append(fieldType)
                        .append(" | ").append(col.getTypeHandler() != null ? col.getTypeHandler() : "-")
                        .append(" | ").append(col.getTargetClass() != null ? col.getTargetClass() : "-")
                        .append(" | ").append(col.isPrimaryKey() ? "是" : "否")
                        .append(" | ").append(col.isAutoIncrement() ? "是" : "否")
                        .append(" | ").append(col.isNullable() ? "是" : "否")
                        .append(" | ").append(col.getComment() != null ? col.getComment() : "-")
                        .append(" |\n");
            }
        } else {
            sb.append("| 列名 | 数据类型 | Java类型 | 主键 | 自增 | 可空 | 注释 |\n");
            sb.append("|------|----------|----------|------|------|------|------|\n");
            for (ColumnSchema col : dbSchema.getColumns()) {
                sb.append("| ").append(col.getColumnName())
                        .append(" | ").append(col.getDataType())
                        .append(" | ").append(col.getJavaType())
                        .append(" | ").append(col.isPrimaryKey() ? "是" : "否")
                        .append(" | ").append(col.isAutoIncrement() ? "是" : "否")
                        .append(" | ").append(col.isNullable() ? "是" : "否")
                        .append(" | ").append(col.getComment() != null ? col.getComment() : "-")
                        .append(" |\n");
            }
        }

        sb.append("\n## 需要生成的文件\n");
        String entityName = toEntityName(tableName);
        String mapperName = entityName + "Mapper";
        String pkgPath = basePackage.replace('.', '/');
        sb.append("1. ").append(localPath).append("/src/main/java/").append(pkgPath).append("/entity/").append(entityName).append(".java\n");
        sb.append("2. ").append(localPath).append("/src/main/java/").append(pkgPath).append("/mapper/").append(mapperName).append(".java\n");
        sb.append("3. ").append(localPath).append("/src/main/resources/mapper/").append(mapperName).append(".xml\n");
        sb.append("4. ").append(localPath).append("/src/main/java/").append(pkgPath).append("/service/I").append(entityName).append("Service.java\n");
        sb.append("5. ").append(localPath).append("/src/main/java/").append(pkgPath).append("/service/impl/").append(entityName).append("ServiceImpl.java\n");

        if (hasJsonb) {
            sb.append("\n## JSONB字段处理说明\n");
            sb.append("- JSONB字段需要使用TypeHandler进行序列化/反序列化\n");
            sb.append("- Entity的注解需要添加typeHandler属性\n");
            sb.append("- Mapper XML的ResultMap中需要配置typeHandler\n");
            sb.append("- 字段类型使用目标类（targetClass）而非String\n");
        }

        sb.append("\n请使用file_write工具将生成的代码写入到对应的文件路径。");

        return sb.toString();
    }

    /**
     * 增量更新 - 使用AI读取现有代码并智能修改
     */
    private MergeResult incrementalUpdateWithAgent(DBAgentConfig config, String tableName, TableSchema dbSchema, String basePackage) {
        log.info("使用AI增量更新: 表={}", tableName);

        String entityName = toEntityName(tableName);
        String mapperName = entityName + "Mapper";
        String pkgPath = basePackage.replace('.', '/');

        // 读取所有现有代码文件
        Map<String, String> existingFiles = readExistingFiles(pkgPath, entityName, mapperName);

        // 如果Entity文件不存在，转为全量生成
        if (!existingFiles.containsKey("entity")) {
            log.info("Entity文件不存在，转为全量生成: {}", entityName);
            return fullGenerateWithAgent(config, basePackage, tableName, dbSchema);
        }

        // 获取框架类型
        String framework = config.getFramework();
        if (framework == null || framework.isBlank()) {
            framework = "mybatis-flex";
        }

        // 构建增量更新提示词
        String prompt = buildIncrementalPrompt(basePackage, tableName, dbSchema, framework, existingFiles);

        // 调用Agent
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(prompt)
                .build();

        try {
            String response = agentPoolManager.getAgent("DBCodeGen")
                    .call(userMsg)
                    .block()
                    .getTextContent();

            log.info("AI增量更新完成: {}", response);

            // 解析AI返回的文件内容并保存
            List<String> changes = saveGeneratedFiles(response, basePackage, tableName, entityName, mapperName);

            return MergeResult.builder()
                    .success(true)
                    .mergedCode(response)
                    .updatedBlocks(List.of("fields", "constants", "methods"))
                    .preservedBlocks(List.of())
                    .changes(changes)
                    .build();

        } catch (Exception e) {
            log.error("AI增量更新失败，回退到模板生成", e);
            return fullGenerateWithTemplate(basePackage, tableName, dbSchema);
        }
    }

    /**
     * 读取所有现有代码文件
     */
    private Map<String, String> readExistingFiles(String pkgPath, String entityName, String mapperName) {
        Map<String, String> files = new HashMap<>();

        // Entity
        Path entityPath = Paths.get(localPath, "src/main/java", pkgPath, "entity", entityName + ".java");
        if (Files.exists(entityPath)) {
            try { files.put("entity", Files.readString(entityPath)); } catch (IOException e) { log.warn("读取Entity文件失败", e); }
        }

        // Mapper接口
        Path mapperPath = Paths.get(localPath, "src/main/java", pkgPath, "mapper", mapperName + ".java");
        if (Files.exists(mapperPath)) {
            try { files.put("mapper", Files.readString(mapperPath)); } catch (IOException e) { log.warn("读取Mapper文件失败", e); }
        }

        // Mapper XML
        Path mapperXmlPath = Paths.get(localPath, "src/main/resources/mapper", mapperName + ".xml");
        if (Files.exists(mapperXmlPath)) {
            try { files.put("mapperXml", Files.readString(mapperXmlPath)); } catch (IOException e) { log.warn("读取MapperXML文件失败", e); }
        }

        // Service接口
        Path servicePath = Paths.get(localPath, "src/main/java", pkgPath, "service", "I" + entityName.replace("Entity", "") + "Service.java");
        if (Files.exists(servicePath)) {
            try { files.put("service", Files.readString(servicePath)); } catch (IOException e) { log.warn("读取Service文件失败", e); }
        }

        // ServiceImpl
        Path serviceImplPath = Paths.get(localPath, "src/main/java", pkgPath, "service/impl", entityName.replace("Entity", "") + "ServiceImpl.java");
        if (Files.exists(serviceImplPath)) {
            try { files.put("serviceImpl", Files.readString(serviceImplPath)); } catch (IOException e) { log.warn("读取ServiceImpl文件失败", e); }
        }

        log.info("读取现有文件: {}", files.keySet());
        return files;
    }

    /**
     * 构建增量更新提示词
     */
    private String buildIncrementalPrompt(String basePackage, String tableName, TableSchema dbSchema,
                                          String framework, Map<String, String> existingFiles) {
        StringBuilder sb = new StringBuilder();

        // 获取框架提示词组件
        CodeGenPrompt prompt = CodeGenPromptFactory.getPrompt(framework);

        sb.append("## 任务说明\n");
        sb.append("你需要根据最新的数据库表结构，修改现有的代码文件。\n");
        sb.append("- 框架类型：").append(prompt.getFrameworkName()).append("\n");
        sb.append("- 基础包名：").append(basePackage).append("\n");
        sb.append("- 代码目录：").append(localPath).append("\n\n");

        // 表结构信息
        sb.append("## 最新表结构\n");
        sb.append("- 表名：").append(tableName).append("\n");
        sb.append("- 表注释：").append(dbSchema.getTableComment() != null ? dbSchema.getTableComment() : "无").append("\n\n");

        // 检查是否有JSONB字段
        boolean hasJsonb = dbSchema.getColumns().stream()
                .anyMatch(col -> typeMappingConfig.isJsonType(col.getDataType()));

        sb.append("### 列信息\n");
        if (hasJsonb) {
            sb.append("| 列名 | 数据类型 | Java类型 | TypeHandler | 目标类 | 主键 | 自增 | 可空 | 注释 |\n");
            sb.append("|------|----------|----------|-------------|--------|------|------|------|------|\n");
            for (ColumnSchema col : dbSchema.getColumns()) {
                String fieldType = col.getJavaType();
                if (col.getTargetClass() != null && !col.getTargetClass().isEmpty()
                        && typeMappingConfig.isJsonType(col.getDataType())) {
                    fieldType = col.getTargetClass();
                }
                sb.append("| ").append(col.getColumnName())
                        .append(" | ").append(col.getDataType())
                        .append(" | ").append(fieldType)
                        .append(" | ").append(col.getTypeHandler() != null ? col.getTypeHandler() : "-")
                        .append(" | ").append(col.getTargetClass() != null ? col.getTargetClass() : "-")
                        .append(" | ").append(col.isPrimaryKey() ? "是" : "否")
                        .append(" | ").append(col.isAutoIncrement() ? "是" : "否")
                        .append(" | ").append(col.isNullable() ? "是" : "否")
                        .append(" | ").append(col.getComment() != null ? col.getComment() : "-")
                        .append(" |\n");
            }
        } else {
            sb.append("| 列名 | 数据类型 | Java类型 | 主键 | 自增 | 可空 | 注释 |\n");
            sb.append("|------|----------|----------|------|------|------|------|\n");
            for (ColumnSchema col : dbSchema.getColumns()) {
                sb.append("| ").append(col.getColumnName())
                        .append(" | ").append(col.getDataType())
                        .append(" | ").append(col.getJavaType())
                        .append(" | ").append(col.isPrimaryKey() ? "是" : "否")
                        .append(" | ").append(col.isAutoIncrement() ? "是" : "否")
                        .append(" | ").append(col.isNullable() ? "是" : "否")
                        .append(" | ").append(col.getComment() != null ? col.getComment() : "-")
                        .append(" |\n");
            }
        }

        // 现有代码
        sb.append("\n## 现有代码文件\n");
        sb.append("请根据上述表结构，修改以下代码文件。只修改需要变更的部分，保留自定义代码。\n\n");

        if (existingFiles.containsKey("entity")) {
            sb.append("### Entity文件\n```java\n").append(existingFiles.get("entity")).append("\n```\n\n");
        }
        if (existingFiles.containsKey("mapper")) {
            sb.append("### Mapper接口文件\n```java\n").append(existingFiles.get("mapper")).append("\n```\n\n");
        }
        if (existingFiles.containsKey("mapperXml")) {
            sb.append("### Mapper XML文件\n```xml\n").append(existingFiles.get("mapperXml")).append("\n```\n\n");
        }
        if (existingFiles.containsKey("service")) {
            sb.append("### Service接口文件\n```java\n").append(existingFiles.get("service")).append("\n```\n\n");
        }
        if (existingFiles.containsKey("serviceImpl")) {
            sb.append("### ServiceImpl文件\n```java\n").append(existingFiles.get("serviceImpl")).append("\n```\n\n");
        }

        // 修改要求
        sb.append("## 修改要求\n");
        sb.append("### 1. Entity文件修改\n");
        sb.append("- 根据表结构更新字段列表：\n");
        sb.append("  - 添加新字段（包含注释、注解）\n");
        sb.append("  - 删除已不存在的字段\n");
        sb.append("  - 更新类型变更的字段\n");
        sb.append("- 保留import语句（可添加新import）\n");
        sb.append("- 保留类注释中的AI-GENERATED标记\n");
        sb.append("- 保留HUMAN-AREA中的自定义代码\n");

        sb.append("\n### 2. Mapper XML文件修改\n");
        sb.append("- 更新ResultMap中的字段映射\n");
        sb.append("- 更新Base_Column_List中的列名\n");
        sb.append("- 保留自定义SQL（HUMAN-AREA部分）\n");

        if (hasJsonb) {
            sb.append("\n### 3. JSONB字段处理要求\n");
            sb.append("- Entity中JSONB字段使用目标类（targetClass）作为字段类型\n");
            sb.append("- Entity注解需要添加typeHandler属性，格式：@Column(value = \"字段名\", typeHandler = XxxTypeHandler.class)\n");
            sb.append("- Mapper XML的ResultMap中需要配置typeHandler属性\n");
            sb.append("- 字段类型使用配置的目标类（如JSONObject、JSONArray或自定义类）\n");
        }

        sb.append("\n### 4. 输出格式要求\n");
        sb.append("- 使用file_write工具将修改后的代码写入对应文件\n");
        sb.append("- 每个文件单独调用一次file_write\n");
        sb.append("- 确保代码格式正确，缩进一致\n");

        // 添加修改示例
        sb.append("\n## 修改示例（仅供参考）\n");
        sb.append("```java\n");
        sb.append("// 添加新字段示例\n");
        sb.append("    /** 新字段注释 */\n");
        sb.append("    @Column(value = \"new_field\")\n");
        sb.append("    private String newField;\n\n");
        sb.append("// JSONB字段示例\n");
        sb.append("    /** JSONB字段 */\n");
        sb.append("    @Column(value = \"json_data\", typeHandler = JacksonTypeHandler.class)\n");
        sb.append("    private JSONObject jsonData;\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 保存AI生成的文件
     */
    private List<String> saveGeneratedFiles(String response, String basePackage, String tableName,
                                            String entityName, String mapperName) {
        List<String> changes = new ArrayList<>();

        // 这里简化处理，实际应该解析AI返回的内容并保存到对应文件
        // 由于AI会直接调用file_write工具，所以这里只记录变更
        changes.add("更新Entity: " + entityName);
        changes.add("更新Mapper: " + mapperName);
        changes.add("更新Service: I" + entityName.replace("Entity", "") + "Service");

        return changes;
    }

    /**
     * 增量更新（旧版本，保留兼容）
     */
    private MergeResult incrementalUpdate(String tableName, TableSchema dbSchema, String basePackage) throws IOException {
        String entityName = toEntityName(tableName);
        String entityDir = localPath + "/src/main/java/" + basePackage.replace('.', '/') + "/entity";
        Path entityPath = Paths.get(entityDir, entityName + ".java");

        // 检查文件是否存在
        if (!Files.exists(entityPath)) {
            log.info("Entity文件不存在，转为全量生成: {}", entityName);
            return fullGenerateWithTemplate(basePackage, tableName, dbSchema);
        }

        // 读取现有代码
        String existingCode = Files.readString(entityPath);
        ParsedCode parsedCode = codeParser.parse(existingCode);

        // 如果没有AI标记，说明是旧代码，转为全量生成
        if (parsedCode.getAiBlocks().isEmpty()) {
            log.info("现有代码缺少AI标记，转为全量生成: {}", entityName);
            return fullGenerateWithTemplate(basePackage, tableName, dbSchema);
        }

        // 检测变更
        ChangeReport changeReport = schemaDiffDetector.detect(dbSchema, parsedCode);

        // 如果没有变更，直接返回
        if (changeReport.getChanges().isEmpty()) {
            log.info("没有检测到变更: {}", tableName);
            return MergeResult.builder()
                    .success(true)
                    .mergedCode(existingCode)
                    .updatedBlocks(List.of())
                    .preservedBlocks(List.of("fields", "constants", "methods"))
                    .changes(List.of("无变更"))
                    .build();
        }

        // 智能合并
        MergeResult mergeResult = smartMerger.merge(existingCode, dbSchema, changeReport);

        // 保存合并后的代码
        if (mergeResult.isSuccess()) {
            Files.writeString(entityPath, mergeResult.getMergedCode());
            log.info("增量更新完成: {}, 变更数={}", tableName, changeReport.getChanges().size());
        }

        return mergeResult;
    }

    /**
     * 生成Entity代码
     */
    private String generateEntityCode(String basePackage, String tableName, TableSchema dbSchema, String entityName) {
        StringBuilder sb = new StringBuilder();

        // package
        sb.append("package ").append(basePackage).append(".entity;\n\n");

        // imports
        sb.append("import com.mybatisflex.annotation.Column;\n");
        sb.append("import com.mybatisflex.annotation.Id;\n");
        sb.append("import com.mybatisflex.annotation.KeyType;\n");
        sb.append("import com.mybatisflex.annotation.Table;\n");
        sb.append("import lombok.AllArgsConstructor;\n");
        sb.append("import lombok.Builder;\n");
        sb.append("import lombok.Data;\n");
        sb.append("import lombok.NoArgsConstructor;\n\n");

        // 需要特殊的import
        boolean needBigDecimal = false;
        boolean needLocalDate = false;
        boolean needLocalDateTime = false;
        boolean needLocalTime = false;

        for (ColumnSchema col : dbSchema.getColumns()) {
            if ("java.math.BigDecimal".equals(col.getJavaType())) needBigDecimal = true;
            if ("java.time.LocalDate".equals(col.getJavaType())) needLocalDate = true;
            if ("java.time.LocalDateTime".equals(col.getJavaType())) needLocalDateTime = true;
            if ("java.time.LocalTime".equals(col.getJavaType())) needLocalTime = true;
        }

        if (needBigDecimal) sb.append("import java.math.BigDecimal;\n");
        if (needLocalDate) sb.append("import java.time.LocalDate;\n");
        if (needLocalDateTime) sb.append("import java.time.LocalDateTime;\n");
        if (needLocalTime) sb.append("import java.time.LocalTime;\n");

        sb.append("\n");

        // class comment
        sb.append("/**\n");
        sb.append(" * ").append(dbSchema.getTableComment() != null ? dbSchema.getTableComment() : entityName).append("\n");
        sb.append(" * 对应数据库表：").append(tableName).append("\n");
        sb.append(" *\n");
        sb.append(" * ========== AI-GENERATED-START: entity ==========\n");
        sb.append(" * 由DBAgent自动生成，请勿手动修改此区域\n");
        sb.append(" * ========== AI-GENERATED-END: entity ==========\n");
        sb.append(" */\n");

        // class annotations
        sb.append("@Data\n");
        sb.append("@Table(value = \"").append(tableName).append("\")\n");
        sb.append("@Builder\n");
        sb.append("@AllArgsConstructor\n");
        sb.append("@NoArgsConstructor\n");

        // class declaration
        sb.append("public class ").append(entityName).append(" {\n\n");

        // fields
        sb.append("    // ========== AI-GENERATED-START: fields ==========\n");
        for (ColumnSchema col : dbSchema.getColumns()) {
            String camelName = toCamelCase(col.getColumnName());

            // comment
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                sb.append("\n    /** ").append(col.getComment()).append(" */\n");
            }

            // @Id annotation
            if (col.isPrimaryKey()) {
                sb.append("    @Id(keyType = ").append(col.isAutoIncrement() ? "KeyType.Auto" : "KeyType.None").append(")\n");
            }

            // @Column annotation
            sb.append("    @Column(value = \"").append(col.getColumnName()).append("\"");
            if ("created_at".equals(col.getColumnName()) || "create_time".equals(col.getColumnName())) {
                sb.append(", onInsertValue = \"now()\"");
            } else if ("updated_at".equals(col.getColumnName()) || "update_time".equals(col.getColumnName())) {
                sb.append(", onInsertValue = \"now()\", onUpdateValue = \"now()\"");
            }
            // JSONB字段添加TypeHandler
            if (col.getTypeHandler() != null && !col.getTypeHandler().isEmpty()) {
                sb.append(", typeHandler = ").append(col.getTypeHandler()).append(".class");
            }
            sb.append(")\n");

            // field declaration
            String fieldType = col.getJavaType();
            // JSONB字段使用目标类作为字段类型
            if (col.getTargetClass() != null && !col.getTargetClass().isEmpty()
                    && typeMappingConfig.isJsonType(col.getDataType())) {
                fieldType = col.getTargetClass();
            }
            sb.append("    private ").append(fieldType).append(" ").append(camelName).append(";\n");
        }
        sb.append("    // ========== AI-GENERATED-END: fields ==========\n\n");

        // human area
        sb.append("    // ========== HUMAN-AREA: custom ==========\n");
        sb.append("    // 在此区域添加自定义字段或方法\n");
        sb.append("    // ========== HUMAN-AREA-END: custom ==========\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成Mapper代码
     */
    private String generateMapperCode(String basePackage, String entityName, String mapperName) {
        return """
                package %s.mapper;

                import com.mybatisflex.core.BaseMapper;
                import org.apache.ibatis.annotations.Mapper;
                import %s.entity.%s;

                /**
                 * %s Mapper
                 *
                 * ========== AI-GENERATED-START: mapper ==========
                 * 由DBAgent自动生成
                 * ========== AI-GENERATED-END: mapper ==========
                 */
                @Mapper
                public interface %s extends BaseMapper<%s> {
                }
                """.formatted(basePackage, basePackage, entityName, entityName, mapperName, entityName);
    }

    /**
     * 生成Service接口代码
     */
    private String generateServiceCode(String basePackage, String entityName, String serviceName) {
        return """
                package %s.service;

                import com.mybatisflex.core.service.IService;
                import %s.entity.%s;

                /**
                 * %s Service接口
                 */
                public interface I%s extends IService<%s> {
                }
                """.formatted(basePackage, basePackage, entityName, entityName, serviceName, entityName);
    }

    /**
     * 生成ServiceImpl代码
     */
    private String generateServiceImplCode(String basePackage, String entityName, String mapperName,
                                           String serviceName, String serviceImplName) {
        return """
                package %s.service.impl;

                import com.mybatisflex.spring.service.impl.ServiceImpl;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.stereotype.Service;
                import %s.entity.%s;
                import %s.mapper.%s;
                import %s.service.I%s;

                /**
                 * %s Service实现
                 */
                @Slf4j
                @Service
                public class %s extends ServiceImpl<%s, %s> implements I%s {
                }
                """.formatted(basePackage, basePackage, entityName, basePackage, mapperName,
                        basePackage, serviceName, entityName, serviceImplName, mapperName,
                        entityName, serviceName);
    }

    /**
     * 生成MyBatis Mapper XML文件
     * 包含ResultMap定义和TypeHandler配置
     */
    private String generateMapperXml(String basePackage, String tableName, TableSchema dbSchema,
                                     String entityName, String mapperName) {
        StringBuilder sb = new StringBuilder();

        // XML header
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n");
        sb.append("        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n\n");

        // mapper namespace
        sb.append("<mapper namespace=\"").append(basePackage).append(".mapper.").append(mapperName).append("\">\n\n");

        // ResultMap（包含TypeHandler配置）
        sb.append("    <!-- AI-GENERATED-START: result-map -->\n");
        sb.append("    <resultMap id=\"BaseResultMap\" type=\"").append(basePackage).append(".entity.").append(entityName).append("\">\n");

        for (ColumnSchema col : dbSchema.getColumns()) {
            String camelName = toCamelCase(col.getColumnName());

            if (col.isPrimaryKey()) {
                sb.append("        <id column=\"").append(col.getColumnName()).append("\" property=\"").append(camelName).append("\"");
            } else {
                sb.append("        <result column=\"").append(col.getColumnName()).append("\" property=\"").append(camelName).append("\"");
            }

            // JSONB字段添加TypeHandler
            if (col.getTypeHandler() != null && !col.getTypeHandler().isEmpty()) {
                sb.append(" typeHandler=\"").append(col.getTypeHandler()).append("\"");
            }

            sb.append("/>\n");
        }

        sb.append("    </resultMap>\n");
        sb.append("    <!-- AI-GENERATED-END: result-map -->\n\n");

        // Base Column List
        sb.append("    <!-- AI-GENERATED-START: base-column-list -->\n");
        sb.append("    <sql id=\"Base_Column_List\">\n");
        sb.append("        ");
        List<String> columnNames = dbSchema.getColumns().stream()
                .map(ColumnSchema::getColumnName)
                .toList();
        sb.append(String.join(", ", columnNames));
        sb.append("\n");
        sb.append("    </sql>\n");
        sb.append("    <!-- AI-GENERATED-END: base-column-list -->\n\n");

        // HUMAN-AREA
        sb.append("    <!-- HUMAN-AREA: custom -->\n");
        sb.append("    <!-- 在此区域添加自定义SQL -->\n");
        sb.append("    <!-- HUMAN-AREA-END: custom -->\n\n");

        sb.append("</mapper>\n");

        return sb.toString();
    }

    /**
     * 创建目录
     */
    private void createDirectories(String... dirs) throws IOException {
        for (String dir : dirs) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    /**
     * 下划线转驼峰
     */
    private String toCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) {
            return snake;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 表名转Entity类名
     */
    private String toEntityName(String tableName) {
        String camel = toCamelCase(tableName);
        return camel.substring(0, 1).toUpperCase() + camel.substring(1) + "Entity";
    }
}
