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
import java.util.Date;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

/**
 * 任务执行过程日志表-记录执行器每一步的细节 实体类
 * 
 * <p>【核心职责】</p>
 * 本实体用于详细记录任务执行过程中的每一步操作，是系统可观测性和问题排查的关键组件。
 * 通过细粒度的日志记录，可以完整还原任务的执行轨迹，便于性能分析和故障诊断。
 * 
 * <p>【日志记录策略】</p>
 * <ul>
 *   <li><b>关键节点记录</b>：任务开始、各步骤执行、请求发送、响应接收、任务结束</li>
 *   <li><b>异常详细记录</b>：捕获异常堆栈、错误信息、失败时间点</li>
 *   <li><b>性能数据记录</b>：每个步骤的耗时、资源占用情况</li>
 * </ul>
 * 
 * <p>【数据结构设计】</p>
 * map_list字段采用JSONB格式存储，可以灵活记录各种类型的执行数据：
 * <pre>
 * [
 *   {
 *     "step": "初始化",
 *     "timestamp": 1699999999999,
 *     "data": {"config": "qcc_pro", "auth_id": 123}
 *   },
 *   {
 *     "step": "发送请求",
 *     "timestamp": 1699999999999,
 *     "data": {"url": "https://...", "method": "GET"}
 *   },
 *   {
 *     "step": "解析响应",
 *     "timestamp": 1699999999999,
 *     "data": {"status": 200, "size": "2.5KB"}
 *   }
 * ]
 * </pre>
 * 
 * <p>【性能优化】</p>
 * <ul>
 *   <li>通过task_type冗余存储，避免连表查询，提高日志检索性能</li>
 *   <li>建议为task_id建立索引，加速"查询某个任务的所有日志"</li>
 *   <li>历史日志可定期归档，避免单表数据量过大</li>
 * </ul>
 * 
 * <p>【典型应用场景】</p>
 * <ul>
 *   <li><b>问题排查</b>：任务失败时，通过日志快速定位失败环节</li>
 *   <li><b>性能分析</b>：统计各步骤平均耗时，识别性能瓶颈</li>
 *   <li><b>审计追踪</b>：完整记录任务执行轨迹，满足合规要求</li>
 *   <li><b>回放重现</b>：基于日志数据重新执行任务流程</li>
 * </ul>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskInfoEntity 任务信息表（父表）
 */
@Table(value = "task_log")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskLogEntity {

    /**
     * 日志表主键ID，唯一标识一条日志记录
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 关联的父任务ID，对应 task_info 表的主键，用于通过任务找寻其产生的所有执行细节日志
     */
    @Column(value = "task_id")
    private Long taskId;

    /**
     * 触发此日志的任务类型（如AI、ENS）。在此处冗余存储是为了查日志时不用再去连表查询，提高性能
     */
    @Column(value = "task_type")
    private String taskType;

    /**
     * 执行器（容器）的具体类型，记录是哪个执行器方法跑的这段逻辑，方便追踪方法调用
     */
    @Column(value = "container_type")
    private String containerType;

    /**
     * 详细的执行过程数据映射表（Map列表）。采用JSONB格式，记录任务每一步产生的中间结果、请求参数或响应报文
     */
    @Column(value = "map_list", typeHandler = JacksonTypeHandler.class)
    private Map<String,Object> mapList;


    /**
     * 这条日志被程序写入数据库的精确时间
     */
    @Column(value = "create_time")
    private Date createTime;


    /**
     * 获取日志表主键ID
     * 
     * @return 日志记录唯一标识ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置日志表主键ID
     * 
     * @param id 日志记录唯一标识ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取关联的父任务ID
     * 
     * <p>外键关联到task_info表的主键ID，用于建立日志与任务的父子关系。
     * 通过此字段可以快速查询某个任务的所有执行日志。</p>
     * 
     * <p>查询示例：</p>
     * <pre>
     * SELECT * FROM task_log WHERE task_id = 123 ORDER BY create_time;
     * </pre>
     * 
     * @return 父任务ID
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 设置关联的父任务ID
     * 
     * @param taskId 父任务ID（必须对应task_info表中存在的记录）
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * 获取触发此日志的任务类型
     * 
     * <p>冗余存储task_type是为了避免查询日志时需要连表查询task_info，
     * 提高日志检索性能。特别是在高并发场景下，这个优化非常关键。</p>
     * 
     * <p>可能值：ENS、AI、特殊</p>
     * 
     * @return 任务类型
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * 设置触发此日志的任务类型
     * 
     * <p>建议从关联的TaskInfoEntity中复制此字段，保持数据一致性。</p>
     * 
     * @param taskType 任务类型
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * 获取执行器（容器）的具体类型
     * 
     * <p>记录是哪个执行器方法或容器执行的这段逻辑，便于追踪问题。
     * 例如：</p>
     * <ul>
     *   <li><b>ENS场景</b>：DockerContainer-001、PythonScript-qcc_query</li>
     *   <li><b>AI场景</b>：AIExecutor-Thread-3、ConfigLoader-pro_mode</li>
     *   <li><b>特殊场景</b>：SpecialHandler-custom_logic</li>
     * </ul>
     * 
     * <p>当任务失败时，通过此字段可以快速定位是哪个执行器/方法出了问题。</p>
     * 
     * @return 执行器类型标识
     */
    public String getContainerType() {
        return containerType;
    }

    /**
     * 设置执行器（容器）的具体类型
     * 
     * @param containerType 执行器类型标识
     */
    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    /**
     * 获取详细的执行过程数据映射表
     * 
     * <p>采用JSONB格式存储任务每一步产生的中间结果、请求参数或响应报文。
     * 数据结构示例：</p>
     * <pre>
     * [
     *   {
     *     "step": "步骤1-初始化",
     *     "timestamp": 1699999999999,
     *     "action": "load_config",
     *     "input": {"config_name": "qcc_pro"},
     *     "output": {"success": true, "config": {...}},
     *     "duration_ms": 50
     *   },
     *   {
     *     "step": "步骤2-鉴权",
     *     "timestamp": 1699999999999,
     *     "action": "validate_cookie",
     *     "input": {"auth_id": 123},
     *     "output": {"valid": true, "user": "张三"},
     *     "duration_ms": 120
     *   },
     *   {
     *     "step": "步骤3-查询",
     *     "timestamp": 1699999999999,
     *     "action": "http_request",
     *     "input": {"url": "https://...", "method": "GET"},
     *     "output": {"status": 200, "data": {...}},
     *     "duration_ms": 350
     *   }
     * ]
     * </pre>
     * 
     * <p>设计要点：</p>
     * <ul>
     *   <li>每个步骤记录完整：输入、输出、耗时</li>
     *   <li>时间戳精确到毫秒，便于性能分析</li>
     *   <li>使用JSONB格式，PostgreSQL支持高效的JSON查询</li>
     * </ul>
     * 
     * @return 执行过程数据列表（JSON格式）
     */
    public Map<String, Object> getMapList() {
        return mapList;
    }

    /**
     * 设置详细的执行过程数据映射表
     * 
     * <p>建议使用List&lt;Map&lt;String, Object&gt;&gt;结构，
     * MyBatis-Flex会自动将其序列化为JSON。</p>
     * 
     * @param mapList 执行过程数据列表
     */
    public void setMapList(Map<String, Object> mapList) {
        this.mapList = mapList;
    }

    /**
     * 获取日志写入数据库的时间
     * 
     * <p>记录这条日志被程序写入数据库的精确时间，用于：</p>
     * <ul>
     *   <li>按时间范围查询日志</li>
     *   <li>分析任务执行的时间分布</li>
     *   <li>审计追踪</li>
     * </ul>
     * 
     * @return 日志创建时间
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * 设置日志写入数据库的时间
     * 
     * <p>通常由数据库自动设置（DEFAULT CURRENT_TIMESTAMP），
     * 也可以在代码中显式设置。</p>
     * 
     * @param createTime 日志创建时间
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
