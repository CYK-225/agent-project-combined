package org.example.common.easyExcel.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.easyExcel.annotation.ExcelExport;
import org.example.common.easyExcel.annotation.ExcelImport;
import org.example.common.easyExcel.demo.ApplicationFormService;
import org.example.common.easyExcel.dto.ApplicationFormDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * EasyExcel 注解驱动测试控制器。
 * <p>
 * 展示三种用法：
 * <ol>
 *   <li>纯导入：{@code @ExcelImport} 参数注解自动解析上传文件</li>
 *   <li>导入+导出一步完成：{@code @ExcelImport} + {@code @ExcelExport} 组合</li>
 *   <li>动态模式：{@code @ExcelImport} 解析任意 Excel 为 {@code List<Map>}</li>
 * </ol>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class EasyExcelTestController {

    private final ApplicationFormService applicationFormService;

    // ======================== 1. 纯导入：只读取，返回 JSON ========================

    /**
     * 上传应聘登记表，@ExcelImport 自动解析成 DTO。
     * <p>
     * 注意参数类型是 ApplicationFormDTO，不是 MultipartFile。
     * 框架自动从请求中取文件，通过 @ExcelCell 注解读取各字段。
     * <pre>{@code
     * curl -X POST http://localhost:8080/api/excel/import \
     *   -F "file=@应聘登记表.xlsx"
     * }</pre>
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importForm(
            @ExcelImport ApplicationFormDTO form) {

        // 到这里 form 已经有值了，直接用
        log.info("自动解析完成 — 姓名: {}, 职位: {}, 电话: {}",
                form.getName(), form.getPosition(), form.getPhone());

        return ResponseEntity.ok(Map.of(
                "姓名", form.getName() != null ? form.getName() : "",
                "职位", form.getPosition() != null ? form.getPosition() : "",
                "电话", form.getPhone() != null ? form.getPhone() : "",
                "工作经历", form.getWorkExperiences(),
                "教育背景", form.getEducations()
        ));
    }

    // ======================== 2. 导入+导出一步完成 ========================

    /**
     * 上传应聘登记表 → 自动解析 → 审核/修改字段 → 导出新 Excel 下载。
     * <p>
     * @ExcelImport 自动解析上传文件到 form 参数
     * @ExcelExport 自动把返回的 form 写成 Excel 下载
     * 上传的文件自动被复用为导出模板
     * <pre>{@code
     * curl -X POST http://localhost:8080/api/excel/process \
     *   -F "file=@应聘登记表.xlsx" --output processed.xlsx
     * }</pre>
     */
    @ExcelExport(fileName = "审核后的应聘登记表")
    @PostMapping("/process")
    public ApplicationFormDTO processForm(
            @ExcelImport ApplicationFormDTO form) {

        // === 对字段做判断 ===
        if (form.getName() == null || form.getName().isBlank()) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (form.getPhone() != null && !form.getPhone().matches(".*\\d.*")) {
            throw new IllegalArgumentException("电话格式不合法");
        }

        // === 对字段做修改 ===
        form.setName(form.getName().replaceAll("\\s+", "").trim());
        if (form.getPhone() != null) {
            form.setPhone(form.getPhone().replaceAll("[^0-9]", ""));
        }
        if (form.getEmail() != null) {
            form.setEmail(form.getEmail().trim().toLowerCase());
        }
        if (form.getIdNumber() != null) {
            form.setIdNumber(form.getIdNumber().replaceAll("\\s+", "").toUpperCase());
        }

        // === 对子表做筛选 ===
        if (form.getWorkExperiences() != null) {
            form.setWorkExperiences(form.getWorkExperiences().stream()
                    .filter(w -> w.getCompany() != null && !w.getCompany().isBlank())
                    .toList());
        }

        // return 的 form 会被 @ExcelExport 自动写成 Excel 下载
        return form;
    }

    /**
     * 完整审核流程：解析 → 校验 → 清洗 → 返回审核结果 + 新 Excel。
     * <pre>{@code
     * curl -X POST http://localhost:8080/api/excel/review \
     *   -F "file=@应聘登记表.xlsx" --output reviewed.xlsx
     * }</pre>
     */
    @ExcelExport(fileName = "应聘登记表_已审核")
    @PostMapping("/review")
    public ApplicationFormDTO reviewForm(
            @ExcelImport ApplicationFormDTO form) {

        // 调用 Service 做完整业务处理（校验、清洗、筛选）
        applicationFormService.review(form);

        // 返回处理后的 DTO，@ExcelExport 自动写成 Excel
        return form;
    }

    // ======================== 3. 动态模式：任意 Excel ========================

    /**
     * 动态导入：上传任意 Excel，自动识别表头，返回 List&lt;Map&gt;。
     * <pre>{@code
     * curl -X POST http://localhost:8080/api/excel/dynamic-import \
     *   -F "file=@任意文件.xlsx"
     * }</pre>
     */
    @PostMapping("/dynamic-import")
    public ResponseEntity<Map<String, Object>> dynamicImport(
            @ExcelImport List<Map<String, String>> rows) {

        // rows 已经自动解析好了，每行一个 Map，key 是表头
        log.info("动态读取完成，{} 行数据", rows.size());

        // 对行数据做筛选：只保留"姓名"列不为空的行
        var filtered = rows.stream()
                .filter(row -> {
                    var name = row.get("姓名");
                    return name != null && !name.isBlank();
                })
                .toList();

        // 对字段做修改：所有姓名去空格
        filtered.forEach(row -> {
            var name = row.get("姓名");
            if (name != null) {
                row.put("姓名", name.trim());
            }
        });

        return ResponseEntity.ok(Map.of(
                "totalRows", rows.size(),
                "filteredRows", filtered.size(),
                "data", filtered
        ));
    }

    /**
     * 动态导出：POST JSON → 下载 Excel。
     * <pre>{@code
     * curl -X POST http://localhost:8080/api/excel/dynamic-export \
     *   -H "Content-Type: application/json" \
     *   -d '[{"姓名":"张三","年龄":"25"},{"姓名":"李四","年龄":"30"}]' \
     *   --output export.xlsx
     * }</pre>
     */
    @ExcelExport(fileName = "导出数据")
    @PostMapping("/dynamic-export")
    public List<Map<String, Object>> dynamicExport(
            @RequestBody List<Map<String, Object>> data) {
        // 返回值被 @ExcelExport 自动写成 Excel，Map key 作为表头
        return data;
    }
}
