package org.example.agent.Sp.dataModel.BaseDataModel;

import lombok.Data;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;


@Data
public class DishInfoAndScore {

    private Long dishId;
    private DishInfoEasy dishInfo;
    private Score score;


    // 核心逻辑：合并另一个同名菜品的分数
    public DishInfoAndScore merge(DishInfoAndScore other) {
        this.score.addOtherScore(other.score);
        return this; // 只能返回自己进行合并
    }
    public int getAllScore(){
        return this.score.getAllScore();
    }
    public String toString() {
        ZeroShotPrompt prompt = new ZeroShotPrompt();
        prompt.of(STR."菜品ID: \{this.dishId}");
        prompt.of(STR."菜品信息: \{this.dishInfo != null ? this.dishInfo.toString() : "无"}");
        prompt.of(STR."菜品评分: \{this.score != null ? this.score.toString() : "无"}");
        return prompt.render();
    }

//
//    public String toString() {
//        ZeroShotPrompt prompt = new ZeroShotPrompt();
//
//        // 1. 全局判空：如果 dishInfo 为 null，直接返回提示或跳过
//        if (this.dishInfo == null) {
//            return "警告：DishInfo 对象为空，无法生成 Prompt。";
//        }
//
//        // --- 数据准备阶段 (加入 Null 安全处理) ---
//
//        // 字符串类型：使用 Optional 判空，如果为 null 或空字符串则给默认值
//        String id = Optional.ofNullable(this.dishInfo.getId()).orElse(0L).toString();
//        String name = Optional.ofNullable(this.dishInfo.getName()).orElse("未知菜名");
//        String source = Optional.ofNullable(this.dishInfo.getSource()).orElse("未知来源");
//        String dishType = Optional.ofNullable(this.dishInfo.getDishType()).orElse("未分类");
//
//        // 整数/枚举类型：防止拆箱空指针
//        int spicyLevelInt = this.dishInfo.getSpicyLevel(); // 假设 getter 返回 Integer
//        String spicyLevel = switch (spicyLevelInt) {
//            case 0 -> "不辣";
//            case 1 -> "微辣";
//            case 2 -> "中辣";
//            case 3 -> "无辣不欢";
//            case -1 -> "数据缺失";
//            default -> "未知辣度";
//        };
//
//        String cookingMethod = Optional.ofNullable(this.dishInfo.getCookingMethod()).orElse("通用烹饪");
//
//        // BigDecimal 类型：防止调用 .toPlainString() 时空指针
//        String edibleRatio = Optional.ofNullable(this.dishInfo.getEdibleRatio())
//                .map(BigDecimal::toPlainString).orElse("未知");
//
//        // Boolean 类型：使用 Boolean.TRUE.equals 防止 null 导致的拆箱错误
//        String isSeasonal = this.dishInfo.isSeasonal() ? "是" : "否";
//
//        String otherCost = Optional.ofNullable(this.dishInfo.getOtherCost())
//                .map(BigDecimal::toPlainString).orElse("0");
//
//        String unit_price_kg = Optional.ofNullable(this.dishInfo.getUnit_price_kg())
//                .map(BigDecimal::toPlainString).orElse("未知");
//
//        String predictedConsumption = Optional.ofNullable(this.dishInfo.getPredictedConsumption())
//                .map(BigDecimal::toPlainString).orElse("未知");
//
//        String predictedLoss = Optional.ofNullable(this.dishInfo.getPredictedLoss())
//                .map(BigDecimal::toPlainString).orElse("未知");
//
//        String preferenceScore = Optional.ofNullable(this.dishInfo.getPreferenceScore())
//                .map(BigDecimal::toPlainString).orElse("未知");
//
//        // 整数/日期
//        String appearanceCount = String.valueOf(Optional.ofNullable(this.dishInfo.getAppearanceCount()).orElse(0));
//        String lastAppearanceDate = Optional.ofNullable(this.dishInfo.getLastAppearanceDate())
//                .map(Object::toString).orElse("无历史记录");
//
//        // 评论内容：如果是空字符串或 null，显示“无”
//        String recentReviews = Optional.ofNullable(this.dishInfo.getRecentReviews())
//                .filter(s -> !s.isBlank()).orElse("无近期评价");
//        String historyReviews = Optional.ofNullable(this.dishInfo.getHistoryReviews())
//                .filter(s -> !s.isBlank()).orElse("无历史评价");
//
//
//        // --- 构建 Prompt 阶段 ---
//
//        // 食材基本信息
//        prompt.of("以下是菜品的详细信息：");
//        prompt.box("菜品id", id, "菜品ID");
//        prompt.box("菜品名称", name, "菜品名称");
//        prompt.box("菜品来源", source, "菜品来源");
//        prompt.box("菜品分类", dishType, "菜品分类");
//        prompt.box("辣度等级", spicyLevel, "辣度等级");
//        prompt.box("主要烹饪方式", cookingMethod, "主要烹饪方式");
//        prompt.box("食材可食用部分比例(0-1)", edibleRatio, "食材可食用部分比例(0-1)");
//        prompt.box("是否为应季菜品", isSeasonal, "是否为应季菜品");
//        prompt.box("其他杂项成本", otherCost, "其他杂项成本");
//        prompt.box("每公斤成品单价", unit_price_kg, "每公斤成品单价");
//        prompt.box("预测人均消耗量(kg)", predictedConsumption, "预测人均消耗量(kg)");
//        prompt.box("预测人均货损量(kg)", predictedLoss, "预测人均货损量(kg)");
//        prompt.box("综合喜好程度评分", preferenceScore, "综合喜好程度评分");
//        prompt.box("历史供应次数", appearanceCount, "历史供应次数");
//        prompt.box("上次供应日期", lastAppearanceDate, "上次供应日期");
//        prompt.box("最近一次评价内容", recentReviews, "最近一次评价内容");
//        prompt.box("历史评价内容", historyReviews, "历史评价内容");
//
//        // --- 食材得分信息 (同样需要判空) ---
//        prompt.of("菜品得分信息如下：");
//
//        if (this.score != null) {
//            prompt.box("综合总分",
//                    Optional.ofNullable(this.score.getAllScore()).map(Object::toString).orElse("0"),
//                    "结合了属性、销售、评分和预测维度的加权得分");
//
//            prompt.box("菜品属性维度得分",
//                    Optional.ofNullable(this.score.getAttributeScore()).map(Object::toString).orElse("0"),
//                    "基于菜品类型、成本、价格等基础属性的匹配度评分");
//
//            prompt.box("菜品历史销售维度得分",
//                    Optional.ofNullable(this.score.getSalesScore()).map(Object::toString).orElse("0"),
//                    "基于历史销量和送达消耗率的综合表现评分");
//
//            prompt.box("菜品历史用户评分维度得分",
//                    Optional.ofNullable(this.score.getHistoricalRatingScore()).map(Object::toString).orElse("0"),
//                    "基于用户历史真实综合喜好评分的量化结果");
//
//            prompt.box("菜品预测营收维度得分",
//                    Optional.ofNullable(this.score.getPredictedRevenueScore()).map(Object::toString).orElse("0"),
//                    "基于人均消耗预测及货损率模型计算出的预期收益评分");
//        } else {
//            prompt.of("（暂无评分数据）");
//        }
//
//        return prompt.render();} // 假设 ZeroShotPrompt 有 toString 方法返回最终结果}

}



