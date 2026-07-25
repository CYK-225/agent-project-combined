package org.example.dbagent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.merger.MergeResult;
import org.example.dbagent.model.DBAgentConfig;
import org.example.dbagent.model.TableSchema;
import org.example.dbagent.prompt.CodeGenPromptFactory;
import org.example.dbagent.service.CodeGenerationService;
import org.example.dbagent.service.GitService;
import org.example.dbagent.service.SchemaReaderService;
import org.example.dbagent.service.TaskStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DBAgent Web接口
 * 提供表结构读取、代码生成、Git操作等API
 */
@Slf4j
@RestController
@RequestMapping("/api/dbagent")
@RequiredArgsConstructor
public class DBAgentController {

    private final SchemaReaderService schemaReaderService;
    private final CodeGenerationService codeGenerationService;
    private final GitService gitService;
    private final TaskStateStore taskStateStore;

    @Value("${dbagent.frontend.show-optional:true}")
    private boolean showOptional;

    /**
     * 获取前端显示配置
     */
    @GetMapping("/frontend-config")
    public Map<String, Object> getFrontendConfig() {
        return Map.of("showOptional", showOptional);
    }

    /**
     * 获取支持的框架列表
     */
    @GetMapping("/frameworks")
    public Map<String, Object> getSupportedFrameworks() {
        Set<String> frameworks = CodeGenPromptFactory.getSupportedFrameworks();
        return Map.of("success", true, "frameworks", frameworks);
    }

    /**
     * 获取PostgreSQL schema列表
     */
    @PostMapping("/schemas")
    public Map<String, Object> listSchemas(@RequestBody DBAgentConfig config) {
        try {
            return Map.of("success", true, "schemas", schemaReaderService.listSchemas(config));
        } catch (Exception e) {
            log.error("获取schema列表失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取表列表（使用前端配置）
     */
    @PostMapping("/tables")
    public Map<String, Object> listTables(@RequestBody DBAgentConfig config) {
        try {
            return Map.of("success", true, "tables", schemaReaderService.listTables(config));
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取表结构详情（使用前端配置）
     */
    @PostMapping("/tables/{tableName}")
    public Map<String, Object> getTableSchema(@PathVariable String tableName, @RequestBody DBAgentConfig config) {
        try {
            TableSchema schema = schemaReaderService.getTableSchema(config, tableName);
            return Map.of("success", true, "schema", schema);
        } catch (Exception e) {
            log.error("获取表结构失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 生成/更新代码（使用前端配置）
     */
    @PostMapping("/generate")
    public Map<String, Object> generateCode(@RequestBody Map<String, Object> request) {
        try {
            DBAgentConfig config = parseConfig(request);
            String tableName = (String) request.get("tableName");
            String mode = (String) request.getOrDefault("mode", "full");

            if (tableName == null || tableName.isBlank()) {
                return Map.of("success", false, "error", "tableName不能为空");
            }

            String taskKey = taskStateStore.buildKey(config, tableName);
            if (!taskStateStore.startIfAbsent(taskKey)) {
                TaskStateStore.TaskStatus current = taskStateStore.getStatus(taskKey);
                return Map.of(
                        "success", false,
                        "error", "该表正在生成中，请等待当前任务完成",
                        "taskState", current.state().name(),
                        "taskMessage", current.message() != null ? current.message() : ""
                );
            }

            try {
                gitService.pullLatest(config);

                TableSchema schema = schemaReaderService.getTableSchema(config, tableName);

                MergeResult result = codeGenerationService.generateOrUpdateCode(config, tableName, schema, mode);

                if (result.isSuccess()) {
                    gitService.commitAndPush(config, tableName, result.getChanges());
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", result.isSuccess());
                response.put("changes", result.getChanges() != null ? result.getChanges() : List.of());
                response.put("updatedBlocks", result.getUpdatedBlocks() != null ? result.getUpdatedBlocks() : List.of());
                response.put("preservedBlocks", result.getPreservedBlocks() != null ? result.getPreservedBlocks() : List.of());

                if (result.isSuccess()) {
                    taskStateStore.complete(taskKey);
                } else {
                    taskStateStore.fail(taskKey, result.getErrorMessage());
                    response.put("error", result.getErrorMessage());
                }

                TaskStateStore.TaskStatus latest = taskStateStore.getStatus(taskKey);
                response.put("taskState", latest.state().name());
                response.put("taskMessage", latest.message() != null ? latest.message() : "");

                return response;
            } catch (Exception ex) {
                taskStateStore.fail(taskKey, ex.getMessage());
                throw ex;
            }
        } catch (Exception e) {
            log.error("代码生成失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 批量生成代码
     */
    @PostMapping("/batch-generate")
    public Map<String, Object> batchGenerateCode(@RequestBody Map<String, Object> request) {
        try {
            DBAgentConfig config = parseConfig(request);
            List<String> tableNames = (List<String>) request.get("tableNames");
            String mode = (String) request.getOrDefault("mode", "full");

            if (tableNames == null || tableNames.isEmpty()) {
                return Map.of("success", false, "error", "tableNames不能为空");
            }

            log.info("批量生成代码: 表数量={}, 表={}", tableNames.size(), tableNames);

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            List<String> allChanges = new ArrayList<>();

            for (String tableName : tableNames) {
                Map<String, Object> tableResult = new HashMap<>();
                tableResult.put("tableName", tableName);

                try {
                    String taskKey = taskStateStore.buildKey(config, tableName);
                    if (!taskStateStore.startIfAbsent(taskKey)) {
                        tableResult.put("success", false);
                        tableResult.put("error", "该表正在生成中");
                        failCount++;
                        results.add(tableResult);
                        continue;
                    }

                    try {
                        TableSchema schema = schemaReaderService.getTableSchema(config, tableName);
                        MergeResult result = codeGenerationService.generateOrUpdateCode(config, tableName, schema, mode);

                        if (result.isSuccess()) {
                            successCount++;
                            tableResult.put("success", true);
                            if (result.getChanges() != null) {
                                allChanges.addAll(result.getChanges());
                                tableResult.put("changes", result.getChanges());
                            }
                            taskStateStore.complete(taskKey);
                        } else {
                            failCount++;
                            tableResult.put("success", false);
                            tableResult.put("error", result.getErrorMessage());
                            taskStateStore.fail(taskKey, result.getErrorMessage());
                        }
                    } catch (Exception ex) {
                        failCount++;
                        tableResult.put("success", false);
                        tableResult.put("error", ex.getMessage());
                        taskStateStore.fail(taskKey, ex.getMessage());
                    }
                } catch (Exception e) {
                    failCount++;
                    tableResult.put("success", false);
                    tableResult.put("error", e.getMessage());
                }

                results.add(tableResult);
            }

            // Git提交
            if (successCount > 0 && config.getGitRepoUrl() != null && !config.getGitRepoUrl().isBlank()) {
                try {
                    gitService.commitAndPush(config, "batch-" + successCount + "-tables", allChanges);
                } catch (Exception e) {
                    log.warn("Git提交失败，但代码已生成", e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", successCount > 0);
            response.put("successCount", successCount);
            response.put("failCount", failCount);
            response.put("total", tableNames.size());
            response.put("results", results);
            response.put("changes", allChanges);

            return response;
        } catch (Exception e) {
            log.error("批量生成代码失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 查询任务状态
     */
    @PostMapping("/task-status")
    public Map<String, Object> getTaskStatus(@RequestBody Map<String, Object> request) {
        try {
            DBAgentConfig config = parseConfig(request);
            String tableName = (String) request.get("tableName");

            if (tableName == null || tableName.isBlank()) {
                return Map.of("success", false, "error", "tableName不能为空");
            }

            TaskStateStore.TaskStatus status = taskStateStore.getStatus(config, tableName);
            return Map.of(
                    "success", true,
                    "taskState", status.state().name(),
                    "taskMessage", status.message() != null ? status.message() : ""
            );
        } catch (Exception e) {
            log.error("查询任务状态失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取远程分支列表
     */
    @PostMapping("/branches")
    public Map<String, Object> listBranches(@RequestBody DBAgentConfig config) {
        try {
            List<String> branches = gitService.listRemoteBranches(config);
            return Map.of("success", true, "branches", branches);
        } catch (Exception e) {
            log.error("获取分支列表失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 测试数据库连接（使用前端配置）
     */
    @PostMapping("/test-db-connection")
    public Map<String, Object> testDbConnection(@RequestBody DBAgentConfig config) {
        boolean connected = schemaReaderService.testConnection(config);
        return Map.of("success", connected);
    }

    /**
     * 测试Git连接（使用前端配置）
     */
    @PostMapping("/test-git-connection")
    public Map<String, Object> testGitConnection(@RequestBody DBAgentConfig config) {
        log.info("测试Git连接 - 收到配置: repoUrl={}, gitUsername={}, gitToken={}",
                config.getGitRepoUrl(),
                config.getGitUsername() != null ? "***" : "null",
                config.getGitToken() != null ? "***" : "null");
        boolean connected = gitService.testConnection(config);
        return Map.of("success", connected);
    }

    /**
     * 解析前端配置
     */
    private DBAgentConfig parseConfig(Map<String, Object> request) {
        return DBAgentConfig.builder()
                .dbType((String) request.get("dbType"))
                .mysqlHost((String) request.get("mysqlHost"))
                .mysqlPort(request.get("mysqlPort") != null ? Integer.parseInt(request.get("mysqlPort").toString()) : null)
                .mysqlDatabase((String) request.get("mysqlDatabase"))
                .mysqlUser((String) request.get("mysqlUser"))
                .mysqlPassword((String) request.get("mysqlPassword"))
                .gitRepoUrl((String) request.get("gitRepoUrl"))
                .gitBranch((String) request.get("gitBranch"))
                .gitUsername((String) request.get("gitUsername"))
                .gitToken((String) request.get("gitToken"))
                .basePackage((String) request.get("basePackage"))
                .framework((String) request.get("framework"))
                .build();
    }
}
