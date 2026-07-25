package org.example.agentScope.util.plan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "plan.notebook")
public class PlanNotebookProperties {
    // 对应 YAML 中的 plan.notebook.default-max-subtasks
    private Integer defaultMaxSubtasks = 10;
    // 对应 YAML 中的 plan.notebook.default-need-confirm
    private Boolean defaultNeedConfirm = false;

}
