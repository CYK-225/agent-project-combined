package org.example.common.easyExcel.dto;

import lombok.Data;

/**
 * 工作经历 DTO，用于应聘登记表和入职信息登记表的工作履历部分。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class WorkExperienceDTO {

    /** 起止时间，如 "2020-06 - 2023-03" */
    private String period;

    /** 单位简称 / 工作单位名称 */
    private String company;

    /** 职位 */
    private String position;

    /** 月薪 */
    private String salary;

    /** 公司包餐情况（应聘登记表特有） */
    private String mealBenefit;

    /** 离职原因（应聘登记表特有） */
    private String reasonForLeaving;

    /** 证明人（应聘登记表特有） */
    private String reference;

    /** 证明人职务（应聘登记表特有） */
    private String referenceTitle;

    /** 证明人联系方式（应聘登记表特有） */
    private String referenceContact;

    /** 月薪及其他福利（入职登记表特有） */
    private String salaryAndBenefits;
}
