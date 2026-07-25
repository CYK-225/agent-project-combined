package org.example.graph.createGraph.engine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图工作流引擎的配置属性。
 * 从 application.yml 中 graph.workflow.engine 下读取。
 */
@Data
@Component
@ConfigurationProperties(prefix = "graph.workflow.engine")
public class GraphEngineProperties {
    private int defaultRecursionLimit = 25;
    private boolean parallelExecutorEnabled = false;
    private String defaultCheckpointStrategy = "memory";
}
