package com.cyk.Service;


import com.cyk.Enity.PromptsVO;
import com.cyk.Enity.addPromptsTo;
import com.cyk.Enity.table.PromptsEntity;
import com.cyk.Enity.updatePromptsTo;
import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词管理表 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface IPromptsService extends IService<PromptsEntity> {

    ResultData<String> add(addPromptsTo promptsList);

    ResultData<String> deleteById(Long id);

    @Transactional(rollbackFor = Exception.class)
    ResultData<String> update(updatePromptsTo promptsTo);

    ResultData<List<PromptsEntity>> selectListByPromptId(String promptId);

    ResultData<Page<PromptsVO>> selectListByPage(Integer pageNumber, Integer pageSize);

    ResultData<List<PromptsEntity>> selectListByName(String name);
}