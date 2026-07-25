package org.example.agent.user.dataModel;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMapping;

import lombok.Data;
import org.example.agent.user.dal.entity.UserInfoEntity;
import org.example.common.validator.GlobalMapAdapter;


// 1. 指定这就源头是 UserInfoEntity，自动生成 Entity -> DTO 的逻辑
@Data
@AutoMapper(target = UserInfoEntity.class,uses = {GlobalMapAdapter.class})
public class UserPreferences {


    @JsonPropertyDescription("用户类型，务必使用中文") // 保留了原本更详细的指令
    // 提示词相关。
    private String userType;

    @JsonPropertyDescription("用户所属部门")
    private String department;

    @JsonPropertyDescription("用户出生省份")
    private String birthProvince;

    @AutoMapping(target = "userPreferenceBias.spicyLevel",expression = "java(userPreferences.getSpicyLevel())")
    @JsonPropertyDescription("用户喜好辣度，0是不辣，1是微辣，2是中辣，3是无辣不欢") // 建议补充：例如 "范围1-5"
    private Integer spicyLevel;

    @AutoMapping(target = "userPreferenceBias.favoriteDishes")
    @JsonPropertyDescription("用户喜欢吃的菜品") // 建议补充：例如 "是一个菜名列表"
    private String favoriteDishes;

    @AutoMapping(target = "userPreferenceBias.dislikedDishesDesc")
    @JsonPropertyDescription("用户一定不吃的菜品")
    private String dislikedDishesDesc;

    @AutoMapping(target = "userPreferenceBias.acceptedTypes")
    @JsonPropertyDescription("平时更容易接受的类型")
    private String acceptedTypes;

    @AutoMapping(target = "userPreferenceBias.unacceptableIngredients")
    @JsonPropertyDescription("无法接受的食材")
    private String unacceptableIngredients;
}


