package com.cyk.task.core.enums;

/**
 * 任务状态枚举
 * 
 * <p>【核心职责】</p>
 * 本枚举定义了任务在生命周期中可能处于的所有状态，是任务调度系统的核心常量定义。
 * 使用枚举而非字符串常量，可以避免拼写错误，提高代码的可维护性。
 * 
 * <p>【状态流转】</p>
 * <pre>
 *                    ┌─────────────────┐
 *                    │    PENDING      │ (排队中)
 *                    └────────┬────────┘
 *                             │
 *                             ▼
 *                    ┌─────────────────┐
 *                    │    RUNNING      │ (执行中)
 *                    └────┬───────┬────┘
 *                         │       │
 *                 成功    │       │  失败
 *                         │       │
 *                         ▼       ▼
 *                ┌──────────┐ ┌──────────┐
 *                │ SUCCESS  │ │  FAILED  │
 *                └──────────┘ └────┬─────┘
 *                                  │
 *                          retry_count < 3 ?
 *                                  │
 *                            Yes  │  No
 *                                  │
 *                                  ▼
 *                          回到PENDING  最终失败
 * </pre>
 * 
 * <p>【状态说明】</p>
 * <ul>
 *   <li><b>PENDING</b>：任务已创建，正在排队等待执行</li>
 *   <li><b>RUNNING</b>：任务已被执行器接管，正在执行中</li>
 *   <li><b>SUCCESS</b>：任务执行成功，结果已收集完毕</li>
 *   <li><b>FAILED</b>：任务执行失败（可能是暂时失败，等待重试）</li>
 * </ul>
 * 
 * <p>【使用建议】</p>
 * <ul>
 *   <li>在代码中使用枚举常量，而非字符串字面量</li>
 *   <li>状态转换时使用枚举比较，而非字符串equals</li>
 *   <li>需要存储到数据库时，调用getCode()获取字符串值</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 */
public enum TaskStatus {

    /**
     * 排队中 - 任务已创建，等待执行器接管
     */
    PENDING("PENDING", "排队中"),

    /**
     * 执行中 - 任务已被执行器接管，正在执行
     */
    RUNNING("RUNNING", "执行中"),

    /**
     * 执行成功 - 任务执行完成，结果已收集
     */
    SUCCESS("SUCCESS", "执行成功"),

    /**
     * 执行失败 - 任务执行失败，可能触发重试
     */
    FAILED("FAILED", "执行失败");



    /**
     * 状态编码（存储到数据库的值）
     */
    private final String code;

    /**
     * 状态描述（用于展示）
     */
    private final String description;

    /**
     * 构造方法
     * 
     * @param code 状态编码
     * @param description 状态描述
     */
    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态编码
     * 
     * <p>用于存储到数据库或进行字符串比较。</p>
     * 
     * @return 状态编码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取状态描述
     * 
     * <p>用于前端展示或日志记录。</p>
     * 
     * @return 状态描述字符串
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取枚举实例
     * 
     * <p>从数据库读取状态字符串后，可以使用此方法转换为枚举。</p>
     * 
     * @param code 状态编码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static TaskStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (TaskStatus status : TaskStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        
        return null;
    }

    /**
     * 判断给定的编码是否为有效的状态编码
     * 
     * @param code 状态编码
     * @return true表示有效，false表示无效
     */
    public static boolean isValidCode(String code) {
        return fromCode(code) != null;
    }
}
