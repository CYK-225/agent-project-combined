package org.example.common.easyExcel.dto;

import lombok.Data;

/**
 * 主要家庭成员 DTO（入职信息登记表专用）。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class FamilyMemberDTO {

    /** 姓名 */
    private String name;

    /** 关系 */
    private String relation;

    /** 工作单位 */
    private String workplace;

    /** 职务 */
    private String title;

    /** 联系方式 */
    private String contact;
}
