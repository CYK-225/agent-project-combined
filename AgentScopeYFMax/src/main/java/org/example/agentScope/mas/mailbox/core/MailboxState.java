package org.example.agentScope.mas.mailbox.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 邮箱状态 — 纯内存对象，不做持久化
 * <p>
 * 持久化完全由 {@link MailboxCenter} 通过 {@code mail_message} 表承担。
 * <p>
 * <h3>内存结构</h3>
 * <ul>
 *   <li>{@code mailboxes}: agentName → 收件箱列表</li>
 *   <li>{@code counters}: agentName → 序号计数器（用于生成 mailId）</li>
 * </ul>
 *
 * <h3>与 MailboxCenter 的协作</h3>
 * <ul>
 *   <li>receive() — MailboxCenter 在线投递时调用（只写内存）</li>
 *   <li>receiveBatch() — MailboxCenter 注册时从 DB 恢复批量加载</li>
 *   <li>mergeCounter() — MailboxCenter 恢复时合并 DB 中已有最大序号</li>
 *   <li>查询类（getUnread / findById 等）— MailTools 直接调用</li>
 * </ul>
 *
 * @see MailboxCenter
 * @see org.example.agentScope.mas.mailbox.tool.MailTools
 */
@Slf4j
@Data
public class MailboxState {

    // ======================== 内存存储 ========================

    /** agentName → 收件箱 */
    private final Map<String, List<Mail>> mailboxes = new ConcurrentHashMap<>();

    /** agentName → 序号计数器（用于生成 alice_1, alice_2 ...） */
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** 当前 Agent 的名称（由框架在创建时设置） */
    private String ownerName;

    // ======================== 写操作（MailboxCenter 调用） ========================

    /**
     * 接收邮件（MailboxCenter 在线投递时调用，只写内存）
     *
     * @return 接收后的 Mail（已设置 ID）
     */
    public Mail receive(Mail mail) {
        Objects.requireNonNull(mail, "邮件不能为空");
        String to = mail.getTo();

        // 如果 mail 没有设置 ID，按规则生成
        if (mail.getId() == null || mail.getId().isBlank()) {
            int seq = nextSeq(to);
            mail.setId(STR."\{mail.getFrom()}_\{seq}");
        }

        mailboxes.computeIfAbsent(to, k -> new CopyOnWriteArrayList<>()).add(mail);
        log.debug("[Mailbox] receive: {} → {} | id={}", mail.getFrom(), to, mail.getId());
        return mail;
    }

    /**
     * 批量接收邮件（MailboxCenter 从 DB 恢复时调用）
     *
     * @return 成功插入的邮件数量
     */
    public int receiveBatch(List<Mail> mails) {
        if (mails == null || mails.isEmpty()) return 0;

        int count = 0;
        for (Mail mail : mails) {
            if (mail.getTo() == null) continue;
            mailboxes.computeIfAbsent(mail.getTo(), k -> new CopyOnWriteArrayList<>()).add(mail);
            count++;
        }
        log.debug("[Mailbox] receiveBatch: 插入 {} 封邮件", count);
        return count;
    }

    /**
     * 合并计数器（MailboxCenter 从 DB 恢复时调用）
     * <p>
     * 取当前值和外部值的最大值，确保后续生成的 ID 不会冲突。
     */
    public void mergeCounter(String agentName, int dbMaxSeq) {
        counters.computeIfAbsent(agentName, k -> new AtomicInteger(0))
                .accumulateAndGet(dbMaxSeq, Math::max);
        log.debug("[Mailbox] mergeCounter: agent={}, maxSeq={}", agentName, dbMaxSeq);
    }

    // ======================== 查询操作（MailTools 调用） ========================

    /**
     * 标记邮件已读
     */
    public void markRead(String mailId, String owner) {
        getInbox(owner).stream()
                .filter(m -> m.getId().equals(mailId))
                .findFirst()
                .ifPresent(m -> {
                    m.setStatus("READ");
                    log.debug("[Mailbox] {} 已标记已读: {}", owner, mailId);
                });
    }

    /**
     * 标记邮件已回复
     */
    public void markReplied(String mailId, String owner) {
        getInbox(owner).stream()
                .filter(m -> m.getId().equals(mailId))
                .findFirst()
                .ifPresent(m -> {
                    m.setStatus("REPLIED");
                    log.debug("[Mailbox] {} 已标记已回复: {}", owner, mailId);
                });
    }

    /**
     * 获取未读邮件
     */
    public List<Mail> getUnread(String agentName) {
        return getInbox(agentName).stream()
                .filter(m -> "UNREAD".equals(m.getStatus()))
                .toList();
    }

    /**
     * 获取全部邮件（只读快照）
     */
    public List<Mail> getAll(String agentName) {
        return List.copyOf(getInbox(agentName));
    }

    /**
     * 按 mailId 查找邮件
     */
    public Optional<Mail> findById(String agentName, String mailId) {
        return getInbox(agentName).stream()
                .filter(m -> m.getId().equals(mailId))
                .findFirst();
    }

    // ======================== 内部工具 ========================

    private List<Mail> getInbox(String agentName) {
        return mailboxes.computeIfAbsent(agentName, k -> new CopyOnWriteArrayList<>());
    }

    /**
     * 获取下一个序号（线程安全）
     */
    private int nextSeq(String agentName) {
        return counters.computeIfAbsent(agentName, k -> new AtomicInteger(0))
                .incrementAndGet();
    }
}
