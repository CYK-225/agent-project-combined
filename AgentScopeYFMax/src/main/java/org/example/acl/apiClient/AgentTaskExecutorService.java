package org.example.acl.apiClient;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.example.acl.hook.callRQHook;
import org.example.agentScope.framework.core.AgentSessionCache;
import org.example.agentScope.mas.phone.dataModel.AgentTaskNotifyDTO;
import org.example.agentScope.mas.phone.hook.ScreenshotInjectionHook;
import org.example.agentScope.mas.reActAgent.AgentConfigPo;
import org.example.agentScope.util.hooksManager.SessionContext;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.example.repository.dal.entity.AgentExecutionDetailEntity;
import org.example.repository.dal.entity.DataModel;
import org.example.repository.dal.service.IAgentExecutionDetailService;
import org.example.repository.dal.service.IAgentTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 任务执行服务
 * <p>
 * 负责：
 * 1. 调用 Agent 实例并启动执行
 * 2. 处理工具挂起（ToolSuspendException）
 * 3. 恢复 Agent 执行
 * </p>
 *
 * <h3>核心流程：</h3>
 * <pre>
 * invokeAgent → 构造容器URL → 创建 DataModel → 注册到 ToolExecutionContext → 创建 Agent
 *   → 注册到 AgentSessionCache → 异步执行
 *   → 工具挂起（TOOL_SUSPENDED） → 保存状态 → 等待容器回调
 * resumeTask → 从 AgentSessionCache 取出同一个 Agent → 注入工具结果 → 继续执行
 * </pre>
 *
 * <h3>缓存机制（关键）：</h3>
 * <pre>
 * ReActAgent 的 ToolSuspend 恢复机制依赖 memory 中的 assistant msg（含 ToolUseBlock）。
 * 如果每次恢复都新建 Agent 实例，memory 为空，getPendingToolUseIds() 找不到挂起工具，恢复失败。
 * 因此必须通过 AgentSessionCache 缓存 Agent 实例，回调时取出同一个实例恢复。
 *
 * 首次：AgentPoolManager.getAgentWithSession() 手动创建 + cache.put() 注册
 * 后续：cache.get() 取出同一个实例
 * </pre>
 *
 * <h3>DataModel 注入机制：</h3>
 * <pre>
 * 每次任务执行前，根据 containerPort 构造容器URL，封装为 DataModel，
 * 通过 ToolExecutionContext 注册到 Agent。当 Agent 调用工具时，
 * 框架自动将 DataModel 注入到工具方法的参数中，工具通过 model.getUrl() 获取容器地址。
 * </pre>
 *
 * <h3>会话机制：</h3>
 * <pre>
 * 首次调用：sessionId 不存在 → 创建新会话
 * 非首次调用：sessionId 已存在 → 拿到历史记忆
 * </pre>
 */
@Slf4j
@Service
public class AgentTaskExecutorService {

    @Resource
    private AgentSessionCache agentSessionCache;

    @Resource
    private IAgentTaskService agentTaskService;

    @Resource
    private IAgentExecutionDetailService executionDetailService;

    @Resource
    private RestTemplate restTemplate;

    /**
     * AI 中台的回调地址，从配置文件 ai-url.ai-callback 注入
     * 用作兜底：当 DB 中的 taskEntity.aiCallbackUrl 为空时使用
     */
    @Value("${ai-url.ai-callback:}")
    private String aiCallbackUrl;

    /**
     * Agent 会话缓存 — 保证挂起-恢复循环使用同一个 Agent 实例
     * <p>
     * 框架层统一缓存，带过期机制和 maxSize 淘汰。
     * key = threadId:agentName，支持 getOrCreate / get / put / remove。
     * </p>



     * 调用 Agent（创建或恢复会话）
     * <p>
     * 完整流程：
     * 1. 根据调用方传入的 containerUrl 构造 GuiApiClient 和 DataModel
     * 2. 将 containerURL 封装为 DataModel，注册到 ToolExecutionContext
     * 3. 创建 ScreenshotInjectionHook，通过 getAgentWithSession 创建带会话的 Agent 实例
     * 4. 使用 AgentSessionCache.put() 注册到缓存（保证恢复时用同一个实例）
     * 5. 使用 GuiApiClient 注册 agent_id 和 callback_url 到容器
     * 6. 更新任务状态为 RUNNING
     * 7. 异步执行 Agent（executeAgentAsync）
     * </p>
     */
    public void invokeAgent(AgentTaskNotifyDTO dto) {
        log.info("[AgentTaskExecutorService] 调用 Agent，taskId: {}, agentName: {}, sessionId: {}, containerUrl: {}, step: {}",
                dto.getTaskId(), dto.getAgentName(), dto.getSessionId(), dto.getContainerUrl(), dto.getStep());

        // ========== 1. 构造容器客户端 ==========
        // containerUrl 由工具平台传入，AI 中台不做任何 IP 拼接
        GuiApiClient guiClient = new GuiApiClient(dto.getContainerUrl());
        log.info("[AgentTaskExecutorService] GuiApiClient 已构造，containerUrl: {}", dto.getContainerUrl());

        try {
            // ========== 2. 创建 ToolExecutionContext（注入 DataModel） ==========
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .register(new DataModel(dto.getContainerUrl()))
                    .build();

            // ========== 3. 创建 ScreenshotInjectionHook ==========
            // 容器回调时会携带截图，Hook 在下一次推理前将截图注入到 LLM 上下文
            SessionContext sessionContext = new SessionContext();
            sessionContext.add(dto);
            ScreenshotInjectionHook screenshotHook = new ScreenshotInjectionHook();
            List<Hook> hooks = new ArrayList<>();
            hooks.add(screenshotHook);
            hooks.add( new callRQHook(sessionContext));

            // ========== 4. 创建 Agent 实例 ==========
            // 通过 ToolExecutionContext 注入 DataModel，框架自动注入到工具方法参数
            // 通过 dynamicHooks 挂载截图注入 Hook
            AgentConfigPo agentConfigPo=AgentConfigPo.builder()
                    .toolExecutionContext(context)
                    .sysPrompt(dto.getSysPrompt())  //每次都执行系统提示词
                    .hooks(hooks)
                    .build();

            // 直接调用 getOrCreate（内部已有缓存检查，命中则返回缓存，未命中则创建）
            ReActAgent agent = agentSessionCache.getOrCreate(dto.getSessionId(), dto.getAgentName(), agentConfigPo);
            if (agent == null) {
                throw new RuntimeException("Agent 不存在: " + dto.getAgentName());
            }

            // 无论缓存命中还是新建，都需要更新 hook 中的 step（step 可能每次都不同）
            updateHookStep(agent, dto.getStep());
            log.info("[AgentTaskExecutorService] Agent 实例已创建，taskId: {}, sessionId: {}", dto.getTaskId(), dto.getSessionId());

//            // ========== 5. 注册到缓存 ==========
//            // 必须缓存 Agent 实例，原因：
//            //   agent.call().block() 挂起后，Agent 的 memory 里有 ToolUseBlock
//            //   恢复时必须用同一个 Agent 实例，否则 memory 为空，恢复失败
//            agentSessionCache.put(sessionId, agentName, agent, hooks);
//            log.info("[AgentTaskExecutorService] Agent 已注册到缓存，sessionId: {}, agentName: {}", sessionId, agentName);

            // ========== 6. 更新任务状态为 RUNNING ==========
            agentTaskService.markTaskStarted(dto.getTaskId(), "SYSTEM");

            // ========== 7. 注册到容器 ==========
            // 告诉容器：这个 agent_id 的回调地址是 AI 中台地址
            // 容器执行完 GUI 操作后，先回调 AI 中台，由 AI 中台判断步骤是否完成
            // 未完成则继续调用容器，完成后回调工具平台
            AgentTaskEntity taskEntity = agentTaskService.selectByTaskId(dto.getTaskId());
            if (taskEntity != null) {
                taskEntity.setCallbackUrl(dto.getCallbackUrl());
                // aiCallbackUrl：优先用 DB 中落盘的值（createOrUpdateTask 从配置写入），
                // DB 为空则用本服务注入的配置兜底，绝不用 DTO 的值（工具平台不知道此字段）
                if (taskEntity.getAiCallbackUrl() == null || taskEntity.getAiCallbackUrl().isBlank()) {
                    taskEntity.setAiCallbackUrl(aiCallbackUrl);
                }
                guiClient.registerAgent(taskEntity);
                log.info("[AgentTaskExecutorService] 已注册到容器，taskId: {}, callbackUrl(工具平台): {}, aiCallbackUrl(AI中台): {}",
                        dto.getTaskId(), dto.getCallbackUrl(), taskEntity.getAiCallbackUrl());
            }

            // ========== 8. 异步执行 Agent ==========
            CompletableFuture.runAsync(() -> executeAgentAsync(agent, dto));

        } catch (Exception e) {
            log.error("[AgentTaskExecutorService] 调用 Agent 失败，taskId: {}", dto.getTaskId(), e);
            agentSessionCache.remove(dto.getSessionId(), dto.getAgentName());// 清理缓存
            agentTaskService.markTaskFailed(dto.getTaskId(), e.getMessage(), "SYSTEM");// 更新任务状态为 FAILED
        }
    }

    /**
     * 异步执行 Agent
     * <p>
     * 核心执行逻辑：
     * 1. 构造用户消息
     * 2. 调用 agent.call().block() 执行
     * 3. 检查 response.getGenerateReason() == TOOL_SUSPENDED 判断是否挂起
     * 4. 如果挂起，等待容器回调 PhoneCallbackController → resumeTask()
     * 5. 如果正常返回，标记任务完成、清理缓存、通知工具平台
     * </p>
     *
     * @param agent Agent 实例（已通过 ToolExecutionContext 注入 DataModel）
     * @param dto   任务通知 DTO（包含 taskId、sessionId、agentName、instruction 等）
     */
    private void executeAgentAsync(ReActAgent agent, AgentTaskNotifyDTO dto) {
        // 从 DTO 提取常用字段，避免重复 getter 调用
        String taskId = dto.getTaskId();
        String sessionId = dto.getSessionId();
        String agentName = dto.getAgentName();
        String instruction = dto.getInstruction();

        // 从 agent 的 hooks 中获取 ScreenshotInjectionHook（用于清理截图）
        ScreenshotInjectionHook screenshotHook = findScreenshotHook(agent.getHooks());

        try {
            log.info("[AgentTaskExecutorService] 开始执行 Agent，taskId: {}", taskId);

            // 构造用户消息（使用 Builder 模式）
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(instruction)
                    .build();

            // 调用 Agent 执行
            // 如果 LLM 调用了工具，工具会：
            //   1. 通过 GuiApiClient 异步发送请求到容器
            //   2. 抛出 ToolSuspendException 挂起 Agent
            //   3. 框架捕获 ToolSuspendException，返回 GenerateReason.TOOL_SUSPENDED 的 Msg
            Msg response = agent.call(userMsg).block();

            // 检查是否被挂起（ToolSuspendException 被框架捕获后，response 的 reason 是 TOOL_SUSPENDED）
            GenerateReason reason = response.getGenerateReason();
            log.info("[AgentTaskExecutorService] Agent 执行返回，taskId: {}, reason: {}", taskId, reason);

            if (reason == GenerateReason.TOOL_SUSPENDED) {
                // ========== 工具挂起 ==========
                // Agent 调用了工具并被挂起，等待容器执行完 GUI 操作后回调
                // Agent 实例保留在 AgentSessionCache 中，等 PhoneCallbackController 取出恢复
                log.info("[AgentTaskExecutorService] Agent 工具挂起，taskId: {}，等待容器回调", taskId);
            } else {
                // ========== Agent 正常完成 ==========
                String responseText = extractText(response);
                log.info("[AgentTaskExecutorService] Agent 执行完成，taskId: {}, response: {}", taskId, responseText);

                // 写入最后一条执行明细（modelOutput 为 Agent 最终输出）
                saveFinalExecutionDetail(taskId, sessionId, responseText);

                // 通知工具平台任务完成（直接复用 dto，补充任务结果字段）
                dto.setStatus("SUCCESS");
                dto.setSuccess(true);
                dto.setResult(responseText);
                notifyToolPlatform(dto, dto.getCallbackUrl());

                // 清理缓存（任务结束，不再需要保留 Agent 实例）
//                agentSessionCache.remove(sessionId, agentName);
//                if (screenshotHook != null) screenshotHook.clearScreenshot();
//                log.info("[AgentTaskExecutorService] 已清理缓存，sessionId: {}", sessionId);

                // 更新任务状态为 SUCCESS
                agentTaskService.markTaskCompleted(taskId, "SYSTEM");
            }

        } catch (Exception e) {
            // 挂起已通过 response.getGenerateReason() == TOOL_SUSPENDED 判断，不会走这里
            // 这里只处理真正的异常（如 LLM 调用失败、网络错误等）
            log.error("[AgentTaskExecutorService] Agent 执行失败，taskId: {}", taskId, e);
            agentSessionCache.remove(sessionId, agentName);
            if (screenshotHook != null) screenshotHook.clearScreenshot();
            agentTaskService.markTaskFailed(taskId, e.getMessage(), "SYSTEM");

            // 通知工具平台失败（直接复用 dto）
            dto.setStatus("FAILED");
            dto.setSuccess(false);
            dto.setErrorMessage(e.getMessage());
            notifyToolPlatform(dto, dto.getCallbackUrl());
        }
    }

    /**todo 需要优化，但是没有优化方向
     * 恢复被挂起的 Agent 任务
     * <p>
     * 由 PhoneCallbackController 回调触发，处理容器返回的工具执行结果 + 新截图，
     * 驱动 Agent 的挂起-恢复循环。
     * <p>
     * 核心要点：必须从 AgentSessionCache 取出同一个 Agent 实例！
     * ReActAgent 的 ToolSuspend 恢复机制依赖 memory 中的 assistant msg（含 ToolUseBlock）。
     * 如果新建 Agent，memory 为空，getPendingToolUseIds() 找不到挂起工具，恢复失败。
     * <p>
     * 与旧实现的区别：
     * <ul>
     *   <li>使用 {@link ToolResultBlock} + toolUseId 替代纯文本 toolResult，
     *       框架可精确匹配挂起的 ToolUseBlock</li>
     *   <li>使用 subscribe() 异步执行替代 block()，不阻塞回调线程</li>
     *   <li>toolUseId 由 {@link ScreenshotInjectionHook} 在 PreActing 时自动缓存</li>
     * </ul>
     *
     * @param dto 容器回调的完整 DTO（包含 taskId、result、screenshot、step、toolName、toolInput 等）
     */
    public void resumeTask(AgentTaskNotifyDTO dto) {
        String taskId = dto.getTaskId();
        String toolResult = dto.getResult();
        String base64Screenshot = dto.getScreenshot();

        log.info("[AgentTaskExecutorService] 恢复任务，taskId: {}, result长度: {},step:{}",
                taskId, toolResult != null ? toolResult.length() : 0,dto.getStep());

        // 提前声明，catch 块中需要用于清理缓存和通知
        AgentTaskEntity task = null;

        try {
            // ========== 1. 查询任务信息 ==========
            task = agentTaskService.selectByTaskId(taskId);
            if (task == null) {
                log.error("[AgentTaskExecutorService] 任务不存在，taskId: {}", taskId);
                return;
            }

            String sessionId = dto.getSessionId();
            String agentName = dto.getAgentName();

            // ========== 2. 从缓存获取 Agent 实例（关键！） ==========
            ReActAgent agent = agentSessionCache.get(sessionId, agentName);
            if (agent == null) {
                log.error("[AgentTaskExecutorService] 缓存中没有找到 Agent，sessionId: {}, agentName: {}，可能已超时清理",
                        sessionId, agentName);
                agentTaskService.markTaskFailed(taskId, "Agent 会话缓存不存在，无法恢复", "SYSTEM");
                return;
            }
            log.info("[AgentTaskExecutorService] 从缓存获取 Agent 成功，sessionId: {}", sessionId);

            // ========== 3. 更新截图（来自容器回调，不是 DB） ==========
            if (base64Screenshot != null && !base64Screenshot.isBlank()) {
                updateAgentScreenshot(agent, base64Screenshot);
                log.info("[AgentTaskExecutorService] 截图已更新，taskId: {}", taskId);
            }

            // ========== 4. 获取工具调用 ID ==========
            // toolUseId 由 ScreenshotInjectionHook 在 handlePreActing 时自动缓存
            // 框架需要通过 id 精确匹配挂起的 ToolUseBlock，才能正确恢复推理链
            ScreenshotInjectionHook hook = findScreenshotHook(agent.getHooks());
            String toolUseId = hook != null ? hook.getAndClearPendingToolUseId() : null;

            if (toolUseId == null) {
                log.warn("[AgentTaskExecutorService] 无待处理的 toolUseId，跳过恢复，taskId: {}", taskId);
                return;
            }

            // ========== 4.5 从 callRQHook 补充字段到当前 DTO ==========
            // callRQHook.handlePreActing 在 Agent 每次工具调用前将 toolName/toolInput 写入
            // SessionContext 中的 taskNotifyDTO（即 invokeAgent 时创建的那个）。
            // 但 resumeTask 的 dto 是从容器 HTTP 回调反序列化的新对象，没有这些字段。
            // 因此需要从 hook 中取出补充。
            callRQHook rqHook = findCallRQHook(agent.getHooks());
            if (rqHook != null && rqHook.getTaskNotifyDTO() != null) {
                AgentTaskNotifyDTO hookDto = rqHook.getTaskNotifyDTO();
                dto.setToolName(hookDto.getToolName());
                dto.setToolInput(hookDto.getToolInput());
                dto.setOutputResult(hookDto.getOutputResult());
                log.debug("[AgentTaskExecutorService] 从 callRQHook 补充 toolName={}, toolInput={}, promptsId={}",
                        dto.getToolName(), dto.getToolInput(), dto.getPromptsId());
            }
            
            // 统一补充 promptsId / callbackUrl（容器回调不携带这些字段）
            // 优先从 hook 获取，hook 为空则从 task 获取（兜底）
            if (dto.getPromptsId() == null) {
                log.info("[AgentTaskExecutorService]：容器回调未携带 promptsId");
                dto.setPromptsId(rqHook != null && rqHook.getTaskNotifyDTO() != null
                        ? rqHook.getTaskNotifyDTO().getPromptsId() : task.getPromptsId());
            }
            if (dto.getCallbackUrl() == null) {
                log.info("[AgentTaskExecutorService]：容器回调未携带 callbackUrl");
                dto.setCallbackUrl(rqHook != null && rqHook.getTaskNotifyDTO() != null
                        ? rqHook.getTaskNotifyDTO().getCallbackUrl() : task.getCallbackUrl());
            }

            // ========== 4.6 修正 step / toolStep ==========
            // 容器发来的 step 实际是工具调用的序列号，应作为 toolStep
            // 真正的 step（工作流步骤号）从 hook 的 taskNotifyDTO 获取
            Integer containerStep = dto.getStep();
            dto.setToolStep(containerStep);
            if (rqHook != null && rqHook.getTaskNotifyDTO() != null && rqHook.getTaskNotifyDTO().getStep() != null) {
                dto.setStep(rqHook.getTaskNotifyDTO().getStep());
            } else {
                dto.setStep(1);  // 工具平台未传 step 时默认 1
            }
            log.info("[AgentTaskExecutorService] step 修正后，step: {}, toolStep: {}", dto.getStep(), dto.getToolStep());

            // ========== 5. 记录执行明细到 agent_execution_detail 表 ==========
            saveExecutionDetail(taskId, sessionId, dto);

            // ========== 5.1 实时推送步骤日志给工具平台 ==========
            notifyStepLog(task, dto);

            // ========== 6. 构建工具结果消息 ==========
            // 使用 ToolResultBlock + toolUseId，框架可精确匹配挂起的 ToolUseBlock 并继续推理
            Msg toolResultMsg = Msg.builder()
                    .role(MsgRole.TOOL)
                    .content(ToolResultBlock.builder()
                            .id(toolUseId)
                            .output(TextBlock.builder()
                                    .text(toolResult != null ? toolResult : "")
                                    .build())
                            .build())
                    .build();

            // ========== 7. 状态保持 RUNNING（挂起是 Agent 内部行为，agent_task 状态不变） ==========

            // ========== 8. 异步恢复 Agent 执行 ==========
            // 使用 subscribe() 异步执行，不阻塞回调线程
            // Agent 可能再次挂起（TOOL_SUSPENDED）或正常完成
            final AgentTaskEntity finalTask = task;
            final AgentTaskNotifyDTO finalDto = dto;
            agent.call(toolResultMsg).subscribe(
                    response -> handleResumeResponse(taskId, finalTask, response, sessionId, agentName, agent, finalDto),
                    error -> {
                        log.error("[AgentTaskExecutorService] Agent 恢复执行异常，taskId: {}", taskId, error);
                        agentSessionCache.remove(sessionId, agentName);
                        clearAgentScreenshot(agent);
                        agentTaskService.markTaskFailed(taskId, error.getMessage(), "SYSTEM");

                        // 通知工具平台失败
                        finalDto.setStatus("FAILED");
                        finalDto.setSuccess(false);
                        finalDto.setErrorMessage(error.getMessage());
                        notifyToolPlatform(finalDto, finalDto.getCallbackUrl());
                    }
            );
            log.info("[AgentTaskExecutorService] Agent 恢复已提交，taskId: {}", taskId);

        } catch (Exception e) {
            log.error("[AgentTaskExecutorService] 恢复任务失败，taskId: {}", taskId, e);

            // 异常时清理缓存
            if (task != null) {
                agentSessionCache.remove(task.getSessionId(), task.getAgentName());
            }

            agentTaskService.markTaskFailed(taskId, e.getMessage(), "SYSTEM");

            // 通知工具平台失败（promptsId/callbackUrl 已在步骤 4.5 统一补充）
            dto.setStatus("FAILED");
            dto.setSuccess(false);
            dto.setErrorMessage(e.getMessage());
            notifyToolPlatform(dto, dto.getCallbackUrl());
        }
    }

    /**
     * 处理 Agent 恢复执行后的响应（subscribe 回调）
     * <p>
     * Agent 恢复后可能：
     * 1. 再次挂起（TOOL_SUSPENDED）— 工具内部已通知容器，等待下次回调
     * 2. 正常完成 — 清理缓存、更新状态、通知工具平台
     * </p>
     */
    private void handleResumeResponse(String taskId, AgentTaskEntity task, Msg response,
                                      String sessionId, String agentName, ReActAgent agent,
                                      AgentTaskNotifyDTO notifyDTO) {
        GenerateReason reason = response.getGenerateReason();
        log.info("[AgentTaskExecutorService] Agent 恢复执行返回，taskId: {}, reason: {}", taskId, reason);

        if (reason == GenerateReason.TOOL_SUSPENDED) {
            // ========== 再次挂起 ==========
            // Agent 又调用了工具，继续等待容器回调
            // Agent 实例保留在缓存中，不清理
            response.getContentBlocks(ToolUseBlock.class).forEach(toolUse ->
                    log.info("[AgentTaskExecutorService] Agent 再次挂起: {}({})", toolUse.getName(), toolUse.getInput()));

            // 清除已使用的截图（避免下次推理重复注入旧截图）
            clearAgentScreenshot(agent);
        } else {
            // ========== Agent 正常完成 ==========
            String responseText = extractText(response);
            log.info("[AgentTaskExecutorService] Agent 恢复执行完成，taskId: {}, response: {}", taskId, responseText);

            // 写入最后一条执行明细（modelOutput 为 Agent 最终输出）
            saveFinalExecutionDetail(taskId, sessionId, responseText);

            // 通知工具平台任务完成（promptsId/callbackUrl 已在 resumeTask 步骤 4.5 统一补充）
            notifyDTO.setStatus("SUCCESS");
            notifyDTO.setSuccess(true);
            notifyDTO.setResult(responseText);
            notifyToolPlatform(notifyDTO, notifyDTO.getCallbackUrl());

            // 清理缓存（任务结束）
//            agentSessionCache.remove(sessionId, agentName);
//            clearAgentScreenshot(agent);
//            log.info("[AgentTaskExecutorService] 已清理缓存，sessionId: {}", sessionId);

            // 更新任务状态为 SUCCESS
            agentTaskService.markTaskCompleted(taskId, "SYSTEM");


        }
    }

    /**
     * 从 Agent 的 hooks 列表中查找 ScreenshotInjectionHook
     */
    private ScreenshotInjectionHook findScreenshotHook(java.util.List<Hook> hooks) {
        if (hooks == null) return null;
        return hooks.stream()
                .filter(h -> h instanceof ScreenshotInjectionHook)
                .map(h -> (ScreenshotInjectionHook) h)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 Agent 的 hooks 列表中查找 callRQHook
     */
    private callRQHook findCallRQHook(java.util.List<Hook> hooks) {
        if (hooks == null) return null;
        return hooks.stream()
                .filter(h -> h instanceof callRQHook)
                .map(h -> (callRQHook) h)
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新 Agent 的 callRQHook 中的 step 值
     * <p>
     * 当 Agent 已缓存时，需要更新 hook 中的 step 为工具平台传入的最新值。
     * 这样 resumeTask 时才能从 hook 中获取正确的 step。
     * </p>
     *
     * @param agent  Agent 实例
     * @param step   工具平台传入的 step 值
     */
    private void updateHookStep(ReActAgent agent, Integer step) {
        if (agent == null || step == null) return;

        callRQHook hook = findCallRQHook(agent.getHooks());
        if (hook != null && hook.getTaskNotifyDTO() != null) {
            Integer oldStep = hook.getTaskNotifyDTO().getStep();
            hook.getTaskNotifyDTO().setStep(step);
            log.info("[AgentTaskExecutorService] 已更新 hook 中的 step: {} -> {}", oldStep, step);
        } else {
            log.warn("[AgentTaskExecutorService] 未找到 callRQHook 或 taskNotifyDTO，无法更新 step");
        }
    }


    /**
     * 更新 Agent 的截图（委托给 ScreenshotInjectionHook）
     *
     * @param agent  Agent 实例
     * @param base64 截图的 base64 编码
     */
    private void updateAgentScreenshot(ReActAgent agent, String base64) {
        ScreenshotInjectionHook hook = findScreenshotHook(agent.getHooks());
        if (hook != null) {
            hook.updateScreenshot(base64);
        } else {
            log.warn("[AgentTaskExecutorService] Agent hooks 中未找到 ScreenshotInjectionHook");
        }
    }

    /**
     * 清除 Agent 的截图（委托给 ScreenshotInjectionHook）
     *
     * @param agent Agent 实例
     */
    private void clearAgentScreenshot(ReActAgent agent) {
        ScreenshotInjectionHook hook = findScreenshotHook(agent.getHooks());
        if (hook != null) {
            hook.clearScreenshot();
        }
    }

    /**
     * 从 Msg 中提取文本内容
     * <p>
     * agentscope 的 Msg.getContent() 返回 List&lt;ContentBlock&gt;，
     * 需要遍历找到 TextBlock 并提取文本。
     * </p>
     *
     * @param msg Agent 返回的消息
     * @return 文本内容
     */
    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (var block : msg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 移除 Agent 会话缓存
     * <p>
     * 手动清理 AgentSessionCache 中指定 threadId + agentName 的 Agent 实例。
     * 适用于任务异常终止、缓存残留等场景。
     *
     * @param threadId  会话 ID（flowId）
     * @param agentName Agent 名称
     */
    public void removeCache(String threadId, String agentName) {
        log.info("[AgentTaskExecutorService] 移除缓存，threadId: {}, agentName: {}", threadId, agentName);
        agentSessionCache.remove(threadId, agentName);
    }

    /**
     * 通知工具平台任务完成/失败
     * <p>
     * 任务结束后，向工具平台的回调地址发送 AgentTaskNotifyDTO。
     * 调用方负责构建 DTO 并设置 callbackUrl。
     * </p>
     *
     * @param notifyDTO  通知 DTO（包含任务结果、截图等信息）
     * @param callbackUrl 工具平台回调地址
     */
    public void notifyToolPlatform(AgentTaskNotifyDTO notifyDTO, String callbackUrl) {
        try {
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                log.warn("[AgentTaskExecutorService] 回调地址为空，跳过通知，taskId: {}", notifyDTO.getTaskId());
                return;
            }

            HttpEntity<AgentTaskNotifyDTO> request = new HttpEntity<>(notifyDTO, new HttpHeaders() {{
                setContentType(MediaType.APPLICATION_JSON);
            }});
            restTemplate.postForObject(callbackUrl, request, String.class);
            log.info("[AgentTaskExecutorService] 已通知工具平台，taskId: {},promptId: {}", notifyDTO.getTaskId(),notifyDTO.getPromptsId());
        } catch (Exception e) {
            log.error("[AgentTaskExecutorService] 通知工具平台失败，taskId: {}", notifyDTO.getTaskId(), e);
        }
    }

    /**
     * 实时推送步骤日志给工具平台
     * <p>
     * 在 saveExecutionDetail() 写 DB 的同时，把同一条执行明细实时推送给工具平台。
     * 使用 status="RUNNING" 区分于任务结束时的 SUCCESS/FAILED 回调。
     * </p>
     *
     * @param task 任务实体（用于获取 callbackUrl、promptsId 等任务级字段）
     * @param dto  容器回调 DTO（用于获取 step、toolName、toolInput 等明细字段）
     */
    private void notifyStepLog(AgentTaskEntity task, AgentTaskNotifyDTO dto) {
        try {
            String callbackUrl = task.getCallbackUrl();
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                log.debug("[AgentTaskExecutorService] 回调地址为空，跳过步骤日志推送，taskId: {}", task.getTaskId());
                return;
            }

            // 构建日志专用 DTO，status = "RUNNING" 表示中间步骤
            AgentTaskNotifyDTO logDTO = AgentTaskNotifyDTO.builder()
                    .taskId(task.getTaskId())
                    .sessionId(task.getSessionId())
                    .agentName(task.getAgentName())
                    .instruction(task.getInstruction())
                    .promptsId(task.getPromptsId())
                    .promptType(task.getPromptType())
                    .status("RUNNING")
                    .step(dto.getStep())
                    .toolStep(dto.getToolStep())
                    .toolName(dto.getToolName())
                    .toolInput(dto.getToolInput())
                    .modelOutput(dto.getModelOutput())
                    .screenshotPath(dto.getScreenshotPath())
                    .outputResult(dto.getOutputResult())
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 复用 notifyToolPlatform 发送
            notifyToolPlatform(logDTO, callbackUrl);
        } catch (Exception e) {
            // 步骤日志推送失败不应阻断主流程
            log.error("[AgentTaskExecutorService] 推送步骤日志失败，taskId: {}, step: {}",
                    task.getTaskId(), dto.getToolStep(), e);
        }
    }

    /**
     * 保存执行明细到 agent_execution_detail 表
     * <p>
     * 从 AgentTaskNotifyDTO 提取 step/toolName/toolInput 等字段，
     * 构建 AgentExecutionDetailEntity 并保存。
     * 写入失败不影响主流程（记录日志即可）。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话 ID
     * @param dto       容器回调的完整 DTO
     */
    private void saveExecutionDetail(String taskId, String sessionId, AgentTaskNotifyDTO dto) {
        try {
            // Hook 负责填充 step/toolName/toolInput，这里从 DTO 提取
            // 如果 DTO 中没有这些字段（Hook 还没更新），跳过写入
            if (dto.getToolStep() == null || dto.getToolName() == null) {
                log.debug("[AgentTaskExecutorService] DTO 中缺少 step/toolName，跳过执行明细写入，taskId: {}", taskId);
                return;
            }

            AgentExecutionDetailEntity detail = executionDetailService.toEntity(dto);
            executionDetailService.save(detail);
            log.info("[AgentTaskExecutorService] 执行明细已保存，taskId: {}, step: {}, tool: {}",
                    taskId, dto.getToolStep(), dto.getToolName());
        } catch (Exception e) {
            // 写入明细失败不应阻断主流程，记录日志即可
            log.error("[AgentTaskExecutorService] 保存执行明细失败，taskId: {}, step: {}",
                    taskId, dto.getToolStep(), e);
        }
    }

    /**
     * 保存最后一条执行明细（Agent 正常完成时）
     * <p>
     * Agent 正常完成时，将最终输出作为 modelOutput 写入明细表。
     * 没有 toolName/toolInput（不是工具调用），step 设为 0 表示最终输出。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话 ID
     * @param responseText Agent 最终输出文本
     */
    private void saveFinalExecutionDetail(String taskId, String sessionId, String responseText) {
        try {
            AgentExecutionDetailEntity detail = AgentExecutionDetailEntity.builder()
                    .taskId(taskId)
                    .sessionId(sessionId)
                    .step(0)  // 0 表示最终输出，不是工具调用步骤
                    .modelOutput(responseText)
                    .toolName(null)
                    .toolInput(null)
                    .screenshotPath(null)
                    .build();

            executionDetailService.save(detail);
            log.info("[AgentTaskExecutorService] 最终执行明细已保存，taskId: {}", taskId);
        } catch (Exception e) {
            log.error("[AgentTaskExecutorService] 保存最终执行明细失败，taskId: {}", taskId, e);
        }
    }

}