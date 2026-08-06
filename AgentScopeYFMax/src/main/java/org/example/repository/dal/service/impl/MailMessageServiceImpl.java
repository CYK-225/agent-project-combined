package org.example.repository.dal.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.example.repository.dal.entity.MailMessageEntity;
import org.example.repository.dal.mapper.MailMessageMapper;
import org.example.repository.dal.service.IMailMessageService;
import org.springframework.stereotype.Service;

import com.mybatisflex.spring.service.impl.ServiceImpl;

/**
 * 邮件消息表 服务层实现。
 *
 * @see MailMessageEntity
 */
@Service
@DS("postgresql-session")
public class MailMessageServiceImpl extends ServiceImpl<MailMessageMapper, MailMessageEntity> implements IMailMessageService {
}
