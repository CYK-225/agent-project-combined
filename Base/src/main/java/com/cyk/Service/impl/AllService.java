package com.cyk.Service.impl;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AllService {

    @Resource
    private ENScanApiService enScanApiService;


    @Resource
    private PromptsServiceImpl promptsService;

}
