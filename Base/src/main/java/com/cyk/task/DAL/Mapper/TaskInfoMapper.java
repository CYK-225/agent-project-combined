package com.cyk.task.DAL.Mapper;


import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * 核心任务流水表-控制ENS/AI/特殊任务的排队与流转 映射层
 * 
 * <p>【核心职责】</p>
 * 本Mapper接口负责task_info表的数据库访问操作，是任务调度系统的数据访问核心。
 * 继承MyBatis-Flex的BaseMapper，自动获得基础的CRUD能力，同时提供业务特定的查询方法。
 * 
 * <p>【继承能力】</p>
 * 通过继承BaseMapper&lt;TaskInfoEntity&gt;，自动拥有以下能力：
 * <ul>
 *   <li><b>插入</b>：insert(entity)、insertBatch(list)</li>
 *   <li><b>更新</b>：update(entity)、updateById(entity)</li>
 *   <li><b>删除</b>：deleteById(id)、deleteBatchIds(list)</li>
 *   <li><b>查询</b>：selectOneById(id)、selectAll()、selectListByQuery(query)</li>
 *   <li><b>分页</b>：paginate(page, query)</li>
 * </ul>
 * 
 * <p>【业务方法】</p>
 * 本接口额外提供以下业务特定的查询和更新方法：
 * <ul>
 *   <li>查询排队中的任务（按时间戳排序）</li>
 *   <li>统计某个任务前面的排队数量</li>
 *   <li>批量更新任务状态</li>
 *   <li>查询需要重试的失败任务</li>
 * </ul>
 * 
 * <p>【性能优化】</p>
 * <ul>
 *   <li>使用@Select注解的查询方法，MyBatis会在启动时预编译SQL</li>
 *   <li>复杂查询建议使用MyBatis-Flex的QueryWrapper构建</li>
 *   <li>批量操作优先使用insertBatch/updateBatch，减少数据库往返</li>
 * </ul>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskInfoEntity 任务信息实体类
 * @see BaseMapper MyBatis-Flex基础Mapper接口
 */
@Mapper
public interface TaskInfoMapper extends BaseMapper<TaskInfoEntity> {

//    /**
//     * 查询指定类型的排队任务（按创建时间升序）
//     *
//     * <p>用于任务调度器从队列中取出待执行的任务。
//     * 查询结果按create_time_ms升序排列，确保严格遵循"先来后到"原则。</p>
//     *
//     * <p>使用场景：</p>
//     * <ul>
//     *   <li>AI任务调度器：获取下一个待执行的AI任务</li>
//     *   <li>ENS任务调度器：获取下一个待执行的ENS任务</li>
//     * </ul>
//     *
//     * @param taskType 任务类型（ENS/AI/特殊）
//     * @param limit 返回的最大记录数
//     * @return 排队中的任务列表（按提交时间升序）
//     */
//    @Select("SELECT * FROM task_info " +
//            "WHERE task_type = #{taskType} AND status = 'PENDING' " +
//            "ORDER BY create_time_ms ASC " +
//            "LIMIT #{limit}")
//    List<TaskInfoEntity> selectPendingTasks(@Param("taskType") String taskType,
//                                            @Param("limit") int limit);
//
//    /**
//     * 统计排在指定任务前面的待处理任务数量
//     *
//     * <p>用于向用户展示"您前面还有N个任务在排队"。
//     * 通过比较create_time_ms，精确计算排队位置。</p>
//     *
//     * <p>计算逻辑：</p>
//     * <pre>
//     * 排在前面 = 同类型 + 待处理 + 提交时间更早
//     * </pre>
//     *
//     * @param taskType 任务类型
//     * @param createTimeMs 当前任务的创建时间戳
//     * @return 排在前面的任务数量
//     */
//    @Select("SELECT COUNT(*) FROM task_info " +
//            "WHERE task_type = #{taskType} " +
//            "AND status = 'PENDING' " +
//            "AND create_time_ms < #{createTimeMs}")
//    int countTasksBeforeMe(@Param("taskType") String taskType,
//                          @Param("createTimeMs") Long createTimeMs);
//
//    /**
//     * 批量更新任务状态（从PENDING到RUNNING）
//     *
//     * <p>当任务被线程池接管开始执行时，需要更新状态为RUNNING，
//     * 同时记录start_time。支持批量更新以提高效率。</p>
//     *
//     * <p>使用场景：</p>
//     * <ul>
//     *   <li>任务调度器取出任务后，批量标记为RUNNING</li>
//     *   <li>系统重启后恢复中断的任务</li>
//     * </ul>
//     *
//     * @param taskIds 任务ID列表
//     * @param status 新状态
//     * @return 更新的记录数
//     */
//    @Update("<script>" +
//            "UPDATE task_info SET status = #{status}, start_time = CURRENT_TIMESTAMP " +
//            "WHERE id IN " +
//            "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>" +
//            "#{id}" +
//            "</foreach>" +
//            "</script>")
//    int batchUpdateStatus(@Param("taskIds") List<Long> taskIds,
//                         @Param("status") String status);
//
//    /**
//     * 查询需要重试的失败任务
//     *
//     * <p>根据架构设计，任务失败且重试次数<3时，需要重新投递到任务队列。
//     * 此方法查询符合条件的失败任务。</p>
//     *
//     * <p>查询条件：</p>
//     * <ul>
//     *   <li>status = 'FAILED'</li>
//     *   <li>retry_count < 3</li>
//     *   <li>按失败时间排序（优先重试早期失败的任务）</li>
//     * </ul>
//     *
//     * @param limit 返回的最大记录数
//     * @return 需要重试的任务列表
//     */
//    @Select("SELECT * FROM task_info " +
//            "WHERE status = 'FAILED' AND retry_count < 3 " +
//            "ORDER BY last_failure_time ASC " +
//            "LIMIT #{limit}")
//    List<TaskInfoEntity> selectRetryableTasks(@Param("limit") int limit);
//
//    /**
//     * 增加任务的重试计数
//     *
//     * <p>当任务执行失败时，调用此方法增加重试计数，
//     * 同时更新失败时间和失败原因。</p>
//     *
//     * @param taskId 任务ID
//     * @param failureReason 失败原因
//     * @return 更新的记录数
//     */
//    @Update("UPDATE task_info SET " +
//            "retry_count = retry_count + 1, " +
//            "status = 'PENDING', " +
//            "failure_reason = #{failureReason}, " +
//            "last_failure_time = CURRENT_TIMESTAMP " +
//            "WHERE id = #{taskId}")
//    int incrementRetryCount(@Param("taskId") Long taskId,
//                           @Param("failureReason") String failureReason);
//
//    /**
//     * 检查是否存在进行中的重复任务
//     *
//     * <p>查询条件：公司名相同、任务类型相同、状态为PENDING或RUNNING</p>
//     *
//     * @param companyName 公司名称
//     * @param taskType 任务类型
//     * @return 如果存在重复任务返回1，否则返回0
//     */
//    @Select("SELECT COUNT(*) FROM task_info " +
//            "WHERE company_name = #{companyName} " +
//            "AND task_type = #{taskType} " +
//            "AND status IN ('PENDING', 'RUNNING') " +
//            "LIMIT 1")
//    boolean existsActiveTask(@Param("companyName") String companyName,
//                            @Param("taskType") String taskType);
}
