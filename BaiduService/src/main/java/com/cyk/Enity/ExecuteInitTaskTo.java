package com.cyk.Enity;

import lombok.Data;

@Data
public class ExecuteInitTaskTo {

    /**
     * 楼宇uid
     */
    private String buildingUid;

    /**
     * 楼宇位置
     */
    private String buildingLocation;

    /**
     * 搜索附近商家的半径
    */
    private String radius;

    /**
     * sse订阅id
     */
    private String sseId;
}
