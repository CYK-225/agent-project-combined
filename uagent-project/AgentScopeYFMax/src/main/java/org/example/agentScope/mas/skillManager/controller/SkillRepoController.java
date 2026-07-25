package org.example.agentScope.mas.skillManager.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.config.CustomThreadSessionManager;
import org.example.agentScope.mas.skillManager.core.GitSkillManager;
import org.example.agentScope.framework.core.SkillRepoRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Skill 仓库管理 REST API。
 * <p>
 * 提供仓库地址绑定、解绑、查询、刷新能力。
 * 刷新时会清除对应的 AG-UI 会话缓存，下次请求自动重建 Agent。
 *
 * @author AgentScope-Team
 */
@Slf4j
@RestController
@RequestMapping("/api/skill-repo")
@RequiredArgsConstructor
public class SkillRepoController {

    private final SkillRepoRegistry skillRepoRegistry;
    private final GitSkillManager gitSkillManager;
    private final CustomThreadSessionManager sessionManager;

    // ======================== DTO ========================

    public record BindRequest(
            String agentName,
            String repoUrl,
            String[] skillPatterns   // 正则过滤，null 或空 = 加载全部
    ) {}

    public record RefreshRequest(
            String agentName,
            String threadId,
            String repoUrl,
            String[] skillPatterns   // 正则过滤，传了会更新
    ) {}

    // ======================== API ========================

    /**
     * 绑定仓库地址到指定 Agent
     * <p>
     * 示例：
     * <pre>{@code
     * // 绑定 + 加载全部
     * {"agentName": "DataAnalyst", "repoUrl": "https://..."}
     *
     * // 绑定 + 正则过滤
     * {"agentName": "DataAnalyst", "repoUrl": "https://...", "skillPatterns": ["data-.*", "security"]}
     * }</pre>
     */
    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bind(@RequestBody BindRequest request) {
        if (request.agentName() == null || request.agentName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "agentName is required"));
        }
        if (request.repoUrl() == null || request.repoUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repoUrl is required"));
        }

        log.info("🔗 Dynamic binding request received:");
        log.info("   agentName: {}", request.agentName());
        log.info("   repoUrl: {}", request.repoUrl());
        log.info("   skillPatterns: {}", request.skillPatterns() != null ? Arrays.toString(request.skillPatterns()) : "all");

        skillRepoRegistry.put(request.agentName(), request.repoUrl(), request.skillPatterns());

        // ⚠️ 重要：绑定后清除该 Agent 的所有会话缓存，确保下次请求时使用新的绑定
        log.info("🔄 Clearing cached sessions for agent: {}...", request.agentName());
        int removedSessions = sessionManager.removeAllSessionsForAgent(request.agentName());

        log.info("✅ Dynamic binding completed successfully:");
        log.info("   📌 Agent: {} → Repo: {}", request.agentName(), request.repoUrl());
        log.info("   🗑️ Cleared {} cached sessions", removedSessions);
        log.info("   💡 Next request will rebuild Agent with new Skills");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "agentName", request.agentName(),
                "repoUrl", request.repoUrl(),
                "skillPatterns", request.skillPatterns() != null
                        ? Arrays.toString(request.skillPatterns()) : "[]",
                "clearedSessions", removedSessions
        ));
    }

    /**
     * 解绑仓库地址
     */
    @DeleteMapping("/unbind/{agentName}")
    public ResponseEntity<Map<String, Object>> unbind(@PathVariable String agentName) {
        log.info("🔓 Unbind request received for agent: {}", agentName);

        String removedRepo = skillRepoRegistry.getRepoUrl(agentName);
        skillRepoRegistry.remove(agentName);

        log.info("✅ Unbind completed:");
        log.info("   🗑️ Agent: {} unbound from repo: {}", agentName, removedRepo);
        log.info("   💡 Agent will use default skill configuration");

        return ResponseEntity.ok(Map.of("success", true, "agentName", agentName));
    }

    /**
     * 查看所有绑定
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, String>> list() {
        return ResponseEntity.ok(skillRepoRegistry.getAll());
    }

    /**
     * 刷新仓库 + 更新过滤规则 + 重建指定会话的 Agent。
     * <p>
     * 流程：
     * 1. 如果传了新的 repoUrl / skillPatterns，更新 Registry
     * 2. gitSkillManager 刷新仓库（pull + 重新解析）
     * 3. 清除指定 threadId 的会话缓存
     * 4. 用户下次发消息时自动重建 Agent
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        if (request.agentName() == null || request.agentName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "agentName is required"));
        }

        log.info("🔄 Refresh request received:");
        log.info("   agentName: {}", request.agentName());
        log.info("   threadId: {}", request.threadId());
        log.info("   repoUrl: {}", request.repoUrl());
        log.info("   skillPatterns: {}", request.skillPatterns() != null ? Arrays.toString(request.skillPatterns()) : "unchanged");

        // 1. 更新绑定（如果传了新值）
        String repoUrl = request.repoUrl();
        if (repoUrl != null && !repoUrl.isBlank()) {
            skillRepoRegistry.put(request.agentName(), repoUrl, request.skillPatterns());
            log.info("   📌 Updated binding: {} → {}", request.agentName(), repoUrl);
        } else {
            repoUrl = skillRepoRegistry.getRepoUrl(request.agentName());
            // 只更新 skillPatterns
            if (request.skillPatterns() != null) {
                skillRepoRegistry.put(request.agentName(), repoUrl, request.skillPatterns());
                log.info("   📌 Updated skillPatterns for: {}", request.agentName());
            }
        }

        // 2. 刷新 Git 仓库
        if (repoUrl != null) {
            log.info("   🔄 Refreshing Git skills from repo: {}", repoUrl);
            gitSkillManager.refreshSkills(repoUrl);
            log.info("   ✅ Git skills refreshed successfully");
        }

        // 3. 清除会话缓存（重建 Agent）
        if (request.threadId() != null && !request.threadId().isBlank()) {
            log.info("   🗑️ Clearing session cache for threadId: {}", request.threadId());
            sessionManager.removeSession(request.threadId());
            log.info("   ✅ Session cache cleared - next request will rebuild Agent");
        }

        // 构建响应（Map.of 不允许 null 值，需要处理）
        var response = new java.util.HashMap<String, Object>();
        response.put("success", true);
        response.put("agentName", request.agentName());
        response.put("repoUrl", repoUrl != null ? repoUrl : "unchanged");
        response.put("skillPatterns", request.skillPatterns() != null
                ? Arrays.toString(request.skillPatterns()) : "unchanged");
        response.put("threadId", request.threadId() != null ? request.threadId() : "none");

        log.info("✅ Refresh completed successfully for agent: {}", request.agentName());

        return ResponseEntity.ok(response);
    }

    /**
     * 查看仓库中的 Skill 列表（不触发刷新）
     */
    @GetMapping("/skills")
    public ResponseEntity<List<String>> listSkills(@RequestParam String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitSkillManager.listSkillNames(repoUrl));
    }
}
