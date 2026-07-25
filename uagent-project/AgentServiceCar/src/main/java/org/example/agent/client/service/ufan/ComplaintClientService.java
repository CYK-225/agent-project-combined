package org.example.agent.client.service.ufan;


import io.agentscope.core.tool.ToolExecutionContext;
import org.example.agent.annotation.UfanClient;
import org.example.agent.annotation.UfanClientService;
import org.example.agent.recommendStoreMenuAgent.domain.condition.DataAnalyzeCondition;

@UfanClientService
public interface ComplaintClientService {


    @UfanClient("/queryComplaintAnalyze")
    String queryComplaintStat(DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext toolExecutionContext);


    @UfanClient("/queryComplaintDetailList")
    String queryCompanyComplaintDetailList(DataAnalyzeCondition dataAnalyzeCondition, ToolExecutionContext toolExecutionContext);
}
