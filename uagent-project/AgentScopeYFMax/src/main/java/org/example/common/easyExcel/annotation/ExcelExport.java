package org.example.common.easyExcel.annotation;

import java.lang.annotation.*;

/**
 * 标注在 Controller 方法上，将返回值自动写入 Excel 响应流，触发浏览器下载。
 * <p>
 * 由 {@link org.example.common.easyExcel.handler.ExcelExportAdvice} 拦截处理。
 * <p>
 * 支持三种模式：
 * <ol>
 *   <li><b>表单模式</b>：方法同时有 {@code @ExcelImport} 参数时，自动用上传文件当模板写入</li>
 *   <li><b>模板模式</b>：指定 {@link #template()} 时，用指定模板写入</li>
 *   <li><b>动态模式</b>：以上都没有时，返回 {@code List<Map>} 动态生成 Excel</li>
 * </ol>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 导入+导出一步完成：上传的文件当模板，处理完直接下载
 * @ExcelExport(fileName = "审核结果")
 * @PostMapping("/process")
 * public ApplicationFormDTO process(@ExcelImport ApplicationFormDTO form) {
 *     form.setName(form.getName().trim());
 *     return form;  // 自动写成 Excel 下载
 * }
 *
 * // 指定模板导出
 * @ExcelExport(fileName = "报表", template = "templates/report.xlsx")
 * @PostMapping("/export")
 * public ReportDTO export(@RequestBody ReportDTO dto) {
 *     return dto;
 * }
 *
 * // 动态导出：无需 DTO
 * @ExcelExport(fileName = "数据")
 * @GetMapping("/data")
 * public List<Map<String, Object>> exportData() {
 *     return dataService.query();
 * }
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelExport {

    /**
     * 导出文件名（不含扩展名，自动追加 .xlsx）。
     */
    String fileName();

    /**
     * Excel 模板的 classpath 路径。为空则不使用模板。
     * 如果方法同时有 @ExcelImport 参数，优先用上传文件当模板。
     */
    String template() default "";

    /**
     * Sheet 名称。默认 "Sheet1"。
     */
    String sheetName() default "Sheet1";

    /**
     * 是否自动列宽。默认 true。
     */
    boolean autoWidth() default true;
}
