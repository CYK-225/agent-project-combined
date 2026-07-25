package com.cyk.task.DAL.Controller;


import com.cyk.task.DAL.DO.TaskLogEntity;
import com.cyk.task.DAL.Service.ITaskLogService;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;

/**
 * 任务执行过程日志表-记录执行器每一步的细节 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/taskLog")
public class TaskLogController {

    @Autowired
    private ITaskLogService taskLogService;

    /**
     * 添加 任务执行过程日志表-记录执行器每一步的细节
     *
     * @param taskLog 任务执行过程日志表-记录执行器每一步的细节
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody TaskLogEntity taskLog) {
        return taskLogService.save(taskLog);
    }


    /**
     * 根据主键删除任务执行过程日志表-记录执行器每一步的细节
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return taskLogService.removeById(id);
    }


    /**
     * 根据主键更新任务执行过程日志表-记录执行器每一步的细节
     *
     * @param taskLog 任务执行过程日志表-记录执行器每一步的细节
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody TaskLogEntity taskLog) {
        return taskLogService.updateById(taskLog);
    }


    /**
     * 查询所有任务执行过程日志表-记录执行器每一步的细节
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<TaskLogEntity> list() {
        return taskLogService.list();
    }


    /**
     * 根据任务执行过程日志表-记录执行器每一步的细节主键获取详细信息。
     *
     * @param id taskLog主键
     * @return 任务执行过程日志表-记录执行器每一步的细节详情
     */
    @GetMapping("/getInfo/{id}")
    public TaskLogEntity getInfo(@PathVariable Serializable id) {
        return taskLogService.getById(id);
    }


    /**
     * 分页查询任务执行过程日志表-记录执行器每一步的细节
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<TaskLogEntity> page(Page<TaskLogEntity> page) {
        return taskLogService.page(page);
    }
}