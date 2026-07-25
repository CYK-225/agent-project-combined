package org.example.common.easyExcel.dto;

import lombok.Data;
import org.example.common.easyExcel.annotation.ExcelCell;

import java.util.List;

/**
 * 入职信息登记表 DTO。
 * <p>
 * 对应【需填写】入职信息登记表.xlsx，包含个人信息、教育背景、培训经历、
 * 工作履历、家庭成员、紧急联系人。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Data
public class OnboardingFormDTO {

    // ======================== 个人信息 ========================

    /** 姓名 */
    @ExcelCell(cellRef = "C4")
    private String name;

    /** 性别 */
    @ExcelCell(cellRef = "E4")
    private String gender;

    /** 民族 */
    @ExcelCell(cellRef = "G4")
    private String ethnicity;

    /** 政治面貌 */
    @ExcelCell(cellRef = "I4")
    private String politicalStatus;

    /** 户口性质 */
    @ExcelCell(cellRef = "D5")
    private String hukouType;

    /** 第一份工作时间 */
    @ExcelCell(cellRef = "F5")
    private String firstJobDate;

    /** 联系电话 */
    @ExcelCell(cellRef = "G5")
    private String phone;

    /** 生日日期 */
    @ExcelCell(cellRef = "J5")
    private String birthday;

    /** 身份证号码 */
    @ExcelCell(cellRef = "D6")
    private String idNumber;

    /** 婚育状况 */
    @ExcelCell(cellRef = "G6")
    private String maritalStatus;

    /** 个人邮箱 */
    @ExcelCell(cellRef = "I6")
    private String email;

    /** 身份证地址 */
    @ExcelCell(cellRef = "D7")
    private String idAddress;

    /** 现居住住址 */
    @ExcelCell(cellRef = "G7")
    private String currentAddress;

    /** 中国银行卡号 */
    @ExcelCell(cellRef = "D8")
    private String bankCardNumber;

    /** 开户行 */
    @ExcelCell(cellRef = "G8")
    private String bankName;

    /** 入职时间 */
    @ExcelCell(cellRef = "D9")
    private String onboardDate;

    /** 入职城市 */
    @ExcelCell(cellRef = "F9")
    private String onboardCity;

    /** 入职部门 */
    @ExcelCell(cellRef = "G9")
    private String department;

    /** 任职岗位 */
    @ExcelCell(cellRef = "J9")
    private String position;

    // ======================== 教育背景 ========================

    /** 教育经历列表 */
    private List<EducationDTO> educations;

    // ======================== 培训经历 ========================

    /** 培训经历列表 */
    private List<TrainingDTO> trainings;

    // ======================== 工作履历 ========================

    /** 工作经历列表 */
    private List<WorkExperienceDTO> workExperiences;

    // ======================== 家庭成员 ========================

    /** 主要家庭成员列表 */
    private List<FamilyMemberDTO> familyMembers;

    // ======================== 紧急联系人 ========================

    /** 是否有亲属在悠饭工作 */
    @ExcelCell(cellRef = "D29")
    private String hasRelativeInCompany;

    /** 紧急联系人姓名 */
    @ExcelCell(cellRef = "F29")
    private String emergencyContactName;

    /** 紧急联系人关系 */
    @ExcelCell(cellRef = "I29")
    private String emergencyContactRelation;
}
