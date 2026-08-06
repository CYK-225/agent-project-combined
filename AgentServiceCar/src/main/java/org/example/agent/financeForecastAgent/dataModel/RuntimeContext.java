
package org.example.agent.financeForecastAgent.dataModel;


import io.agentscope.core.state.StateModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 业务运行时上下文 - 实现 StateModule 以支持自动持久化
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RuntimeContext implements StateModule {

    private String skillDir;

    private String skillName;
}