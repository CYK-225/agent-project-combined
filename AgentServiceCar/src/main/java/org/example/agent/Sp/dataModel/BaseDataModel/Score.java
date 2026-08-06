package org.example.agent.Sp.dataModel.BaseDataModel;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class Score {
    @JsonPropertyDescription("综合总分，结合了属性、销售、评分和预测维度的加权得分")
    private Integer allScore=0;

    @JsonPropertyDescription("菜品属性维度得分：基于菜品类型、成本、价格等基础属性的匹配度评分")
    private Integer attributeScore=0;

    @JsonPropertyDescription("菜品历史销售维度得分：基于历史销量和送达消耗率的综合表现评分")
    private Integer salesScore=0;

    @JsonPropertyDescription("菜品历史用户评分维度得分：基于用户历史真实综合喜好评分的量化结果")
    private Integer historicalRatingScore=0;

    @JsonPropertyDescription("菜品预测营收维度得分：基于人均消耗预测及货损率模型计算出的预期收益评分")
    private Integer predictedRevenueScore=0;

    public void setAllScoreByAdd(){
        this.allScore=this.attributeScore+this.salesScore+this.historicalRatingScore+this.predictedRevenueScore;
    }

    private void addAllScore(int score){
        if(this.allScore==null){
            this.allScore=0;
        }
        this.allScore+=score;
    }
    private void addAttributeScore(int score){
        if(this.attributeScore==null){
            this.attributeScore=0;
        }
        this.attributeScore+=score;
    }
    private void addSalesScore(int score){
        if(this.salesScore==null){
            this.salesScore=0;
        }
        this.salesScore+=score;
    }
    private void addHistoricalRatingScore(int score){
        if(this.historicalRatingScore==null){
            this.historicalRatingScore=0;}
        this.historicalRatingScore+=score;
    }
    private void addPredictedRevenueScore(int score){
        if(this.predictedRevenueScore==null){
            this.predictedRevenueScore=0;}
        this.predictedRevenueScore+=score;
    }
    public Boolean addOtherScore(Score score){
        this.addAllScore(score.getAllScore()==null?0:score.getAllScore());
        this.addAttributeScore(score.getAttributeScore()==null?0:score.getAttributeScore());
        this.addSalesScore(score.getSalesScore()==null?0:score.getSalesScore());
        this.addHistoricalRatingScore(score.getHistoricalRatingScore()==null?0:score.getHistoricalRatingScore());
        this.addPredictedRevenueScore(score.getPredictedRevenueScore()==null?0:score.getPredictedRevenueScore());
        return true;
    }
    public String toString() {
        return STR."总分: \{this.allScore}, 属性维度得分: \{this.attributeScore}, 销售维度得分: \{this.salesScore}, 历史评分维度得分: \{this.historicalRatingScore}, 预测营收维度得分: \{this.predictedRevenueScore}";
    }


}
