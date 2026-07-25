package com.cyk.task.DAL.DO;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.Long;
import java.time.LocalDate;

import java.lang.String;
import java.lang.Integer;
import java.util.Map;

/**
 * 核心任务流水表-控制ENS/AI/特殊任务的排队与流转 实体类
 * * <p>【核心职责】</p>
 * 本实体是整个任务调度系统的核心数据载体，负责记录和管理所有类型任务的生命周期。
 * 系统通过此表实现任务的统一排队、状态流转、容错重试和结果收集。
 * * <p>【任务类型说明】</p>
 * <ul>
 * <li><b>ENS任务</b>：企业信息查询任务，通过ENS执行器在Docker容器中运行</li>
 * <li><b>AI任务</b>：AI驱动的自动化任务，需要读取配置文件并使用鉴权信息</li>
 * <li><b>特殊任务</b>：其他定制化任务类型</li>
 * </ul>
 * * <p>【状态流转】</p>
 * <pre>
 * PENDING(排队中) → RUNNING(执行中) → SUCCESS(成功)
 * ↓
 * FAILED(失败) → 重试(若retry_count < 3)
 * </pre>
 * * <p>【关键设计】</p>
 * <ul>
 * <li>使用create_time_ms毫秒级时间戳确保AI任务的严格FIFO排队</li>
 * <li>collected_fields采用JSONB格式，灵活存储任务收集的动态字段</li>
 * <li>支持最多3次自动重试，超过则标记为最终失败</li>
 * <li>通过company_id而非company_name进行唯一绑定，避免同名公司冲突</li>
 * </ul>
 * * <p>【性能优化】</p>
 * 建议在(task_type, status, create_time_ms)上建立复合索引，加速排队查询
 * * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskLogEntity 任务执行日志表
 * @see AuthInfoEntity 鉴权信息表
 */
@Table(value = "task_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskInfoEntity {

    /**
     * 任务表主键ID，唯一标识一条任务流水记录
     */
    @Id(keyType = KeyType.None)
    private Long id;

    /**
     * 公司ID，由于可能存在同名的公司，必须用ID作为唯一绑定标识（对应图中“可能存在公司名称相同，但是id不同的情况”）
     */
    @Column(value = "company_id")
    private String companyId;

    /**
     * 公司名称，用于直观展示和部分无需严格ID匹配的业务场景
     */
    @Column(value = "company_name")
    private String companyName;

    /**
     * 任务执行时需要读取的具体配置名称
     */
    @Column(value = "config_name")
    private String configName;

    /**
     * 任务的具体类型，主要分为：ENS、AI、OTHER。路由分发时依赖此字段
     */
    @Column(value = "task_type")
    private String taskType;

    /**
     * 任务当前所处的生命周期状态。主要包含：PENDING(排队中)、RUNNING(执行中)、SUCCESS(执行成功)、FAILED(执行失败)
     */
    @Column(value = "status")
    private String status;

    /**
     * 任务收集的字段列表（对应图中的Map/var数组）。JSONB格式，任务分发时创建，存放待做（值为null）和执行后已做的数据结果
     */
    @Column(value = "collected_fields", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> collectedFields;

    /**
     * 任务提交时的13位时间戳（毫秒值），极其核心！AI任务排队时，严格依赖此字段计算先来后到，判断当前排在第几位
     */
    @Column(value = "create_time_ms")
    private Long createTimeMs;

    /**
     * 任务真正开始由线程池接手执行的时间。对于AI任务，基于此时间加上预估时长（如10min）即可向前端展示倒计时
     */
    @Column(value = "start_time")
    private LocalDate startTime;

    /**
     * 任务执行失败后的自动重试次数。根据架构图，未完成且重试次数<3时，会将整体任务重新投递到任务中心
     */
    @Column(value = "retry_count")
    private Integer retryCount;

    /**
     * 任务失败的具体原因（如Cookie过期问题、程序逻辑报错等），由故障处理模块写入，便于后期人工排查
     */
    @Column(value = "failure_reason")
    private String failureReason;

    /**
     * 任务的附加备注信息，预留给人工排查或特殊标记使用
     */
    @Column(value = "remark")
    private String remark;

    /**
     * 该任务最近一次执行失败的时间，结合重试次数一起作为容错机制的参考
     */
    @Column(value = "last_failure_time")
    private LocalDate lastFailureTime;

    /**
     * 任务是否独占配置
     */
    @Column(value="is_exclusive")
    private Boolean isExclusive;

    /**
     * 任务内容（ens为网址，ai则为提示词）
     */
    @Column(value = "mission")
    private String mission;

    /**
     * 所使用的网址
     */
    @Column(value = "web_address")
    private String webAddress;

    /**
     * 用户ID
     */
    @Column(value = "user_id")
    private String userId;

    /**
     * 大提示词ID（V3任务专用，关联prompts表的prompt_id）
     */
    @Column(value = "prompt_id")
    private Long promptId;

}