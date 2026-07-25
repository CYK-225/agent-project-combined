package com.cyk.task.DAL.Controller;

import com.cyk.task.DAL.Controller.DTO.CreateTaskTO;
import com.cyk.task.DAL.DO.TaskInfoEntity;
import com.cyk.task.DAL.Mapper.TaskInfoMapper;
import com.cyk.task.DAL.Service.ITaskInfoService;
import com.cyk.task.core.scheduler.CustomTaskScheduler;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心任务流水表-控制ENS/AI/特殊任务的排队与流转 控制层
 * 
 * <p>【核心职责】</p>
 * 本控制器提供任务信息的HTTP接口，是前端与任务调度系统交互的主要入口。
 * 支持任务的创建、查询、更新、删除等基础操作，以及业务特定的排队查询、状态查询等功能。
 * 
 * <p>【接口分类】</p>
 * <ul>
 *   <li><b>基础CRUD</b>：save、update、remove、list、page、getInfo</li>
 *   <li><b>业务查询</b>：getQueuePosition（排队位置）、getTaskStatus（任务状态）</li>
 *   <li><b>任务创建</b>：createTask（推荐使用此接口创建任务）</li>
 * </ul>
 * 
 * <p>【接口规范】</p>
 * <ul>
 *   <li>所有接口返回JSON格式</li>
 *   <li>成功响应：直接返回数据对象或true</li>
 *   <li>失败响应：返回false或错误信息</li>
 *   <li>异常由统一异常处理器处理</li>
 * </ul>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 创建任务
 * POST /taskInfo/createTask
 * {
 *   "companyId": "123456",
 *   "companyName": "示例公司",
 *   "taskType": "AI",
 *   "configName": "qcc_pro"
 * }
 * 
 * // 查询排队位置
 * GET /taskInfo/queuePosition/123
 * 
 * // 查询任务状态
 * GET /taskInfo/status/123
 * </pre>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see ITaskInfoService 任务信息服务
 * @see TaskInfoEntity 任务信息实体
 */
@RestController
@RequestMapping("/taskInfo")
public class TaskInfoController {

    /**
     * 任务信息服务
     */
    @Autowired
    private ITaskInfoService taskInfoService;

    @Resource
    private CustomTaskScheduler customTaskScheduler;
    @Resource
    private TaskInfoMapper taskInfoMapper;

    /**
     * 创建新任务（推荐使用）
     * 
     * <p>创建一个新的任务并加入排队。相比save接口，此接口会自动初始化
     * createTimeMs、status、retryCount等字段。</p>
     * 
     * <p>请求示例：</p>
     * <pre>
     * POST /taskInfo/createTask
     * {
     *   "companyId": "123456",
     *   "companyName": "示例公司",
     *   "taskType": "AI",
     *   "configName": "qcc_pro"
     * }
     * </pre>
     * 
     * @return 创建结果（包含任务ID和排队信息）
     */
    @PostMapping("/createTask")
    public Map<String, Object> createTask(@RequestBody CreateTaskTO createTaskTO) throws InterruptedException {

        
        Long taskId = customTaskScheduler.startTask(createTaskTO);
        int queuePosition = taskInfoService.getQueuePosition(taskId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("queuePosition", queuePosition);
        result.put("message", "任务创建成功，当前排在第" + (queuePosition + 1) + "位");
        
        return result;
    }

    /**
     * 查询任务的排队位置
     * 
     * <p>返回当前任务在队列中的位置（0表示队首）。</p>
     * 
     * @param taskId 任务ID
     * @return 排队位置信息
     */
    @GetMapping("/queuePosition/{taskId}")
    public Map<String, Object> getQueuePosition(@PathVariable Long taskId) {
        int position = taskInfoService.getQueuePosition(taskId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("position", position);
        result.put("displayText", position >= 0 ? "您前面还有" + position + "个任务" : "任务不在队列中");
        
        return result;
    }

    /**
     * 查询任务状态
     * 
     * <p>返回任务的详细状态信息，包括当前状态、重试次数、失败原因等。</p>
     * 
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    @GetMapping("/status/{taskId}")
    public Map<String, Object> getTaskStatus(@PathVariable Long taskId) {
        TaskInfoEntity task = taskInfoService.getById(taskId);
        
        Map<String, Object> result = new HashMap<>();
        if (task == null) {
            result.put("success", false);
            result.put("error", "任务不存在");
            return result;
        }
        
        result.put("success", true);
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        result.put("taskType", task.getTaskType());
        result.put("retryCount", task.getRetryCount());
        result.put("failureReason", task.getFailureReason());
        result.put("createTime", task.getCreateTimeMs());
        result.put("startTime", task.getStartTime());
        result.put("userId", task.getUserId());
        return result;
    }

    /**
     * 添加任务（基础接口）
     * 
     * <p>直接保存任务实体，不自动初始化字段。建议使用createTask接口。</p>
     *
     * @param taskInfo 任务信息
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody TaskInfoEntity taskInfo) {
        return taskInfoService.save(taskInfo);
    }

    /**
     * 根据主键删除任务
     *
     * @param id 任务主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return taskInfoService.removeById(id);
    }

    /**
     * 根据主键更新任务
     *
     * @param taskInfo 任务信息
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody TaskInfoEntity taskInfo) {
        return taskInfoService.updateById(taskInfo);
    }

    /**
     * 查询所有任务
     * 
     * <p>注意：如果任务数量很多，建议使用分页查询接口。</p>
     *
     * @return 所有任务数据
     */
    @GetMapping("/list")
    public List<TaskInfoEntity> list() {
        return taskInfoService.list();
    }

    /**
     * 根据主键获取任务详细信息
     *
     * @param id 任务主键
     * @return 任务详情
     */
    @GetMapping("/getInfo/{id}")
    public TaskInfoEntity getInfo(@PathVariable Serializable id) {
        return taskInfoService.getById(id);
    }

    /**
     * 分页查询任务
     *
     * @param pageNumber 页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @return 分页对象（包含数据列表和总数）
     */
    @GetMapping("/page")
    public Page<TaskInfoEntity> page(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<TaskInfoEntity> page = new Page<>(pageNumber, pageSize);
        return taskInfoService.page(page);
    }

    /**
     * 根据公司名称获取任务列表
     *
     * @param companyName 公司名称
     * <p>如果为空，则返回空列表</p>
     */
    @GetMapping("/listByCompanyName")
    public List<TaskInfoEntity> listByCompanyName(@RequestParam(required = false) String companyName) {
        if (companyName == null) {
            return taskInfoService.list();
        }
        return taskInfoService.getTasksByCompanyName(companyName);
    }

    /**
     * 根据日期范围分页查询任务
     * * <p>查询条件：开始日期和结束日期，包含当天。支持单边查询（仅传开始或仅传结束）。</p>
     * * @param pageNumber 页码（从1开始，默认1）
     * @param pageSize   每页条数（默认10）
     * @param startDate  开始日期，格式：yyyy-MM-dd
     * @param endDate    结束日期，格式：yyyy-MM-dd
     * @return 分页对象（包含数据列表和总数）
     */
    @GetMapping("/pageByDate")
    public Page<TaskInfoEntity> pageByDate(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        return taskInfoService.getTasksByDatePage(pageNumber, pageSize, startDate, endDate);
    }
}