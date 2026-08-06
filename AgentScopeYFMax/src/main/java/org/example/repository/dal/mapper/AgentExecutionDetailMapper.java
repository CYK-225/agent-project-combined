package org.example.repository.dal.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.repository.dal.entity.AgentExecutionDetailEntity;

/**
 * Agent 执行明细 Mapper（通用）
 * <p>
 * 数据源：postgresql-session
 * 基础 CRUD 方法由 MyBatis-Flex BaseMapper 自动提供
 * </p>
 */
@Mapper
@DS("postgresql-session")
public interface AgentExecutionDetailMapper extends BaseMapper<AgentExecutionDetailEntity> {

}
