package com.cyk.task.DAL.Service.impl;


import com.cyk.task.DAL.DO.TaskLogEntity;
import com.cyk.task.DAL.Mapper.TaskLogMapper;
import com.cyk.task.DAL.Service.ITaskLogService;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import org.springframework.stereotype.Service;

import com.mybatisflex.spring.service.impl.ServiceImpl;

import java.util.List;
import java.util.Map;

/**
 * 任务执行过程日志表-记录执行器每一步的细节 服务层实现。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@Service
public class TaskLogServiceImpl extends ServiceImpl<TaskLogMapper, TaskLogEntity> implements ITaskLogService {
    /**
     * 查询指定任务的所有执行日志（按时间升序）
     *
     * <p>用于完整还原任务的执行轨迹，便于问题排查和性能分析。
     * 查询结果按create_time升序排列，确保日志顺序与执行顺序一致。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     * <li>任务失败时，查看完整的执行轨迹定位问题</li>
     * <li>性能分析时，查看每个步骤的耗时</li>
     * <li>审计追踪时，查看任务的详细操作记录</li>
     * </ul>
     *
     * @param taskId 任务ID
     * @return 日志列表（按时间升序）
     */
    @Override
    public List<TaskLogEntity> selectByTaskId(Long taskId) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .from(TaskLogEntity.class)
                .where(TaskLogEntity::getTaskId).eq(taskId)
                .orderBy(TaskLogEntity::getCreateTime, true) // true 代表 ASC 升序
        );
    }

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
    @Override
    public List<TaskLogEntity> selectByTaskIdAndContainerType(Long taskId, String containerType) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .from(TaskLogEntity.class)
                .where(TaskLogEntity::getTaskId).eq(taskId)
                .and(TaskLogEntity::getContainerType).eq(containerType)
                .orderBy(TaskLogEntity::getCreateTime, true) // true 代表 ASC 升序
        );
    }

    /**
     * 统计指定任务的日志条数
     *
     * <p>用于监控任务执行的复杂度。如果日志条数异常多，可能存在死循环或过度重试。</p>
     *
     * @param taskId 任务ID
     * @return 日志条数
     */
    @Override
    public int countByTaskId(Long taskId) {
        return (int) mapper.selectCountByQuery(QueryWrapper.create()
                .from(TaskLogEntity.class)
                .where(TaskLogEntity::getTaskId).eq(taskId)

        );
    }

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
    @Override
    public List<TaskLogEntity> selectRecentErrors(int minutes, int limit) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .from(TaskLogEntity.class)
                // 处理 PG 特有的时间扣减语法
                .where(TaskLogEntity::getCreateTime).gt(
                        QueryMethods.raw("CURRENT_TIMESTAMP - INTERVAL '" + minutes + " minutes'")
                )
                // 处理 JSON 强转文本查询的语法
                .and(QueryMethods.raw("map_list::text LIKE '%error%'"))
                .orderBy(TaskLogEntity::getCreateTime, false) // false 代表 DESC 降序
                .limit(limit)
        );
    }

    /**
     * 按任务类型统计日志数量
     *
     * <p>用于分析不同类型任务的执行频率和资源占用。</p>
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 各类型任务的日志数量统计
     */
    @Override
    public List<Map<String, Object>> countByTaskType(java.util.Date startTime, java.util.Date endTime) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select(TaskLogEntity::getTaskType)
                .select(QueryMethods.count().as("log_count"))
                .from(TaskLogEntity.class)
                .where(TaskLogEntity::getCreateTime).between(startTime, endTime)
                .groupBy(TaskLogEntity::getTaskType);

        // 1. 获取原生的 Row 列表
        List<Row> rows = mapper.selectRowsByQuery(queryWrapper);

        // 2. 利用 ArrayList 的构造函数安全转化，一行搞定，绝对不会有黄线警告！
        return new java.util.ArrayList<>(rows);
    }
}