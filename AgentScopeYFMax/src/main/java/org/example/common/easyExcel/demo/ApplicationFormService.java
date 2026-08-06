package org.example.common.easyExcel.demo;

import lombok.extern.slf4j.Slf4j;
import org.example.common.easyExcel.dto.ApplicationFormDTO;
import org.example.common.easyExcel.dto.EducationDTO;
import org.example.common.easyExcel.dto.WorkExperienceDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 应聘登记表业务处理 Service。
 * <p>
 * 演示在实际业务代码中怎么使用解析后的 DTO：
 * <ul>
 *   <li><b>字段判断</b>：校验必填项、格式是否合法</li>
 *   <li><b>字段修改</b>：标准化格式、补全数据</li>
 *   <li><b>字段筛选</b>：过滤无效的工作经历、按条件筛选教育背景</li>
 * </ul>
 *
 * <h3>关键理解：@ExcelCell 注解做了什么</h3>
 * <pre>{@code
 * // DTO 上的注解：
 * @ExcelCell(cellRef = "D8")
 * private String name;
 *
 * // FormExcelHelper.readForm() 内部做的事：
 * // 1. 扫描 DTO 所有字段，找到带 @ExcelCell 的
 * // 2. 把 "D8" 解析成 行号=8, 列号=D
 * // 3. 从 Excel 的 D8 单元格读取值
 * // 4. 通过反射 set 到 name 字段上
 * // 所以你拿到 DTO 后，字段已经有值了，直接用就行
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
@Service
public class ApplicationFormService {

    /**
     * 审核应聘登记表 — 完整的业务处理示例。
     * <p>
     * 展示：字段判断（校验）、字段修改（清洗）、字段筛选（过滤）。
     *
     * @param form 由 FormExcelHelper 通过 @ExcelCell 注解自动解析好的 DTO
     * @return 审核结果
     */
    public Map<String, Object> review(ApplicationFormDTO form) {
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        var fixes = new ArrayList<String>();

        // ======================== 1. 字段判断：校验必填项 ========================

        // 判断姓名是否为空
        if (isBlank(form.getName())) {
            errors.add("姓名不能为空");
        }

        // 判断电话格式是否合法（11位数字）
        if (isBlank(form.getPhone())) {
            errors.add("联系电话不能为空");
        } else if (!form.getPhone().matches("^1[3-9]\\d{9}$")) {
            // 这里先清洗再判断，所以用清洗后的值
            String cleaned = form.getPhone().replaceAll("[^0-9]", "");
            if (cleaned.matches("^1[3-9]\\d{9}$")) {
                fixes.add(STR."电话格式已自动修正: \{form.getPhone()} → \{cleaned}");
                form.setPhone(cleaned);  // 修改字段
            } else {
                errors.add(STR."电话格式不合法: \{form.getPhone()}");
            }
        }

        // 判断身份证号格式
        if (isBlank(form.getIdNumber())) {
            errors.add("身份证号码不能为空");
        } else if (!form.getIdNumber().matches("^\\d{17}[\\dXx]$")) {
            warnings.add(STR."身份证号格式可能有误: \{form.getIdNumber()}");
        }

        // 判断是否有竞业限制
        if ("是".equals(form.getHasNonCompete())) {
            warnings.add("候选人有竞业限制协议，需要法务审核");
        }

        // 判断是否有法律纠纷
        if ("是".equals(form.getHasDispute())) {
            warnings.add("候选人与前公司存在纠纷，需要进一步核实");
        }

        // ======================== 2. 字段修改：清洗和标准化 ========================

        // 修改姓名：去除多余空格
        if (form.getName() != null) {
            String cleaned = form.getName().replaceAll("\\s+", "").trim();
            if (!cleaned.equals(form.getName())) {
                fixes.add(STR."姓名已去除空格: '\{form.getName()}' → '\{cleaned}'");
                form.setName(cleaned);
            }
        }

        // 修改身份证号：统一大写、去空格
        if (form.getIdNumber() != null) {
            String cleaned = form.getIdNumber().replaceAll("\\s+", "").toUpperCase().trim();
            if (!cleaned.equals(form.getIdNumber())) {
                fixes.add("身份证号已标准化");
                form.setIdNumber(cleaned);
            }
        }

        // 修改邮箱：统一小写
        if (form.getEmail() != null) {
            String cleaned = form.getEmail().trim().toLowerCase();
            if (!cleaned.equals(form.getEmail())) {
                fixes.add(STR."邮箱已转小写: \{form.getEmail()} → \{cleaned}");
                form.setEmail(cleaned);
            }
        }

        // ======================== 3. 字段筛选：过滤子表数据 ========================

        // 筛选：只保留有效的工作经历（公司名不为空的）
        if (form.getWorkExperiences() != null) {
            int before = form.getWorkExperiences().size();
            var validExperiences = form.getWorkExperiences().stream()
                    .filter(w -> !isBlank(w.getCompany()))
                    .collect(Collectors.toList());
            form.setWorkExperiences(validExperiences);
            int removed = before - validExperiences.size();
            if (removed > 0) {
                fixes.add(STR."已过滤掉 \{removed} 条空工作经历");
            }
        }

        // 筛选：只保留有效的教育背景
        if (form.getEducations() != null) {
            int before = form.getEducations().size();
            var validEducations = form.getEducations().stream()
                    .filter(e -> !isBlank(e.getSchool()))
                    .collect(Collectors.toList());
            form.setEducations(validEducations);
            int removed = before - validEducations.size();
            if (removed > 0) {
                fixes.add(STR."已过滤掉 \{removed} 条空教育背景");
            }
        }

        // 判断：工作年限是否满足（从工作经历推算）
        int workYears = estimateWorkYears(form.getWorkExperiences());
        if (workYears > 0) {
            log.info("候选人预估工作年限: {} 年", workYears);
            if (workYears < 2) {
                warnings.add(STR."工作年限较短（约\{workYears}年），需要确认是否符合岗位要求");
            }
        }

        // ======================== 4. 汇总结果 ========================

        boolean passed = errors.isEmpty();
        String conclusion = passed
                ? (warnings.isEmpty() ? "审核通过" : "审核通过（有注意事项）")
                : STR."审核不通过（\{errors.size()} 项错误）";

        log.info("应聘登记表审核完成: {}", conclusion);

        return Map.of(
                "passed", passed,
                "conclusion", conclusion,
                "errors", errors,
                "warnings", warnings,
                "fixes", fixes,
                "estimatedWorkYears", workYears
        );
    }

    // ======================== 辅助方法 ========================

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 从工作经历粗略估算工作年限。
     */
    private int estimateWorkYears(List<WorkExperienceDTO> experiences) {
        if (experiences == null || experiences.isEmpty()) return 0;
        // 取最早的一段经历的起始年
        return experiences.stream()
                .map(WorkExperienceDTO::getPeriod)
                .filter(Objects::nonNull)
                .mapToInt(p -> {
                    try {
                        // 粗略取 period 开头的年份，如 "2020-06 - 2023-03"
                        String yearStr = p.substring(0, 4);
                        return java.time.Year.now().getValue() - Integer.parseInt(yearStr);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
    }
}
