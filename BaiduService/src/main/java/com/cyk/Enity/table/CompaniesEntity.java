package com.cyk.Enity.table;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.lang.String;
import java.lang.Integer;

/**
 * 公司表 (Excel全量字段) 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "companies")
@Data
public class CompaniesEntity {

    /**
     * 主键，公司唯一标识
     */
    @Id(keyType = KeyType.None)
    private String uid;

    /**
     * 所属楼宇uid
     */
    @Column(value = "building_uid")
    private String buildingUid;

    @Column(value = "district")
    private String district;

    @Column(value = "area_name")
    private String areaName;

    @Column(value = "business_district")
    private String businessDistrict;

    @Column(value = "park_type")
    private String parkType;

    @Column(value = "building_name")
    private String buildingName;

    @Column(value = "total_floors")
    private String totalFloors;

    @Column(value = "sales_access")
    private String salesAccess;

    @Column(value = "delivery_access")
    private String deliveryAccess;

    @Column(value = "dining_convenience")
    private String diningConvenience;

    @Column(value = "avg_delivery_price")
    private Integer avgDeliveryPrice;

    @Column(value = "occupancy_rate")
    private String occupancyRate;

    @Column(value = "floor_info")
    private String floorInfo;

    /**
     * 企业名称
     */
    @Column(value = "company_name")
    private String companyName;

    @Column(value = "office_area")
    private String officeArea;

    @Column(value = "industry")
    private String industry;

    @Column(value = "employee_count")
    private String employeeCount;

    @Column(value = "gender_ratio")
    private String genderRatio;

    @Column(value = "age_ratio")
    private String ageRatio;

    @Column(value = "workplace_structure")
    private String workplaceStructure;

    @Column(value = "dining_policy")
    private String diningPolicy;

    @Column(value = "insured_count")
    private String insuredCount;

    @Column(value = "established_years")
    private String establishedYears;

    @Column(value = "registered_capital")
    private String registeredCapital;

    @Column(value = "avg_salary")
    private String avgSalary;

    @Column(value = "lunch_solution")
    private String lunchSolution;

    @Column(value = "catering_history")
    private String cateringHistory;

    @Column(value = "subsidy_status")
    private String subsidyStatus;

    @Column(value = "subsidy_amount")
    private BigDecimal subsidyAmount;

    @Column(value = "key_contact")
    private String keyContact;

    @Column(value = "peak_delivery_time")
    private String peakDeliveryTime;

    @Column(value = "nearby_fast_food_count")
    private String nearbyFastFoodCount;

    /**
     * 客单价最小值(元)
     */
    @Column(value = "min_fast_food_price")
    private Integer minFastFoodPrice;

    /**
     * 客单价最大值(元)
     */
    @Column(value = "max_fast_food_price")
    private Integer maxFastFoodPrice;

    @Column(value = "is_target_customer")
    private String isTargetCustomer;

    @Column(value = "remarks")
    private String remarks;

    /**
     * 公司信息状态: 0-未初始化, 1-已初始化, 2-已完善
     */
    @Column(value = "info_status")
    private Integer infoStatus;

    @Column(value = "is_deleted")
    private Integer isDeleted;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_time")
    private Date updateTime;


    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getBuildingUid() {
        return buildingUid;
    }

    public void setBuildingUid(String buildingUid) {
        this.buildingUid = buildingUid;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getBusinessDistrict() {
        return businessDistrict;
    }

    public void setBusinessDistrict(String businessDistrict) {
        this.businessDistrict = businessDistrict;
    }

    public String getParkType() {
        return parkType;
    }

    public void setParkType(String parkType) {
        this.parkType = parkType;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public String getTotalFloors() {
        return totalFloors;
    }

    public void setTotalFloors(String totalFloors) {
        this.totalFloors = totalFloors;
    }

    public String getSalesAccess() {
        return salesAccess;
    }

    public void setSalesAccess(String salesAccess) {
        this.salesAccess = salesAccess;
    }

    public String getDeliveryAccess() {
        return deliveryAccess;
    }

    public void setDeliveryAccess(String deliveryAccess) {
        this.deliveryAccess = deliveryAccess;
    }

    public String getDiningConvenience() {
        return diningConvenience;
    }

    public void setDiningConvenience(String diningConvenience) {
        this.diningConvenience = diningConvenience;
    }

    public Integer getAvgDeliveryPrice() {
        return avgDeliveryPrice;
    }

    public void setAvgDeliveryPrice(Integer avgDeliveryPrice) {
        this.avgDeliveryPrice = avgDeliveryPrice;
    }

    public String getOccupancyRate() {
        return occupancyRate;
    }

    public void setOccupancyRate(String occupancyRate) {
        this.occupancyRate = occupancyRate;
    }

    public String getFloorInfo() {
        return floorInfo;
    }

    public void setFloorInfo(String floorInfo) {
        this.floorInfo = floorInfo;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getOfficeArea() {
        return officeArea;
    }

    public void setOfficeArea(String officeArea) {
        this.officeArea = officeArea;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(String employeeCount) {
        this.employeeCount = employeeCount;
    }

    public String getGenderRatio() {
        return genderRatio;
    }

    public void setGenderRatio(String genderRatio) {
        this.genderRatio = genderRatio;
    }

    public String getAgeRatio() {
        return ageRatio;
    }

    public void setAgeRatio(String ageRatio) {
        this.ageRatio = ageRatio;
    }

    public String getWorkplaceStructure() {
        return workplaceStructure;
    }

    public void setWorkplaceStructure(String workplaceStructure) {
        this.workplaceStructure = workplaceStructure;
    }

    public String getDiningPolicy() {
        return diningPolicy;
    }

    public void setDiningPolicy(String diningPolicy) {
        this.diningPolicy = diningPolicy;
    }

    public String getInsuredCount() {
        return insuredCount;
    }

    public void setInsuredCount(String insuredCount) {
        this.insuredCount = insuredCount;
    }

    public String getEstablishedYears() {
        return establishedYears;
    }

    public void setEstablishedYears(String establishedYears) {
        this.establishedYears = establishedYears;
    }

    public String getRegisteredCapital() {
        return registeredCapital;
    }

    public void setRegisteredCapital(String registeredCapital) {
        this.registeredCapital = registeredCapital;
    }

    public String getAvgSalary() {
        return avgSalary;
    }

    public void setAvgSalary(String avgSalary) {
        this.avgSalary = avgSalary;
    }

    public String getLunchSolution() {
        return lunchSolution;
    }

    public void setLunchSolution(String lunchSolution) {
        this.lunchSolution = lunchSolution;
    }

    public String getCateringHistory() {
        return cateringHistory;
    }

    public void setCateringHistory(String cateringHistory) {
        this.cateringHistory = cateringHistory;
    }

    public String getSubsidyStatus() {
        return subsidyStatus;
    }

    public void setSubsidyStatus(String subsidyStatus) {
        this.subsidyStatus = subsidyStatus;
    }

    public BigDecimal getSubsidyAmount() {
        return subsidyAmount;
    }

    public void setSubsidyAmount(BigDecimal subsidyAmount) {
        this.subsidyAmount = subsidyAmount;
    }

    public String getKeyContact() {
        return keyContact;
    }

    public void setKeyContact(String keyContact) {
        this.keyContact = keyContact;
    }

    public String getPeakDeliveryTime() {
        return peakDeliveryTime;
    }

    public void setPeakDeliveryTime(String peakDeliveryTime) {
        this.peakDeliveryTime = peakDeliveryTime;
    }

    public String getNearbyFastFoodCount() {
        return nearbyFastFoodCount;
    }

    public void setNearbyFastFoodCount(String nearbyFastFoodCount) {
        this.nearbyFastFoodCount = nearbyFastFoodCount;
    }

    public Integer getMinFastFoodPrice() {
        return minFastFoodPrice;
    }

    public void setMinFastFoodPrice(Integer minFastFoodPrice) {
        this.minFastFoodPrice = minFastFoodPrice;
    }

    public Integer getMaxFastFoodPrice() {
        return maxFastFoodPrice;
    }

    public void setMaxFastFoodPrice(Integer maxFastFoodPrice) {
        this.maxFastFoodPrice = maxFastFoodPrice;
    }

    public String getIsTargetCustomer() {
        return isTargetCustomer;
    }

    public void setIsTargetCustomer(String isTargetCustomer) {
        this.isTargetCustomer = isTargetCustomer;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Integer getInfoStatus() {
        return infoStatus;
    }

    public void setInfoStatus(Integer infoStatus) {
        this.infoStatus = infoStatus;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
