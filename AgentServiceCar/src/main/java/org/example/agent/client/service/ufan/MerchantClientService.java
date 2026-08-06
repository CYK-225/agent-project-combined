package org.example.agent.client.service.ufan;


import io.agentscope.core.tool.ToolExecutionContext;
import org.example.agent.annotation.UfanClient;
import org.example.agent.annotation.UfanClientService;

@UfanClientService
public interface MerchantClientService {


    @UfanClient("/searchMerchantStore")
    String searchStore(String name, ToolExecutionContext toolExecutionContext);

    @UfanClient("/getMerchantFullInfo")
    String getMerchantFullInfo(Long merchantStoreId, ToolExecutionContext toolExecutionContext);

    @UfanClient("/getStoreRecommendMenu")
    String getStoreRecommendMenu(Long merchantStoreId, String beginDate,String endDate,ToolExecutionContext toolExecutionContext);


}
