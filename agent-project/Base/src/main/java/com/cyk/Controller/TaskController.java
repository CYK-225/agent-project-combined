package com.cyk.Controller;

import com.cyk.task.DAL.Service.impl.TaskInfoServiceImpl;
import com.cyk.task.DAL.Service.impl.TaskLogServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class TaskController {

    @Resource
    private TaskInfoServiceImpl taskInfoService;

    @Resource
    private TaskLogServiceImpl taskLogService;

//
//    /**
//     * 分页查询任务列表TaskInfo
//     *
//     * @param page
//     * @param size
//     */
//    @GetMapping("/task/list/{page}/")


}
