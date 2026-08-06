package org.example.agentScope.mas.mailbox.hook;

import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.mas.mailbox.core.Mail;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.mas.mailbox.core.MailboxState;
import org.example.agentScope.util.hooksManager.AbstractAgentHook;

import java.util.List;

/**
 * 邮件 Hook — 覆盖 Agent 完整生命周期：注册 → 通知 → 注销
 * <p>
 * <h3>三个生命周期锚点</h3>
 * <ul>
 *   <li>{@code handlePreCall}  — Agent 启动，注册到 MailboxCenter，从 DB 恢复历史邮件</li>
 *   <li>{@code handlePreReasoning} — 每轮推理前，检查未读邮件，注入轻量通知</li>
 *   <li>{@code handlePostCall}  — Agent 结束，注销并同步内存状态到 DB</li>
 * </ul>
 *
 * <h3>设计原则：通知而非注入正文</h3>
 * 不把邮件正文塞进上下文（浪费 token），而是通知 Agent "你有 N 封未读"，
 * 让 Agent 自行决定是否调用 {@code check_mailbox} / {@code read_mail} 工具查看。
 *
 * @see MailboxState
 * @see MailboxCenter
 */
@Slf4j
public class MailHook extends AbstractAgentHook {

    private final MailboxState mailboxState;
    private final MailboxCenter mailboxCenter;
    private final String threadId;

    /**
     * @param mailboxState  该 Agent 的内存邮箱
     * @param mailboxCenter 中心化邮局单例
     * @param threadId      当前会话 ID
     */
    public MailHook(MailboxState mailboxState, MailboxCenter mailboxCenter, String threadId) {
        super(3); // 优先级 3，在推理前尽早通知
        this.mailboxState = mailboxState;
        this.mailboxCenter = mailboxCenter;
        this.threadId = threadId;
    }

    // ======================== Agent 启动：注册 ========================

    /**
     * Agent 调用前 — 注册到 MailboxCenter，从 DB 恢复历史邮件
     * <p>
     * 幂等：同一个 MailboxState 实例重复注册会自动跳过。
     */
    @Override
    protected void handlePreCall(PreCallEvent event) {
        String agentName = mailboxState.getOwnerName();
        if (agentName == null || agentName.isBlank()) return;

        // 幂等注册（MailboxCenter 内部有守卫，同一实例不重复加载 DB）
        mailboxCenter.register(threadId, agentName, mailboxState);
        log.debug("[MailHook] 📬 Agent [{}] 已注册, threadId={}", agentName, threadId);
    }

    // ======================== 每轮推理前：通知未读 ========================

    /**
     * 每轮推理前 — 检查未读邮件，注入轻量通知
     */
    @Override
    protected void handlePreReasoning(PreReasoningEvent event) {
        String owner = mailboxState.getOwnerName();
        if (owner == null || owner.isBlank()) return;

        List<Mail> unread = mailboxState.getUnread(owner);
        if (unread.isEmpty()) return;

        String notification = formatNotification(unread);
        log.debug("[MailHook] Agent [{}] 有 {} 封未读邮件", owner, unread.size());

        // 只注入轻量通知，不注入正文
        injectSystemPrompt(event, notification);
    }

    // ======================== Agent 结束：注销 ========================

    /**
     * Agent 调用后 — 注销并同步内存状态到 DB
     * <p>
     * 内存中的邮件状态变更（已读/已回复）会 flush 到 mail_message 表。
     */
    @Override
    protected void handlePostCall(PostCallEvent event) {
        String agentName = mailboxState.getOwnerName();
        if (agentName == null || agentName.isBlank()) return;

        mailboxCenter.unregister(threadId, agentName);
        log.debug("[MailHook] 📭 Agent [{}] 已注销, threadId={}", agentName, threadId);
    }

    // ======================== 工具方法 ========================

    /**
     * 格式化轻量通知（只有 ID + 发件人 + 主题，没有正文）
     */
    private String formatNotification(List<Mail> mails) {
        StringBuilder sb = new StringBuilder();
        sb.append(STR."\n\n📬 你有 \{mails.size()} 封未读邮件：\n");
        for (Mail mail : mails) {
            sb.append(STR."- [\{mail.getId()}] 来自 \{mail.getFrom()}: \{mail.getSubject()}\n");
        }
        sb.append("使用 check_mailbox 查看完整列表，read_mail 阅读具体邮件。\n");
        return sb.toString();
    }
}
