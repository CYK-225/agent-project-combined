package com.cyk.task.core.enums;

/**
 * 任务类型枚举
 * 
 * <p>【核心职责】</p>
 * 本枚举定义了系统支持的所有任务类型，是任务路由分发的核心依据。
 * 不同类型的任务会被分发到不同的执行器进行处理。
 * 
 * <p>【任务类型说明】</p>
 * <ul>
 *   <li><b>ENS</b>：企业信息查询任务，通过Docker容器执行Python脚本</li>
 *   <li><b>AI</b>：AI驱动的自动化任务，需要读取配置文件并使用鉴权信息</li>
 *   <li><b>SPECIAL</b>：特殊任务，本质是AI任务的变体，复用AI执行器基础设施</li>
 * </ul>
 * 
 * <p>【路由分发逻辑】</p>
 * <pre>
 * 任务创建时指定task_type
 *         │
 *         ▼
 * ┌───────────────────┐
 * │   任务路由器      │
 * └────┬──────┬───────┘
 *      │      │
 *      ▼      ▼
 *   ┌────┐ ┌────┐
 *   │ENS │ │ AI │ (SPECIAL复用AI)
 *   │队列│ │队列│
 *   └──┬─┘ └──┬─┘
 *      │      │
 *      ▼      ▼
 *   ENS    AI/SPECIAL
 *   执行器 执行器
 * </pre>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 创建ENS任务
 * TaskInfoEntity task = new TaskInfoEntity();
 * task.setTaskType(TaskType.ENS.getCode());
 * 
 * // 判断任务类型
 * if (TaskType.AI.getCode().equals(task.getTaskType())) {
 *     // 执行AI任务逻辑
 * }
 * </pre>
 * 
 * @author system
 * @since 1.0
 */
public enum TaskType {

    /**
     * ENS任务 - 企业信息查询
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从账号池获取鉴权信息（Cookie）</li>
     *   <li>启动Docker容器，执行Python脚本</li>
     *   <li>脚本访问目标网站，抓取企业信息</li>
     *   <li>通过回调接口返回结果</li>
     *   <li>释放账号锁</li>
     * </ol>
     */
    ENS("ENS", "企业信息查询任务", "ENS执行器"),

    /**
     * AI任务 - AI驱动的自动化任务
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>读取配置文件（config_name指定）</li>
     *   <li>从账号池获取鉴权信息</li>
     *   <li>根据配置执行自动化流程</li>
     *   <li>收集字段数据，填充collected_fields</li>
     *   <li>释放账号锁</li>
     * </ol>
     * 
     * <p>特点：</p>
     * <ul>
     *   <li>严格按create_time_ms排队（FIFO）</li>
     *   <li>需要显示排队位置给用户</li>
     *   <li>执行时长相对固定（可预估）</li>
     * </ul>
     */
    AI("AI", "AI自动化任务", "AI执行器"),

    /**
     * 特殊任务 - 复用AI执行器基础设施
     * 
     * <p>特殊任务本质上是AI任务的变体，区别仅在于提示词不同。
     * 执行器、队列、线程池等都复用AI的基础设施。</p>
     */
    SPECIAL("OTHER", "特殊任务（复用AI基础设施）", "AI执行器"),

    /**
     * V3任务 - V3 GUI自动化任务（逐步调度大提示词工作流）
     *
     * <p>V3使用独立的基础设施（独立队列、独立线程池、独立执行器），
     * 不与AI/SPECIAL共享。任务通过promptId从PromptsEntity表逐步加载指令，
     * Agent回调完成一步后再查DB发下一步，直到工作流结束。</p>
     */
    V3("V3", "V3 GUI自动化任务", "V3执行器");

    /**
     * 类型编码（存储到数据库的值）
     */
    private final String code;

    /**
     * 类型描述（用于展示）
     */
    private final String description;

    /**
     * 对应的执行器名称
     */
    private final String executorName;

    /**
     * 构造方法
     * 
     * @param code 类型编码
     * @param description 类型描述
     * @param executorName 执行器名称
     */
    TaskType(String code, String description, String executorName) {
        this.code = code;
        this.description = description;
        this.executorName = executorName;
    }

    /**
     * 获取类型编码
     * 
     * @return 类型编码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取类型描述
     * 
     * @return 类型描述字符串
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取执行器名称
     * 
     * @return 执行器名称
     */
    public String getExecutorName() {
        return executorName;
    }

    /**
     * 根据编码获取枚举实例
     * 
     * @param code 类型编码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static TaskType fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (TaskType type : TaskType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        
        return null;
    }

    /**
     * 判断给定的编码是否为有效的类型编码
     * 
     * @param code 类型编码
     * @return true表示有效，false表示无效
     */
    public static boolean isValidCode(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取实际执行器类型
     *
     * <p>SPECIAL类型复用AI的执行器，此方法返回实际应该使用的执行器类型。</p>
     *
     * @return 实际执行器类型
     */
    public TaskType getActualExecutorType() {
        return this == SPECIAL ? AI : this;
    }

    /**
     * 获取实际队列类型
     *
     * <p>SPECIAL类型复用AI的队列，此方法返回实际应该使用的队列类型。</p>
     *
     * @return 实际队列类型
     */
    public TaskType getActualQueueType() {
        return this == SPECIAL ? AI : this;
    }

    /**
     * 判断是否使用AI基础设施（执行器、队列、线程池）
     *
     * @return true表示使用AI基础设施
     */
    public boolean usesAIInfrastructure() {
        return this == AI || this == SPECIAL;
    }
}
