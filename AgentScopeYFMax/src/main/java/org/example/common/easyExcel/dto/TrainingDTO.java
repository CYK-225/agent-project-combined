package org.example.common.easyExcel.dto;

import lombok.Data;

/**
 * 培训经历 DTO（入职信息登记表专用）。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class TrainingDTO {

    /** 起止时间 */
    private String period;

    /** 学校/培训机构 */
    private String institution;

    /** 专业/培训内容 */
    private String content;

    /** 资格证书 */
    private String certificate;
}
