package com.cyk.Enity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchBuildingsTo {

    /**
     * 中心坐标点
     */
    private String location;

    /**
     * 搜索关键字
     */
    private String keywords;

    /**
     * 搜索半径
     */
    private String radius;

}
