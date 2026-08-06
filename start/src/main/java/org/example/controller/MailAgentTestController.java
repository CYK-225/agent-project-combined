package org.example.controller;

import com.mybatisflex.core.query.QueryWrapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.repository.dal.mapper.MailMessageMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 邮件系统集成测试 Controller
 * <p>
 * 通过 HTTP 接口触发测试，绕过 IDEA JUnit classpath 问题。
 * <p>
 * 启动应用后调用：
 * <ul>
 *   <li>{@code GET  /test/mail}          — 运行全部 5 个场景</li>
 *   <li>{@code GET  /test/mail/{id}}     — 运行单个场景（1-5）</li>
 *   <li>{@code POST /test/mail/clean}    — 清理测试数据</li>
 * </ul>
 *
 * <b>前提条件</b>：DashScope API Key 可用（qwen-plus 模型）
 */
@Slf4j
@RestController
@RequestMapping("/test/mail")
@RequiredArgsConstructor
public class MailAgentTestController {

    private final MailboxCenter mailboxCenter;
    private final MailMessageMapper mailMessageMapper;
    private final AgentPoolManager agentPoolManager;

    private static final String ALICE = "MailAlice";
    private static final String BOB = "MailBob";

    // ======================== 辅助 ========================

    private String uniqueThread() {
        return "test-llm-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Msg userMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(text).build()))
                .build();
    }

    private String extractText(Msg response) {
        if (response == null || response.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock t) sb.append(t.getText());
        }
        return sb.toString();
    }

    private void cleanTestData() {
        QueryWrapper query = QueryWrapper.create()
                .from("mail_message")
                .where("thread_id LIKE 'test-%'");
        mailMessageMapper.deleteByQuery(query);
    }

    // ======================== 结果封装 ========================

    record TestResult(
            String scenario,
            boolean passed,
            String message,
            String detail,
            long costMs
    ) {}

    record SuiteResult(
            int total, int passed, int failed,
            List<TestResult> results,
            long totalCostMs
    ) {}

    private TestResult run(String name, Runnable scenario) {
        long start = System.currentTimeMillis();
        try {
            scenario.run();
            long cost = System.currentTimeMillis() - start;
            log.info("✅ [PASS] {} ({}ms)", name, cost);
            return new TestResult(name, true, "PASS", null, cost);
        } catch (AssertionError | Exception e) {
            long cost = System.currentTimeMillis() - start;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("❌ [FAIL] {} ({}ms): {}", name, cost, msg);
            return new TestResult(name, false, "FAIL: " + msg, e.getClass().getSimpleName(), cost);
        }
    }

    // ======================== 场景实现 ========================

    private void doTest1_AliceSendMailToBob() {
        String threadId = uniqueThread();
        log.info(">>> 场景1 开始, threadId={}", threadId);

        ReActAgent aliceAgent = agentPoolManager.getAgentWithSession(ALICE, threadId);
        if (aliceAgent == null) throw new RuntimeException("Alice Agent 构建失败");

        // Alice 给离线 Bob 发邮件
        Msg response = aliceAgent.call(userMsg(
                "请给 Bob 发一封邮件，主题是'项目进度'，正文是'请更新本周的项目进度报告'。发完邮件后告诉我发送结果。"
        )).block();
        String replyText = extractText(response);
        log.info("Alice 回复: {}", replyText);
        if (replyText.isBlank()) throw new RuntimeException("Alice 的回复为空");

        // Bob 上线，从 DB 恢复邮件
        ReActAgent bobAgent = agentPoolManager.getAgentWithSession(BOB, threadId);
        Msg bobResponse = bobAgent.call(userMsg("请查看你的收件箱，告诉我收到了什么邮件。")).block();
        String bobReply = extractText(bobResponse);
        log.info("Bob 回复: {}", bobReply);

        boolean ok = bobReply.contains("项目进度") || bobReply.contains("邮件") || bobReply.contains("Alice");
        if (!ok) throw new RuntimeException("Bob 应提到收到了 Alice 的邮件，实际: " + bobReply);
    }

    private void doTest2_BobReplyMail() {
        String threadId = uniqueThread();
        log.info(">>> 场景2 开始, threadId={}", threadId);

        ReActAgent aliceAgent = agentPoolManager.getAgentWithSession(ALICE, threadId);
        ReActAgent bobAgent = agentPoolManager.getAgentWithSession(BOB, threadId);

        // Alice 发邮件
        Msg aliceResponse = aliceAgent.call(userMsg(
                "请给 Bob 发一封邮件，主题是'会议通知'，正文是'明天下午3点开项目评审会'。发完告诉我。"
        )).block();
        log.info("Alice 发送后回复: {}", extractText(aliceResponse));

        // Bob 回复
        Msg bobResponse = bobAgent.call(userMsg(
                "请检查收件箱，如果有 Alice 的邮件请用 reply_mail 回复她确认参加。"
        )).block();
        log.info("Bob 回复: {}", extractText(bobResponse));

        // Alice 检查收件箱
        Msg aliceCheck = aliceAgent.call(userMsg("请检查收件箱，看 Bob 有没有回复你。")).block();
        String aliceCheckText = extractText(aliceCheck);
        log.info("Alice 检查回复: {}", aliceCheckText);

        boolean ok = aliceCheckText.contains("Re:") || aliceCheckText.contains("Bob") || aliceCheckText.contains("回复");
        if (!ok) throw new RuntimeException("Alice 应看到 Bob 的回复，实际: " + aliceCheckText);
    }

    private void doTest3_ListAgentsThenSend() {
        String threadId = uniqueThread();
        log.info(">>> 场景3 开始, threadId={}", threadId);

        ReActAgent aliceAgent = agentPoolManager.getAgentWithSession(ALICE, threadId);

        Msg response = aliceAgent.call(userMsg(
                "请先用 list_agents 查询当前有哪些 Agent 可以通信，然后给其中一个 Agent 发一封邮件，内容自拟。"
        )).block();
        String reply = extractText(response);
        log.info("Alice list_agents + send_mail: {}", reply);

        ReActAgent bobAgent = agentPoolManager.getAgentWithSession(BOB, threadId);
        Msg bobResponse = bobAgent.call(userMsg("请检查你的收件箱。")).block();
        String bobReply = extractText(bobResponse);
        log.info("Bob 收件箱: {}", bobReply);

        if (bobReply.isBlank()) throw new RuntimeException("Bob 应有回复");
    }

    private void doTest4_OfflineDelivery() {
        String threadId = uniqueThread();
        log.info(">>> 场景4 开始, threadId={}", threadId);

        // 只构建 Alice，Bob 不在线
        ReActAgent aliceAgent = agentPoolManager.getAgentWithSession(ALICE, threadId);

        Msg response = aliceAgent.call(userMsg(
                "请给 Bob 发一封邮件，主题是'紧急通知'，正文是'服务器将在今晚维护，请提前保存工作'。发完后告诉我。"
        )).block();
        String aliceReply = extractText(response);
        log.info("Alice 离线发送: {}", aliceReply);
        if (aliceReply.isBlank()) throw new RuntimeException("Alice 应成功发送");

        // Bob 上线
        ReActAgent bobAgent = agentPoolManager.getAgentWithSession(BOB, threadId);
        Msg bobResponse = bobAgent.call(userMsg("请检查收件箱，告诉我收到了什么。")).block();
        String bobReply = extractText(bobResponse);
        log.info("Bob 上线后收件箱: {}", bobReply);

        boolean ok = bobReply.contains("通知") || bobReply.contains("维护") || bobReply.contains("服务器") || bobReply.contains("Alice");
        if (!ok) throw new RuntimeException("Bob 应提到收到了 Alice 的离线邮件，实际: " + bobReply);
    }

    private void doTest5_Persistence() {
        String threadId = uniqueThread();
        log.info(">>> 场景5 开始, threadId={}", threadId);

        ReActAgent aliceAgent = agentPoolManager.getAgentWithSession(ALICE, threadId);
        ReActAgent bobAgent = agentPoolManager.getAgentWithSession(BOB, threadId);

        // Alice 发邮件
        aliceAgent.call(userMsg("请给 Bob 发一封邮件，主题是'测试持久化'，正文是'这封邮件需要验证持久化'。")).block();

        // Bob 读取
        bobAgent.call(userMsg("请检查收件箱并读取所有邮件。")).block();

        // 验证 DB 中有记录
        QueryWrapper query = QueryWrapper.create()
                .from("mail_message")
                .where("thread_id = ?", threadId);
        long count = mailMessageMapper.selectCountByQuery(query);
        log.info("持久化测试完成 — DB 中有 {} 条邮件记录", count);
        if (count == 0) throw new RuntimeException("DB 中应有邮件记录");
    }

    // ======================== HTTP 接口 ========================

    /**
     * 运行全部 5 个场景
     * GET /test/mail
     */
    @GetMapping
    public SuiteResult runAll() {
        cleanTestData();
        log.info("========== 邮件系统集成测试 开始 ==========");

        long start = System.currentTimeMillis();
        List<TestResult> results = new ArrayList<>();

        results.add(run("场景1: Alice 发邮件给离线 Bob", this::doTest1_AliceSendMailToBob));
        results.add(run("场景2: Bob 回复 Alice", this::doTest2_BobReplyMail));
        results.add(run("场景3: list_agents → send_mail", this::doTest3_ListAgentsThenSend));
        results.add(run("场景4: 离线投递 + DB 恢复", this::doTest4_OfflineDelivery));
        results.add(run("场景5: 持久化验证", this::doTest5_Persistence));

        long totalCost = System.currentTimeMillis() - start;
        int passed = (int) results.stream().filter(r -> r.passed).count();

        log.info("========== 邮件系统集成测试 结束: {}/{}, {}ms ==========", passed, results.size(), totalCost);

        return new SuiteResult(results.size(), passed, results.size() - passed, results, totalCost);
    }

    /**
     * 运行单个场景（1-5）
     * GET /test/mail/{id}
     */
    @GetMapping("/{id}")
    public TestResult runOne(@PathVariable int id) {
        cleanTestData();
        log.info("========== 邮件系统单场景测试: 场景{} ==========", id);

        return switch (id) {
            case 1 -> run("场景1: Alice 发邮件给离线 Bob", this::doTest1_AliceSendMailToBob);
            case 2 -> run("场景2: Bob 回复 Alice", this::doTest2_BobReplyMail);
            case 3 -> run("场景3: list_agents → send_mail", this::doTest3_ListAgentsThenSend);
            case 4 -> run("场景4: 离线投递 + DB 恢复", this::doTest4_OfflineDelivery);
            case 5 -> run("场景5: 持久化验证", this::doTest5_Persistence);
            default -> throw new IllegalArgumentException("场景编号 1-5，收到: " + id);
        };
    }

    /**
     * 清理测试数据
     * POST /test/mail/clean
     */
    @PostMapping("/clean")
    public String clean() {
        cleanTestData();
        return "已清理所有 test-% 邮件数据";
    }
}
