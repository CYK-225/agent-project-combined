package com.cyk.Service.impl;

import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.cyk.Service.IMiniPromptsService;
import com.cyk.Enity.table.MiniPromptsEntity;
import com.cyk.Mapper.MiniPromptsMapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;

import java.util.List;

/**
 * 服务层实现。
 */
@Service
@Slf4j
public class MiniPromptsServiceImpl extends ServiceImpl<MiniPromptsMapper, MiniPromptsEntity> implements IMiniPromptsService {

    @Resource
    private MiniPromptsMapper miniPromptsMapper;

    @Override
    public ResultData<String> add(MiniPromptsEntity miniPromptsEntity) {
        log.info("执行新增小提示词, 入参: {}", miniPromptsEntity);
        try {
            int count = miniPromptsMapper.insert(miniPromptsEntity);
            if (count > 0) {
                log.info("新增小提示词成功, 生成ID: {}", miniPromptsEntity.getId());
                return ResultData.success("新增成功");
            } else {
                log.warn("新增小提示词失败, 数据库受影响行数为0");
                return ResultData.error("新增失败");
            }
        } catch (Exception e) {
            log.error("新增小提示词发生异常", e);
            return ResultData.error("新增失败: " + e.getMessage());
        }
    }

    @Override
    public ResultData<String> update(MiniPromptsEntity miniPromptsEntity) {
        log.info("执行修改小提示词, 入参: {}", miniPromptsEntity);
        try {
            int count = miniPromptsMapper.update(miniPromptsEntity);
            if (count > 0) {
                log.info("修改小提示词成功, ID: {}", miniPromptsEntity.getId());
                return ResultData.success("修改成功");
            } else {
                log.warn("修改小提示词失败, 未找到对应ID: {}", miniPromptsEntity.getId());
                return ResultData.error("修改失败");
            }
        } catch (Exception e) {
            log.error("修改小提示词发生异常, ID: {}", miniPromptsEntity.getId(), e);
            return ResultData.error("修改失败: " + e.getMessage());
        }
    }

    @Override
    public ResultData<String> deleteById(Integer id) {
        log.info("执行删除小提示词, ID: {}", id);
        try {
            int count = miniPromptsMapper.deleteById(id);
            if (count > 0) {
                log.info("删除小提示词成功, ID: {}", id);
                return ResultData.success("删除成功");
            } else {
                log.warn("删除小提示词失败, 未找到对应ID: {}", id);
                return ResultData.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除小提示词发生异常, ID: {}", id, e);
            return ResultData.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResultData<Page<MiniPromptsEntity>> selectListByType(String type, int pageNumber, int pageSize, String keyword) {
        log.info("执行根据类型分页查询小提示词, type: {}, pageNumber: {}, pageSize: {}, keyword: {}", type, pageNumber, pageSize, keyword);
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .where(MiniPromptsEntity::getType).eq(type);

            // 关键字模糊搜索
            if (keyword != null && !keyword.trim().isEmpty()) {
                queryWrapper.and(MiniPromptsEntity::getTitle).like("%" + keyword.trim() + "%");
            }

            Page<MiniPromptsEntity> resultPage = miniPromptsMapper.paginate(pageNumber, pageSize, queryWrapper);

            if (resultPage == null || resultPage.getRecords().isEmpty()) {
                log.info("根据类型分页查询小提示词结果为空, type: {}", type);
                return ResultData.error("没有查询到数据");
            }
            log.info("根据类型分页查询小提示词成功, type: {}, 当前页共 {} 条数据, 总条数: {}",
                    type, resultPage.getRecords().size(), resultPage.getTotalRow());
            return ResultData.success(resultPage);
        } catch (Exception e) {
            log.error("根据类型分页查询小提示词发生异常, type: {}", type, e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<MiniPromptsEntity> selectById(Integer id) {
        log.info("执行根据ID查询小提示词, ID: {}", id);
        try {
            MiniPromptsEntity miniPromptsEntity = miniPromptsMapper.selectOneById(id);
            if (miniPromptsEntity == null) {
                log.info("根据ID查询小提示词结果为空, ID: {}", id);
                return ResultData.error("没有查询到数据");
            }
            log.info("根据ID查询小提示词成功");
            return ResultData.success(miniPromptsEntity);
        } catch (Exception e) {
            log.error("根据ID查询小提示词发生异常, ID: {}", id, e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<Page<MiniPromptsEntity>> selectPage(int pageNumber, int pageSize, String keyword) {
        log.info("执行分页查询小提示词, pageNumber: {}, pageSize: {}, keyword: {}", pageNumber, pageSize, keyword);
        try {
            QueryWrapper queryWrapper = QueryWrapper.create();

            // 关键字模糊搜索
            if (keyword != null && !keyword.trim().isEmpty()) {
                queryWrapper.and(MiniPromptsEntity::getTitle).like("%" + keyword.trim() + "%");
            }

            Page<MiniPromptsEntity> resultPage = miniPromptsMapper.paginate(pageNumber, pageSize, queryWrapper);

            if (resultPage == null || resultPage.getRecords().isEmpty()) {
                log.info("分页查询小提示词结果为空");
                return ResultData.error("没有查询到数据");
            }
            log.info("分页查询小提示词成功, 当前页共 {} 条数据, 总条数: {}", resultPage.getRecords().size(), resultPage.getTotalRow());
            return ResultData.success(resultPage);
        } catch (Exception e) {
            log.error("分页查询小提示词发生异常", e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<List<MiniPromptsEntity>> selectListByUserId(Integer userId) {
        log.info("执行根据用户ID查询小提示词, userId: {}", userId);
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .where(MiniPromptsEntity::getUserId).eq(userId);

            List<MiniPromptsEntity> list = miniPromptsMapper.selectListByQuery(queryWrapper);
            if (list.isEmpty()) {
                log.info("根据用户ID查询小提示词结果为空, userId: {}", userId);
                return ResultData.error("没有查询到数据");
            }
            log.info("根据用户ID查询小提示词成功, 共 {} 条数据", list.size());
            return ResultData.success(list);
        } catch (Exception e) {
            log.error("根据用户ID查询小提示词发生异常, userId: {}", userId, e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<List<MiniPromptsEntity>> selectListByCategory(Integer type) {
        log.info("执行根据分类类型查询小提示词, type: {}", type);
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .from(MiniPromptsEntity.class)
                    .where(MiniPromptsEntity::getType).eq(type);

            List<MiniPromptsEntity> list = miniPromptsMapper.selectListByQuery(queryWrapper);
            if (list.isEmpty()) {
                log.info("根据分类类型查询小提示词结果为空, type: {}", type);
                return ResultData.error("没有查询到数据");
            }
            log.info("根据分类类型查询小提示词成功, 共 {} 条数据", list.size());
            return ResultData.success(list);
        } catch (Exception e) {
            log.error("根据分类类型查询小提示词发生异常, type: {}", type, e);
            return ResultData.error(e.getMessage());
        }
    }
}