package com.cyk.Service;


import com.cyk.Enity.table.MiniPromptsEntity;
import com.cyk.common.ResultData;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 服务层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
public interface IMiniPromptsService extends IService<MiniPromptsEntity> {

    ResultData<String> add(MiniPromptsEntity miniPromptsEntity);

    ResultData<String> update(MiniPromptsEntity miniPromptsEntity);

    ResultData<String> deleteById(Integer id);

    ResultData<Page<MiniPromptsEntity>> selectListByType(String type, int pageNumber, int pageSize, String keyword);

    ResultData<MiniPromptsEntity> selectById(Integer id);

    ResultData<Page<MiniPromptsEntity>> selectPage(int pageNumber, int pageSize, String keyword);

    ResultData<List<MiniPromptsEntity>> selectListByUserId(Integer userId);

    ResultData<List<MiniPromptsEntity>> selectListByCategory(Integer type);
}