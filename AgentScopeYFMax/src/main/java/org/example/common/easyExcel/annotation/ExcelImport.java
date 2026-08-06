package org.example.common.easyExcel.annotation;

import java.lang.annotation.*;

/**
 * 标注在 Controller 方法参数上，自动将上传的 Excel 解析为 Java 对象。
 * <p>
 * 由 {@link org.example.common.easyExcel.handler.ExcelImportResolver} 自动处理。
 * <p>
 * 参数类型决定解析模式：
 * <ul>
 *   <li>参数是具体 DTO 类（如 {@code ApplicationFormDTO}）→ 表单模式，通过 {@code @ExcelCell} 注解读取</li>
 *   <li>参数是 {@code List<Map<String, String>>} → 动态模式，自动识别表头</li>
 * </ul>
 * <p>
 * 不需要在参数上写 {@code MultipartFile}，框架自动从请求中获取上传文件。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 表单模式 — 参数直接是 DTO，不用写 MultipartFile
 * @PostMapping("/import")
 * public Result handle(@ExcelImport ApplicationFormDTO form) {
 *     String name = form.getName();  // 已经有值了
 *     return Result.ok(form);
 * }
 *
 * // 动态模式 — 任意 Excel 都能读
 * @PostMapping("/import")
 * public Result handle(@ExcelImport List<Map<String, String>> rows) {
 *     rows.forEach(row -> System.out.println(row.get("姓名")));
 *     return Result.ok(rows);
 * }
 *
 * // 配合 @ExcelExport 一步完成导入+导出
 * @ExcelExport(fileName = "审核结果")
 * @PostMapping("/process")
 * public ApplicationFormDTO process(@ExcelImport ApplicationFormDTO form) {
 *     form.setName(form.getName().trim());
 *     return form;
 * }
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelImport {

    /**
     * Sheet 索引，从 0 开始。默认 0（第一个 Sheet）。
     */
    int sheetIndex() default 0;

    /**
     * 表头所在行号（从 1 开始）。默认 1。仅动态模式有效。
     */
    int headRowNumber() default 1;
}
