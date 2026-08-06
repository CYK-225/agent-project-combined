package org.example.mailbox;

import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.example.agentScope.mas.mailbox.core.Mail;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.mas.mailbox.core.MailboxState;
import org.example.repository.dal.mapper.MailMessageMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 邮件系统集成测试 — 测试各种场景
 * <p>
 * 每个测试使用独立的 threadId，避免数据污染。
 */
@SpringBootTest(classes = org.example.Main.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.main.web-application-type=none"
})
public class MailboxIntegrationTest {

    @Resource
    private MailboxCenter mailboxCenter;
    
    @Resource
    private MailMessageMapper mailMessageMapper;

    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";

    /** 每个测试前清理测试数据 */
    @BeforeEach
    void cleanTestData() {
        // 清理测试产生的数据
        QueryWrapper query = QueryWrapper.create()
                .from("mail_message")
                .where("thread_id LIKE 'test-%'");
        // 直接执行删除
        mailMessageMapper.deleteByQuery(query);
    }

    /** 生成唯一 threadId，确保测试隔离 */
    private String uniqueThread() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ======================== 场景 1：基本发邮件（在线投递） ========================

    @Test
    @Order(1)
    @DisplayName("场景1: 基本发邮件 — Alice 在线时 Bob 发邮件给 Alice")
    void testSendMailWhenOnline() {
        String threadId = uniqueThread();

        // 1. 注册 Alice（在线）
        MailboxState aliceState = new MailboxState();
        aliceState.setOwnerName(ALICE);
        mailboxCenter.register(threadId, ALICE, aliceState);

        // 2. 验证 Alice 在线
        assertTrue(mailboxCenter.isOnline(threadId, ALICE), "Alice 应该在线");

        // 3. Bob 发邮件给 Alice
        Mail sent = mailboxCenter.send(threadId, BOB, ALICE, "测试邮件", "你好 Alice，这是一封测试邮件");
        assertNotNull(sent, "发送的邮件不应为空");
        assertNotNull(sent.getId(), "邮件 ID 应该被生成");
        assertTrue(sent.getId().startsWith(BOB + "_"), "邮件 ID 应该以 Bob_ 开头");
        assertEquals("UNREAD", sent.getStatus(), "新邮件状态应该是 UNREAD");

        // 4. 验证 Alice 收件箱有邮件
        List<Mail> unread = aliceState.getUnread(ALICE);
        assertFalse(unread.isEmpty(), "Alice 应该有未读邮件");
        assertEquals(1, unread.size(), "Alice 应该有 1 封未读邮件");
        assertEquals("测试邮件", unread.get(0).getSubject(), "邮件主题应该匹配");
    }

    // ======================== 场景 2：离线投递 + 上线恢复 ========================

    @Test
    @Order(2)
    @DisplayName("场景2: 离线投递 — Bob 发邮件给不在线的 Charlie，Charlie 上线后恢复")
    void testOfflineDeliveryAndRecovery() {
        String threadId = uniqueThread();
        String charlie = "Charlie";

        // 1. 发送邮件给不在线的 Charlie
        Mail sent = mailboxCenter.send(threadId, BOB, charlie, "离线消息", "Charlie 你好，你不在的时候我给你发了消息");
        assertNotNull(sent, "发送的邮件不应为空");
        assertFalse(mailboxCenter.isOnline(threadId, charlie), "Charlie 应该不在线");

        // 2. Charlie 上线（注册）
        MailboxState charlieState = new MailboxState();
        charlieState.setOwnerName(charlie);
        mailboxCenter.register(threadId, charlie, charlieState);

        // 3. 验证 Charlie 从 DB 恢复了邮件
        List<Mail> charlieMails = charlieState.getAll(charlie);
        assertFalse(charlieMails.isEmpty(), "Charlie 上线后应该有邮件");
        assertEquals(1, charlieMails.size(), "Charlie 应该有 1 封邮件");
        assertEquals("离线消息", charlieMails.get(0).getSubject(), "邮件主题应该匹配");
        assertEquals("UNREAD", charlieMails.get(0).getStatus(), "邮件状态应该是 UNREAD");
    }

    // ======================== 场景 3：回复邮件 ========================

    @Test
    @Order(3)
    @DisplayName("场景3: 回复邮件 — Alice 回复 Bob 的邮件")
    void testReplyMail() {
        String threadId = uniqueThread();

        // 1. Alice 先注册上线
        MailboxState aliceState = new MailboxState();
        aliceState.setOwnerName(ALICE);
        mailboxCenter.register(threadId, ALICE, aliceState);

        // 2. Bob 发邮件给 Alice
        Mail original = mailboxCenter.send(threadId, BOB, ALICE, "需要确认", "请确认任务完成情况");

        // 3. Alice 回复 Bob
        Mail reply = mailboxCenter.reply(threadId, original, "任务已完成，请查收");
        assertNotNull(reply, "回复邮件不应为空");
        assertEquals(ALICE, reply.getFrom(), "回复发件人应该是 Alice");
        assertEquals(BOB, reply.getTo(), "回复收件人应该是 Bob");
        assertTrue(reply.getSubject().startsWith("Re: "), "回复主题应该以 Re: 开头");
        assertEquals(original.getId(), reply.getInReplyTo(), "回复应该引用原始邮件 ID");

        // 4. 验证原始邮件状态更新为 REPLIED（在 Alice 的收件箱中）
        Mail originalInAlice = aliceState.getAll(ALICE).stream()
                .filter(m -> m.getSubject().equals("需要确认"))
                .findFirst()
                .orElse(null);
        assertNotNull(originalInAlice, "Alice 应该保留原始邮件");
        assertEquals("REPLIED", originalInAlice.getStatus(), "原始邮件状态应该是 REPLIED");

        // 5. Bob 注册上线，验证收到回复
        MailboxState bobState = new MailboxState();
        bobState.setOwnerName(BOB);
        mailboxCenter.register(threadId, BOB, bobState);

        List<Mail> bobMails = bobState.getAll(BOB);
        assertTrue(bobMails.stream().anyMatch(m -> "Re: 需要确认".equals(m.getSubject())),
                "Bob 应该收到回复邮件");
    }

    // ======================== 场景 4：标记已读 ========================

    @Test
    @Order(4)
    @DisplayName("场景4: 标记已读 — 标记邮件为已读")
    void testMarkMailRead() {
        String threadId = uniqueThread();
        String markReadUser = "MarkReadUser";

        MailboxState userState = new MailboxState();
        userState.setOwnerName(markReadUser);
        mailboxCenter.register(threadId, markReadUser, userState);

        // 1. 发送邮件
        Mail sent = mailboxCenter.send(threadId, BOB, markReadUser, "请已读", "这封邮件请标记已读");

        // 2. 验证有未读邮件
        assertFalse(userState.getUnread(markReadUser).isEmpty(), "应该有未读邮件");

        // 3. 标记已读
        mailboxCenter.markRead(threadId, markReadUser, sent.getId());

        // 4. 验证未读邮件为空
        assertTrue(userState.getUnread(markReadUser).isEmpty(), "标记后应该没有未读邮件");
    }

    // ======================== 场景 5：收件箱查询 ========================

    @Test
    @Order(5)
    @DisplayName("场景5: 收件箱查询 — 查询所有邮件和未读邮件")
    void testInboxQuery() {
        String threadId = uniqueThread();
        String dave = "Dave";

        MailboxState daveState = new MailboxState();
        daveState.setOwnerName(dave);
        mailboxCenter.register(threadId, dave, daveState);

        // 1. 发送多封邮件
        mailboxCenter.send(threadId, ALICE, dave, "邮件1", "内容1");
        Mail mail2 = mailboxCenter.send(threadId, BOB, dave, "邮件2", "内容2");
        mailboxCenter.send(threadId, ALICE, dave, "邮件3", "内容3");

        // 2. 标记第二封为已读
        mailboxCenter.markRead(threadId, dave, mail2.getId());

        // 3. 查询所有邮件
        List<Mail> all = daveState.getAll(dave);
        assertEquals(3, all.size(), "应该有 3 封邮件");

        // 4. 查询未读邮件
        List<Mail> unread = daveState.getUnread(dave);
        assertEquals(2, unread.size(), "应该有 2 封未读邮件");

        // 5. 按 ID 查找
        assertTrue(daveState.findById(dave, mail2.getId()).isPresent(), "应该能找到指定邮件");
    }

    // ======================== 场景 6：跨 Agent 通信 ========================

    @Test
    @Order(6)
    @DisplayName("场景6: 跨 Agent 通信 — 多个 Agent 互相通信")
    void testCrossAgentCommunication() {
        String threadId = uniqueThread();
        String eve = "Eve";

        // 1. Eve 和 Alice 注册
        MailboxState eveState = new MailboxState();
        eveState.setOwnerName(eve);
        mailboxCenter.register(threadId, eve, eveState);

        MailboxState aliceState = new MailboxState();
        aliceState.setOwnerName(ALICE);
        mailboxCenter.register(threadId, ALICE, aliceState);

        // 2. Alice 给 Bob 和 Eve 发邮件
        Mail toBob = mailboxCenter.send(threadId, ALICE, BOB, "群发1", "Bob 收到请回复");
        Mail toEve = mailboxCenter.send(threadId, ALICE, eve, "群发2", "Eve 收到请回复");

        // 3. Bob 注册上线，给 Alice 回复
        MailboxState bobState = new MailboxState();
        bobState.setOwnerName(BOB);
        mailboxCenter.register(threadId, BOB, bobState);

        mailboxCenter.reply(threadId, toBob, "Bob 已收到");

        // 4. 验证各自收件箱
        List<Mail> aliceMails = aliceState.getAll(ALICE);
        assertTrue(aliceMails.stream().anyMatch(m -> "Bob 已收到".equals(m.getBody())),
                "Alice 应该收到 Bob 的回复");

        List<Mail> eveMails = eveState.getAll(eve);
        assertEquals(1, eveMails.size(), "Eve 应该有 1 封邮件");
        assertEquals("群发2", eveMails.get(0).getSubject(), "Eve 的邮件主题应该匹配");
    }

    // ======================== 场景 7：多个 Agent 在同一会话 ========================

    @Test
    @Order(7)
    @DisplayName("场景7: 多个 Agent 在同一会话 — 同一 threadId 下多个 Agent")
    void testMultipleAgentsInSameSession() {
        String threadId = uniqueThread();

        // 1. 创建多个 Agent
        MailboxState agent1State = new MailboxState();
        agent1State.setOwnerName("Agent1");
        mailboxCenter.register(threadId, "Agent1", agent1State);

        MailboxState agent2State = new MailboxState();
        agent2State.setOwnerName("Agent2");
        mailboxCenter.register(threadId, "Agent2", agent2State);

        MailboxState agent3State = new MailboxState();
        agent3State.setOwnerName("Agent3");
        mailboxCenter.register(threadId, "Agent3", agent3State);

        // 2. Agent1 给 Agent2 发邮件
        Mail sent = mailboxCenter.send(threadId, "Agent1", "Agent2", "内部通信", "Agent2 你好");

        // 3. 验证隔离
        List<Mail> agent1Mails = agent1State.getAll("Agent1");
        assertEquals(0, agent1Mails.size(), "Agent1 发件人不在收件箱中");

        List<Mail> agent2Mails = agent2State.getAll("Agent2");
        assertEquals(1, agent2Mails.size(), "Agent2 应该收到邮件");

        List<Mail> agent3Mails = agent3State.getAll("Agent3");
        assertEquals(0, agent3Mails.size(), "Agent3 不应该收到邮件");
    }

    // ======================== 场景 8：Agent 下线注销 ========================

    @Test
    @Order(8)
    @DisplayName("场景8: Agent 下线注销 — 注销后内存状态同步到 DB")
    void testUnregisterAgent() {
        String threadId = uniqueThread();

        // 1. 注册 Agent
        MailboxState testState = new MailboxState();
        testState.setOwnerName("TestAgent");
        mailboxCenter.register(threadId, "TestAgent", testState);

        // 2. 发送邮件
        Mail sent = mailboxCenter.send(threadId, BOB, "TestAgent", "下线测试", "请注销后检查 DB");

        // 3. 验证在线
        assertTrue(mailboxCenter.isOnline(threadId, "TestAgent"), "Agent 应该在线");

        // 4. 标记已读
        mailboxCenter.markRead(threadId, "TestAgent", sent.getId());

        // 5. 注销
        mailboxCenter.unregister(threadId, "TestAgent");

        // 6. 验证离线
        assertFalse(mailboxCenter.isOnline(threadId, "TestAgent"), "Agent 应该离线");

        // 7. 再次注册，验证 DB 中的状态
        MailboxState restoredState = new MailboxState();
        restoredState.setOwnerName("TestAgent");
        mailboxCenter.register(threadId, "TestAgent", restoredState);

        List<Mail> restoredMails = restoredState.getAll("TestAgent");
        assertFalse(restoredMails.isEmpty(), "从 DB 恢复的邮件不应为空");

        Mail restoredMail = restoredMails.get(0);
        assertEquals("READ", restoredMail.getStatus(), "DB 中的邮件状态应该是 READ");
    }
}
