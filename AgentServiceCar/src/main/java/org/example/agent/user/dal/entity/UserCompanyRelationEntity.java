package org.example.agent.user.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.lang.Long;
import java.util.Date;
import java.lang.String;
import java.lang.Integer;

/**
 * 员工所属关联表(人事档案) 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "user_company_relation")
@Data
public class UserCompanyRelationEntity {

    /**
     * 关联ID
     */
    @Id(keyType = KeyType.Auto)
    private Long relationId;

    /**
     * 用户ID(关联user_info)
     */
    @Column(value = "user_id")
    private Long userId;

    /**
     * 公司ID(关联company_info)
     */
    @Column(value = "company_id")
    private Long companyId;

    /**
     * 工号
     */
    @Column(value = "employee_no")
    private String employeeNo;

    /**
     * 职位/岗位
     */
    @Column(value = "position")
    private String position;

    @Column(value = "entry_date")
    private Date entryDate;

    /**
     * 在职状态(1:在职 0:离职)
     */
    @Column(value = "status")
    private Integer status;

    /**
     * 创建者
     */
    @Column(value = "create_by")
    private String createBy;

    /**
     * 创建时间
     */
    @Column(value = "create_time")
    private Date createTime;

    /**
     * 更新者
     */
    @Column(value = "update_by")
    private String updateBy;

    /**
     * 更新时间
     */
    @Column(value = "update_time")
    private Date updateTime;


    public Long getRelationId() {
        return relationId;
    }

    public void setRelationId(Long relationId) {
        this.relationId = relationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Date getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(Date entryDate) {
        this.entryDate = entryDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
