package com.cyk.Controller;

import com.cyk.Enity.table.MiniPromptsEntity;
import com.cyk.Service.impl.MiniPromptsServiceImpl;
import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
public class MiniPromptsController {

    @Resource
    MiniPromptsServiceImpl miniPromptsService;

    /**
     * 新增小提示词
     * @return
     */
    @PostMapping("/addMiniPrompts")
    public ResultData<String> addMiniPrompts(@RequestBody MiniPromptsEntity miniPromptsEntity) {
        try {
            if (miniPromptsEntity == null) throw new Exception("参数错误");
            return miniPromptsService.add(miniPromptsEntity);
        } catch (Exception e) {
            log.error("新增失败", e);
            return ResultData.error("新增失败");
        }
    }

    /**
     * 修改小提示词
     * @return
     */
    @PostMapping("/updateMiniPrompts")
    public ResultData<String> updateMiniPrompts(@RequestBody MiniPromptsEntity miniPromptsEntity) {
        try {
            if (miniPromptsEntity == null) throw new Exception("参数错误");
            return miniPromptsService.update(miniPromptsEntity);
        } catch (Exception e) {
            log.error("修改失败", e);
            return ResultData.error("修改失败");
        }
    }

    /**
     * 删除小提示词
     * @return
     */
    @PostMapping("/deleteMiniPrompts/{id}")
    public ResultData<String> deleteMiniPrompts(@PathVariable Integer id) {
        try {
            if (id == null) throw new Exception("参数错误");
            return miniPromptsService.deleteById(id);
        } catch (Exception e) {
            log.error("删除失败", e);
            return ResultData.error("删除失败");
        }
    }

    /**
     * 分页查询小提示词列表
     * @return
     */
    @PostMapping("/selectMiniPromptsList")
    public ResultData<Page<MiniPromptsEntity>> selectMiniPromptsList(
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return miniPromptsService.selectPage(pageNumber, pageSize, keyword);
    }

    /**
     * 根据ID查询小提示词
     * @return
     */
    @PostMapping("/selectMiniPromptsById/{id}")
    public ResultData<MiniPromptsEntity> selectMiniPromptsById(@PathVariable Integer id) {
        return miniPromptsService.selectById(id);
    }

    /**
     * 根据用户ID查询小提示词
     * @return
     */
    @PostMapping("/selectMiniPromptsByUserId/{userId}")
    public ResultData<List<MiniPromptsEntity>> selectMiniPromptsByUserId(@PathVariable Integer userId) {
        return miniPromptsService.selectListByUserId(userId);
    }

    /**
     * 根据类型分页查询小提示词
     * @return
     */
    @PostMapping("/selectMiniPromptsByType/{type}")
    public ResultData<Page<MiniPromptsEntity>> selectMiniPromptsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return miniPromptsService.selectListByType(type, pageNumber, pageSize, keyword);
    }
}
