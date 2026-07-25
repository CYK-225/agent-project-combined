package org.example.common.easyExcel.annotation;

import java.lang.annotation.*;

/**
 * 标注在 DTO 字段上，标记该字段在 Excel 表单中的单元格位置。
 * <p>
 * 用于 {@link org.example.common.easyExcel.FormExcelHelper} 自动从表单式 Excel 中读取字段值。
 * 支持标准单元格引用（如 "C4"、"B12"）。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Data
 * public class UserFormDTO {
 *     @ExcelCell(cellRef = "C4")
 *     private String name;
 *
 *     @ExcelCell(cellRef = "E4")
 *     private String gender;
 * }
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelCell {

    /**
     * 单元格引用，如 "C4"、"B12"。
     * 列用字母表示（A=1, B=2, ...），行用数字表示。
     */
    String cellRef();
}
