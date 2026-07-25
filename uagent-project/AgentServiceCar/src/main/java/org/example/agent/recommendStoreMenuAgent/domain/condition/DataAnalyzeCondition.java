package org.example.agent.recommendStoreMenuAgent.domain.condition;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
public class DataAnalyzeCondition implements Serializable {


    @JsonPropertyDescription("公司id列表")
    private List<Long> companyIdList;


    @JsonPropertyDescription("商家id列表")
    private List<Long> merchantStoreIdList;


    @JsonPropertyDescription("查询开始日期，格式yyyy-MM-dd")
    private String beginDate;

    @JsonPropertyDescription("查询结束日期，格式yyyy-MM-dd")
    private String endDate;


    @JsonPropertyDescription("餐段列表，枚举值：1=早餐,2=午餐,3=晚餐,4=宵夜,5=下午茶,6=午晚餐")
    private List<Integer> intervalNoList;

    @JsonPropertyDescription("分组类型，必传参数，若未告知，根据情况按公司id或按商家，可选值：0=按公司ID，1=按商家ID，2=按日期分组，3=按餐段分组，4=按公司ID、商家ID分组")
    private Integer groupByType;
}
