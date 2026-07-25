package org.example.sliders.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersDocumentEntity;
import org.example.sliders.entity.SlidersTaskEntity;
import org.example.sliders.service.SlidersTaskService;

import java.util.List;
import java.util.UUID;

/**
 * Master Agent 专用工具 — 任务创建与查询
 */
@Slf4j
public class MasterTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SlidersTaskService taskService;
    private final DocumentSaver documentSaver;

    /** 函数式接口，由外部提供文档保存能力（避免直接依赖 Mapper） */
    @FunctionalInterface
    public interface DocumentSaver {
        void save(String taskId, String name, String description, String content);
    }

    public MasterTools(SlidersTaskService taskService, DocumentSaver documentSaver) {
        this.taskService = taskService;
        this.documentSaver = documentSaver;
    }

    @Tool(
            name = "create_task",
            description = "创建 SLIDERS 流水线任务。传入用户问题和文档列表，返回任务 ID。"
                    + "创建后需要通过 send_mail 将 taskId 发给 SlidersChunker Agent。"
    )
    public String createTask(
            @ToolParam(name = "question", description = "用户的原始问题") String question,
            @ToolParam(name = "documentsJson", description = "文档列表 JSON 数组，每个元素含 name 和 content 字段，"
                    + "例如 [{\"name\":\"report.md\",\"content\":\"# 报告\\n内容...\"}]")
            String documentsJson
    ) {
        try {
            // 1. 生成 taskId 和 threadId
            String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String threadId = "sliders-" + taskId;

            // 2. 创建任务
            taskService.save(SlidersTaskEntity.builder()
                    .taskId(taskId)
                    .threadId(threadId)
                    .question(question)
                    .status("PENDING")
                    .build());

            // 3. 保存文档
            var docs = MAPPER.readTree(documentsJson);
            int docCount = 0;
            for (var doc : docs) {
                String name = doc.has("name") ? doc.get("name").asText() : "document_" + docCount;
                String content = doc.has("content") ? doc.get("content").asText() : "";
                String description = doc.has("description") ? doc.get("description").asText() : null;
                documentSaver.save(taskId, name, description, content);
                docCount++;
            }

            log.info("Created SLIDERS task: taskId={}, docs={}, question={}", taskId, docCount, question);

            return String.format("""
                    ✅ 任务创建成功！
                    - 任务 ID: %s
                    - 文档数量: %d
                    - 问题: %s
                    
                    请通过 send_mail 将以下内容发给 SlidersChunker：
                    主题：SLIDERS Task: %s
                    正文：请处理任务 %s，问题为：%s
                    """, taskId, docCount, question, taskId, taskId, question);

        } catch (Exception e) {
            log.error("Failed to create task", e);
            return "❌ 创建任务失败: " + e.getMessage();
        }
    }

    @Tool(
            name = "read_task_status",
            description = "查询任务状态和结果。用于检查流水线进度或获取最终答案。"
    )
    public String readTaskStatus(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        SlidersTaskEntity task = taskService.getByTaskId(taskId);
        if (task == null) {
            return "❌ 未找到任务: " + taskId;
        }

        String answer = task.getAnswer() != null ? task.getAnswer() : "（尚未生成）";
        String error = task.getErrorMessage() != null ? "\n错误信息: " + task.getErrorMessage() : "";

        return String.format("""
                📋 任务状态：
                - 任务 ID: %s
                - 状态: %s
                - 问题: %s
                - 答案: %s%s
                """, task.getTaskId(), task.getStatus(), task.getQuestion(), answer, error);
    }
}
