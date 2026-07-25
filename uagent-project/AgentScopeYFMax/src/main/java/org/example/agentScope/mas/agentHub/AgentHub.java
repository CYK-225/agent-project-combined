package org.example.agentScope.mas.agentHub;

import io.agentscope.core.message.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AgentHub
 * 作为一个静态工具门面，提供链式调用能力。
 * 它依然被 Spring 管理（@Component），目的是为了注入 ToolDispatcher。
 */
@Component
public class AgentHub {

    // 1. 定义静态的 Dispatcher，供静态方法内部使用
    private static ToolDispatcher staticDispatcher;
    @Autowired
    public void setStaticDispatcher(ToolDispatcher toolDispatcher) {
        AgentHub.staticDispatcher = toolDispatcher;
    }


    /**
     * 3. 静态入口方法
     * 每次调用都会创建一个新的 Runner 实例，保证线程安全
     */
    public static AgentRunner from(List<ToolUseBlock> toolUseBlocks) {
        // 校验 Spring 是否已初始化
        if (staticDispatcher == null) {
            throw new IllegalStateException("AgentHub 尚未初始化，请确保 Spring 容器已启动");
        }
        return new AgentRunner(toolUseBlocks);
    }

    /**
     * 4. 静态内部类：执行器 (Runner)
     * 这里持有“状态” (toolUseBlocks)，因为每次都是 new 的，所以无并发问题
     */
    public static class AgentRunner {
        private final List<ToolUseBlock> blocks;
        private final String id;
        private final String name;


        // 私有构造，强制通过 AgentHub.from() 创建
        private AgentRunner(List<ToolUseBlock> blocks) {
            this.blocks = blocks;
            this.id = blocks.getFirst().getId();
            this.name = blocks.getFirst().getName();
        }

        /**
         * 执行方法 (核心逻辑)
         */
        public Msg execute() {
            if (blocks == null || blocks.isEmpty()) {
                return null;
            }

            // 使用虚拟线程 (Java 21+)
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

                // A. 提交任务
                List<CompletableFuture<String>> futures = blocks.stream()
                        .map(block -> CompletableFuture.supplyAsync(() -> {
                                    // 使用外层类的静态 dispatcher

                                    return AgentHub.staticDispatcher.dispatch(block.getName(), block.getInput());
                                }, executor)
                                .exceptionally(ex -> {
                                    // 简单的错误处理
                                    return STR."❌ 工具 [\{block.getName()}] 失败: \{ex.getMessage()}";
                                }))
                        .toList();

                // B. 等待所有任务
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // C. 聚合结果
                String result= futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining("\n"));

                return  Msg.builder()
                        .role(MsgRole.TOOL)
                        .content(ToolResultBlock.of(id, name,
                        TextBlock.builder().text(result).build()))
                        .build();
            }
        }
    }

    /*
    使用示例
    // 检查是否被挂起
    Msg response1 = agent.call(userMsg).block();
    if (response.getGenerateReason() == GenerateReason.TOOL_SUSPENDED) {
    // 获取待执行的工具调用
        response = agent.call(
        agentHub.from(response1
        .getContentBlocks(ToolUseBlock.class))
        .execute()).block();
     */
}