package com.cyk.Enity;

import lombok.Data;

@Data
public class GetAverageDeliveryTimeTo {

    /**
     * 楼宇坐标
     */
    private String origin;

    /**
     * 商家坐标数组
     */
    private String destinations;
}
