package com.cyk.Service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import com.cyk.Enity.PromptsVO;
import com.cyk.Enity.addPromptsTo;
import com.cyk.Enity.miniPromptsTo;
import com.cyk.Enity.table.PromptsEntity;
import com.cyk.Enity.updatePromptsTo;
import com.cyk.Mapper.PromptsMapper;
import com.cyk.Service.IPromptsService;
import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.cyk.Enity.table.table.PromptsEntityTableDef.PROMPTS_ENTITY;

/**
 * 提示词管理表 服务层实现。
 */
@Service
@Slf4j
public class PromptsServiceImpl extends ServiceImpl<PromptsMapper, PromptsEntity> implements IPromptsService {

    @Resource
    private PromptsMapper promptsMapper;

    @Override
    public ResultData<String> add(addPromptsTo promptsList) {
        log.info("执行添加大提示词, userId: {}, promptTitle: {}",
                promptsList != null ? promptsList.getUserId() : "null",
                promptsList != null ? promptsList.getPromptTitle() : "null");

        try {
            if (promptsList == null) {
                log.warn("添加大提示词失败, 入参 promptsList 为空");
                return ResultData.error("参数错误");
            }

            Long promptId = RandomUtil.randomLong(1000000000L, 9999999999L);
            log.info("生成大提示词组 ID: {}", promptId);

            // 1. 处理系统提示词（step强制为-1）
            int systemPromptsSize = promptsList.getSystemPrompts() != null ? promptsList.getSystemPrompts().size() : 0;
            log.info("准备插入系统提示词, 共 {} 条", systemPromptsSize);
            for (int i = 0; i < systemPromptsSize; i++) {
                PromptsEntity promptsEntity = new PromptsEntity();
                promptsEntity.setUserId(promptsList.getUserId());
                promptsEntity.setPromptId(promptId);
                promptsEntity.setPromptTitle(promptsList.getPromptTitle());
                promptsEntity.setTitle(promptsList.getSystemPrompts().get(i).getTitle());
                promptsEntity.setCategoryId(Long.valueOf(promptsList.getCategoryId()));
                promptsEntity.setContent(promptsList.getSystemPrompts().get(i).getContent());
                promptsEntity.setStep(-1);  // 系统提示词强制为-1
                promptsEntity.setStatus(Integer.valueOf(promptsList.getStatus()));
                promptsEntity.setIsPublic(Integer.valueOf(promptsList.getIsPublic()));

                promptsMapper.insert(promptsEntity);
            }

            // 2. 处理普通小提示词
            int miniPromptsSize = promptsList.getMiniPrompts() != null ? promptsList.getMiniPrompts().size() : 0;
            log.info("准备插入关联的小提示词, 共 {} 条", miniPromptsSize);
            for (int i = 0; i < miniPromptsSize; i++) {
                PromptsEntity promptsEntity = new PromptsEntity();
                promptsEntity.setUserId(promptsList.getUserId());
                promptsEntity.setPromptId(promptId);
                promptsEntity.setPromptTitle(promptsList.getPromptTitle());
                promptsEntity.setTitle(promptsList.getMiniPrompts().get(i).getTitle());
                promptsEntity.setCategoryId(Long.valueOf(promptsList.getCategoryId()));
                promptsEntity.setContent(promptsList.getMiniPrompts().get(i).getContent());
                promptsEntity.setStep(promptsList.getMiniPrompts().get(i).getStep());
                promptsEntity.setStatus(Integer.valueOf(promptsList.getStatus()));
                promptsEntity.setIsPublic(Integer.valueOf(promptsList.getIsPublic()));

                promptsMapper.insert(promptsEntity);
            }

            log.info("添加大提示词及关联步骤成功, promptId: {}, 系统提示词: {} 条, 普通步骤: {} 条",
                    promptId, systemPromptsSize, miniPromptsSize);
            return ResultData.success("添加成功");
        } catch (Exception e) {
            log.error("添加大提示词发生异常", e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<String> deleteById(Long id) {
        log.info("执行删除大提示词, promptId: {}", id);
        try {
            QueryWrapper wrapper = QueryWrapper.create()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_ID.eq(id));

            int count = promptsMapper.deleteByQuery(wrapper);
            if (count > 0) {
                log.info("删除大提示词成功, promptId: {}, 删除了 {} 条关联记录", id, count);
                return ResultData.success("删除成功");
            } else {
                log.warn("删除大提示词失败, 未找到 promptId: {}", id);
                return ResultData.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除大提示词发生异常, promptId: {}", id, e);
            return ResultData.error(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultData<String> update(updatePromptsTo promptsTo) {
        log.info("执行修改大提示词(覆盖模式), promptId: {}", promptsTo != null ? promptsTo.getPromptId() : "null");
        try {
            if (promptsTo == null || promptsTo.getPromptId() == null) {
                log.warn("修改大提示词失败, 入参或 promptId 为空");
                return ResultData.error("参数错误");
            }

            // 1. 删除旧数据
            QueryWrapper deleteWrapper = QueryWrapper.create()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_ID.eq(promptsTo.getPromptId()));
            int deleteCount = promptsMapper.deleteByQuery(deleteWrapper);
            log.info("大提示词覆盖更新 - 步骤1: 成功清理旧数据 {} 条", deleteCount);

            // 2. 准备新数据 - 系统提示词（step强制为-1）
            List<PromptsEntity> entityList = new ArrayList<>();
            if (promptsTo.getSystemPrompts() != null) {
                for (var sysPrompt : promptsTo.getSystemPrompts()) {
                    PromptsEntity entity = new PromptsEntity();
                    entity.setPromptId(promptsTo.getPromptId());
                    entity.setUserId(promptsTo.getUserId());
                    entity.setPromptTitle(promptsTo.getPromptTitle());
                    entity.setCategoryId(Convert.toLong(promptsTo.getCategoryId()));
                    entity.setStatus(Convert.toInt(promptsTo.getStatus()));
                    entity.setIsPublic(Convert.toInt(promptsTo.getIsPublic()));
                    entity.setTitle(sysPrompt.getTitle());
                    entity.setContent(sysPrompt.getContent());
                    entity.setStep(-1);  // 系统提示词强制为-1
                    entityList.add(entity);
                }
            }

            // 3. 准备新数据 - 普通小提示词
            if (promptsTo.getMiniPrompts() != null) {
                for (var mini : promptsTo.getMiniPrompts()) {
                    PromptsEntity entity = new PromptsEntity();
                    entity.setPromptId(promptsTo.getPromptId());
                    entity.setUserId(promptsTo.getUserId());
                    entity.setPromptTitle(promptsTo.getPromptTitle());
                    entity.setCategoryId(Convert.toLong(promptsTo.getCategoryId()));
                    entity.setStatus(Convert.toInt(promptsTo.getStatus()));
                    entity.setIsPublic(Convert.toInt(promptsTo.getIsPublic()));
                    entity.setTitle(mini.getTitle());
                    entity.setContent(mini.getContent());
                    entity.setStep(mini.getStep());
                    entityList.add(entity);
                }
            }

            // 4. 批量插入
            if (!entityList.isEmpty()) {
                promptsMapper.insertBatch(entityList);
                log.info("大提示词覆盖更新 - 步骤2: 成功批量插入新数据 {} 条", entityList.size());
            } else {
                log.info("大提示词覆盖更新 - 步骤2: 传入的提示词数组均为空，无需插入");
            }

            return ResultData.success("修改成功");
        } catch (Exception e) {
            log.error("修改大提示词发生异常, promptId: {}", promptsTo != null ? promptsTo.getPromptId() : "null", e);
            // 事务会自动回滚
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<List<PromptsEntity>> selectListByPromptId(String promptId) {
        log.info("执行根据 promptId 查询提示词明细, promptId: {}", promptId);
        try {
            QueryWrapper wrapper = QueryWrapper.create()
                    .select()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_ID.eq(promptId));
            List<PromptsEntity> promptsEntityList = promptsMapper.selectListByQuery(wrapper);

            log.info("查询提示词明细成功, 查到 {} 条子步骤", promptsEntityList.size());
            return ResultData.success(promptsEntityList);
        } catch (Exception e) {
            log.error("根据 promptId 查询提示词明细发生异常, promptId: {}", promptId, e);
            return ResultData.error(e.getMessage());
        }
    }

    @Override
    public ResultData<Page<PromptsVO>> selectListByPage(Integer pageNumber, Integer pageSize) {
        log.info("执行分页查询大提示词, pageNumber: {}, pageSize: {}", pageNumber, pageSize);
        try {
            // 1. 分组查询“大提示词”并进行分页
            QueryWrapper wrapper = QueryWrapper.create()
                    .select(
                            PROMPTS_ENTITY.PROMPT_ID,
                            PROMPTS_ENTITY.USER_ID,
                            PROMPTS_ENTITY.CATEGORY_ID,
                            PROMPTS_ENTITY.STATUS,
                            PROMPTS_ENTITY.IS_PUBLIC,
                            PROMPTS_ENTITY.PROMPT_TITLE
                    )
                    .from(PROMPTS_ENTITY)
                    .groupBy(
                            PROMPTS_ENTITY.PROMPT_ID,
                            PROMPTS_ENTITY.USER_ID,
                            PROMPTS_ENTITY.CATEGORY_ID,
                            PROMPTS_ENTITY.STATUS,
                            PROMPTS_ENTITY.IS_PUBLIC,
                            PROMPTS_ENTITY.PROMPT_TITLE
                    )
                    .orderBy(QueryMethods.max(PROMPTS_ENTITY.CREATE_TIME).desc());

            Page<PromptsVO> page = promptsMapper.paginateAs(pageNumber, pageSize, wrapper, PromptsVO.class);
            List<PromptsVO> records = page.getRecords();

            if (records == null || records.isEmpty()) {
                log.info("分页查询大提示词结果为空");
                return ResultData.success(page);
            }

            log.info("分页查询大提示词主干完成, 当前页有 {} 个大提示词, 准备挂载子步骤", records.size());

            // 2. 批量查询关联的“小提示词”
            List<Long> promptIds = records.stream()
                    .map(PromptsVO::getPromptId)
                    .collect(Collectors.toList());

            QueryWrapper miniWrapper = QueryWrapper.create()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_ID.in(promptIds))
                    .orderBy(PROMPTS_ENTITY.STEP.asc());

            List<PromptsEntity> miniEntities = promptsMapper.selectListByQuery(miniWrapper);
            log.info("成功查询到所有对应的子步骤, 共 {} 条, 准备进行内存组装", miniEntities.size());

            // 3. 内存中将小提示词挂载到对应的大提示词下（分离系统提示词和普通提示词）
            Map<Long, List<miniPromptsTo>> systemMap = new java.util.HashMap<>();
            Map<Long, List<miniPromptsTo>> miniMap = new java.util.HashMap<>();

            for (PromptsEntity entity : miniEntities) {
                miniPromptsTo mini = new miniPromptsTo();
                mini.setId(String.valueOf(entity.getId()));
                mini.setTitle(entity.getTitle());
                mini.setContent(entity.getContent());
                mini.setStep(entity.getStep());

                Long promptId = Long.valueOf(entity.getPromptId());
                if (entity.getStep() != null && entity.getStep() == -1) {
                    // 系统提示词
                    systemMap.computeIfAbsent(promptId, k -> new ArrayList<>()).add(mini);
                } else {
                    // 普通提示词
                    miniMap.computeIfAbsent(promptId, k -> new ArrayList<>()).add(mini);
                }
            }

            for (PromptsVO record : records) {
                record.setSystemPrompts(systemMap.getOrDefault(record.getPromptId(), new ArrayList<>()));
                record.setMiniPrompts(miniMap.getOrDefault(record.getPromptId(), new ArrayList<>()));
            }

            log.info("分页查询大提示词全部流程完成");
            return ResultData.success(page);

        } catch (Exception e) {
            log.error("分页查询大提示词发生异常", e);
            return ResultData.error(e.getMessage());
        }
    }

    /**
     * 根据名字模糊查询
     */
    @Override
    public ResultData<List<PromptsEntity>> selectListByName(String name) {
        log.info("执行根据名字模糊查询, name: {}", name);
        try {
            // 使用 QueryWrapper 构建查询
            QueryWrapper wrapper = QueryWrapper.create()
                    .select()
                    .from(PROMPTS_ENTITY)
                    .where(PROMPTS_ENTITY.PROMPT_TITLE.like("%" + name + "%")
                            .when(name != null && !name.trim().isEmpty()));

            List<PromptsEntity> promptsEntityList = promptsMapper.selectListByQuery(wrapper);

            log.info("查询成功, 查到 {} 条记录", promptsEntityList.size());
            return ResultData.success(promptsEntityList);
        }
        catch (Exception e) {
            log.error("模糊查询发生异常, name: {}", name, e);
            return ResultData.error("查询失败：" + e.getMessage());
        }
    }
}