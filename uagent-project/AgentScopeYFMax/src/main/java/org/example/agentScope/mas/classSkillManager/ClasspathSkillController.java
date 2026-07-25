package org.example.agentScope.mas.classSkillManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Classpath Skill 资源管理 REST API。
 * <p>
 * 查看 classpath 中预打包的 Skill 资源。只读，不可修改。
 *
 * @author AgentScope-Team
 */
@Slf4j
@RestController
@RequestMapping("/api/classpath-skill")
@RequiredArgsConstructor
public class ClasspathSkillController {

    private final ClasspathSkillManager classpathSkillManager;

    /**
     * 查看指定资源路径下的 Skill 名称列表
     * <p>
     * 示例：GET /api/classpath-skill/skills?resourcePath=skills
     */
    @GetMapping("/skills")
    public ResponseEntity<List<String>> listSkills(@RequestParam String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(classpathSkillManager.listSkillNames(resourcePath));
    }

    /**
     * 刷新指定资源路径的缓存（用于开发热重载场景）
     * <p>
     * 示例：POST /api/classpath-skill/refresh?resourcePath=skills
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestParam String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        classpathSkillManager.refreshSkills(resourcePath);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "resourcePath", resourcePath
        ));
    }
}
