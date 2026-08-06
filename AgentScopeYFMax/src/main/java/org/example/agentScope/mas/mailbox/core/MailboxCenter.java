package org.example.agentScope.mas.mailbox.core;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repository.dal.entity.MailMessageEntity;
import org.example.repository.dal.mapper.MailMessageMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 邮局 — 中心化邮件路由单例 Bean
 * <p>
 * 核心职责：
 * <ol>
 *   <li><b>在线投递</b>：收件人在线 → 写内存 MailboxState + 写 DB（双写）</li>
 *   <li><b>离线落盘</b>：收件人离线 → 只写 DB</li>
 *   <li><b>上线恢复</b>：Agent 注册 → 从 DB 查询历史邮件加载到内存</li>
 *   <li><b>下线保存</b>：Agent 注销 → 内存中的状态变更同步到 DB</li>
 * </ol>
 *
 * <h3>持久化策略：方案 B（在线双写）</h3>
 * <ul>
 *   <li>在线时：内存 + DB 同时写 → 读走内存（快），DB 保证不丢</li>
 *   <li>离线时：只写 DB → 上线时加载</li>
 *   <li>崩溃恢复：DB 是 source of truth，内存是缓存</li>
 * </ul>
 *
 * <h3>隔离维度：threadId + agentName</h3>
 *
 * <h3>生命周期</h3>
 * <pre>
 * Spring 容器启动 → MailboxCenter 单例创建（注入 MailMessageMapper）
 *
 * Agent 上线（buildAgentWithSession 末尾，只执行一次）:
 *   register(threadId, agentName, mailboxState)
 *     └── loadFromDB() → SELECT FROM mail_message WHERE thread_id=? AND to_agent=?
 *         → receiveBatch() + mergeCounter()
 *
 * Agent 运行中:
 *   send() → 在线：receive() + insertDB() [双写]
 *          → 离线：insertDB() [只写 DB]
 *   markRead() → state.markRead() + updateDB() [双写]
 *
 * Agent 下线:
 *   unregister() → flushToDB() → UPDATE 内存状态到 DB → 从路由表移除
 * </pre>
 *
 * @see MailboxState
 * @see org.example.agentScope.mas.mailbox.tool.MailTools
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailboxCenter {

    // ======================== 在线注册表 ========================

    /**
     * 在线邮箱注册表
     * <p>
     * Key: {@code threadId:agentName}
     * Value: 对应 Agent 的 {@link MailboxState} 内存实例
     */
    private final ConcurrentHashMap<String, MailboxState> onlineMailboxes = new ConcurrentHashMap<>();

    /** 邮件 Mapper（MyBatis-Flex，postgresql-session 数据源） */
    private final MailMessageMapper mailMessageMapper;

    // ======================== 注册 / 注销 ========================

    /**
     * Agent 上线注册（只在 buildAgentWithSession 末尾调用一次）
     * <p>
     * 1. 幂等守卫：同一个 MailboxState 实例已注册则跳过<br>
     * 2. 注册到在线路由表<br>
     * 3. 从 DB 加载历史邮件到内存
     *
     * @param threadId     会话 ID
     * @param agentName    Agent 名称
     * @param mailboxState 该 Agent 的内存邮箱
     */
    public void register(String threadId, String agentName, MailboxState mailboxState) {
        Objects.requireNonNull(threadId, "threadId 不能为空");
        Objects.requireNonNull(agentName, "agentName 不能为空");
        Objects.requireNonNull(mailboxState, "mailboxState 不能为空");

        String key = registryKey(threadId, agentName);

        // 幂等注册：放回路由表（无论是否同一实例）
        onlineMailboxes.put(key, mailboxState);

        // 始终从 DB 恢复历史邮件（解决场景：Agent 下线期间收到离线邮件，再次 call() 时需恢复）
        loadFromDB(threadId, agentName, mailboxState);

        log.info("[MailboxCenter] 📬 Agent [{}] 上线注册, threadId={}", agentName, threadId);
    }

    /**
     * Agent 下线注销
     * <p>
     * 1. 将内存中状态变更同步到 DB<br>
     * 2. 从在线路由表移除
     *
     * @param threadId  会话 ID
     * @param agentName Agent 名称
     */
    public void unregister(String threadId, String agentName) {
        String key = registryKey(threadId, agentName);
        MailboxState state = onlineMailboxes.get(key);
        if (state != null) {
            flushToDB(threadId, agentName, state);
            onlineMailboxes.remove(key);
            log.info("[MailboxCenter] 📭 Agent [{}] 下线注销, threadId={}", agentName, threadId);
        }
    }

    /**
     * 按线程注销所有 Agent
     *
     * @param threadId 会话 ID
     * @return 被注销的 Agent 数量
     */
    public int unregisterByThread(String threadId) {
        List<String> keysToRemove = onlineMailboxes.keySet().stream()
                .filter(k -> k.startsWith(threadId + ":"))
                .toList();

        for (String key : keysToRemove) {
            MailboxState state = onlineMailboxes.get(key);
            if (state != null) {
                String agentName = key.substring(threadId.length() + 1);
                flushToDB(threadId, agentName, state);
            }
            onlineMailboxes.remove(key);
        }

        if (!keysToRemove.isEmpty()) {
            log.info("[MailboxCenter] 📭 线程 [{}] 下线，注销 {} 个 Agent", threadId, keysToRemove.size());
        }
        return keysToRemove.size();
    }

    /**
     * 检查目标 Agent 是否在线
     */
    public boolean isOnline(String threadId, String agentName) {
        return onlineMailboxes.containsKey(registryKey(threadId, agentName));
    }

    /**
     * 获取当前在线 Agent 数量
     */
    public int getOnlineCount() {
        return onlineMailboxes.size();
    }

    /**
     * 获取指定会话中所有在线 Agent 的名称列表
     *
     * @param threadId 会话 ID
     * @return 在线 Agent 名称列表
     */
    public List<String> getOnlineAgents(String threadId) {
        String prefix = threadId + ":";
        return onlineMailboxes.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .toList();
    }

    /**
     * 获取指定会话中所有有邮件记录的 Agent（包括在线和离线）
     * <p>
     * 从 DB 查询，去重返回
     *
     * @param threadId 会话 ID
     * @return Agent 名称列表
     */
    public List<String> getAllAgentsInSession(String threadId) {
        // 查询 to_agent（收件人）
        QueryWrapper toQuery = QueryWrapper.create()
                .select("DISTINCT to_agent")
                .from("mail_message")
                .where("thread_id = ?", threadId);
        List<String> toAgents = mailMessageMapper.selectListByQuery(toQuery)
                .stream()
                .map(MailMessageEntity::getToAgent)
                .distinct()
                .toList();

        // 查询 from_agent（发件人）
        QueryWrapper fromQuery = QueryWrapper.create()
                .select("DISTINCT from_agent")
                .from("mail_message")
                .where("thread_id = ?", threadId);
        List<String> fromAgents = mailMessageMapper.selectListByQuery(fromQuery)
                .stream()
                .map(MailMessageEntity::getFromAgent)
                .distinct()
                .toList();

        // 合并去重
        return Stream.concat(toAgents.stream(), fromAgents.stream())
                .distinct()
                .toList();
    }

    // ======================== 邮件路由 ========================

    /**
     * 发送邮件 — 核心路由方法
     * <p>
     * 在线 → 内存 + DB 双写<br>
     * 离线 → 只写 DB
     *
     * @param threadId 会话 ID
     * @param from     发件人
     * @param to       收件人
     * @param subject  主题
     * @param body     正文
     * @return 发送后的 Mail
     */
    public Mail send(String threadId, String from, String to, String subject, String body) {
        Objects.requireNonNull(threadId, "threadId 不能为空");
        Objects.requireNonNull(from, "发件人不能为空");
        Objects.requireNonNull(to, "收件人不能为空");

        // 生成 mailId：查询 DB 中该收件人的最大序号 + 1
        int seq = nextSeqFromDB(threadId, to);
        Mail mail = Mail.create(from, to, subject, body);
        mail.setId(STR."\{from}_\{seq}");

        MailboxState targetState = onlineMailboxes.get(registryKey(threadId, to));

        if (targetState != null) {
            // ===== 在线：内存 + DB 双写 =====
            targetState.receive(mail);
            insertMailToDB(threadId, mail);
            log.info("[MailboxCenter] ✉️ 在线投递: {} → {}, id={}, threadId={}",
                    from, to, mail.getId(), threadId);
        } else {
            // ===== 离线：只写 DB =====
            insertMailToDB(threadId, mail);
            log.info("[MailboxCenter] 📦 离线落盘: {} → {}, id={}, threadId={}",
                    from, to, mail.getId(), threadId);
        }

        return mail;
    }

    /**
     * 回复邮件 — 通过路由机制发送
     */
    public Mail reply(String threadId, Mail original, String body) {
        Objects.requireNonNull(original, "原始邮件不能为空");

        String from = original.getTo();    // 回复方 = 原收件人
        String to = original.getFrom();    // 收件方 = 原发件人

        Mail replyMail = Mail.reply(original, body);
        Mail delivered = send(threadId, from, to, replyMail.getSubject(), body);

        // 补充关联信息到 DB
        if (delivered != null) {
            delivered.setInReplyTo(original.getId());
            delivered.setRequestId(original.getRequestId());
            updateMailInDB(threadId, delivered);
        }

        // 标记原始邮件为已回复（内存 + DB）
        String owner = original.getTo();
        MailboxState senderState = onlineMailboxes.get(registryKey(threadId, owner));
        if (senderState != null) {
            senderState.markReplied(original.getId(), owner);
        }
        updateMailStatusInDB(threadId, original.getId(), "REPLIED");

        return delivered;
    }

    /**
     * 标记邮件已读（内存 + DB 同步）
     */
    public void markRead(String threadId, String agentName, String mailId) {
        MailboxState state = onlineMailboxes.get(registryKey(threadId, agentName));
        if (state != null) {
            state.markRead(mailId, agentName);
        }
        updateMailStatusInDB(threadId, mailId, "READ");
    }

    // ======================== DB 操作 ========================

    /**
     * 从 DB 加载邮件到内存（Agent 上线时调用）
     */
    private void loadFromDB(String threadId, String agentName, MailboxState mailboxState) {
        try {
            QueryWrapper qw = QueryWrapper.create()
                    .where("thread_id = ?", threadId)
                    .and("to_agent = ?", agentName)
                    .orderBy("created_at ASC");

            List<MailMessageEntity> entities = mailMessageMapper.selectListByQuery(qw);
            if (entities == null || entities.isEmpty()) {
                log.debug("[MailboxCenter] DB 无历史邮件: agent={}, threadId={}", agentName, threadId);
                return;
            }

            // Entity → Mail
            List<Mail> mails = entities.stream()
                    .map(this::entityToMail)
                    .toList();

            // 批量加载到内存
            mailboxState.receiveBatch(mails);

            // 合并 DB 中最大序号，防止后续生成的 mailId 冲突
            int maxSeq = mails.stream()
                    .mapToInt(m -> extractSeq(m.getId()))
                    .max().orElse(0);
            mailboxState.mergeCounter(agentName, maxSeq);

            log.info("[MailboxCenter] 📬 从 DB 加载 {} 封邮件: agent={}, threadId={}",
                    mails.size(), agentName, threadId);
        } catch (Exception e) {
            log.error("[MailboxCenter] 从 DB 加载邮件失败: agent={}, threadId={}", agentName, threadId, e);
        }
    }

    /**
     * 将内存邮件状态同步到 DB（Agent 下线时调用）
     */
    private void flushToDB(String threadId, String agentName, MailboxState mailboxState) {
        try {
            List<Mail> allMails = mailboxState.getAll(agentName);
            if (allMails.isEmpty()) return;

            // 同步内存中的状态变更到 DB（在线双写时大部分已在 DB，只更新状态）
            for (Mail mail : allMails) {
                updateMailStatusInDB(threadId, mail.getId(), mail.getStatus());
            }

            log.info("[MailboxCenter] 📬 flushToDB: agent={}, threadId={}, 邮件数={}",
                    agentName, threadId, allMails.size());
        } catch (Exception e) {
            log.error("[MailboxCenter] flushToDB 失败: agent={}, threadId={}", agentName, threadId, e);
        }
    }

    /**
     * 插入邮件到 DB
     */
    private void insertMailToDB(String threadId, Mail mail) {
        try {
            MailMessageEntity entity = mailToEntity(threadId, mail);
            mailMessageMapper.insert(entity);
        } catch (Exception e) {
            log.error("[MailboxCenter] 插入邮件到 DB 失败: mailId={}, threadId={}", mail.getId(), threadId, e);
        }
    }

    /**
     * 更新邮件状态到 DB
     */
    private void updateMailStatusInDB(String threadId, String mailId, String status) {
        try {
            QueryWrapper qw = QueryWrapper.create()
                    .where("thread_id = ?", threadId)
                    .and("mail_id = ?", mailId);

            MailMessageEntity update = new MailMessageEntity();
            update.setStatus(status);
            mailMessageMapper.updateByQuery(update, qw);
        } catch (Exception e) {
            log.warn("[MailboxCenter] 更新邮件状态失败: mailId={}", mailId, e);
        }
    }

    /**
     * 更新邮件完整信息到 DB
     */
    private void updateMailInDB(String threadId, Mail mail) {
        try {
            QueryWrapper qw = QueryWrapper.create()
                    .where("thread_id = ?", threadId)
                    .and("mail_id = ?", mail.getId());

            MailMessageEntity update = new MailMessageEntity();
            update.setInReplyTo(mail.getInReplyTo());
            update.setRequestId(mail.getRequestId());
            update.setStatus(mail.getStatus());
            mailMessageMapper.updateByQuery(update, qw);
        } catch (Exception e) {
            log.warn("[MailboxCenter] 更新邮件信息失败: mailId={}", mail.getId(), e);
        }
    }

    /**
     * 从 DB 获取下一个序号（查询该收件人已有邮件数 + 1）
     */
    private int nextSeqFromDB(String threadId, String agentName) {
        try {
            QueryWrapper countQw = QueryWrapper.create()
                    .where("thread_id = ?", threadId)
                    .and("to_agent = ?", agentName);

            long count = mailMessageMapper.selectCountByQuery(countQw);
            return (int) count + 1;
        } catch (Exception e) {
            log.warn("[MailboxCenter] 查询序号失败，使用时间戳降级", e);
            return (int) (System.currentTimeMillis() % 100000);
        }
    }

    // ======================== Entity ↔ Mail 转换 ========================

    private Mail entityToMail(MailMessageEntity e) {
        return Mail.builder()
                .id(e.getMailId())
                .from(e.getFromAgent())
                .to(e.getToAgent())
                .subject(e.getSubject())
                .body(e.getBody())
                .status(e.getStatus())
                .requestId(e.getRequestId())
                .inReplyTo(e.getInReplyTo())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().getTime() : 0L)
                .build();
    }

    private MailMessageEntity mailToEntity(String threadId, Mail m) {
        return MailMessageEntity.builder()
                .mailId(m.getId())
                .threadId(threadId)
                .fromAgent(m.getFrom())
                .toAgent(m.getTo())
                .subject(m.getSubject())
                .body(m.getBody())
                .status(m.getStatus())
                .requestId(m.getRequestId())
                .inReplyTo(m.getInReplyTo())
                .build();
    }

    // ======================== 辅助方法 ========================

    private String registryKey(String threadId, String agentName) {
        return STR."\{threadId}:\{agentName}";
    }

    /**
     * 从 mailId 中提取序号（如 "alice_3" → 3）
     */
    private int extractSeq(String mailId) {
        if (mailId == null || !mailId.contains("_")) return 0;
        try {
            return Integer.parseInt(mailId.substring(mailId.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
