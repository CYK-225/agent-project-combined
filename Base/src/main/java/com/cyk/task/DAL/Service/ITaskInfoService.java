package com.cyk.task.DAL.Service;



import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 核心任务流水表-控制ENS/AI/特殊任务的排队与流转 服务层接口
 * 
 * <p>【核心职责】</p>
 * 本服务接口是任务调度系统的核心业务层，封装了任务的创建、查询、状态管理和重试逻辑。
 * 继承MyBatis-Flex的IService接口，自动获得基础的CRUD能力，同时定义业务特定的方法。
 * 
 * <p>【继承能力】</p>
 * 通过继承IService&lt;TaskInfoEntity&gt;，自动拥有以下能力：
 * <ul>
 *   <li><b>基础操作</b>：save()、updateById()、removeById()、getById()</li>
 *   <li><b>批量操作</b>：saveBatch()、updateBatch()、removeBatch()</li>
 *   <li><b>列表查询</b>：list()、listByIds()、listByQuery()</li>
 *   <li><b>分页查询</b>：page()</li>
 *   <li><b>计数统计</b>：count()</li>
 * </ul>
 * 
 * <p>【业务方法】</p>
 * 本接口额外定义以下业务特定的方法：
 * <ul>
 *   <li><b>任务创建</b>：createTask() - 创建新任务并加入排队</li>
 *   <li><b>排队查询</b>：getPendingTasks() - 获取待执行的任务列表</li>
 *   <li><b>状态管理</b>：startTask()、completeTask()、failTask() - 更新任务状态</li>
 *   <li><b>重试机制</b>：retryTask() - 失败任务的重试逻辑</li>
 *   <li><b>排队位置</b>：getQueuePosition() - 查询任务在队列中的位置</li>
 * </ul>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 注入服务
 * {@literal @}Autowired
 * private ITaskInfoService taskInfoService;
 * 
 * // 创建任务
 * TaskInfoEntity task = taskInfoService.createTask(companyId, companyName, taskType);
 * 
 * // 查询排队位置
 * int position = taskInfoService.getQueuePosition(task.getId());
 * 
 * // 开始执行
 * taskInfoService.startTask(task.getId());
 * 
 * // 完成任务
 * taskInfoService.completeTask(task.getId(), collectedFields);
 * </pre>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskInfoEntity 任务信息实体类
 * @see IService MyBatis-Flex基础服务接口
 */
public interface ITaskInfoService extends IService<TaskInfoEntity> {

    /**
     * 创建新任务并加入排队
     * 
     * <p>任务创建流程：</p>
     * <ol>
     *   <li>设置任务基本信息（公司ID、公司名称、任务类型）</li>
     *   <li>记录创建时间戳（用于排队排序）</li>
     *   <li>初始化状态为PENDING</li>
     *   <li>初始化重试次数为0</li>
     *   <li>保存到数据库</li>
     * </ol>
     * 
     * @param companyId 公司ID（唯一标识）
     * @param companyName 公司名称（展示用）
     * @param taskType 任务类型（ENS/AI/特殊）
     * @param configName 配置名称（仅AI任务需要）
     * @return 创建的任务实体
     */


    @Transactional(rollbackFor = Exception.class)
    TaskInfoEntity createTask(String companyId, String companyName, String taskType, String configName
            , Map<String, Object> collectedFields
            , Boolean isUpdate, String mission, String webAddress, String userId, Long promptId);

    /**
     * 获取指定类型的待执行任务列表
     * 
     * <p>按create_time_ms升序排列，确保先提交的任务先被执行。</p>
     * 
     * @param taskType 任务类型
     * @param limit 返回的最大记录数
     * @return 待执行的任务列表
     */
    List<TaskInfoEntity> getPendingTasks(String taskType, int limit);

    Page<TaskInfoEntity> getTasksByDatePage(int pageNumber, int pageSize, LocalDate startDate, LocalDate endDate);

    /**
     * 开始执行任务（状态：PENDING → RUNNING）
     * 
     * <p>更新任务状态为RUNNING，同时记录start_time。</p>
     * 
     * @param taskId 任务ID
     * @return 是否成功开始
     */
    boolean startTask(Long taskId, String configName);

    /**
     * 完成任务（状态：RUNNING → SUCCESS）
     * 
     * <p>更新任务状态为SUCCESS，同时保存收集的字段数据。</p>
     * 
     * @param taskId 任务ID
     * @param collectedFields 收集的字段数据
     * @return 是否成功完成
     */
    @Transactional(rollbackFor = Exception.class)
    boolean completeTask(Long taskId, Map<String, Object> collectedFields);

    /**
     * 任务执行失败（状态：RUNNING → FAILED）
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>更新状态为FAILED</li>
     *   <li>记录失败原因和失败时间</li>
     *   <li>检查重试次数，若<3则自动重新加入队列</li>
     * </ol>
     * 
     * @param taskId 任务ID
     * @param failureReason 失败原因
     * @return 是否成功标记为失败
     */
    boolean failTask(Long taskId, String failureReason,String status);

    /**
     * 获取任务在队列中的位置
     *
     * <p>用于向前端展示"您前面还有N个任务在排队"。</p>
     *
     * @param taskId 任务ID
     * @return 队列位置（0表示队首，-1表示任务不存在或不在队列中）
     */
    int getQueuePosition(Long taskId);

    /**
     * 获取需要重试的失败任务列表
     *
     * <p>查询条件：status=FAILED 且 retry_count < 3</p>
     *
     * @param limit 返回的最大记录数
     * @return 需要重试的任务列表
     */
    List<TaskInfoEntity> getRetryableTasks(int limit);

    /**
     * 批量更新任务状态
     *
     * <p>用于批量标记任务为RUNNING状态。</p>
     *
     * @param taskIds 任务ID列表
     * @param status 新状态
     * @return 更新的记录数
     */
    int batchUpdateStatus(List<Long> taskIds, String status);

    /**
     * 检查是否存在重复的进行中任务
     * 
     * <p>查询条件：公司名相同、任务类型相同、状态为PENDING或RUNNING</p>
     * 
     * @param companyName 公司名称
     * @param taskType 任务类型
     * @return 如果存在重复任务返回true，否则返回false
     */
    boolean existsActiveTask(String companyName, String taskType);

    List<TaskInfoEntity> getTasksByCompanyName(String companyName);
}