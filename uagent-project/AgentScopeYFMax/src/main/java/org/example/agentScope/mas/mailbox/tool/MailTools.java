package org.example.agentScope.mas.mailbox.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.agentScope.mas.mailbox.core.Mail;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.mas.mailbox.core.MailboxState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件工具集 — 提供给 LLM 的五个邮件操作工具
 * <p>
 * 设计原则：Agent 主动调用工具读取邮件，系统不自动注入正文。
 * <p>
 * <h3>工具列表</h3>
 * <ol>
 *   <li>{@code list_agents} — 获取当前会话中可通信的 Agent 列表</li>
 *   <li>{@code check_mailbox} — 查看收件箱列表</li>
 *   <li>{@code read_mail} — 读取单封邮件全文（自动标记已读，同步 DB）</li>
 *   <li>{@code send_mail} — 发送邮件（MailboxCenter 路由：在线双写 / 离线写 DB）</li>
 *   <li>{@code reply_mail} — 回复邮件（MailboxCenter 路由）</li>
 *   <li>{@code mark_mail_read} — 标记已读（内存 + DB 同步）</li>
 * </ol>
 *
 * @see MailboxCenter
 * @see MailboxState
 */
@Slf4j
public class MailTools {

    private final MailboxState mailboxState;
    private final MailboxCenter mailboxCenter;
    private final AgentPoolManager agentPoolManager;
    private final String threadId;

    public MailTools(MailboxState mailboxState, MailboxCenter mailboxCenter, AgentPoolManager agentPoolManager, String threadId) {
        this.mailboxState = mailboxState;
        this.mailboxCenter = mailboxCenter;
        this.agentPoolManager = agentPoolManager;
        this.threadId = threadId;
    }

    // ======================== 查看收件箱 ========================

    @Tool(
            name = "check_mailbox",
            description = "查看你的收件箱，返回所有邮件的摘要列表（ID、发件人、主题、状态）。"
                    + "使用 read_mail 查看某封邮件的完整内容。"
    )
    public String checkMailbox() {
        String owner = mailboxState.getOwnerName();
        List<Mail> all = mailboxState.getAll(owner);

        if (all.isEmpty()) {
            return "📭 收件箱为空，没有邮件。";
        }

        long unread = all.stream().filter(m -> "UNREAD".equals(m.getStatus())).count();
        StringBuilder sb = new StringBuilder();
        sb.append(STR."📬 收件箱共 \{all.size()} 封邮件（\{unread} 封未读）：\n");

        for (Mail mail : all) {
            String statusIcon = switch (mail.getStatus()) {
                case "UNREAD" -> "🔵";
                case "READ" -> "⚪";
                case "REPLIED" -> "↩️";
                default -> "📄";
            };
            sb.append(STR."""
                    \{statusIcon} [ID: \{mail.getId()}] \{mail.getSubject()}
                       发件人: \{mail.getFrom()} | 状态: \{mail.getStatus()}
                    """);
        }
        sb.append("\n使用 read_mail 查看邮件详情，reply_mail 回复，mark_mail_read 标记已读。");
        return sb.toString();
    }

    // ======================== 读取邮件（自动已读） ========================

    @Tool(
            name = "read_mail",
            description = "读取指定邮件的完整内容。查看后自动标记为已读。先用 check_mailbox 查看列表。"
    )
    public String readMail(
            @ToolParam(name = "mailId", description = "邮件ID（如 alice_1）", required = true) String mailId
    ) {
        if (mailId == null || mailId.isBlank()) {
            return "[错误] 邮件ID(mailId)不能为空";
        }

        String owner = mailboxState.getOwnerName();
        Mail mail = mailboxState.findById(owner, mailId).orElse(null);
        if (mail == null) {
            return STR."[错误] 未找到邮件: \{mailId}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(STR."""
                📧 邮件详情
                ━━━━━━━━━━━━━━━━━━━━
                ID: \{mail.getId()}
                发件人: \{mail.getFrom()}
                主题: \{mail.getSubject()}
                状态: \{mail.getStatus()}
                \{mail.getInReplyTo() != null ? STR."回复自: \{mail.getInReplyTo()}\n" : ""}
                ─────────────────────
                \{mail.getBody()}
                ━━━━━━━━━━━━━━━━━━━━
                """);

        // 自动标记已读（内存 + DB）
        if ("UNREAD".equals(mail.getStatus())) {
            mailboxCenter.markRead(threadId, owner, mailId);
            sb.append("(已自动标记为已读)\n");
        }

        return sb.toString();
    }

    // ======================== 发送邮件 ========================

    @Tool(
            name = "send_mail",
            description = "发送邮件给其他 Agent。即使目标 Agent 当前不在线，邮件也会被保存。"
    )
    public String sendMail(
            @ToolParam(name = "to", description = "收件人 Agent 名称", required = true) String to,
            @ToolParam(name = "subject", description = "邮件主题", required = true) String subject,
            @ToolParam(name = "body", description = "邮件正文内容", required = true) String body
    ) {
        if (to == null || to.isBlank()) {
            return "[错误] 收件人(to)不能为空";
        }
        if (subject == null || subject.isBlank()) {
            return "[错误] 主题(subject)不能为空";
        }

        String from = mailboxState.getOwnerName();
        Mail mail = mailboxCenter.send(threadId, from, to, subject, body);

        boolean online = mailboxCenter.isOnline(threadId, to);
        if (online) {
            return STR."✅ 邮件已发送至 \{to}（ID: \{mail.getId()}），对方已在线收到。";
        } else {
            return STR."✅ 邮件已发送至 \{to}（ID: \{mail.getId()}），对方当前离线，上线后会自动送达。";
        }
    }

    // ======================== 回复邮件 ========================

    @Tool(
            name = "reply_mail",
            description = "回复收到的邮件。系统自动反转收发方向，只需提供邮件ID和回复内容。"
    )
    public String replyMail(
            @ToolParam(name = "mailId", description = "要回复的邮件ID（如 alice_1）", required = true) String mailId,
            @ToolParam(name = "body", description = "回复内容", required = true) String body
    ) {
        if (mailId == null || mailId.isBlank()) {
            return "[错误] 邮件ID(mailId)不能为空";
        }
        if (body == null || body.isBlank()) {
            return "[错误] 回复内容(body)不能为空";
        }

        String owner = mailboxState.getOwnerName();
        Mail original = mailboxState.findById(owner, mailId).orElse(null);
        if (original == null) {
            return STR."[错误] 未找到邮件: \{mailId}";
        }

        Mail reply = mailboxCenter.reply(threadId, original, body);
        return STR."✅ 已回复 \{original.getFrom()}（回复ID: \{reply.getId()}）";
    }

    // ======================== 标记已读 ========================

    @Tool(
            name = "mark_mail_read",
            description = "将邮件标记为已读。"
    )
    public String markMailRead(
            @ToolParam(name = "mailId", description = "要标记已读的邮件ID（如 alice_1）", required = true) String mailId
    ) {
        if (mailId == null || mailId.isBlank()) {
            return "[错误] 邮件ID(mailId)不能为空";
        }

        String owner = mailboxState.getOwnerName();
        mailboxCenter.markRead(threadId, owner, mailId);
        return STR."✅ 邮件 \{mailId} 已标记为已读";
    }

    // ======================== 查询可用 Agent ========================

    @Tool(
            name = "list_agents",
            description = "获取当前会话中可以发送邮件的 Agent 列表。"
                    + "返回系统中注册的所有 Agent 及其在线状态。"
    )
    public String listAgents(
            @ToolParam(name = "onlineOnly", description = "是否只返回在线的 Agent，默认 false", required = false) Boolean onlineOnly
    ) {
        boolean onlyOnline = Boolean.TRUE.equals(onlineOnly);
        String owner = mailboxState.getOwnerName();

        // 1. 从 MailboxCenter 获取当前在线的 Agent
        Set<String> onlineAgents = mailboxCenter.getOnlineAgents(threadId).stream()
                .collect(Collectors.toSet());

        // 2. 从 AgentPoolManager 获取所有注册的 Agent
        List<String> allRegisteredAgents = agentPoolManager.getMetadataRegistry().keySet().stream()
                .filter(name -> !name.equals(owner))  // 排除自己
                .toList();

        if (onlyOnline) {
            List<String> onlineList = allRegisteredAgents.stream()
                    .filter(onlineAgents::contains)
                    .toList();
            if (onlineList.isEmpty()) {
                return "📭 当前没有在线的 Agent。";
            }
            StringBuilder sb = new StringBuilder("🟢 在线 Agent 列表：\n");
            for (String agent : onlineList) {
                sb.append(STR."  • \{agent}\n");
            }
            return sb.toString();
        }

        // 返回所有 Agent（在线 + 离线）
        if (allRegisteredAgents.isEmpty()) {
            return "📭 系统中没有注册的 Agent。";
        }

        StringBuilder sb = new StringBuilder("📋 可通信的 Agent 列表：\n");
        for (String agent : allRegisteredAgents) {
            boolean isOnline = onlineAgents.contains(agent);
            String status = isOnline ? "🟢 在线" : "⚪ 离线";
            sb.append(STR."  • \{agent} [\{status}]\n");
        }
        sb.append("\n使用 send_mail 发送邮件，即使对方离线也会保存到数据库。");
        return sb.toString();
    }
}
