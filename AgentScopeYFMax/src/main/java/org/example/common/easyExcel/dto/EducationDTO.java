package org.example.common.easyExcel.dto;

import lombok.Data;

/**
 * 教育背景 DTO。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class EducationDTO {

    /** 起止时间，如 "2016-09 - 2020-06" */
    private String period;

    /** 毕业学校 */
    private String school;

    /** 专业 */
    private String major;

    /** 学历 */
    private String degree;

    /** 是否全日制 */
    private String fullTime;
}
