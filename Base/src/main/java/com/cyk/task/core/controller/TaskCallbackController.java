package com.cyk.task.core.controller;


import com.cyk.task.core.enums.TaskType;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务回调控制器
 * 
 * <p>【核心职责】</p>
 * 接收外部执行器的任务完成回调。
 * SPECIAL类型的回调使用AI回调接口。
 * 
 * <p>【回调接口】</p>
 * <ul>
 *   <li><b>POST /api/task/callback/ens</b>：ENS任务完成回调</li>
 *   <li><b>POST /api/task/callback/ai</b>：AI和SPECIAL任务完成回调</li>
 * </ul>
 *
 * @author system
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/task/callback")
public class TaskCallbackController {

    private final CustomTaskScheduler customTaskScheduler;

    @Autowired
    public TaskCallbackController(CustomTaskScheduler customTaskScheduler) {
        this.customTaskScheduler = customTaskScheduler;
    }

    /**
     * ENS 任务完成回调
     * 
     * <p>Python 端的 ENS 脚本执行完成后调用此接口。</p>
     * 
     * @param request 回调请求
     * @return 响应结果
     */
    @PostMapping("/ens")
    public ResponseEntity<Map<String, Object>> onENSCallback(
            @RequestBody TaskCallbackRequest request) {
        
        log.info("[ENS回调] 收到回调: taskId={}, success={}", 
                request.getTaskId(), request.isSuccess());
        
        return handleCallback(request, TaskType.ENS.getCode());
    }

    /**
     * AI/SPECIAL任务完成回调
     * 
     * <p>SPECIAL类型任务也使用此回调接口</p>
     */
    @PostMapping("/ai")
    public ResponseEntity<Map<String, Object>> onAICallback(
            @RequestBody TaskCallbackRequest request) {
        
        log.info("[AI/SPECIAL回调] 收到回调: taskId={}, success={}, taskType={}",
                request.getTaskId(), request.isSuccess(), request.getTaskType());
        
        // 如果请求中包含taskType，使用它；否则默认为AI
        String taskType = request.getTaskType() != null ?
                request.getTaskType() : TaskType.AI.getCode();
        
        return handleCallback(request, taskType);
    }

    private ResponseEntity<Map<String, Object>> handleCallback(
            TaskCallbackRequest request, String taskType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (request.getTaskId() == null) {
                response.put("success", false);
                response.put("error", "taskId 不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> result = new HashMap<>();
            if (request.getResult() != null) {
                result.putAll(request.getResult());
            }

            if (!request.isSuccess() && request.getErrorMessage() != null) {
                result.put("error", request.getErrorMessage());
            }

            if (request.getAuthId() != null) {
                result.put("authId", request.getAuthId());
            }

            customTaskScheduler.onTaskCompleted(
                    request.getTaskId(), 
                    taskType,
                    request.isSuccess(), 
                    result
            );
            
            response.put("success", true);
            response.put("taskId", request.getTaskId());
            response.put("message", "回调处理成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("处理任务回调失败: taskId={}, taskType={}", 
                    request.getTaskId(), taskType, e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 任务回调请求体
     */
    public static class TaskCallbackRequest {

        private Long taskId;
        private boolean success;
        private Long authId;
        private Map<String, Object> result;
        
        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * 任务类型（用于区分AI和SPECIAL）
         */
        private String taskType;

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Long getAuthId() {
            return authId;
        }

        public void setAuthId(Long authId) {
            this.authId = authId;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(Map<String, Object> result) {
            this.result = result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }
    }
}
