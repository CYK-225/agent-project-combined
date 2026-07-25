package org.example.repository.dal.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.example.repository.dal.entity.AgentTaskEntity;
import org.example.repository.dal.mapper.AgentTaskEntityMapper;
import org.example.repository.dal.service.IAgentTaskEntityService;

/**
 * AgentTaskEntity Service实现
 */
@Slf4j
@Service
public class AgentTaskEntityServiceImpl extends ServiceImpl<AgentTaskEntityMapper, AgentTaskEntity> implements IAgentTaskEntityService {
}