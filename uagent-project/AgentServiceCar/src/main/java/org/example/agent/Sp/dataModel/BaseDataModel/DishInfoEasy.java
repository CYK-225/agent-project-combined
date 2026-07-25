package org.example.agent.Sp.dataModel.BaseDataModel;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;


import java.util.Optional;

@Data
@AutoMapper(target = DishInfo.class)
public class DishInfoEasy {
    // 菜品 id
    @JsonPropertyDescription("菜品唯一标识ID")
    private Long id;

    //菜品名称
    @JsonPropertyDescription("菜品名称")
    private String name;

    //菜品类型
    @JsonPropertyDescription("菜品分类")
    private String dishType;

    @JsonPropertyDescription("辣度等级")
    private int spicyLevel;

    public String toString(){
                String spicyLevelStr = switch (spicyLevel) {
            case 0 -> "不辣";
            case 1 -> "微辣";
            case 2 -> "中辣";
            case 3 -> "无辣不欢";
            case -1 -> "数据缺失";
            default -> "未知辣度";
        };
                String id = Optional.ofNullable(this.getId()).orElse(0L).toString();
        String name = Optional.ofNullable(this.getName()).orElse("未知菜名");
        String dishType = Optional.ofNullable(this.getDishType()).orElse("未分类");

        ZeroShotPrompt str=new ZeroShotPrompt();
        str.box("id", id);
        str.box("name",name);
        str.box("dishType",dishType);
        str.box("spicyLevel",spicyLevelStr);
        return  str.render();
    }

}
