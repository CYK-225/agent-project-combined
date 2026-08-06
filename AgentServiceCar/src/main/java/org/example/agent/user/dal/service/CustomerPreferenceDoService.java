package org.example.agent.user.dal.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Data
@RequiredArgsConstructor
@Component
public class CustomerPreferenceDoService {
    private final IUserInfoService userInfoService;
    private final IUserPreferenceBiasService userPreferenceBiasService;
    private final IUserCompanyRelationService userCompanyRelationService;
    private final ICompanyInfoService companyInfoService;
}
