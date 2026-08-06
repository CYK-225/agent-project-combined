package org.example.agent.client.service.ufan;


import io.agentscope.core.tool.ToolExecutionContext;
import org.example.agent.annotation.UfanClient;
import org.example.agent.annotation.UfanClientService;

@UfanClientService
public interface CompanyClientService {

    @UfanClient("/queryCompanyList")
    String queryCompanyList(String name, ToolExecutionContext toolExecutionContext);
}
