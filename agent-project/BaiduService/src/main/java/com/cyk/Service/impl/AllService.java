package com.cyk.Service.impl;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AllService {

    @Resource
    private SearchByKeywordsService searchByKeywordsService;

    @Resource
    private GetMerchantListService getMerchantListService;

    @Resource
    private SearchCompanyByBuildingService searchCompanyByBuildingService;

    @Resource
    private ENScanApiService enScanApiService;

    @Resource
    private BuildingInitService buildingInitService;

    @Resource
    private PromptsServiceImpl promptsService;

}
