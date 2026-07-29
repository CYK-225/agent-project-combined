package com.cyk.task.core.constants;

/**
 * 任务调度系统常量定义类
 * 
 * <p>【核心职责】</p>
 * 本类集中定义任务调度系统中使用的各种常量，避免魔法数字和字符串散落在代码各处，
 * 提高代码的可维护性和可读性。
 * 
 * <p>【常量分类】</p>
 * <ul>
 *   <li><b>重试相关</b>：最大重试次数、重试间隔等</li>
 *   <li><b>队列相关</b>：队列容量、轮询间隔等</li>
 *   <li><b>超时相关</b>：任务超时时间、连接超时时间等</li>
 *   <li><b>线程池相关</b>：核心线程数、最大线程数、队列容量等</li>
 *   <li><b>心跳相关</b>：心跳检测间隔、心跳超时时间等</li>
 * </ul>
 * 
 * <p>【使用建议】</p>
 * <ul>
 *   <li>所有常量都使用static final修饰</li>
 *   <li>常量名使用全大写，单词间用下划线分隔</li>
 *   <li>建议在配置文件中配置这些值，而非硬编码</li>
 *   <li>生产环境可以通过配置中心动态调整</li>
 * </ul>
 * 
 * @author system
 * @since 1.0
 */
public final class TaskConstants {

    /**
     * 私有构造方法，防止实例化
     */
    private TaskConstants() {
        throw new AssertionError("常量类不应被实例化");
    }

    // ==================== 重试相关常量 ====================

    /**
     * 任务最大重试次数
     * 
     * <p>当任务执行失败时，如果重试次数小于此值，会自动重新加入队列。</p>
     */
    public static final int MAX_RETRY_COUNT = 3;

    /**
     * 重试间隔时间（毫秒）
     * 
     * <p>任务失败后，等待此时间再重新加入队列，避免立即重试导致连续失败。</p>
     */
    public static final long RETRY_INTERVAL_MS = 60_000L; // 1分钟

    // ==================== 队列相关常量 ====================

    /**
     * 任务队列容量
     * 
     * <p>每种类型任务的内存队列最大容量，超过此容量的任务需要等待队列腾出空间。</p>
     */
    public static final int QUEUE_CAPACITY = 1000;

    /**
     * 队列轮询间隔（毫秒）
     * 
     * <p>调度器从数据库查询待执行任务的间隔时间。</p>
     */
    public static final long QUEUE_POLL_INTERVAL_MS = 1000L; // 1秒

    /**
     * 每次从数据库获取的任务数量
     * 
     * <p>调度器每次从数据库查询待执行任务时的批量大小。</p>
     */
    public static final int BATCH_FETCH_SIZE = 10;

    // ==================== 超时相关常量 ====================

    /**
     * 任务执行超时时间（毫秒）
     * 
     * <p>单个任务的最大执行时间，超过此时间将被强制中断。</p>
     */
    public static final long TASK_TIMEOUT_MS = 600_000L; // 10分钟

    /**
     * Docker容器启动超时时间（毫秒）
     */
    public static final long CONTAINER_START_TIMEOUT_MS = 30_000L; // 30秒

    /**
     * HTTP请求超时时间（毫秒）
     */
    public static final int HTTP_REQUEST_TIMEOUT_MS = 30_000; // 30秒

    // ==================== 线程池相关常量 ====================

    /**
     * ENS任务线程池核心线程数
     */
    public static final int ENS_POOL_CORE_SIZE = 3;

    /**
     * ENS任务线程池最大线程数
     */
    public static final int ENS_POOL_MAX_SIZE = 2;

    /**
     * AI任务线程池核心线程数
     * 
     * <p>AI任务通常执行时间较长，核心线程数设置较小，避免资源耗尽。</p>
     */
    public static final int AI_POOL_CORE_SIZE = 3;

    /**
     * AI任务线程池最大线程数
     */
    public static final int AI_POOL_MAX_SIZE = 5;

    /**
     * 特殊任务线程池核心线程数
     */
    public static final int SPECIAL_POOL_CORE_SIZE = 2;

    /**
     * 特殊任务线程池最大线程数
     */
    public static final int SPECIAL_POOL_MAX_SIZE = 3;

    /**
     * 线程池空闲线程存活时间（秒）
     */
    public static final int THREAD_KEEP_ALIVE_SECONDS = 60;

    // ==================== 心跳检测相关常量 ====================

    /**
     * 心跳检测间隔（小时）
     * 
     * <p>定时任务每隔此时间检查一次账号健康状态。</p>
     */
    public static final int HEARTBEAT_CHECK_INTERVAL_HOURS = 24;

    /**
     * 账号未心跳超时阈值（小时）
     * 
     * <p>如果账号超过此时间未进行心跳检测，将被纳入检测范围。</p>
     */
    public static final int HEARTBEAT_TIMEOUT_HOURS = 24;

    // ==================== 账号池相关常量 ====================

    /**
     * 账号获取最大重试次数
     * 
     * <p>当账号池中所有账号都被占用时，重试获取账号的最大次数。</p>
     */
    public static final int ACCOUNT_ACQUIRE_MAX_RETRY = 3;

    /**
     * 可用账号数量告警阈值
     * 
     * <p>当可用账号数量低于此值时，触发告警。</p>
     */
    public static final int ACCOUNT_AVAILABLE_THRESHOLD = 2;

    // ==================== 日志相关常量 ====================

    /**
     * 日志保留天数
     * 
     * <p>超过此天数的日志将被归档或删除。</p>
     */
    public static final int LOG_RETENTION_DAYS = 90;

    /**
     * 单个任务最大日志条数
     * 
     * <p>如果任务的日志条数超过此值，可能存在异常。</p>
     */
    public static final int MAX_LOG_COUNT_PER_TASK = 1000;

    // ==================== 其他常量 ====================

    /**
     * 任务ID前缀 - ENS任务
     */
    public static final String TASK_ID_PREFIX_ENS = "ens_";

    /**
     * 任务ID前缀 - AI任务
     */
    public static final String TASK_ID_PREFIX_AI = "ai_";

    /**
     * 任务ID前缀 - 特殊任务
     */
    public static final String TASK_ID_PREFIX_SPECIAL = "special_";

    // ==================== V3 任务相关常量 ====================

    /**
     * V3任务线程池最大线程数
     */
    public static final int V3_POOL_MAX_SIZE = 3;

    /**
     * V3任务每步预估执行耗时（毫秒）
     *
     * <p>大提示词中每一条指令的预估执行时间，用于动态计算整体超时。</p>
     */
    public static final long V3_PER_STEP_ESTIMATED_MS = 120_000L; // 2分钟

    /**
     * V3任务超时安全系数
     *
     * <p>总预估时间乘以此系数，防止意外耗时导致超时误杀。</p>
     */
    public static final double V3_TIMEOUT_SAFETY_FACTOR = 2.0;

    /**
     * V3任务整体超时下限（毫秒）
     *
     * <p>就算大提示词只有1步，也至少给这么多时间。</p>
     */
    public static final long V3_MIN_TIMEOUT_MS = 600_000L; // 10分钟

    /**
     * V3任务整体超时上限（毫秒）
     *
     * <p>防止大提示词步骤太多算出离谱的超时。</p>
     */
    public static final long V3_MAX_TIMEOUT_MS = 3_600_000L; // 60分钟

}
