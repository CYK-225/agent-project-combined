package org.example.common.easyExcel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EasyExcel 导出路径配置。
 * <p>
 * 在 application.yml 中配置：
 * <pre>
 * easyexcel:
 *   export-path: C:/Users/陈元科/Desktop/excel-output/
 * </pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
@Component
@ConfigurationProperties(prefix = "easyexcel")
public class EasyExcelProperties {

    /**
     * 导出文件保存到本地的目录路径。
     * 为空则不保存到本地（仅写入 HTTP 响应流）。
     */
    private String exportPath = "";
}
