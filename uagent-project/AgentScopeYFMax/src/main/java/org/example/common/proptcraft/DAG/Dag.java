package org.example.common.proptcraft.DAG;


import org.example.common.proptcraft.PromptComponent;

public class Dag extends PromptComponent {

    //初始化
    public Dag() {
        this.prepend( this.of(
                """
    在这一阶段，你是一个专业的任务规划专家。请将用户问题分解为一个有向无环图（DAG）：

  

    要求：
    1. 每个节点代表一个原子子任务，描述必须清晰、具体、可执行；
    2. 边表示“必须先完成”的依赖关系（例如：A → B 表示 A 必须在 B 之前完成）；
    3. **绝对禁止出现循环依赖**（例如：A → B → A 是非法的）；
    4. 请按拓扑排序的顺序列出所有节点（即每个任务只能依赖它前面的任务）；
    5. **严格按以下 JSON 格式输出，不要包含任何额外解释、注释或 Markdown 语法**：
    6. 节点状态必须是“已解决”，“未解决”，“该层级暂时无法解决”三种状态之一。
    8. 第一次生成的节点都是未解决状态。
    9. 层级与问题复杂度挂钩，最高不能超过9层
    10.子层级节点只能来源于父节点,一个子节点可以链接多个父节点,一个父节点可以链接多个子节点
    11.如果该问题已经解决了，则标记解决

    {
      "nodes": [
        {"id": "唯一字符串标识", "description": "子任务描述","status":"任务状态(只可以是“已解决”，“未解决”，“该层级暂时无法解决”)","height":"当前问题的层级" }
      ],
      "edges": [
        {"from": "前置任务ID", "to": "后置任务ID"}
      ]
    }
    """
        ));
    }


}
