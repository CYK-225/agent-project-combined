package org.example.repository.dal.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.repository.dal.entity.MailMessageEntity;

/**
 * 邮件消息表 映射层
 * <p>
 * 使用 postgresql-session 数据源，和 agentscope_sessions 同 schema。
 */
@Mapper
@DS("postgresql-session")
public interface MailMessageMapper extends BaseMapper<MailMessageEntity> {
}
