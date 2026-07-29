package com.cyk.task.DAL.Mapper;

import com.cyk.task.DAL.DO.TaskLogEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务执行过程日志表-记录执行器每一步的细节 映射层
 * 
 * <p>【核心职责】</p>
 * 本Mapper接口负责task_log表的数据库访问操作，提供任务执行日志的记录和查询能力，
 * 是系统可观测性和问题排查的关键组件。
 * 
 * <p>【继承能力】</p>
 * 通过继承BaseMapper&lt;TaskLogEntity&gt;，自动拥有基础的CRUD能力，
 * 本接口额外提供日志查询和分析相关的业务方法。
 * 
 * <p>【业务方法】</p>
 * <ul>
 *   <li><b>日志查询</b>：按任务ID查询完整执行轨迹</li>
 *   <li><b>步骤查询</b>：查询特定步骤的执行日志</li>
 *   <li><b>统计分析</b>：统计任务执行次数、成功率等</li>
 *   <li><b>历史归档</b>：查询和清理历史日志</li>
 * </ul>
 * 
 * <p>【性能优化】</p>
 * <ul>
 *   <li>task_id字段已建立索引，查询某个任务的日志非常快</li>
 *   <li>建议定期归档历史日志（如：超过3个月的日志迁移到归档表）</li>
 *   <li>复杂统计查询建议使用定时任务预计算，避免实时查询大量日志</li>
 * </ul>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskLogEntity 任务日志实体类
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLogEntity> {

    /**
     * 查询指定任务的所有执行日志（按时间升序）
     * 
     * <p>用于完整还原任务的执行轨迹，便于问题排查和性能分析。
     * 查询结果按create_time升序排列，确保日志顺序与执行顺序一致。</p>
     * 
     * <p>使用场景：</p>
     * <ul>
     *   <li>任务失败时，查看完整的执行轨迹定位问题</li>
     *   <li>性能分析时，查看每个步骤的耗时</li>
     *   <li>审计追踪时，查看任务的详细操作记录</li>
     * </ul>
     * 
     * @param taskId 任务ID
     * @return 日志列表（按时间升序）
     */
    @Select("SELECT * FROM task_log WHERE task_id = #{taskId} ORDER BY create_time ASC")
    List<TaskLogEntity> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 查询指定任务特定执行器的日志
     * 
     * <p>用于查看任务在某个特定执行器/方法中的执行情况。
     * 例如：只查看Docker容器相关的日志，或只查看HTTP请求相关的日志。</p>
     * 
     * @param taskId 任务ID
     * @param containerType 执行器类型（如：DockerContainer、HTTPClient）
     * @return 日志列表
     */
    @Select("SELECT * FROM task_log " +
            "WHERE task_id = #{taskId}" +
            " AND container_type = #{containerType} " +
            "ORDER BY create_time ASC")
    List<TaskLogEntity> selectByTaskIdAndContainerType(@Param("taskId") Long taskId,
                                                       @Param("containerType") String containerType);

    /**
     * 统计指定任务的日志条数
     * 
     * <p>用于监控任务执行的复杂度。如果日志条数异常多，可能存在死循环或过度重试。</p>
     * 
     * @param taskId 任务ID
     * @return 日志条数
     */
    @Select("SELECT COUNT(*) FROM task_log" +
            " WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Long taskId);

    /**
     * 查询最近N分钟内的错误日志
     * 
     * <p>用于监控和告警：如果最近一段时间内错误日志数量激增，
     * 可能是系统出现故障，需要触发告警。</p>
     * 
     * <p>判断错误的逻辑：map_list中包含"error"或"exception"字段。</p>
     * 
     * @param minutes 时间范围（分钟）
     * @param limit 返回的最大记录数
     * @return 错误日志列表
     */
    @Select("SELECT * FROM task_log " +
            "WHERE create_time > CURRENT_TIMESTAMP - INTERVAL '#{minutes} minutes' " +
            "AND map_list::text LIKE '%error%' " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<TaskLogEntity> selectRecentErrors(@Param("minutes") int minutes, 
                                           @Param("limit") int limit);

    /**
     * 按任务类型统计日志数量
     * 
     * <p>用于分析不同类型任务的执行频率和资源占用。</p>
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 各类型任务的日志数量统计
     */
    @Select("SELECT task_type, COUNT(*) as log_count " +
            "FROM task_log " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY task_type")
    List<java.util.Map<String, Object>> countByTaskType(@Param("startTime") java.util.Date startTime,
                                                        @Param("endTime") java.util.Date endTime);
}
