package com.cyk.Enity.table;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.lang.Long;
import java.util.Date;
import java.lang.String;
import java.lang.Integer;

/**
 * 爬虫 Cookie 池管理表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "spider_cookie_pool")
public class SpiderCookiePoolEntity {

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 目标站点标识 (如: fengniao, aiqicha)
     */
    @Column(value = "site")
    private String site;

    /**
     * 登录账号/手机号
     */
    @Column(value = "account")
    private String account;

    /**
     * 纯文本格式的 Cookie 字符串
     */
    @Column(value = "cookie_value")
    private String cookieValue;

    /**
     * 状态: 1-有效可用, 0-失效或获取失败
     */
    @Column(value = "status")
    private Integer status;

    /**
     * 失败原因 (用于记录重试3次依然失败的原因)
     */
    @Column(value = "fail_reason")
    private String failReason;

    /**
     * 最后一次验证成功/获取成功的时间
     */
    @Column(value = "last_verify_time")
    private Date lastVerifyTime;

    /**
     * 创建时间
     */
    @Column(value = "create_time")
    private Date createTime;

    /**
     * 更新时间 (需配合触发器自动更新)
     */
    @Column(value = "update_time")
    private Date updateTime;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getCookieValue() {
        return cookieValue;
    }

    public void setCookieValue(String cookieValue) {
        this.cookieValue = cookieValue;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Date getLastVerifyTime() {
        return lastVerifyTime;
    }

    public void setLastVerifyTime(Date lastVerifyTime) {
        this.lastVerifyTime = lastVerifyTime;
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
