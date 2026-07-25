package com.cyk.Controller;

import com.cyk.Enity.PromptsVO;
import com.cyk.Enity.addPromptsTo;
import com.cyk.Enity.table.PromptsEntity;
import com.cyk.Enity.updatePromptsTo;
import com.cyk.Service.impl.AllService;
import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
public class PromptsController {

    @Resource
    private AllService allService;

    /**
     * 1. 新增提示词 (包含大提示词及小提示词数组)
     * 对应 Service: add(addPromptsTo promptsList)
     */
    @PostMapping("/add")
    public ResultData<String> add(@RequestBody addPromptsTo promptsList) {
        return allService.getPromptsService().add(promptsList);
    }

    /**
     * 2. 删除提示词 (根据主键 ID)
     * 对应 Service: deleteById(Integer id)
     */
    @DeleteMapping("/{id}")
    public ResultData<String> delete(@PathVariable Long id) {
        return allService.getPromptsService().deleteById(id);
    }

    /**
     * 3. 修改提示词 (直接覆盖小提示词数组)
     * 对应 Service: updateById(updatePromptsTo promptsTo)
     */
    @PostMapping("/updatePrompts")
    public ResultData<String> update(@RequestBody updatePromptsTo promptsTo) {
        return allService.getPromptsService().update(promptsTo);
    }

    /**
     * 4. 根据大提示词 promptId 查询小提示词数组
     * 对应 Service: selectListByPromptId(String promptId)
     * 注意：为了和 getById 区分，这里路径加上了 /list/
     */
    @GetMapping("/list/{promptId}")
    public ResultData<List<PromptsEntity>> getListByPromptId(@PathVariable String promptId) {
        return allService.getPromptsService().selectListByPromptId(promptId);
    }

    /**
     * 5. 分页查询大提示词
     * 对应 Service: selectListByPage(Integer pageNumber, Integer pageSize)
     */
    @GetMapping("/page")
    public ResultData<Page<PromptsVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return allService.getPromptsService().selectListByPage(pageNumber, pageSize);
    }

    /**
     * 6. 查询单个提示词详情 (根据主键 ID)
     * 保留 Mybatis-Flex 自带的方法，并用 ResultData 包装返回
     */
    @GetMapping("/{id}")
    public ResultData<PromptsEntity> getById(@PathVariable Long id) {
        PromptsEntity entity = allService.getPromptsService().getById(id);
        if (entity == null) {
            return ResultData.error("未查询到相关提示词");
        }
        return ResultData.success(entity);
    }

    /**
     * 7. 查询提示词 (根据提示词标题模糊查询)
     */
    @PostMapping("/listByName")
    public ResultData<List<PromptsEntity>> selectByName(@RequestParam("name") String name) {
        if (name == null) return ResultData.error("参数错误");
        return allService.getPromptsService().selectListByName(name);
    }


}