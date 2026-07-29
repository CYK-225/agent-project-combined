package com.cyk.Enity.table;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import org.apache.ibatis.type.ArrayTypeHandler;

import java.util.Date;
import java.lang.String;

/**
 * 楼宇表 实体类。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Table(value = "buildings")
@Data
public class BuildingsEntity {

    /**
     * 主键，楼宇唯一标识
     */
    @Id(keyType = KeyType.None)
    private String uid;

    /**
     * 楼宇名字
     */
    @Column(value = "name")
    private String name;

    @Column(value = "location")
    private String location;

    @Column(value = "address")
    private String address;

    /**
     * 楼宇公司uid列表 (PG原生数组)
     */
    @Column(typeHandler = ArrayTypeHandler.class)
    private String[] companyUidList;

    @Column(value = "create_time")
    private Date createTime;

    @Column(value = "update_time")
    private Date updateTime;



}
