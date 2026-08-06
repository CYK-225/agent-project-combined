package org.example.repository.Graph; // 建议放在 Controller 包下

import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.session.SessionManager;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;

import org.example.agentScope.util.memory.AutoContextMemoryFactory;
import org.example.agentScope.util.modelFactory.DashScopeModelBuilder;
import org.example.agentScope.util.session.PostgresSession;
import org.example.repository.Graph.Entity.KnowledgeNode;

import org.example.repository.Graph.Entity.Subgraph;
import org.example.repository.Graph.Service.KnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 知识图谱功能测试类
 */

@RequiredArgsConstructor
public class test { // 建议类名首字母大写

    private final KnowledgeService knowledgeService;

    @Resource
    private PostgresSession postgresSession;

    @Resource
    private AutoContextMemoryFactory memoryFactory;

    @Resource
    private DashScopeModelBuilder modelBuilder;



    @GetMapping("/test/neo4j")
    public String testNeo4jFunctions() {
        System.out.println("========== 开始 MySQL 部署知识链条存储测试 (新架构) ==========");

        // 1. 定义 MySQL 部署的八个核心步骤节点 ID
        String[] nodeIds = {
                "MySQL_Step_1_Install",
                "MySQL_Step_2_Init",
                "MySQL_Step_3_Config",
                "MySQL_Step_4_Startup",
                "MySQL_Step_5_Root_Security",
                "MySQL_Step_6_User_Create",
                "MySQL_Step_7_Grant_Privileges",
                "MySQL_Step_8_Firewall_Allow"
        };

        // 2. 定义对应的详细操作内容
        String[] contents = {
                "安装阶段：下载并部署 MySQL 8.0 核心二进制程序包，准备运行环境。",
                "初始化阶段：执行 mysqld --initialize 命令初始化数据字典，并获取 root 初始临时密码。",
                "环境配置：编辑 /etc/my.cnf，配置 bind-address, max_connections 以及设置服务器默认字符集为 utf8mb4。",
                "服务管理：使用 systemctl start mysqld 启动数据库服务，并配置开机自启动任务。",
                "安全加固：使用初始密码登录，执行 ALTER USER 更新 root 强密码，并删除匿名用户和测试库。",
                "用户管理：创建名为 'mas_admin' 的业务专用账号，并限制其仅允许从内网网段登录。",
                "权限分配：执行 GRANT ALL PRIVILEGES 命令，授予 'mas_admin' 对核心业务库的所有管理权限。",
                "网络开放：在系统控制层开启 3306 端口访问权限，并配置防火墙策略以允许外部业务连接。"
        };

        StringBuilder resultLog = new StringBuilder();
        resultLog.append("执行详情：\n");

        try {
            // 3. 第一步：存储节点 (直接使用 Entity)
            for (int i = 0; i < nodeIds.length; i++) {
                // 构建实体对象
                KnowledgeNode node = KnowledgeNode.builder()
                        .nodeId(nodeIds[i])
                        .detail(contents[i])
                        .status("Active") // 设置一个默认状态
                        .description("MySQL Deployment Guide") // 设置默认描述
                        .topicId("1111")
                        .build();

                // 调用 Service (Service 内部会自动计算向量并 MERGE)
                knowledgeService.store(node);
                resultLog.append("✅ 已存储节点: ").append(nodeIds[i]).append("\n");
            }

            // 4. 第二步：建立关联 (直接传 String 参数)
            // 现在的 link 接口非常简单：fromId, toId, detail
            for (int i = 0; i < nodeIds.length - 1; i++) {
                String from = nodeIds[i];
                String to = nodeIds[i + 1];
                String relationDetail = "MySQL 部署后续步骤";

                // 调用 Service (Service 会自动处理上下文拼接和向量计算)
                knowledgeService.link(from, to, relationDetail,"1111");

                resultLog.append("🔗 已建立关联: ").append(from)
                        .append(" -> (").append(relationDetail).append(") -> ")
                        .append(to).append("\n");
            }

            // 5. 第三步：验证查询 (可选)
            // 尝试获取从第一个节点开始的子图，深度为 8，验证整条链是否连通
            Subgraph graph = knowledgeService.getGraph(nodeIds[0], 8);
            resultLog.append("\n🔎 验证查询成功: 从 Step_1 检索到 ")
                    .append(graph.getNodes().size()).append(" 个节点，")
                    .append(graph.getEdges().size()).append(" 条边。");

            System.out.println("========== MySQL 知识链条测试结束 ==========");
            return "MySQL 部署流程测试成功！\n" + resultLog.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "测试过程中出现异常: " + e.getMessage();
        }
    }

    @GetMapping("/test/standard-memory")
    public Map<String, Object> testStandardMemory(@RequestParam(defaultValue = "test_standard_001") String sessionId) {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            Model model = modelBuilder.buildDashScopeModel();
            Memory memory = memoryFactory.builder(model).build();

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER) // 使用枚举指定角色
                    .name("user")
                    .textContent("你好，请帮我规划一个商业点位。") // 使用 Builder 提供的 textContent 便捷方法
                    .build();

            Msg assistantMsg = Msg.builder()
                    .role(MsgRole.ASSISTANT) // 使用枚举指定角色
                    .name("assistant")
                    .textContent("好的，请问您关注哪个城市的写字楼？")
                    .build();

            // 将消息加入记忆中
            memory.addMessage(userMsg);
            memory.addMessage(assistantMsg);

            // 3. 使用官方 SessionManager 串联起来
            SessionManager sessionManager = SessionManager.forSessionId(sessionId)
                    .withSession(postgresSession) // 注入您的 PostgreSQL 驱动
                    .addComponent(memory);        // 注册 Memory 模块

            // 4. 执行持久化
            // SessionManager 会调用 memory，memory 会提炼出 List<Msg> 并交给 PostgresSession 保存
            sessionManager.saveSession();
            response.put("save_status", "成功通过 SessionManager 持久化到了 PostgreSQL");

            // ==========================================
            // 模拟系统重启或新请求到达时的恢复过程
            // ==========================================

            // 重新创建一个空的 Memory，同样需要传入 Model
            AutoContextMemory restoredMemory = memoryFactory.builder(model).build();

            // 重新构建 SessionManager
            SessionManager restoreManager = SessionManager.forSessionId(sessionId)
                    .withSession(postgresSession)
                    .addComponent(restoredMemory);

            // 5. 从数据库加载已有会话，这会自动把 Msg 列表装载进 restoredMemory
            restoreManager.loadIfExists();

            // 6. 验证恢复结果
            List<Msg> history = restoredMemory.getMessages();
            response.put("restored_messages", history);
            response.put("message_count", history.size());

        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "测试过程中出现异常: " + e.getMessage());
        }
        return response;
    }


}