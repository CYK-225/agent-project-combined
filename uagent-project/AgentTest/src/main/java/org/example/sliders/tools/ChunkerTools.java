package org.example.sliders.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.sliders.entity.SlidersChunkEntity;
import org.example.sliders.entity.SlidersDocumentEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunker Agent 专用工具 — 读取文档、程序化分块、存储结果
 * <p>
 * 分块逻辑：按段落边界切分，目标块大小 2000-4000 字符，表格不跨块。
 */
@Slf4j
public class ChunkerTools {

    private final DocumentReader documentReader;
    private final ChunkSaver chunkSaver;
    private final StatusUpdater statusUpdater;

    @FunctionalInterface
    public interface DocumentReader {
        List<SlidersDocumentEntity> readByTaskId(String taskId);
    }

    @FunctionalInterface
    public interface ChunkSaver {
        void saveAll(String taskId, List<SlidersChunkEntity> chunks);
    }

    @FunctionalInterface
    public interface StatusUpdater {
        boolean update(String taskId, String status);
    }

    public ChunkerTools(DocumentReader documentReader, ChunkSaver chunkSaver, StatusUpdater statusUpdater) {
        this.documentReader = documentReader;
        this.chunkSaver = chunkSaver;
        this.statusUpdater = statusUpdater;
    }

    @Tool(
            name = "read_task_documents",
            description = "读取任务关联的所有文档。返回文档名称、大小和内容摘要。"
                    + "用于了解需要处理哪些文档。"
    )
    public String readTaskDocuments(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId
    ) {
        statusUpdater.update(taskId, "CHUNKING");

        List<SlidersDocumentEntity> docs = documentReader.readByTaskId(taskId);
        if (docs.isEmpty()) {
            return "❌ 未找到任务 " + taskId + " 的文档";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📄 任务 ").append(taskId).append(" 共有 ").append(docs.size()).append(" 个文档：\n\n");

        for (SlidersDocumentEntity doc : docs) {
            String content = doc.getContent();
            int charCount = content != null ? content.length() : 0;
            String preview = content != null && content.length() > 200
                    ? content.substring(0, 200) + "..." : content;

            sb.append("### 文档: ").append(doc.getDocumentName()).append("\n");
            if (doc.getDescription() != null) {
                sb.append("描述: ").append(doc.getDescription()).append("\n");
            }
            sb.append("字符数: ").append(charCount).append("\n");
            sb.append("内容预览:\n```\n").append(preview).append("\n```\n\n");
        }

        return sb.toString();
    }

    @Tool(
            name = "chunk_document",
            description = "对指定文档执行程序化分块。按段落边界切分，保留章节头信息。"
                    + "分块完成后自动保存到数据库。"
    )
    public String chunkDocument(
            @ToolParam(name = "taskId", description = "任务 ID") String taskId,
            @ToolParam(name = "documentName", description = "要分块的文档名称") String documentName,
            @ToolParam(name = "targetChunkSize", description = "目标块大小（字符数），默认 3000，范围 1500-6000")
            int targetChunkSize
    ) {
        List<SlidersDocumentEntity> docs = documentReader.readByTaskId(taskId);
        SlidersDocumentEntity target = docs.stream()
                .filter(d -> d.getDocumentName().equals(documentName))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return "❌ 未找到文档: " + documentName;
        }

        // 执行分块
        int chunkSize = Math.max(1500, Math.min(6000, targetChunkSize));
        List<SlidersChunkEntity> chunks = doChunking(taskId, target, chunkSize);

        // 保存
        chunkSaver.saveAll(taskId, chunks);

        log.info("Chunked document {} into {} chunks, targetSize={}", documentName, chunks.size(), chunkSize);

        return String.format("""
                ✅ 文档分块完成！
                - 文档: %s
                - 分块数: %d
                - 目标块大小: %d 字符
                
                分块摘要：
                %s
                
                请通过 send_mail 通知 SlidersSchemaAgent 进行 Schema 归纳。
                邮件主题：SLIDERS Schema: %s
                """, documentName, chunks.size(), chunkSize,
                buildChunkSummary(chunks), taskId);
    }

    // ======================== 分块逻辑 ========================

    private List<SlidersChunkEntity> doChunking(String taskId, SlidersDocumentEntity doc, int targetSize) {
        String content = doc.getContent();
        String docName = doc.getDocumentName();

        // 按段落分割 — 先尝试双换行，若结果太少则降级到单换行
        String[] paragraphs = content.split("\\n\\n+");
        if (paragraphs.length <= 2) {
            // 双换行分割结果太少，降级到单换行
            paragraphs = content.split("\\n+");
            log.info("文档 {} 无双换行分隔，降级到单换行分割，得到 {} 个段落", docName, paragraphs.length);
        }
        List<SlidersChunkEntity> result = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        String currentHeader = "";
        int chunkIndex = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 检测章节头（Markdown 标题）
            if (trimmed.startsWith("#")) {
                currentHeader = trimmed.split("\\n")[0]; // 取第一行作为标题
            }

            // 如果加入这段会超限，先保存当前块
            if (currentChunk.length() > 0 && currentChunk.length() + trimmed.length() > targetSize) {
                result.add(buildChunkEntity(taskId, docName, chunkIndex++, currentChunk.toString(), currentHeader));
                currentChunk = new StringBuilder();
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(trimmed);
        }

        // 保存最后一块
        if (currentChunk.length() > 0) {
            result.add(buildChunkEntity(taskId, docName, chunkIndex, currentChunk.toString(), currentHeader));
        }

        return result;
    }

    private SlidersChunkEntity buildChunkEntity(String taskId, String docName, int index, String content, String header) {
        return SlidersChunkEntity.builder()
                .taskId(taskId)
                .documentName(docName)
                .chunkIndex(index)
                .chunkId(docName + "_chunk" + index)
                .content(content)
                .chunkHeader(header)
                .build();
    }

    private String buildChunkSummary(List<SlidersChunkEntity> chunks) {
        StringBuilder sb = new StringBuilder();
        for (var c : chunks) {
            sb.append(String.format("- 块 %d [%s]: %d 字符\n",
                    c.getChunkIndex(),
                    c.getChunkHeader() != null ? c.getChunkHeader() : "无标题",
                    c.getContent().length()));
        }
        return sb.toString();
    }
}
