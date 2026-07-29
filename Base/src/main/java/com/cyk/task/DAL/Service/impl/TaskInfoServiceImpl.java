package com.cyk.task.DAL.Service.impl;



import com.cyk.Utils.ShortIdUtil;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Mapper.TaskInfoMapper;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.mybatisflex.core.update.UpdateChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 核心任务流水表-控制ENS/AI/特殊任务的排队与流转 服务层实现
 * 
 * <p>【核心职责】</p>
 * 本服务实现类是任务调度系统的核心业务逻辑层，实现了任务的生命周期管理、
 * 状态流转、容错重试和排队查询等关键业务功能。
 * 
 * <p>【继承关系】</p>
 * <ul>
 *   <li>继承ServiceImpl：获得MyBatis-Flex提供的基础CRUD能力</li>
 *   <li>实现ITaskInfoService：定义业务特定的方法契约</li>
 * </ul>
 * 
 * <p>【事务管理】</p>
 * 本类中的所有公共方法默认开启事务，确保数据一致性：
 * <ul>
 *   <li>任务创建、状态更新等操作具有原子性</li>
 *   <li>失败时自动回滚，避免数据不一致</li>
 *   <li>建议在Service层而非Controller层控制事务边界</li>
 * </ul>
 * 
 * <p>【设计原则】</p>
 * <ul>
 *   <li><b>单一职责</b>：本类只负责任务信息的业务逻辑，不涉及鉴权和日志</li>
 *   <li><b>依赖注入</b>：通过继承获得Mapper，无需显式注入</li>
 *   <li><b>异常处理</b>：业务异常向上抛出，由统一异常处理器处理</li>
 * </ul>
 * 
 * <p>【扩展建议】</p>
 * <ul>
 *   <li>如需更复杂的查询，可以使用QueryWrapper构建动态查询</li>
 *   <li>如需缓存，可以在方法上添加@Cacheable注解</li>
 *   <li>如需异步执行，可以在方法上添加@Async注解</li>
 * </ul>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see ITaskInfoService 任务信息服务接口
 * @see TaskInfoMapper 任务信息Mapper接口
 * @see TaskInfoEntity 任务信息实体类
 */
@Slf4j
@Service
public class TaskInfoServiceImpl extends ServiceImpl<TaskInfoMapper, TaskInfoEntity> implements ITaskInfoService {

    /**
     * 创建新任务并加入排队
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>构造TaskInfoEntity对象</li>
     *   <li>设置create_time_ms为当前时间戳（毫秒级）</li>
     *   <li>设置初始状态为PENDING</li>
     *   <li>调用save()方法保存到数据库</li>
     * </ol>
     * 
     * @param companyId 公司ID
     * @param companyName 公司名称
     * @param taskType 任务类型
     * @param configName 配置名称
     * @return 创建的任务实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfoEntity createTask(String companyId, String companyName, String taskType, String configName
            , Map<String, Object> collectedFields
            , Boolean isUpdate, String mission, String webAddress, String userId, Long promptId) {

        // 1. 使用 MyBatis-Flex 的 QueryWrapper 查询是否已存在历史任务
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(TaskInfoEntity::getCompanyId).eq(companyId)
                .and(TaskInfoEntity::getTaskType).eq(taskType);

        TaskInfoEntity task = this.getOne(queryWrapper);

        // 【核心修改点】：判断当前任务是否需要独占账号（只有 ens 任务需要）
        boolean requireExclusive = "ens".equalsIgnoreCase(taskType);

        if (task != null) {
            // 2. 如果存在，执行覆盖逻辑
            task.setCompanyName(companyName);
            task.setConfigName(configName);
            task.setCollectedFields(collectedFields);
            task.setIsExclusive(requireExclusive); // 修正：按任务类型赋予独占标志
            task.setMission(mission);
            task.setWebAddress(webAddress);
            task.setUserId(userId);

            // 重置状态机
            task.setStatus("PENDING");
            task.setCreateTimeMs(System.currentTimeMillis());
            task.setRetryCount(0);

            // 清空上一轮的脏数据
            task.setFailureReason(null);
            task.setStartTime(null);
            task.setLastFailureTime(null);

            // 使用 MyBatis-Flex 的 UpdateChain 强制更新
            // Flex 的 UpdateChain 天然支持将字段更新为 null，非常适合做数据重置
            UpdateChain.of(TaskInfoEntity.class)
                    .set(TaskInfoEntity::getCompanyName, companyName)
                    .set(TaskInfoEntity::getConfigName, configName)
                    .set(TaskInfoEntity::getCollectedFields, collectedFields)
                    .set(TaskInfoEntity::getIsExclusive, requireExclusive) // 修正：按任务类型赋予独占标志
                    .set(TaskInfoEntity::getMission, mission)
                    .set(TaskInfoEntity::getWebAddress, webAddress)
                    .set(TaskInfoEntity::getUserId, userId)
                    .set(TaskInfoEntity::getPromptId, promptId)
                    .set(TaskInfoEntity::getStatus, "PENDING")
                    .set(TaskInfoEntity::getCreateTimeMs, System.currentTimeMillis())
                    .set(TaskInfoEntity::getRetryCount, 0)
                    .set(TaskInfoEntity::getFailureReason, null, true) // 第三个参数为 true 时强制设置 null (部分 Flex 版本可选)
                    .set(TaskInfoEntity::getStartTime, null, true)
                    .set(TaskInfoEntity::getLastFailureTime, null, true)
                    .where(TaskInfoEntity::getId).eq(task.getId())
                    .update();

            log.info("║  [任务覆盖] 检测到公司 [{}] 已有 {} 任务(ID:{})，执行覆盖逻辑", companyName, taskType, task.getId());
        } else {
            // 3. 如果不存在，执行新增逻辑
            task = new TaskInfoEntity();

            // 提取雪花ID，避免调用两次浪费ID
            long newId = ShortIdUtil.nextId();
            task.setId(newId);
            log.info("雪花生成任务id：" + newId);

            task.setCompanyId(companyId);
            task.setCompanyName(companyName);
            task.setTaskType(taskType);
            task.setConfigName(configName);
            task.setStatus("PENDING");
            task.setCreateTimeMs(System.currentTimeMillis());
            task.setRetryCount(0);
            task.setCollectedFields(collectedFields);
            task.setIsExclusive(requireExclusive); // 修正：按任务类型赋予独占标志
            task.setMission(mission);
            task.setWebAddress(webAddress);
            task.setUserId(userId);
            task.setPromptId(promptId);

            this.save(task);
            log.info("║  [任务新建] 为公司 [{}] 创建新的 {} 任务(ID:{})", companyName, taskType, task.getId());
        }

        return task;
    }

    /**
     * 获取指定类型的待执行任务列表
     * 
     * <p>调用Mapper的selectPendingTasks方法，
     * 按create_time_ms升序排列，确保严格遵循FIFO原则。</p>
     * 
     * @param taskType 任务类型
     * @param limit 返回的最大记录数
     * @return 待执行的任务列表
     */
    @Override
    public List<TaskInfoEntity> getPendingTasks(String taskType, int limit) {
        return   selectPendingTasks(taskType, limit);
    }

    /**
     * 根据创建获取任务列表
     */
    @Override
    public Page<TaskInfoEntity> getTasksByDatePage(int pageNumber, int pageSize, LocalDate startDate, LocalDate endDate) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(TaskInfoEntity.class);

        // 处理开始时间：转换为当天的 00:00:00.000 的毫秒时间戳
        if (startDate != null) {
            long startMs = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            queryWrapper.and(TaskInfoEntity::getCreateTimeMs).ge(startMs);
        }

        // 处理结束时间：转换为当天的 23:59:59.999 的毫秒时间戳
        if (endDate != null) {
            long endMs = endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            queryWrapper.and(TaskInfoEntity::getCreateTimeMs).le(endMs);
        }

        // 默认按创建时间倒序排列，优先展示最新任务
        queryWrapper.orderBy(TaskInfoEntity::getCreateTimeMs, false);

        // 构建分页对象并执行分页查询
        Page<TaskInfoEntity> page = new Page<>(pageNumber, pageSize);
        return mapper.paginate(page, queryWrapper);
    }


    /**
     * 开始执行任务
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>查询任务是否存在</li>
     *   <li>检查当前状态是否为PENDING</li>
     *   <li>更新状态为RUNNING</li>
     *   <li>记录start_time</li>
     * </ol>
     * 
     * @param taskId 任务ID
     * @return 是否成功开始
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startTask(Long taskId, String configName) {
        TaskInfoEntity task = this.getById(taskId);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            return false;
        }

        return UpdateChain.of(TaskInfoEntity.class)
                .set(TaskInfoEntity::getStatus, "RUNNING")
                .set(TaskInfoEntity::getStartTime, LocalDate.now())
                .set(TaskInfoEntity::getFailureReason, null)
                .set(TaskInfoEntity::getConfigName, configName) // 【核心修改点】将最新绑定的账号配置持久化到数据库
                .where(TaskInfoEntity::getId).eq(taskId)
                .update();
    }

    /**
     * 完成任务
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>查询任务是否存在</li>
     *   <li>检查当前状态是否为RUNNING</li>
     *   <li>更新状态为SUCCESS</li>
     *   <li>保存收集的字段数据</li>
     * </ol>
     * 
     * @param taskId 任务ID
     * @param collectedFields 收集的字段数据
     * @return 是否成功完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(Long taskId, Map<String, Object> collectedFields) {
        TaskInfoEntity task = this.getById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) {
            return false;
        }

        // 使用 UpdateChain 强制置空，保持数据库干净，利于后续审计
        return UpdateChain.of(TaskInfoEntity.class)
                .set(TaskInfoEntity::getStatus, "SUCCESS")
                .set(TaskInfoEntity::getCollectedFields, collectedFields)
                .set(TaskInfoEntity::getFailureReason, null)
                .where(TaskInfoEntity::getId).eq(taskId)
                .update();
    }

    /**
     * 任务执行失败
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>更新任务状态为FAILED</li>
     *   <li>记录失败原因和时间</li>
     *   <li>调用Mapper的incrementRetryCount方法增加重试计数</li>
     *   <li>如果重试次数<3，状态会自动重置为PENDING，等待重新执行</li>
     * </ol>
     * 
     * @param taskId 任务ID
     * @param failureReason 失败原因
     * @return 是否成功标记为失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean failTask(Long taskId, String failureReason,String status) {
        TaskInfoEntity task = this.getById(taskId);
        if (task == null) {
            return false;
        }
        log.info("  进入了failTask方法,任务失败回调: taskId={}, taskType={}, failureReason={}", taskId, task.getTaskType(), failureReason);
        // 调用Mapper方法增加重试计数并判断是否需要重试
        int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
        
        if (status.equals("RESTART")) {
            // 增加重试计数，状态自动重置为PENDING
              incrementRetryCount(taskId, failureReason);
        } else {
            // 已达到最大重试次数，标记为最终失败
            task.setStatus("FAILED");
            task.setFailureReason(failureReason);
            task.setLastFailureTime(LocalDate.now());
            this.updateById(task);
        }
        
        return true;
    }




    /**
     * 获取任务在队列中的位置
     *
     * <p>实现细节：</p>
     * <ol>
     *   <li>查询任务是否存在</li>
     *   <li>检查任务状态是否为PENDING</li>
     *   <li>调用Mapper的countTasksBeforeMe方法计算排队位置</li>
     * </ol>
     *
     * @param taskId 任务ID
     * @return 队列位置（0表示队首，-1表示任务不存在或不在队列中）
     */
    @Override
    public int getQueuePosition(Long taskId) {
        TaskInfoEntity task = this.getById(taskId);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            return -1;
        }

        return   countTasksBeforeMe(task.getTaskType(), task.getCreateTimeMs());
    }

    /**
     * 获取需要重试的失败任务列表
     *
     * <p>调用Mapper的selectRetryableTasks方法，
     * 查询status=FAILED且retry_count<3的任务。</p>
     *
     * @param limit 返回的最大记录数
     * @return 需要重试的任务列表
     */
    @Override
    public List<TaskInfoEntity> getRetryableTasks(int limit) {
        return   selectRetryableTasks(limit);
    }

    /**
     * 批量更新任务状态
     *
     * <p>调用Mapper的batchUpdateStatus方法，
     * 用于批量标记任务为RUNNING状态。</p>
     *
     * @param taskIds 任务ID列表
     * @param status 新状态
     * @return 更新的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(List<Long> taskIds, String status) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        return   executeBatchUpdateStatus(taskIds, status);
    }

    /**
     * 检查是否存在重复的进行中任务
     *
     * <p>查询条件：公司名相同、任务类型相同、状态为PENDING或RUNNING</p>
     *
     * @param companyName 公司名称
     * @param taskType 任务类型
     * @return 如果存在重复任务返回true，否则返回false
     */
    @Override
    public boolean existsActiveTask(String companyName, String taskType) {
        return   checkExistsActiveTask(companyName, taskType);
    }

    /**
     * 查询指定类型的排队任务（按创建时间升序）
     * * <p>用于任务调度器从队列中取出待执行的任务。
     * 查询结果按create_time_ms升序排列，确保严格遵循"先来后到"原则。</p>
     * * <p>使用场景：</p>
     * <ul>
     * <li>AI任务调度器：获取下一个待执行的AI任务</li>
     * <li>ENS任务调度器：获取下一个待执行的ENS任务</li>
     * </ul>
     * * @param taskType 任务类型（ENS/AI/特殊）
     * @param limit 返回的最大记录数
     * @return 排队中的任务列表（按提交时间升序）
     */
    private List<TaskInfoEntity> selectPendingTasks(String taskType, int limit) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .where(TaskInfoEntity::getTaskType).eq(taskType)
                .and(TaskInfoEntity::getStatus).eq("PENDING")
                .orderBy(TaskInfoEntity::getCreateTimeMs, true) // true 代表 ASC
                .limit(limit)
        );
    }

    /**
     * 统计排在指定任务前面的待处理任务数量
     * * <p>用于向用户展示"您前面还有N个任务在排队"。
     * 通过比较create_time_ms，精确计算排队位置。</p>
     * * <p>计算逻辑：</p>
     * <pre>
     * 排在前面 = 同类型 + 待处理 + 提交时间更早
     * </pre>
     * * @param taskType 任务类型
     * @param createTimeMs 当前任务的创建时间戳
     * @return 排在前面的任务数量
     */
    private int countTasksBeforeMe(String taskType, Long createTimeMs) {
        return (int) mapper.selectCountByQuery(QueryWrapper.create()
                .where(TaskInfoEntity::getTaskType).eq(taskType)
                .and(TaskInfoEntity::getStatus).eq("PENDING")
                .and(TaskInfoEntity::getCreateTimeMs).lt(createTimeMs)
        );
    }

    /**
     * 批量更新任务状态（从PENDING到RUNNING）
     * * <p>当任务被线程池接管开始执行时，需要更新状态为RUNNING，
     * 同时记录start_time。支持批量更新以提高效率。</p>
     * * <p>使用场景：</p>
     * <ul>
     * <li>任务调度器取出任务后，批量标记为RUNNING</li>
     * <li>系统重启后恢复中断的任务</li>
     * </ul>
     * * @param taskIds 任务ID列表
     * @param status 新状态
     * @return 更新的记录数
     */
    private int executeBatchUpdateStatus(List<Long> taskIds, String status) {
        boolean updated = UpdateChain.of(TaskInfoEntity.class)
                .set(TaskInfoEntity::getStatus, status)
                .setRaw(TaskInfoEntity::getStartTime, "CURRENT_TIMESTAMP")
                .where(TaskInfoEntity::getId).in(taskIds)
                .update();
        return updated ? taskIds.size() : 0;
    }

    /**
     * 查询需要重试的失败任务
     * * <p>根据架构设计，任务失败且重试次数<3时，需要重新投递到任务队列。
     * 此方法查询符合条件的失败任务。</p>
     * * <p>查询条件：</p>
     * <ul>
     * <li>status = 'FAILED'</li>
     * <li>retry_count < 3</li>
     * <li>按失败时间排序（优先重试早期失败的任务）</li>
     * </ul>
     * * @param limit 返回的最大记录数
     * @return 需要重试的任务列表
     */
    private List<TaskInfoEntity> selectRetryableTasks(int limit) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .where(TaskInfoEntity::getStatus).eq("FAILED")
                .and(TaskInfoEntity::getRetryCount).lt(3)
                .orderBy(TaskInfoEntity::getLastFailureTime, true) // true 代表 ASC
                .limit(limit)
        );
    }

    /**
     * 增加任务的重试计数
     * * <p>当任务执行失败时，调用此方法增加重试计数，
     * 同时更新失败时间和失败原因。</p>
     * * @param taskId 任务ID
     * @param failureReason 失败原因
     * @return 更新的记录数
     */
    private int incrementRetryCount(Long taskId, String failureReason) {
        return UpdateChain.of(TaskInfoEntity.class)
                .setRaw(TaskInfoEntity::getRetryCount, "retry_count + 1")
                .set(TaskInfoEntity::getStatus, "PENDING")
                .set(TaskInfoEntity::getFailureReason, failureReason)
                .setRaw(TaskInfoEntity::getLastFailureTime, "CURRENT_TIMESTAMP")
                .where(TaskInfoEntity::getId).eq(taskId)
                .update() ? 1:0 ;
    }

    /**
     * 检查是否存在进行中的重复任务
     * * <p>查询条件：公司名相同、任务类型相同、状态为PENDING或RUNNING</p>
     * * @param companyName 公司名称
     * @param taskType 任务类型
     * @return 如果存在重复任务返回true，否则返回false
     */
    private boolean checkExistsActiveTask(String companyName, String taskType) {
        long count = mapper.selectCountByQuery(QueryWrapper.create()
                .where(TaskInfoEntity::getCompanyName).eq(companyName)
                .and(TaskInfoEntity::getTaskType).eq(taskType)
                .and(TaskInfoEntity::getStatus).in("PENDING", "RUNNING")
                .limit(1)
        );
        return count > 0;
    }

    /**
     * 根据公司名查询任务
     * * <p>查询条件：公司名相同</p>
     * * @param companyName 公司名称
     */
    @Override
    public List<TaskInfoEntity> getTasksByCompanyName(String companyName) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .where(TaskInfoEntity::getCompanyName).eq(companyName)
        );
    }
}