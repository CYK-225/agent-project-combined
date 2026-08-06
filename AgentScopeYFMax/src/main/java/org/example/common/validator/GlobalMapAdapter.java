package org.example.common.validator;


import org.mapstruct.Named;
import org.springframework.stereotype.Component;


@Component // 注册为 Bean，MapStruct Plus 生成的代码会自动注入它
public class GlobalMapAdapter {

    // === 逻辑 1: Boolean 防空指针 ===
    public Boolean mapBoolean(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    // === 逻辑 2: 比如处理字符串去空 ===
    public String mapString(String str) {
        return str == null ? "" : str.trim();
    }

    /**
     * 处理辣度等级，确保在 0 到 3 之间
     */
    @Named("SpicyLevel")
    public int SpicyLevel(Integer level) {
        if (level == null) {
            return 0;
        }else{
            return level;
        }
    }


    // === 逻辑 3: 其他通用逻辑... ===
}
