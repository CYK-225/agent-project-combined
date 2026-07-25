package org.example.common.easyExcel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.common.easyExcel.annotation.ExcelCell;
import org.example.common.easyExcel.dto.*;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 表单式 Excel 读取/写入工具。
 * <p>
 * 针对非标准行式 Excel（如应聘登记表、入职信息登记表等表单），
 * 通过 {@link ExcelCell} 注解映射字段到单元格位置，自动完成读取。
 * 同时支持子表区域（工作经历、教育背景等）的列表数据读取。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 读取应聘登记表
 * ApplicationFormDTO form = FormExcelHelper.readForm(
 *     new FileInputStream("应聘登记表.xlsx"),
 *     ApplicationFormDTO.class
 * );
 *
 * // 读取入职信息登记表
 * OnboardingFormDTO onboard = FormExcelHelper.readForm(
 *     new FileInputStream("入职信息登记表.xlsx"),
 *     OnboardingFormDTO.class
 * );
 *
 * // 写入到新文件（基于模板）
 * FormExcelHelper.writeForm(templateStream, outputPath, form);
 * }</pre>
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Slf4j
public final class FormExcelHelper {

    private FormExcelHelper() {
        // 工具类，禁止实例化
    }

    // ======================== 表单读取 ========================

    /**
     * 从 Excel 输入流读取表单数据到 DTO。
     * 自动扫描 DTO 字段上的 {@link ExcelCell} 注解，按单元格位置读取值。
     *
     * @param inputStream Excel 文件输入流
     * @param dtoClass    DTO 类型
     * @param <T>         DTO 类型
     * @return 填充好的 DTO 对象
     */
    public static <T> T readForm(InputStream inputStream, Class<T> dtoClass) {
        try (var workbook = new XSSFWorkbook(inputStream)) {
            var sheet = workbook.getSheetAt(0);
            var dto = dtoClass.getDeclaredConstructor().newInstance();
            fillAnnotatedFields(sheet, dto);
            fillSubTables(sheet, dto);
            return dto;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(STR."\{dtoClass.getSimpleName()} 需要无参构造函数", e);
        } catch (Exception e) {
            throw new RuntimeException(STR."读取 Excel 表单失败: \{e.getMessage()}", e);
        }
    }

    /**
     * 从文件路径读取表单。
     */
    public static <T> T readForm(String filePath, Class<T> dtoClass) {
        try (var is = new FileInputStream(filePath)) {
            return readForm(is, dtoClass);
        } catch (IOException e) {
            throw new RuntimeException(STR."读取文件失败: \{filePath}", e);
        }
    }

    /**
     * 从 File 对象读取表单。
     */
    public static <T> T readForm(File file, Class<T> dtoClass) {
        try (var is = new FileInputStream(file)) {
            return readForm(is, dtoClass);
        } catch (IOException e) {
            throw new RuntimeException(STR."读取文件失败: \{file.getPath()}", e);
        }
    }

    // ======================== 表单写入（模板填充） ========================

    /**
     * 基于模板将 DTO 数据写入新 Excel 文件。
     * DTO 字段上的 {@link ExcelCell} 注解决定写入的单元格位置。
     *
     * @param templateStream 模板文件输入流
     * @param outputPath     输出文件路径
     * @param dto            数据 DTO
     * @param <T>            DTO 类型
     */
    public static <T> void writeForm(InputStream templateStream,
                                     String outputPath,
                                     T dto) {
        try (var workbook = new XSSFWorkbook(templateStream)) {
            var sheet = workbook.getSheetAt(0);
            writeAnnotatedFields(sheet, dto);
            writeSubTables(sheet, dto);
            try (var fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            throw new RuntimeException(STR."写入 Excel 表单失败: \{e.getMessage()}", e);
        }
    }

    /**
     * 基于模板将 DTO 数据写入 OutputStream。
     */
    public static <T> void writeForm(InputStream templateStream,
                                     OutputStream outputStream,
                                     T dto) {
        try (var workbook = new XSSFWorkbook(templateStream)) {
            var sheet = workbook.getSheetAt(0);
            writeAnnotatedFields(sheet, dto);
            writeSubTables(sheet, dto);
            workbook.write(outputStream);
        } catch (Exception e) {
            throw new RuntimeException(STR."写入 Excel 表单失败: \{e.getMessage()}", e);
        }
    }

    /**
     * 基于模板将 DTO 数据写入字节数组（适合直接返回给前端）。
     */
    public static <T> byte[] writeFormToBytes(InputStream templateStream, T dto) {
        try (var workbook = new XSSFWorkbook(templateStream);
             var baos = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            writeAnnotatedFields(sheet, dto);
            writeSubTables(sheet, dto);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(STR."写入 Excel 表单失败: \{e.getMessage()}", e);
        }
    }

    /**
     * 基于模板文件路径写入。
     */
    public static <T> void writeForm(String templatePath,
                                     String outputPath,
                                     T dto) {
        try (var is = new FileInputStream(templatePath)) {
            writeForm(is, outputPath, dto);
        } catch (IOException e) {
            throw new RuntimeException(STR."读取模板文件失败: \{templatePath}", e);
        }
    }

    // ======================== 内部实现：读取 ========================

    /**
     * 扫描 DTO 中带 @ExcelCell 注解的字段，从 Sheet 对应位置读取值。
     */
    private static void fillAnnotatedFields(Sheet sheet, Object dto) throws IllegalAccessException {
        for (var field : dto.getClass().getDeclaredFields()) {
            var annotation = field.getAnnotation(ExcelCell.class);
            if (annotation == null) continue;

            var cellRef = annotation.cellRef();
            var pos = parseCellRef(cellRef);
            var row = sheet.getRow(pos[0]);
            if (row == null) continue;

            var cell = row.getCell(pos[1]);
            var value = getCellValueAsString(cell);
            if (value != null && !value.isEmpty()) {
                field.setAccessible(true);
                field.set(dto, value);
            }
        }
    }

    /**
     * 根据表单类型，填充子表（工作经历、教育背景等列表数据）。
     */
    private static void fillSubTables(Sheet sheet, Object dto) {
        switch (dto) {
            case ApplicationFormDTO appForm -> {
                appForm.setWorkExperiences(readApplicationWorkExperiences(sheet));
                appForm.setEducations(readApplicationEducations(sheet));
            }
            case OnboardingFormDTO onboardForm -> {
                onboardForm.setEducations(readOnboardingEducations(sheet));
                onboardForm.setTrainings(readOnboardingTrainings(sheet));
                onboardForm.setWorkExperiences(readOnboardingWorkExperiences(sheet));
                onboardForm.setFamilyMembers(readOnboardingFamilyMembers(sheet));
            }
            default -> log.debug("未识别的 DTO 类型，跳过子表填充: {}", dto.getClass().getSimpleName());
        }
    }

    // ======================== 应聘登记表 子表读取 ========================

    /** 应聘登记表工作履历：Row 17-19, 列 B-K */
    private static List<WorkExperienceDTO> readApplicationWorkExperiences(Sheet sheet) {
        var list = new ArrayList<WorkExperienceDTO>();
        for (int rowNum = 17; rowNum <= 19; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var exp = new WorkExperienceDTO();
            exp.setPeriod(getCellStr(row, 1));   // B
            exp.setCompany(getCellStr(row, 3));   // D
            exp.setPosition(getCellStr(row, 4));  // E
            exp.setSalary(getCellStr(row, 5));    // F
            exp.setMealBenefit(getCellStr(row, 6)); // G
            exp.setReasonForLeaving(getCellStr(row, 7)); // H
            exp.setReference(getCellStr(row, 8)); // I
            exp.setReferenceTitle(getCellStr(row, 9)); // J
            exp.setReferenceContact(getCellStr(row, 10)); // K
            list.add(exp);
        }
        return list;
    }

    /** 应聘登记表教育背景：Row 23-24, 列 B, D, F, H, J */
    private static List<EducationDTO> readApplicationEducations(Sheet sheet) {
        var list = new ArrayList<EducationDTO>();
        for (int rowNum = 23; rowNum <= 24; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var edu = new EducationDTO();
            edu.setPeriod(getCellStr(row, 1));   // B
            edu.setSchool(getCellStr(row, 3));   // D
            edu.setMajor(getCellStr(row, 5));    // F
            edu.setDegree(getCellStr(row, 7));   // H
            edu.setFullTime(getCellStr(row, 9)); // J
            list.add(edu);
        }
        return list;
    }

    // ======================== 入职信息登记表 子表读取 ========================

    /** 入职登记表教育背景：Row 13-14, 列 B, C, F, H, J */
    private static List<EducationDTO> readOnboardingEducations(Sheet sheet) {
        var list = new ArrayList<EducationDTO>();
        for (int rowNum = 13; rowNum <= 14; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var edu = new EducationDTO();
            edu.setPeriod(getCellStr(row, 1));   // B
            edu.setSchool(getCellStr(row, 2));   // C
            edu.setMajor(getCellStr(row, 5));    // F
            edu.setDegree(getCellStr(row, 7));   // H
            edu.setFullTime(getCellStr(row, 9)); // J
            list.add(edu);
        }
        return list;
    }

    /** 入职登记表培训经历：Row 17-18, 列 B, C, F, I */
    private static List<TrainingDTO> readOnboardingTrainings(Sheet sheet) {
        var list = new ArrayList<TrainingDTO>();
        for (int rowNum = 17; rowNum <= 18; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var t = new TrainingDTO();
            t.setPeriod(getCellStr(row, 1));      // B
            t.setInstitution(getCellStr(row, 2)); // C
            t.setContent(getCellStr(row, 5));     // F
            t.setCertificate(getCellStr(row, 8)); // I
            list.add(t);
        }
        return list;
    }

    /** 入职登记表工作履历：Row 21-23, 列 B, C, F, I */
    private static List<WorkExperienceDTO> readOnboardingWorkExperiences(Sheet sheet) {
        var list = new ArrayList<WorkExperienceDTO>();
        for (int rowNum = 21; rowNum <= 23; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var exp = new WorkExperienceDTO();
            exp.setPeriod(getCellStr(row, 1));      // B
            exp.setCompany(getCellStr(row, 2));     // C
            exp.setPosition(getCellStr(row, 5));    // F
            exp.setSalaryAndBenefits(getCellStr(row, 8)); // I
            list.add(exp);
        }
        return list;
    }

    /** 入职登记表家庭成员：Row 26-27, 列 C, E, F, H, I */
    private static List<FamilyMemberDTO> readOnboardingFamilyMembers(Sheet sheet) {
        var list = new ArrayList<FamilyMemberDTO>();
        for (int rowNum = 26; rowNum <= 27; rowNum++) {
            var row = sheet.getRow(rowNum - 1);
            if (row == null) continue;
            var member = new FamilyMemberDTO();
            member.setName(getCellStr(row, 2));     // C
            member.setRelation(getCellStr(row, 4)); // E
            member.setWorkplace(getCellStr(row, 5)); // F
            member.setTitle(getCellStr(row, 7));    // H
            member.setContact(getCellStr(row, 8));  // I
            list.add(member);
        }
        return list;
    }

    // ======================== 内部实现：写入 ========================

    /**
     * 将 DTO 中带 @ExcelCell 注解的字段值写入 Sheet 对应位置。
     */
    private static void writeAnnotatedFields(Sheet sheet, Object dto) throws IllegalAccessException {
        for (var field : dto.getClass().getDeclaredFields()) {
            var annotation = field.getAnnotation(ExcelCell.class);
            if (annotation == null) continue;

            field.setAccessible(true);
            var value = field.get(dto);
            if (value == null) continue;

            var pos = parseCellRef(annotation.cellRef());
            var row = sheet.getRow(pos[0]);
            if (row == null) {
                row = sheet.createRow(pos[0]);
            }
            var cell = row.getCell(pos[1]);
            if (cell == null) {
                cell = row.createCell(pos[1]);
            }
            cell.setCellValue(value.toString());
        }
    }

    /**
     * 根据表单类型，写入子表数据。
     */
    private static void writeSubTables(Sheet sheet, Object dto) {
        switch (dto) {
            case ApplicationFormDTO appForm -> {
                writeApplicationWorkExperiences(sheet, appForm.getWorkExperiences());
                writeApplicationEducations(sheet, appForm.getEducations());
            }
            case OnboardingFormDTO onboardForm -> {
                writeOnboardingEducations(sheet, onboardForm.getEducations());
                writeOnboardingTrainings(sheet, onboardForm.getTrainings());
                writeOnboardingWorkExperiences(sheet, onboardForm.getWorkExperiences());
                writeOnboardingFamilyMembers(sheet, onboardForm.getFamilyMembers());
            }
            default -> log.debug("未识别的 DTO 类型，跳过子表写入: {}", dto.getClass().getSimpleName());
        }
    }

    // ======================== 应聘登记表 子表写入 ========================

    private static void writeApplicationWorkExperiences(Sheet sheet, List<WorkExperienceDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 3); i++) {
            int rowNum = 17 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var exp = list.get(i);
            setCellStr(row, 1, exp.getPeriod());
            setCellStr(row, 3, exp.getCompany());
            setCellStr(row, 4, exp.getPosition());
            setCellStr(row, 5, exp.getSalary());
            setCellStr(row, 6, exp.getMealBenefit());
            setCellStr(row, 7, exp.getReasonForLeaving());
            setCellStr(row, 8, exp.getReference());
            setCellStr(row, 9, exp.getReferenceTitle());
            setCellStr(row, 10, exp.getReferenceContact());
        }
    }

    private static void writeApplicationEducations(Sheet sheet, List<EducationDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 2); i++) {
            int rowNum = 23 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var edu = list.get(i);
            setCellStr(row, 1, edu.getPeriod());
            setCellStr(row, 3, edu.getSchool());
            setCellStr(row, 5, edu.getMajor());
            setCellStr(row, 7, edu.getDegree());
            setCellStr(row, 9, edu.getFullTime());
        }
    }

    // ======================== 入职信息登记表 子表写入 ========================

    private static void writeOnboardingEducations(Sheet sheet, List<EducationDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 2); i++) {
            int rowNum = 13 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var edu = list.get(i);
            setCellStr(row, 1, edu.getPeriod());
            setCellStr(row, 2, edu.getSchool());
            setCellStr(row, 5, edu.getMajor());
            setCellStr(row, 7, edu.getDegree());
            setCellStr(row, 9, edu.getFullTime());
        }
    }

    private static void writeOnboardingTrainings(Sheet sheet, List<TrainingDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 2); i++) {
            int rowNum = 17 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var t = list.get(i);
            setCellStr(row, 1, t.getPeriod());
            setCellStr(row, 2, t.getInstitution());
            setCellStr(row, 5, t.getContent());
            setCellStr(row, 8, t.getCertificate());
        }
    }

    private static void writeOnboardingWorkExperiences(Sheet sheet, List<WorkExperienceDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 3); i++) {
            int rowNum = 21 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var exp = list.get(i);
            setCellStr(row, 1, exp.getPeriod());
            setCellStr(row, 2, exp.getCompany());
            setCellStr(row, 5, exp.getPosition());
            setCellStr(row, 8, exp.getSalaryAndBenefits());
        }
    }

    private static void writeOnboardingFamilyMembers(Sheet sheet, List<FamilyMemberDTO> list) {
        if (list == null) return;
        for (int i = 0; i < Math.min(list.size(), 2); i++) {
            int rowNum = 26 + i;
            var row = sheet.getRow(rowNum - 1);
            if (row == null) row = sheet.createRow(rowNum - 1);
            var member = list.get(i);
            setCellStr(row, 2, member.getName());
            setCellStr(row, 4, member.getRelation());
            setCellStr(row, 5, member.getWorkplace());
            setCellStr(row, 7, member.getTitle());
            setCellStr(row, 8, member.getContact());
        }
    }

    // ======================== 单元格工具方法 ========================

    /**
     * 解析单元格引用（如 "C4"）为 [rowIndex, colIndex]。
     * "C4" → [3, 2]（0-based）
     */
    static int[] parseCellRef(String cellRef) {
        var matcher = java.util.regex.Pattern.compile("^([A-Z]+)(\\d+)$")
                .matcher(cellRef.toUpperCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(STR."无效的单元格引用: \{cellRef}");
        }
        int col = colLetterToIndex(matcher.group(1));
        int row = Integer.parseInt(matcher.group(2)) - 1;
        return new int[]{row, col};
    }

    /** 列字母转索引（A=0, B=1, ..., Z=25, AA=26） */
    private static int colLetterToIndex(String letters) {
        int col = 0;
        for (char c : letters.toCharArray()) {
            col = col * 26 + (c - 'A' + 1);
        }
        return col - 1;
    }

    /** 获取单元格文本值 */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                var num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    /** 安全获取 Row 中指定列的文本值 */
    private static String getCellStr(Row row, int colIndex) {
        if (row == null) return "";
        return getCellValueAsString(row.getCell(colIndex));
    }

    /** 安全设置 Row 中指定列的文本值 */
    private static void setCellStr(Row row, int colIndex, String value) {
        if (value == null) return;
        var cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        cell.setCellValue(value);
    }
}
