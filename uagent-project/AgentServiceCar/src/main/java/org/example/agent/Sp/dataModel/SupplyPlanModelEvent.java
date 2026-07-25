package org.example.agent.Sp.dataModel;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.agentscope.core.state.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.example.agent.Sp.dataModel.BaseDataModel.DishInfoAndScore;

import java.util.HashMap;

/**
 * SupplyPlan Agent 的事件模型。
 * 承载从 Search Agent 到 Report Agent 流程中的所有状态数据。
 * <p>
 * 实现 {@link State} 标记接口，支持通过 {@link SessionContext} 自动持久化到 Session。
 *
 * @author lxz
 * @since 2026-01-05
 */
@Data
@Accessors(chain = true) // 支持链式调用
@Builder
@AllArgsConstructor
@NoArgsConstructor //一定要有无参构造方法
public class SupplyPlanModelEvent implements State {


//    /**
//     * 失败的历史记录，由食材+
//     */
//    private List<String> emptyKeywordsHistory=new java.util.ArrayList<>();




    // 白名单的菜品信息key为id,value为具体的值
    @JsonPropertyDescription("白名单的菜品信息,key为id,value为菜品的值(类型为DishInfoAndScore)，只允许管理白名单模式的时候编辑")
    private HashMap<Long, DishInfoAndScore> whiteDishMap=new HashMap<>();
    // 黑名单的菜品信息:key为id，value为压缩后的数据,例如名+逆检索理由。
    @JsonPropertyDescription("黑名单的菜品信息,key为id,value为具体的值(类型为String，存放黑名单理由等信息)，只允许管理黑名单模式的时候编辑")
    private HashMap<Long,String> blackDishMap=new HashMap<>();
    /**
     * 用户画像
      */
    private String UserReport;
    /**
     * 利润报告
      */
    private String financialReport;
    /**
     * 用户问题
     */
    private String userQuestion;
}
