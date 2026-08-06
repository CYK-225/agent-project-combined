package org.example.common.easyExcel.dto;

import lombok.Data;
import org.example.common.easyExcel.annotation.ExcelCell;

import java.util.List;

/**
 * 应聘登记表 DTO。
 * <p>
 * 对应应聘登记表.xlsx，包含个人信息、工作履历、教育背景、其他信息。
 * 字段上的 {@link ExcelCell} 注解标记了该字段在 Excel 中的单元格位置，
 * 用于 {@link org.example.common.easyExcel.FormExcelHelper} 自动读取。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class ApplicationFormDTO {

    // ======================== 基本信息 ========================

    /** 应聘职位 */
    @ExcelCell(cellRef = "D6")
    private String position;

    /** 最快到岗时间 */
    @ExcelCell(cellRef = "H6")
    private String earliestStartDate;

    /** 最低可接受薪酬 */
    @ExcelCell(cellRef = "J6")
    private String minSalary;

    // ======================== 个人信息 ========================

    /** 姓名 */
    @ExcelCell(cellRef = "D8")
    private String name;

    /** 籍贯 - 省 */
    @ExcelCell(cellRef = "G8")
    private String nativeProvince;

    /** 籍贯 - 市 */
    @ExcelCell(cellRef = "I8")
    private String nativeCity;

    /** 电子邮件 */
    @ExcelCell(cellRef = "J8")
    private String email;

    /** 性别 */
    @ExcelCell(cellRef = "D9")
    private String gender;

    /** 户口所在地 - 省 */
    @ExcelCell(cellRef = "G9")
    private String hukouProvince;

    /** 户口所在地 - 市 */
    @ExcelCell(cellRef = "I9")
    private String hukouCity;

    /** 联系电话 */
    @ExcelCell(cellRef = "J9")
    private String phone;

    /** 出生日期 */
    @ExcelCell(cellRef = "D10")
    private String birthDate;

    /** 户口性质（农村/城镇） */
    @ExcelCell(cellRef = "H10")
    private String hukouType;

    /** 微信号 */
    @ExcelCell(cellRef = "J10")
    private String wechat;

    /** 婚育状况 */
    @ExcelCell(cellRef = "D11")
    private String maritalStatus;

    /** 身高（cm） */
    @ExcelCell(cellRef = "G11")
    private String height;

    /** 体重（kg） */
    @ExcelCell(cellRef = "H11")
    private String weight;

    /** 现工作状态（在职/已离职/交接中/实习应届生） */
    @ExcelCell(cellRef = "K11")
    private String workStatus;

    /** 身份证号码 */
    @ExcelCell(cellRef = "D12")
    private String idNumber;

    /** 现住址 */
    @ExcelCell(cellRef = "H12")
    private String currentAddress;

    /** 获得招聘信息途径 */
    @ExcelCell(cellRef = "B13")
    private String recruitmentSource;

    // ======================== 工作履历（最多3段） ========================

    /** 工作经历列表 */
    private List<WorkExperienceDTO> workExperiences;

    // ======================== 教育背景（最多2段） ========================

    /** 教育经历列表 */
    private List<EducationDTO> educations;

    // ======================== 其他信息 ========================

    /** 选择本公司及岗位的主要理由 */
    @ExcelCell(cellRef = "D27")
    private String reasonForCompany;

    /** 对应聘工作的打算 */
    @ExcelCell(cellRef = "D28")
    private String workPlan;

    /** 兴趣/爱好/特长 */
    @ExcelCell(cellRef = "D29")
    private String hobbies;

    /** 是否与上一家公司存在纠纷 */
    @ExcelCell(cellRef = "E30")
    private String hasDispute;

    /** 是否有竞业限制协议 */
    @ExcelCell(cellRef = "K30")
    private String hasNonCompete;

    /** 是否与前用人单位有未尽法律事宜 */
    @ExcelCell(cellRef = "E31")
    private String hasLegalIssues;

    /** 是否受过法律法规处罚 */
    @ExcelCell(cellRef = "K31")
    private String hasPunishment;

    /** 是否有重大疾病 */
    @ExcelCell(cellRef = "E32")
    private String hasMajorDisease;
}
